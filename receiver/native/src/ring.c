/*
 * ParkCast native receiver (S3): the bounded event ring and its one delivery thread.
 * docs/design/S3_RECEIVER.md, "Threads" and "JNI contract".
 *
 * UxPlay threads only copy into the ring. One delivery thread, attached to the JVM, pops entries and calls
 * NativeBridge.onNative. Access units (kind 4) and log records (kind 7) use the general slots and are
 * dropped when those are full; the control kinds may also use the reserved slots, so they are not dropped
 * behind a backlog of access units. An entry whose generation is not the current one is never delivered.
 */
#include "parkcast.h"

#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>

#define RING_GENERAL   64
#define RING_RESERVED  8
#define RING_SLOTS     (RING_GENERAL + RING_RESERVED)
#define TEXT_MAX       2048

typedef struct {
    int gen, kind, a, b;
    int64_t pts;
    char *text;
    unsigned char *data;
    int len;
} entry_t;

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_cond = PTHREAD_COND_INITIALIZER;
static entry_t g_q[RING_SLOTS];
static int g_head, g_count, g_open, g_running;
static unsigned g_epoch;
static pthread_t g_thr;

static atomic_int g_gen = 1;
static atomic_uint g_au_drops, g_log_withheld, g_control_drops;

static JavaVM *g_vm;
static jclass g_cls;
static jmethodID g_mid;

int ring_set_target(JavaVM *vm, jclass cls, jmethodID mid)
{
    if (!vm || !cls || !mid) return -1;
    g_vm = vm;
    g_cls = cls;
    g_mid = mid;
    return 0;
}

int ring_generation(void) { return atomic_load(&g_gen); }
void ring_next_generation(void) { atomic_fetch_add(&g_gen, 1); }
uint32_t ring_au_drops(void) { return atomic_load(&g_au_drops); }
uint32_t ring_log_withheld(void) { return atomic_load(&g_log_withheld); }
uint32_t ring_control_drops(void) { return atomic_load(&g_control_drops); }

static void entry_free(entry_t *e)
{
    free(e->text);
    free(e->data);
    e->text = NULL;
    e->data = NULL;
}

/* NewStringUTF takes modified UTF-8. Keep valid two- and three-byte sequences (no surrogates, no NUL);
   anything else becomes '?'. The copy is capped at TEXT_MAX bytes. */
static char *sanitise(const char *s)
{
    size_t n = strlen(s), o = 0, i = 0;
    if (n > TEXT_MAX) n = TEXT_MAX;
    char *out = malloc(n + 1);
    if (!out) return NULL;
    while (i < n) {
        unsigned char c = (unsigned char)s[i];
        if (c < 0x80) {
            out[o++] = (char)c;
            i++;
        } else if ((c & 0xE0) == 0xC0 && c >= 0xC2 && i + 1 < n
                   && ((unsigned char)s[i + 1] & 0xC0) == 0x80) {
            out[o++] = s[i];
            out[o++] = s[i + 1];
            i += 2;
        } else if ((c & 0xF0) == 0xE0 && i + 2 < n
                   && ((unsigned char)s[i + 1] & 0xC0) == 0x80 && ((unsigned char)s[i + 2] & 0xC0) == 0x80
                   && !(c == 0xE0 && (unsigned char)s[i + 1] < 0xA0)
                   && !(c == 0xED && (unsigned char)s[i + 1] >= 0xA0)) {
            out[o++] = s[i];
            out[o++] = s[i + 1];
            out[o++] = s[i + 2];
            i += 3;
        } else {
            out[o++] = '?';
            i++;
        }
    }
    out[o] = '\0';
    return out;
}

int ring_push(int gen, int kind, int a, int b, int64_t pts_ms, const char *text,
              const unsigned char *data, int len)
{
    if (gen != atomic_load(&g_gen)) return 0;
    entry_t e = { gen, kind, a, b, pts_ms, NULL, NULL, 0 };
    if (text && !(e.text = sanitise(text))) return 0;
    if (data && len > 0) {
        if (!(e.data = malloc((size_t)len))) {
            entry_free(&e);
            return 0;
        }
        memcpy(e.data, data, (size_t)len);
        e.len = len;
    }
    int bulk = (kind == PC_EV_AU || kind == PC_EV_LOG);
    pthread_mutex_lock(&g_lock);
    if (!g_open || gen != atomic_load(&g_gen)) {
        pthread_mutex_unlock(&g_lock);
        entry_free(&e);
        return 0;
    }
    if (g_count >= (bulk ? RING_GENERAL : RING_SLOTS)) {
        pthread_mutex_unlock(&g_lock);
        if (kind == PC_EV_AU) atomic_fetch_add(&g_au_drops, 1);
        else if (kind == PC_EV_LOG) atomic_fetch_add(&g_log_withheld, 1);
        else atomic_fetch_add(&g_control_drops, 1);
        entry_free(&e);
        return 0;
    }
    g_q[(g_head + g_count) % RING_SLOTS] = e;
    g_count++;
    pthread_cond_signal(&g_cond);
    pthread_mutex_unlock(&g_lock);
    return 1;
}

static void deliver(JNIEnv *env, entry_t *e)
{
    jstring text = NULL;
    jbyteArray data = NULL;
    if (e->text) text = (*env)->NewStringUTF(env, e->text);
    if ((*env)->ExceptionCheck(env)) goto out;
    if (e->data) {
        data = (*env)->NewByteArray(env, e->len);
        if (!data || (*env)->ExceptionCheck(env)) goto out;
        (*env)->SetByteArrayRegion(env, data, 0, e->len, (const jbyte *)e->data);
        if ((*env)->ExceptionCheck(env)) goto out;
    }
    (*env)->CallStaticVoidMethod(env, g_cls, g_mid, (jint)e->gen, (jint)e->kind, (jint)e->a, (jint)e->b,
                                 (jlong)e->pts, text, data);
out:
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (text) (*env)->DeleteLocalRef(env, text);
    if (data) (*env)->DeleteLocalRef(env, data);
}

static void *delivery_main(void *arg)
{
    unsigned epoch = (unsigned)(uintptr_t)arg;
    JNIEnv *env = NULL;
    JavaVMAttachArgs args = { JNI_VERSION_1_6, "parkcast-deliver", NULL };
    if ((*g_vm)->AttachCurrentThread(g_vm, &env, &args) != JNI_OK)
        return NULL; /* nothing is delivered; ring_close still joins this thread */
    for (;;) {
        pthread_mutex_lock(&g_lock);
        while (g_open && g_epoch == epoch && g_count == 0)
            pthread_cond_wait(&g_cond, &g_lock);
        if (!g_open || g_epoch != epoch) {
            pthread_mutex_unlock(&g_lock);
            break;
        }
        entry_t e = g_q[g_head];
        memset(&g_q[g_head], 0, sizeof(entry_t));
        g_head = (g_head + 1) % RING_SLOTS;
        g_count--;
        pthread_mutex_unlock(&g_lock);
        if (e.gen == atomic_load(&g_gen)) deliver(env, &e);
        entry_free(&e);
    }
    (*g_vm)->DetachCurrentThread(g_vm);
    return NULL;
}

static __thread int t_is_delivery;

static void *delivery_entry(void *arg)
{
    t_is_delivery = 1;
    return delivery_main(arg);
}

int ring_on_delivery_thread(void) { return t_is_delivery; }

int ring_start(void)
{
    if (!g_vm || !g_cls || !g_mid) return -1;
    pthread_mutex_lock(&g_lock);
    if (g_running) {
        g_open = 1;
        pthread_mutex_unlock(&g_lock);
        return 0;
    }
    g_open = 1;
    g_head = g_count = 0;
    if (pthread_create(&g_thr, NULL, delivery_entry, (void *)(uintptr_t)g_epoch) != 0) {
        g_open = 0;
        pthread_mutex_unlock(&g_lock);
        return -1;
    }
    g_running = 1;
    pthread_mutex_unlock(&g_lock);
    return 0;
}

void ring_close(void)
{
    pthread_mutex_lock(&g_lock);
    int running = g_running;
    pthread_t thr = g_thr;
    g_open = 0;
    g_epoch++;
    g_running = 0;
    while (g_count > 0) {
        entry_free(&g_q[g_head]);
        g_head = (g_head + 1) % RING_SLOTS;
        g_count--;
    }
    g_head = 0;
    pthread_cond_broadcast(&g_cond);
    pthread_mutex_unlock(&g_lock);
    if (!running) return;
    if (pthread_equal(thr, pthread_self())) pthread_detach(thr);
    else pthread_join(thr, NULL);
}

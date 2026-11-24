/*
 * Native test harness for 16_receiver_diagnostics.feature (S3 revision 10, AIR-085 to AIR-088).
 *
 * tests/steps/receiver_diag_native_steps.py builds it into a DLL for this PC with MSYS2's gcc, together with
 * the APK's own receiver/native/src/logpath.c and rawdiag.c (each force-including harness_wrap.h) and
 * harness_hooks.c. ring.c is included here unchanged, so the harness can reach the ring's queue. It supplies
 * a fake clock and a fake file layer through rawdiag's test seams; a journal of every native-path ring push,
 * every harness callback entry and return and every JNI call; a fake JavaVM and JNIEnv for the delivery
 * thread; and fixture events put straight into the ring's queue, where no count measures them.
 */
#define HARNESS_NO_RING_WRAP
#include "harness_wrap.h"
#include "../../receiver/native/src/ring.c"
#include "logpath.h"
#include "rawdiag.h"

#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>

int harness_held_only(void *m);
int harness_held_count(void);

#define FIXTURE_KIND 99

/* ---- sequence numbers and thread ids ---- */

static atomic_long g_seq;
static atomic_int g_tids;
static __thread int t_tid;

static long next_seq(void) { return atomic_fetch_add(&g_seq, 1) + 1; }
int harness_thread_id(void)
{
    if (!t_tid) t_tid = atomic_fetch_add(&g_tids, 1) + 1;
    return t_tid;
}

/* ---- the fake clock ---- */

static atomic_llong g_now = 1000;
static int64_t fake_clock(void) { return atomic_load(&g_now); }
void harness_set_clock(long long ms) { atomic_store(&g_now, ms); }

/* ---- the fake file layer ---- */

#define FS_MAX (18L * 1024 * 1024)
#define FOP_MAX 8192
#define FOP_OPEN 1
#define FOP_WRITE 2
#define FOP_CLOSE 3

typedef struct {
    int type, tid, held_only_raw, held_n;
    long seq0, seq1, len, result, off;
    int flags, mode;
    char path[256];
} fop_t;

static unsigned char *g_fs;
static long g_fs_len;
static fop_t g_fops[FOP_MAX];
static int g_nfops, g_fops_lost;
static int g_opens, g_writes;
static int g_fail_open_n, g_fail_write_n, g_fail_write_mode, g_fail_all_writes;
static int g_write_delay_ms;
static atomic_int g_in_raw;

static fop_t *fop_new(int type)
{
    if (g_nfops >= FOP_MAX) {
        g_fops_lost++;
        return NULL;
    }
    fop_t *f = &g_fops[g_nfops++];
    memset(f, 0, sizeof(*f));
    f->type = type;
    f->tid = harness_thread_id();
    f->held_only_raw = harness_held_only(rawdiag_mutex_addr());
    f->held_n = harness_held_count();
    return f;
}

static int fake_open(const char *path, int flags, int mode)
{
    atomic_fetch_add(&g_in_raw, 1);
    long s0 = next_seq();
    int n = ++g_opens;
    int r = (g_fail_open_n && n == g_fail_open_n) ? -1 : 3;
    if (r >= 0 && (flags & O_TRUNC)) g_fs_len = 0;
    fop_t *f = fop_new(FOP_OPEN);
    if (f) {
        f->seq0 = s0;
        snprintf(f->path, sizeof f->path, "%s", path ? path : "(null)");
        f->flags = flags;
        f->mode = mode;
        f->result = r;
        f->seq1 = next_seq();
    }
    atomic_fetch_sub(&g_in_raw, 1);
    return r;
}

static long fake_write(int fd, const void *buf, size_t len)
{
    atomic_fetch_add(&g_in_raw, 1);
    long s0 = next_seq();
    int n = ++g_writes;
    long off = g_fs_len;
    if (g_write_delay_ms) usleep((useconds_t)g_write_delay_ms * 1000);
    long r = (long)len;
    if (fd != 3) r = -1;
    else if (g_fail_all_writes) r = -1;
    else if (g_fail_write_n && n == g_fail_write_n) r = (g_fail_write_mode == 1) ? -1 : (long)len - 1;
    if (r > 0) {
        long room = FS_MAX - g_fs_len;
        long k = r < room ? r : room;
        memcpy(g_fs + g_fs_len, buf, (size_t)k);
        g_fs_len += k;
    }
    fop_t *f = fop_new(FOP_WRITE);
    if (f) {
        f->seq0 = s0;
        f->len = (long)len;
        f->result = r;
        f->off = off;
        f->seq1 = next_seq();
    }
    atomic_fetch_sub(&g_in_raw, 1);
    return r;
}

static int fake_close(int fd)
{
    atomic_fetch_add(&g_in_raw, 1);
    fop_t *f = fop_new(FOP_CLOSE);
    if (f) {
        f->seq0 = f->seq1 = next_seq();
        f->result = fd;
    }
    atomic_fetch_sub(&g_in_raw, 1);
    return 0;
}

static const rawdiag_fileops_t g_fake_ops = { fake_open, fake_write, fake_close };

/* Failure injection: the nth open (1-based) fails; the nth write fails (mode 1) or writes short (mode 2). */
void harness_fail_open(int n) { g_fail_open_n = n; }
void harness_fail_write(int n, int mode)
{
    g_fail_write_n = n;
    g_fail_write_mode = mode;
}
void harness_fail_all_writes(int on) { g_fail_all_writes = on; }
void harness_fs_working(void) { g_fail_open_n = g_fail_write_n = g_fail_write_mode = g_fail_all_writes = 0; }
void harness_set_write_delay(int ms) { g_write_delay_ms = ms; }
long harness_fs_len(void) { return g_fs_len; }
const unsigned char *harness_fs_data(void) { return g_fs; }
int harness_fop_count(void) { return g_nfops; }
int harness_fops_lost(void) { return g_fops_lost; }
int harness_fop(int i, int *type, int *tid, int *held_only_raw, int *held_n, long *seq0, long *seq1, long *len,
                long *result, long *off, int *flags, int *mode, char *path, int path_len)
{
    if (i < 0 || i >= g_nfops) return -1;
    fop_t *f = &g_fops[i];
    *type = f->type;
    *tid = f->tid;
    *held_only_raw = f->held_only_raw;
    *held_n = f->held_n;
    *seq0 = f->seq0;
    *seq1 = f->seq1;
    *len = f->len;
    *result = f->result;
    *off = f->off;
    *flags = f->flags;
    *mode = f->mode;
    snprintf(path, (size_t)path_len, "%s", f->path);
    return 0;
}
int harness_flag_trunc(void) { return O_TRUNC; }
int harness_flag_creat(void) { return O_CREAT; }
int harness_flag_wronly(void) { return O_WRONLY; }
int harness_flag_accmode(void) { return O_WRONLY | O_RDWR | O_RDONLY; }

/* ---- the journal: native-path ring pushes, callback entry and return, JNI calls ---- */

#define J_PUSH 1
#define J_CB_ENTER 2
#define J_CB_RETURN 3
#define J_JNI 4
#define J_MAX 8192

typedef struct {
    int type, tid, kind, a, ret, in_raw;
    long seq;
    char text[160];
} jent_t;

/* A spin lock, not a pthread mutex: the journal's lock is not one the scenarios ask about. */
static atomic_flag g_jlock = ATOMIC_FLAG_INIT;
static jent_t g_j[J_MAX];
static int g_nj;

static void journal(int type, int kind, int a, int ret, const char *text)
{
    jent_t e;
    memset(&e, 0, sizeof e);
    e.type = type;
    e.tid = harness_thread_id();
    e.kind = kind;
    e.a = a;
    e.ret = ret;
    e.in_raw = atomic_load(&g_in_raw);
    if (text) snprintf(e.text, sizeof e.text, "%s", text);
    e.seq = next_seq();
    while (atomic_flag_test_and_set(&g_jlock)) {
    }
    if (g_nj < J_MAX) g_j[g_nj++] = e;
    atomic_flag_clear(&g_jlock);
}

int harness_ring_push(int gen, int kind, int a, int b, int64_t pts_ms, const char *text,
                      const unsigned char *data, int len)
{
    int r = ring_push(gen, kind, a, b, pts_ms, text, data, len);
    journal(J_PUSH, kind, a, r, text);
    return r;
}

int harness_journal_count(void)
{
    while (atomic_flag_test_and_set(&g_jlock)) {
    }
    int n = g_nj;
    atomic_flag_clear(&g_jlock);
    return n;
}
int harness_journal(int i, int *type, int *tid, int *kind, int *a, int *ret, int *in_raw, long *seq, char *text,
                    int text_len)
{
    if (i < 0 || i >= harness_journal_count()) return -1;
    jent_t *e = &g_j[i];
    *type = e->type;
    *tid = e->tid;
    *kind = e->kind;
    *a = e->a;
    *ret = e->ret;
    *in_raw = e->in_raw;
    *seq = e->seq;
    snprintf(text, (size_t)text_len, "%s", e->text);
    return 0;
}

/* ---- the log callback, on a harness thread or on the caller's ---- */

typedef struct {
    int level;
    const char *text;
    int tid;
} cbarg_t;

static void *cb_thread(void *p)
{
    cbarg_t *c = p;
    c->tid = harness_thread_id();
    journal(J_CB_ENTER, 0, c->level, 0, NULL);
    lp_log(c->level, c->text);
    journal(J_CB_RETURN, 0, c->level, 0, NULL);
    return NULL;
}

/* Calls the native log path's callback entry on a new thread and joins it. Returns that thread's id. */
int harness_log_on_thread(int level, const char *text)
{
    cbarg_t c = { level, text, 0 };
    pthread_t t;
    if (pthread_create(&t, NULL, cb_thread, &c) != 0) return -1;
    pthread_join(t, NULL);
    return c.tid;
}

/* The callback entry on the calling thread, journalled the same way. */
void harness_log(int level, const char *text)
{
    journal(J_CB_ENTER, 0, level, 0, NULL);
    lp_log(level, text);
    journal(J_CB_RETURN, 0, level, 0, NULL);
}

/* ---- the fake JavaVM and JNIEnv ---- */

static struct JNINativeInterface g_envtab;
static struct JNIInvokeInterface g_vmtab;
static JNIEnv g_env = &g_envtab;
static JavaVM g_fakevm = &g_vmtab;
static atomic_int g_pending, g_filter_failing;
static atomic_int g_attached, g_csvm_calls, g_jni_calls, g_jni_in_raw;

static void jni_note(const char *what)
{
    atomic_fetch_add(&g_jni_calls, 1);
    if (atomic_load(&g_in_raw) > 0) atomic_fetch_add(&g_jni_in_raw, 1);
    journal(J_JNI, 0, 0, 0, what);
}

static jstring j_NewStringUTF(JNIEnv *env, const char *s)
{
    (void)env;
    (void)s;
    jni_note("NewStringUTF");
    return (jstring)(uintptr_t)0x1000;
}
static jboolean j_ExceptionCheck(JNIEnv *env)
{
    (void)env;
    jni_note("ExceptionCheck");
    return atomic_load(&g_pending) ? JNI_TRUE : JNI_FALSE;
}
static void j_ExceptionClear(JNIEnv *env)
{
    (void)env;
    jni_note("ExceptionClear");
    atomic_store(&g_pending, 0);
}
static jbyteArray j_NewByteArray(JNIEnv *env, jsize n)
{
    (void)env;
    (void)n;
    jni_note("NewByteArray");
    return (jbyteArray)(uintptr_t)0x2000;
}
static void j_SetByteArrayRegion(JNIEnv *env, jbyteArray a, jsize s, jsize n, const jbyte *b)
{
    (void)env;
    (void)a;
    (void)s;
    (void)n;
    (void)b;
    jni_note("SetByteArrayRegion");
}
static void j_CallStaticVoidMethod(JNIEnv *env, jclass c, jmethodID m, ...)
{
    (void)env;
    (void)c;
    (void)m;
    jni_note("CallStaticVoidMethod");
    atomic_fetch_add(&g_csvm_calls, 1);
    if (atomic_load(&g_filter_failing)) atomic_store(&g_pending, 1); /* the Java filter threw */
}
static void j_DeleteLocalRef(JNIEnv *env, jobject o)
{
    (void)env;
    (void)o;
    jni_note("DeleteLocalRef");
}
static jint j_Attach(JavaVM *vm, JNIEnv **penv, void *args)
{
    (void)vm;
    (void)args;
    *penv = &g_env;
    jni_note("AttachCurrentThread");
    atomic_store(&g_attached, 1);
    return JNI_OK;
}
static jint j_Detach(JavaVM *vm)
{
    (void)vm;
    jni_note("DetachCurrentThread");
    atomic_store(&g_attached, 0);
    return JNI_OK;
}

/* Sets the ring's target to the fake JVM and starts the delivery thread. 0 when it attached within 5 s. */
int harness_start_delivery(void)
{
    g_envtab.NewStringUTF = j_NewStringUTF;
    g_envtab.ExceptionCheck = j_ExceptionCheck;
    g_envtab.ExceptionClear = j_ExceptionClear;
    g_envtab.NewByteArray = j_NewByteArray;
    g_envtab.SetByteArrayRegion = j_SetByteArrayRegion;
    g_envtab.CallStaticVoidMethod = j_CallStaticVoidMethod;
    g_envtab.DeleteLocalRef = j_DeleteLocalRef;
    g_vmtab.AttachCurrentThread = j_Attach;
    g_vmtab.DetachCurrentThread = j_Detach;
    if (ring_set_target(&g_fakevm, (jclass)(uintptr_t)0x10, (jmethodID)(uintptr_t)0x20) != 0) return -1;
    if (ring_start() != 0) return -1;
    for (int i = 0; i < 500 && !atomic_load(&g_attached); i++) usleep(10000);
    return atomic_load(&g_attached) ? 0 : -1;
}
void harness_set_filter_failing(int on) { atomic_store(&g_filter_failing, on); }
int harness_csvm_calls(void) { return atomic_load(&g_csvm_calls); }
int harness_jni_calls(void) { return atomic_load(&g_jni_calls); }
int harness_jni_in_raw(void) { return atomic_load(&g_jni_in_raw); }

/* ---- the ring: open with no delivery thread, generation, fixture events, the queue ---- */

void harness_ring_open_stopped(void)
{
    pthread_mutex_lock(&g_lock);
    g_open = 1;
    g_head = g_count = 0;
    pthread_mutex_unlock(&g_lock);
}

void harness_set_generation(int g) { atomic_store(&g_gen, g); }
int harness_generation(void) { return atomic_load(&g_gen); }
unsigned harness_au_drops(void) { return ring_au_drops(); }
unsigned harness_log_drops(void) { return ring_log_withheld(); }
unsigned harness_control_drops(void) { return ring_control_drops(); }

/* Puts fixture events into the queue, uncounted, until it holds `target` entries. Returns the count. */
int harness_fixture_fill(int target)
{
    pthread_mutex_lock(&g_lock);
    while (g_count < target && g_count < RING_SLOTS) {
        entry_t e = { atomic_load(&g_gen), FIXTURE_KIND, 0, 0, 0, NULL, NULL, 0 };
        g_q[(g_head + g_count) % RING_SLOTS] = e;
        g_count++;
    }
    int n = g_count;
    pthread_mutex_unlock(&g_lock);
    return n;
}

/* Removes every fixture event, keeping the other entries in order. Returns the count left. */
int harness_fixture_empty(void)
{
    entry_t keep[RING_SLOTS];
    int k = 0;
    pthread_mutex_lock(&g_lock);
    for (int i = 0; i < g_count; i++) {
        entry_t *e = &g_q[(g_head + i) % RING_SLOTS];
        if (e->kind == FIXTURE_KIND) entry_free(e);
        else keep[k++] = *e;
        memset(e, 0, sizeof(*e));
    }
    for (int i = 0; i < k; i++) g_q[i] = keep[i];
    g_head = 0;
    g_count = k;
    pthread_mutex_unlock(&g_lock);
    return k;
}

int harness_ring_count(void)
{
    pthread_mutex_lock(&g_lock);
    int n = g_count;
    pthread_mutex_unlock(&g_lock);
    return n;
}

/* The ith queued entry (0 = oldest). text and data point into the queue; read them before the next call. */
int harness_ring_entry(int i, int *gen, int *kind, int *a, int *b, long long *pts, const char **text,
                       const unsigned char **data, int *len)
{
    pthread_mutex_lock(&g_lock);
    if (i < 0 || i >= g_count) {
        pthread_mutex_unlock(&g_lock);
        return -1;
    }
    entry_t *e = &g_q[(g_head + i) % RING_SLOTS];
    *gen = e->gen;
    *kind = e->kind;
    *a = e->a;
    *b = e->b;
    *pts = e->pts;
    *text = e->text;
    *data = e->data;
    *len = e->len;
    pthread_mutex_unlock(&g_lock);
    return 0;
}

/* ---- reset ---- */

/* Back to a fresh app start: ring closed and empty, counts zero, generation 1, the native path's video state
   cleared, the raw-file module closed with its state cleared, the fake file layer empty and working. */
int harness_reset(void)
{
    ring_close();
    atomic_store(&g_au_drops, 0);
    atomic_store(&g_log_withheld, 0);
    atomic_store(&g_control_drops, 0);
    atomic_store(&g_gen, 1);
    atomic_store(&g_filter_failing, 0);
    atomic_store(&g_pending, 0);
    atomic_store(&g_csvm_calls, 0);
    atomic_store(&g_jni_calls, 0);
    atomic_store(&g_jni_in_raw, 0);
    lp_reset_video();
    if (!g_fs && !(g_fs = malloc((size_t)FS_MAX))) return -1;
    rawdiag_set_fileops(&g_fake_ops);
    rawdiag_set_clock(fake_clock);
    harness_fs_working();
    g_write_delay_ms = 0;
    rawdiag_open(NULL); /* closes any raw file left from an earlier scenario; opens nothing */
    g_fs_len = 0;
    g_nfops = g_fops_lost = 0;
    g_opens = g_writes = 0;
    g_nj = 0;
    atomic_store(&g_now, 1000);
    return 0;
}

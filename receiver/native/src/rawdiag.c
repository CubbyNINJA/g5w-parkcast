/*
 * ParkCast native receiver (S3 revision 10): the diagnostic raw log, uxplay-raw.log (AIR-085 to AIR-088).
 * docs/design/S3_RECEIVER.md, "Diagnostic raw log".
 *
 * Every entry point runs on its caller's thread (logpath.c), takes only g_mu, never the ring lock, never calls
 * Java and never waits for delivery. The ring's drop counts are read through ring.c's atomic getters.
 * With PARKCAST_RAW_DIAG 0 every entry point is a no-op and no file is touched (AIR-085(e)).
 */
#include "rawdiag.h"

#include <pthread.h>

#if PARKCAST_RAW_DIAG

#include "parkcast.h"

#include <errno.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#ifdef _WIN32
#include <io.h> /* the host test harness only: open() lives here under MinGW */
#endif

/* Found by the build check in the APK's libparkcast.so (AIR-084); read in rawdiag_open so it is kept. */
__attribute__((used)) static const char RAWDIAG_MARKER[] = "parkcast raw diagnostics on";

#define RAWDIAG_FILE "/uxplay-raw.log"
#define REDACTED "[redacted]"
#define REDACTED_LEN 10

typedef struct {
    int (*open)(const char *path, int flags, int mode);
    long (*write)(int fd, const void *buf, size_t len);
    int (*close)(int fd);
} fileops_t;

static int real_open(const char *path, int flags, int mode) { return open(path, flags, mode); }
static long real_write(int fd, const void *buf, size_t len) { return (long)write(fd, buf, len); }
static int real_close(int fd) { return close(fd); }

static int64_t real_clock(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static pthread_mutex_t g_mu = PTHREAD_MUTEX_INITIALIZER;
static fileops_t g_ops = { real_open, real_write, real_close };
static int64_t (*g_clock)(void) = real_clock;

/* All of the state below is guarded by g_mu. */
static int g_fd = -1;
static bool g_on;            /* writing allowed: opened, no failure, no final line yet */
static long g_len;           /* bytes in the file */
static int64_t g_last;       /* uptime of the last counter line */
static char *g_key;          /* copy of the key file's path, for redaction only (never opened) */
static const char *g_base;   /* its name, inside g_key */
static unsigned long long g_au_in, g_csd_sent, g_raw_written, g_raw_dropped;
static uint32_t g_snap_au, g_snap_log, g_snap_ctl;

static void stop_file(void)
{
    if (g_fd >= 0) g_ops.close(g_fd);
    g_fd = -1;
    g_on = false;
}

/* One line in one write call; a failed or short write ends raw writes for this app start (AIR-088). */
static void put(const char *buf, size_t len)
{
    long r = g_ops.write(g_fd, buf, len);
    if (r < 0 || (size_t)r != len) { stop_file(); return; }
    g_len += (long)len;
}

static int counter_fields(char *buf, size_t cap)
{
    return snprintf(buf, cap, "gen=%d au_in=%llu csd_sent=%llu au_ring_drops=%lu log_ring_drops=%lu "
                    "control_drops=%lu raw_written=%llu raw_dropped=%llu\n", ring_generation(), g_au_in,
                    g_csd_sent, (unsigned long)(uint32_t)(ring_au_drops() - g_snap_au),
                    (unsigned long)(uint32_t)(ring_log_withheld() - g_snap_log),
                    (unsigned long)(uint32_t)(ring_control_drops() - g_snap_ctl), g_raw_written, g_raw_dropped);
}

/* AIR-087(a): the one final line, in the reserve; nothing is written after it until the next open. */
static void final_line(int64_t now)
{
    char buf[RAWDIAG_RESERVE];
    int n = snprintf(buf, sizeof buf, "%lld raw log full ", (long long)now);
    n += counter_fields(buf + n, sizeof buf - (size_t)n);
    put(buf, (size_t)n);
    g_on = false;
}

/* Returns true when a non-final line of len bytes fits under the threshold; otherwise writes the final line. */
static bool fits(size_t len, int64_t now)
{
    if (g_len + (long)len <= RAWDIAG_THRESHOLD) return true;
    final_line(now);
    return false;
}

static void counter_line(int64_t now)
{
    char buf[RAWDIAG_RESERVE];
    int n = snprintf(buf, sizeof buf, "%lld counters ", (long long)now);
    n += counter_fields(buf + n, sizeof buf - (size_t)n);
    g_last = now;
    if (fits((size_t)n, now)) put(buf, (size_t)n);
}

/* AIR-086(b): an event at least 1,000 ms after the last counter line gets one. */
static void tick(int64_t now)
{
    if (g_on && now - g_last >= RAWDIAG_COUNTER_MS) counter_line(now);
}

static bool is_hex(unsigned char c)
{
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
}

static bool is_b64(unsigned char c)
{
    return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '+' || c == '/'
           || c == '=';
}

static bool is_sep(unsigned char c) { return c == ' ' || c == '\r' || c == '\n'; }

static void mark_runs(const unsigned char *s, size_t n, unsigned char *m, bool (*in)(unsigned char), size_t min)
{
    size_t i = 0;
    while (i < n) {
        if (!in(s[i])) { i++; continue; }
        size_t j = i;
        while (j < n && in(s[j])) j++;
        if (j - i >= min) memset(m + i, 1, j - i);
        i = j;
    }
}

static void mark_all(const char *s, size_t n, unsigned char *m, const char *what)
{
    size_t w = what ? strlen(what) : 0;
    if (!w) return;
    for (const char *p = strstr(s, what); p; p = strstr(p + 1, what)) memset(m + (p - s), 1, w);
    (void)n;
}

/* A two-digit hex byte at i: at the start or after a separator, followed by at least one separator. */
static bool dump_byte(const unsigned char *s, size_t n, size_t i)
{
    return i + 2 < n && (i == 0 || is_sep(s[i - 1])) && is_hex(s[i]) && is_hex(s[i + 1]) && is_sep(s[i + 2]);
}

static void mark_dumps(const unsigned char *s, size_t n, unsigned char *m)
{
    size_t i = 0;
    while (i < n) {
        if (!dump_byte(s, n, i)) { i++; continue; }
        size_t start = i, end = i, count = 0, q = i;
        while (q < n && dump_byte(s, n, q)) {
            count++;
            end = q + 2;
            q = end;
            while (q < n && is_sep(s[q])) q++;
        }
        if (count >= 8) memset(m + start, 1, end - start); /* separators after the last byte are kept */
        i = q > i ? q : i + 1;
    }
}

/* AIR-085(c): marks every byte to be replaced; a run of marked bytes becomes one marker. */
static void redact(const char *text, size_t n, unsigned char *m)
{
    const unsigned char *s = (const unsigned char *)text;
    mark_runs(s, n, m, is_hex, 32);
    mark_runs(s, n, m, is_b64, 44);
    for (const char *p = strstr(text, "<data>"); p; ) {
        const char *v = p + 6, *e = strstr(v, "</data>");
        size_t end = e ? (size_t)(e - text) : n; /* an unterminated value is redacted to the end */
        if (end > (size_t)(v - text)) memset(m + (v - text), 1, end - (size_t)(v - text));
        p = e ? strstr(e + 7, "<data>") : NULL;
    }
    for (const char *p = strstr(text, "PIN = \""); p; ) {
        const char *q = p + 6, *e = strchr(q + 1, '"');
        size_t end = e ? (size_t)(e - text) + 1 : n;
        memset(m + (q - text), 1, end - (size_t)(q - text));
        p = e ? strstr(e + 1, "PIN = \"") : NULL;
    }
    mark_all(text, n, m, g_key);
    mark_all(text, n, m, g_base);
    mark_dumps(s, n, m);
}

static size_t esc_len(unsigned char c)
{
    if (c == '\r' || c == '\n' || c == '\\') return 2;
    return (c < 0x20 || c > 0x7e) ? 4 : 1;
}

static char *esc_put(char *o, unsigned char c)
{
    static const char hex[] = "0123456789abcdef";
    if (c == '\r') { *o++ = '\\'; *o++ = 'r'; }
    else if (c == '\n') { *o++ = '\\'; *o++ = 'n'; }
    else if (c == '\\') { *o++ = '\\'; *o++ = '\\'; }
    else if (c < 0x20 || c > 0x7e) { *o++ = '\\'; *o++ = 'x'; *o++ = hex[c >> 4]; *o++ = hex[c & 15]; }
    else *o++ = (char)c;
    return o;
}

static void write_record(int level, const char *msg, int64_t now)
{
    size_t n = strlen(msg);
    unsigned char *m = calloc(n + 1, 1);
    if (!m) { g_raw_dropped++; return; }
    redact(msg, n, m);
    size_t tl = 0;
    for (size_t i = 0; i < n; i++) tl += m[i] ? ((i == 0 || !m[i - 1]) ? REDACTED_LEN : 0) : esc_len((unsigned char)msg[i]);
    if (tl > RAWDIAG_TEXT_MAX) { free(m); g_raw_dropped++; return; } /* AIR-087(b): dropped whole */
    char pre[48];
    int pl = snprintf(pre, sizeof pre, "%lld L%d ", (long long)now, level);
    size_t len = (size_t)pl + tl + 1;
    if (g_len + (long)len > RAWDIAG_THRESHOLD) { free(m); g_raw_dropped++; final_line(now); return; }
    char *line = malloc(len);
    if (!line) { free(m); g_raw_dropped++; return; }
    memcpy(line, pre, (size_t)pl);
    char *o = line + pl;
    for (size_t i = 0; i < n; i++) {
        if (!m[i]) o = esc_put(o, (unsigned char)msg[i]);
        else if (i == 0 || !m[i - 1]) { memcpy(o, REDACTED, REDACTED_LEN); o += REDACTED_LEN; }
    }
    *o = '\n';
    free(m);
    put(line, len);
    free(line);
    if (g_on) g_raw_written++;
}

void rawdiag_open(const char *keyfile)
{
    (void)*(volatile const char *)RAWDIAG_MARKER;
    pthread_mutex_lock(&g_mu);
    stop_file();
    free(g_key);
    g_key = NULL;
    g_base = NULL;
    g_len = 0;
    g_last = 0;
    g_au_in = g_csd_sent = g_raw_written = g_raw_dropped = 0;
    const char *slash = keyfile ? strrchr(keyfile, '/') : NULL;
    if (slash) {
        size_t dir = (size_t)(slash - keyfile), kl = strlen(keyfile);
        char *path = malloc(dir + sizeof RAWDIAG_FILE);
        g_key = malloc(kl + 1);
        if (path && g_key) {
            memcpy(g_key, keyfile, kl + 1);
            g_base = g_key + dir + 1;
            memcpy(path, keyfile, dir);
            memcpy(path + dir, RAWDIAG_FILE, sizeof RAWDIAG_FILE);
            int flags = O_WRONLY | O_CREAT | O_TRUNC;
#ifdef O_CLOEXEC
            flags |= O_CLOEXEC;
#endif
            g_fd = g_ops.open(path, flags, 0600); /* AIR-088(a): a failed open leaves raw writes off */
            if (g_fd >= 0) {
                g_on = true;
                g_snap_au = ring_au_drops();
                g_snap_log = ring_log_withheld();
                g_snap_ctl = ring_control_drops();
                counter_line(g_clock());
            } else {
                g_fd = -1;
            }
        }
        free(path);
    }
    pthread_mutex_unlock(&g_mu);
}

void rawdiag_record(int level, const char *msg)
{
    if (!msg) return;
    pthread_mutex_lock(&g_mu);
    if (g_on) {
        int64_t now = g_clock();
        tick(now); /* a due counter line goes before the record's line */
        if (g_on) write_record(level, msg, now);
        else g_raw_dropped++;
    }
    pthread_mutex_unlock(&g_mu);
}

void rawdiag_video_process(int nonempty)
{
    pthread_mutex_lock(&g_mu);
    if (nonempty) g_au_in++;
    if (g_on) tick(g_clock());
    pthread_mutex_unlock(&g_mu);
}

void rawdiag_codec_config(int accepted)
{
    pthread_mutex_lock(&g_mu);
    if (accepted) g_csd_sent++;
    if (g_on) tick(g_clock());
    pthread_mutex_unlock(&g_mu);
}

void rawdiag_event(void)
{
    pthread_mutex_lock(&g_mu);
    if (g_on) tick(g_clock());
    pthread_mutex_unlock(&g_mu);
}

void rawdiag_session_end(void)
{
    pthread_mutex_lock(&g_mu);
    if (g_on) counter_line(g_clock()); /* always one line; it also restarts the 1,000 ms timer */
    pthread_mutex_unlock(&g_mu);
}

void rawdiag_last(void)
{
    pthread_mutex_lock(&g_mu);
    if (g_on) counter_line(g_clock());
    stop_file();
    pthread_mutex_unlock(&g_mu);
}

#ifdef PARKCAST_TEST_SEAMS
void rawdiag_set_fileops(const rawdiag_fileops_t *ops)
{
    pthread_mutex_lock(&g_mu);
    g_ops.open = ops ? ops->open : real_open;
    g_ops.write = ops ? ops->write : real_write;
    g_ops.close = ops ? ops->close : real_close;
    pthread_mutex_unlock(&g_mu);
}

void rawdiag_set_clock(int64_t (*clock)(void))
{
    pthread_mutex_lock(&g_mu);
    g_clock = clock ? clock : real_clock;
    pthread_mutex_unlock(&g_mu);
}

void *rawdiag_mutex_addr(void) { return &g_mu; }
#endif

#else /* PARKCAST_RAW_DIAG 0: no file is created, opened, truncated or written (AIR-085(e)). */

void rawdiag_open(const char *keyfile) { (void)keyfile; }
void rawdiag_record(int level, const char *msg) { (void)level; (void)msg; }
void rawdiag_video_process(int nonempty) { (void)nonempty; }
void rawdiag_codec_config(int accepted) { (void)accepted; }
void rawdiag_event(void) {}
void rawdiag_session_end(void) {}
void rawdiag_last(void) {}

#ifdef PARKCAST_TEST_SEAMS
static pthread_mutex_t g_mu = PTHREAD_MUTEX_INITIALIZER;
void rawdiag_set_fileops(const rawdiag_fileops_t *ops) { (void)ops; }
void rawdiag_set_clock(int64_t (*clock)(void)) { (void)clock; }
void *rawdiag_mutex_addr(void) { return &g_mu; }
#endif

#endif

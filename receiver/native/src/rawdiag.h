/*
 * ParkCast native receiver (S3 revision 10): the diagnostic raw log, uxplay-raw.log (AIR-085 to AIR-088).
 * docs/design/S3_RECEIVER.md, "Diagnostic raw log".
 *
 * Built only when PARKCAST_RAW_DIAG is 1; otherwise every entry point is a no-op and no file is touched.
 * Every call is made from the native log path (logpath.c) on the calling thread. The module takes only its
 * own mutex, never the ring lock, never calls Java and never waits for delivery.
 */
#ifndef PARKCAST_RAWDIAG_H
#define PARKCAST_RAWDIAG_H

#include <stddef.h>
#include <stdint.h>

#define RAWDIAG_CAP        16777216L                         /* AIR-087: the file never grows past this */
#define RAWDIAG_RESERVE    512L                              /* kept for the one "raw log full" line */
#define RAWDIAG_THRESHOLD  (RAWDIAG_CAP - RAWDIAG_RESERVE)   /* 16,776,704 */
#define RAWDIAG_TEXT_MAX   65536                             /* escaped text longer than this is dropped */
#define RAWDIAG_COUNTER_MS 1000                              /* an event this long after the last counter line */

/* App start (KEYCHECK): truncates or creates uxplay-raw.log next to keyfile, mode 0600, zeroes every count
   and writes the first counter line. The key file itself is never opened. */
void rawdiag_open(const char *keyfile);
/* One UxPlay log record: redacted, escaped and written as "<uptime ms> L<level> <text>". */
void rawdiag_record(int level, const char *msg);
/* One video_process call; nonempty counts it in au_in. */
void rawdiag_video_process(int nonempty);
/* One codec-configuration push; accepted is ring_push's result, counted in csd_sent. */
void rawdiag_codec_config(int accepted);
/* Session start. */
void rawdiag_event(void);
/* Session end: always one counter line. */
void rawdiag_session_end(void);
/* Stop's last phase: one counter line, then the file is closed. */
void rawdiag_last(void);

#ifdef PARKCAST_TEST_SEAMS
typedef struct {
    int (*open)(const char *path, int flags, int mode);
    long (*write)(int fd, const void *buf, size_t len);
    int (*close)(int fd);
} rawdiag_fileops_t;
void rawdiag_set_fileops(const rawdiag_fileops_t *ops); /* NULL restores the real calls */
void rawdiag_set_clock(int64_t (*clock)(void));         /* NULL restores CLOCK_MONOTONIC */
void *rawdiag_mutex_addr(void);
#endif

#endif

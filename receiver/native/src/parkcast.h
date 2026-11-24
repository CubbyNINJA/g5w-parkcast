/*
 * ParkCast native receiver (S3): shared declarations for parkcast_jni.c, host.c, logpath.c, rawdiag.c and ring.c.
 * docs/design/S3_RECEIVER.md, "Threads" and "JNI contract".
 */
#ifndef PARKCAST_H
#define PARKCAST_H

#include <jni.h>
#include <stdint.h>

/* Event kinds handed to NativeBridge.onNative. */
#define PC_EV_ACCEPTED       1   /* text = client model */
#define PC_EV_SESSION_START  2
#define PC_EV_CODEC_CONFIG   3   /* text = "h264" | "h265", a = width, b = height, data = csd */
#define PC_EV_AU             4   /* ptsMs, data = one Annex-B access unit */
#define PC_EV_SESSION_END    5
#define PC_EV_ADVERTISE_LOST 6
#define PC_EV_LOG            7   /* text = native log record, unfiltered; a = level */

/* Start stages and stop phases (NativeLibrary.Stage / Phase ordinals). */
#define PC_STAGE_KEYCHECK  0
#define PC_STAGE_BIND      1
#define PC_STAGE_ADVERTISE 2
#define PC_STAGE_ACCEPT    3

#define PC_PHASE_ACCEPT        0
#define PC_PHASE_SESSION       1
#define PC_PHASE_REGISTRATIONS 2
#define PC_PHASE_PORTS         3

/* ring.c: bounded event ring and its single delivery thread. */
int  ring_set_target(JavaVM *vm, jclass cls, jmethodID mid);
int  ring_start(void);                 /* starts the delivery thread if it is not running; 0 or -1 */
void ring_close(void);                 /* closes the ring, drops what is queued, joins the delivery thread */
int  ring_on_delivery_thread(void);
int  ring_generation(void);
void ring_next_generation(void);
/* Copies text and data. Dropped when the ring is closed or gen is not the current generation, or when the
 * slots for its kind are full. Returns 1 when the event was queued, 0 on every other path. */
int  ring_push(int gen, int kind, int a, int b, int64_t pts_ms, const char *text,
               const unsigned char *data, int len);
uint32_t ring_au_drops(void);
uint32_t ring_log_withheld(void);
uint32_t ring_control_drops(void);

/* host.c: the staged start and stop over UxPlay. The result is written as "ok <token>" / "fail <token>". */
void host_start(int stage, const char *keyfile, const char *address, int width, int height, int fps,
                char *result, int result_len);
void host_stop(int phase);
int  host_accepting(void);

#endif

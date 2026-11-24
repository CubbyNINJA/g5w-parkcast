/*
 * ParkCast native receiver (S3): the native log and video path between UxPlay's callbacks (host.c) and the
 * ring (ring.c). With raw diagnostics on, it also feeds the raw log (rawdiag.c) on the caller's thread,
 * before the ring push. docs/design/S3_RECEIVER.md, "Threads" and "Diagnostic raw log".
 */
#ifndef PARKCAST_LOGPATH_H
#define PARKCAST_LOGPATH_H

#include <stdint.h>

void lp_open(const char *keyfile);                       /* KEYCHECK, after the stdio redirect */
void lp_log(int level, const char *msg);                 /* UxPlay's log callback */
void lp_video_process(const unsigned char *data, int len, int is_h265, uint64_t ntp_time_remote);
int  lp_push_codec_config(int width, int height, const char *codec, const unsigned char *csd, int len);
void lp_set_h264(int width, int height);                 /* an accepted H.264 stream: CSD is due */
void lp_reset_video(void);
void lp_session_start(int gen);
void lp_session_end(int gen);
void lp_stop_last(void);                                 /* stop's last phase, before ring_close */

#endif

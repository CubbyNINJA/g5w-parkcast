/*
 * ParkCast native receiver (S3): the native log and video path. host.c's UxPlay callbacks call these entry
 * points; they push to the ring, and with raw diagnostics on they first feed the raw log on the calling
 * thread (AIR-085(a)), so a full ring, a stopped delivery thread or a failing Java filter can't hide a
 * record from it. docs/design/S3_RECEIVER.md, "Threads" and "Diagnostic raw log".
 */
#include "parkcast.h"
#include "logpath.h"
#include "rawdiag.h"

#include <stdbool.h>
#include <string.h>

/* Video state, touched only by UxPlay's mirror thread (and reset under host.c's g_api while no session runs). */
static bool g_need_csd;
static int g_width, g_height;

void lp_open(const char *keyfile) { rawdiag_open(keyfile); }

void lp_log(int level, const char *msg)
{
    if (!msg) return;
    rawdiag_record(level, msg); /* AIR-085(a): written before the ring push, on this thread */
    ring_push(ring_generation(), PC_EV_LOG, level, 0, 0, msg, NULL, 0);
}

static int push_codec_config(int gen, int width, int height, const char *codec, const unsigned char *csd,
                             int len)
{
    int ok = ring_push(gen, PC_EV_CODEC_CONFIG, width, height, 0, codec, csd, len);
    rawdiag_codec_config(ok == 1); /* AIR-086: csd_sent counts only a push the ring accepted */
    return ok;
}

int lp_push_codec_config(int width, int height, const char *codec, const unsigned char *csd, int len)
{
    return push_codec_config(ring_generation(), width, height, codec, csd, len);
}

/* Appends the NAL at [nal, nal + len) to out with a four-byte start code. */
static int csd_append(unsigned char *out, int at, int cap, const unsigned char *nal, int len)
{
    if (at + 4 + len > cap) return at;
    out[at] = 0; out[at + 1] = 0; out[at + 2] = 0; out[at + 3] = 1;
    memcpy(out + at + 4, nal, (size_t)len);
    return at + 4 + len;
}

/* Finds the SPS (type 7) and PPS (type 8) NALs in an Annex-B access unit and pushes them as kind 3. */
static bool push_h264_csd(int gen, const unsigned char *p, int n)
{
    const unsigned char *sps = NULL, *pps = NULL;
    int sps_len = 0, pps_len = 0;
    int i = 0;
    while (i + 3 <= n) {
        int sc = 0;
        if (p[i] == 0 && p[i + 1] == 0 && p[i + 2] == 1) sc = 3;
        else if (i + 4 <= n && p[i] == 0 && p[i + 1] == 0 && p[i + 2] == 0 && p[i + 3] == 1) sc = 4;
        if (!sc) { i++; continue; }
        int start = i + sc, end = start;
        while (end + 3 <= n && !(p[end] == 0 && p[end + 1] == 0 && (p[end + 2] == 1
               || (end + 4 <= n && p[end + 2] == 0 && p[end + 3] == 1))))
            end++;
        if (end + 3 > n) end = n;
        if (start < end) {
            int type = p[start] & 0x1f;
            if (type == 7 && !sps) { sps = p + start; sps_len = end - start; }
            else if (type == 8 && !pps) { pps = p + start; pps_len = end - start; }
        }
        if (sps && pps) break;
        i = end;
    }
    if (!sps || !pps) return false;
    unsigned char csd[1024];
    int len = csd_append(csd, 0, (int)sizeof csd, sps, sps_len);
    len = csd_append(csd, len, (int)sizeof csd, pps, pps_len);
    if (len != 8 + sps_len + pps_len) return false;
    push_codec_config(gen, g_width, g_height, "h264", csd, len);
    return true;
}

void lp_video_process(const unsigned char *data, int len, int is_h265, uint64_t ntp_time_remote)
{
    int gen = ring_generation();
    rawdiag_video_process(data && len > 0); /* AIR-086: au_in counts before any check */
    if (!data || len < 5 || data[0] != 0 || is_h265) return;
    if (g_need_csd) {
        if (!push_h264_csd(gen, data, len)) return; /* no decoder config yet */
        g_need_csd = false;
    }
    ring_push(gen, PC_EV_AU, 0, 0, (int64_t)(ntp_time_remote / 1000000ULL), NULL, data, len);
}

void lp_set_h264(int width, int height)
{
    g_width = width;
    g_height = height;
    g_need_csd = true;
}

void lp_reset_video(void) { g_need_csd = false; }

void lp_session_start(int gen)
{
    rawdiag_event();
    ring_push(gen, PC_EV_SESSION_START, 0, 0, 0, NULL, NULL, 0);
}

void lp_session_end(int gen)
{
    rawdiag_session_end();
    ring_push(gen, PC_EV_SESSION_END, 0, 0, 0, NULL, NULL, 0);
}

void lp_stop_last(void) { rawdiag_last(); }

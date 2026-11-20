/*
 * ParkCast native receiver (S3): the staged start and phased stop over UxPlay, and the raop callback table.
 * docs/design/S3_RECEIVER.md, "Start and stop", "Threads" and "JNI contract".
 *
 * Start stages: KEYCHECK (stdio to /dev/null, raop_init, log callback and level, raop_init2), BIND (bind
 * address and port holds), ADVERTISE (first call: the mdnsd connect probe; second call: both dns_sd
 * registrations), ACCEPT (raop_start_httpd on the held port). Stop phases: ACCEPT, SESSION, REGISTRATIONS,
 * PORTS; each one is idempotent.
 *
 * Host code never logs addresses, keys or PINs. UxPlay's own records go up unfiltered as kind 7; the Java
 * filter runs before anything is written.
 */
#include "parkcast.h"

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

#include "raop.h"
#include "dnssd.h"
#include "netutils.h"

#define RTSP_PORT          35001
#define REGISTER_WAIT_MS   2000
#define NAME_CONFLICT      (-65548)
#define LOG_LEVEL_DEBUG    7

static const char DEVICE_ID[] = "02:00:00:00:00:01";
static const char HW_ADDR[6] = { 0x02, 0x00, 0x00, 0x00, 0x00, 0x01 };
static const char SERVICE_NAME[] = "ParkCast";
static const char MDNSD_SOCKET[] = "/dev/socket/mdnsd";

static pthread_mutex_t g_api = PTHREAD_MUTEX_INITIALIZER;
static raop_t *g_raop;
static dnssd_t *g_dnssd;
static bool g_stdio_done, g_bound, g_probed;
static atomic_int g_accepting;
static atomic_int g_conns;

/* Registration watch: services the dns_sd sockets after "registered" and reports a lost registration. */
static pthread_t g_watch;
static bool g_watching;
static int g_wake[2] = { -1, -1 };
static atomic_int g_reply[2], g_seen[2], g_registered, g_lost_sent;

/* Video state, touched only by UxPlay's mirror thread (and reset under g_api while no session runs). */
static bool g_need_csd;
static int g_width, g_height;

/* ------------------------------------------------------------------------------------------------------ */

static void hlog(int level, const char *fmt, ...)
{
    char buf[256];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof buf, fmt, ap);
    va_end(ap);
    ring_push(ring_generation(), PC_EV_LOG, level, 0, 0, buf, NULL, 0);
}

static void result_set(char *result, int result_len, bool ok, const char *fmt, ...)
{
    char token[96];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(token, sizeof token, fmt, ap);
    va_end(ap);
    snprintf(result, (size_t)result_len, "%s %s", ok ? "ok" : "fail", token);
}

static const char *errno_name(int e, char *buf, size_t n)
{
    switch (e) {
    case ENOENT: return "ENOENT";
    case EACCES: return "EACCES";
    case EPERM: return "EPERM";
    case ECONNREFUSED: return "ECONNREFUSED";
    case EAGAIN: return "EAGAIN";
    case EINPROGRESS: return "EINPROGRESS";
    case ETIMEDOUT: return "ETIMEDOUT";
    case ENOTDIR: return "ENOTDIR";
    case EMFILE: return "EMFILE";
    case ENFILE: return "ENFILE";
    default: snprintf(buf, n, "errno %d", e); return buf;
    }
}

static int64_t now_ms(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

/* ------------------------------------------------------------------------------------------------------ */
/* raop callbacks. Each takes the generation first; ring_push drops anything from an older generation.     */

static void cb_audio_process(void *cls, raop_ntp_t *ntp, audio_decode_struct *data)
{
    /* Audio is discarded (AIR-055(b)): nothing is decoded or played. */
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
    ring_push(gen, PC_EV_CODEC_CONFIG, g_width, g_height, 0, "h264", csd, len);
    return true;
}

static void cb_video_process(void *cls, raop_ntp_t *ntp, video_decode_struct *data)
{
    int gen = ring_generation();
    if (!data || !data->data || data->data_len < 5 || data->data[0] != 0 || data->is_h265) return;
    if (g_need_csd) {
        if (!push_h264_csd(gen, data->data, data->data_len)) return; /* no decoder config yet */
        g_need_csd = false;
    }
    ring_push(gen, PC_EV_AU, 0, 0, (int64_t)(data->ntp_time_remote / 1000000ULL), NULL, data->data,
              data->data_len);
}

static void cb_video_pause(void *cls) {}
static void cb_video_resume(void *cls) {}
static void cb_conn_feedback(void *cls) {}
static void cb_conn_reset(void *cls, int reason) {}
static void cb_video_reset(void *cls, reset_type_t reset_type) {}

static int cb_video_set_codec_and_size(void *cls, video_codec_t codec, unsigned short width_source,
                                       unsigned short height_source, unsigned short x_offset,
                                       unsigned short y_offset, unsigned short width, unsigned short height)
{
    int gen = ring_generation();
    if (codec == VIDEO_CODEC_H265) {
        ring_push(gen, PC_EV_CODEC_CONFIG, width_source, height_source, 0, "h265", NULL, 0);
        return -1; /* refused: UxPlay drops the connection */
    }
    if (codec != VIDEO_CODEC_H264) return -1;
    g_width = width_source;
    g_height = height_source;
    g_need_csd = true;
    return 0;
}

static void cb_conn_init(void *cls)
{
    int gen = ring_generation();
    if (atomic_fetch_add(&g_conns, 1) == 0) ring_push(gen, PC_EV_SESSION_START, 0, 0, 0, NULL, NULL, 0);
}

static void cb_conn_destroy(void *cls)
{
    int gen = ring_generation();
    int before = atomic_load(&g_conns);
    while (before > 0 && !atomic_compare_exchange_weak(&g_conns, &before, before - 1)) {}
    if (before == 1) ring_push(gen, PC_EV_SESSION_END, 0, 0, 0, NULL, NULL, 0);
}

static double cb_audio_set_client_volume(void *cls) { return 0.0; }

static void cb_report_client_request(void *cls, char *deviceid, char *model, char *name, bool *admit)
{
    int gen = ring_generation();
    if (admit) *admit = true;
    ring_push(gen, PC_EV_ACCEPTED, 0, 0, 0, model ? model : "", NULL, 0);
}

static void cb_on_video_play(void *cls, const char *location, const float start_position) {}
static void cb_on_video_scrub(void *cls, const float position) {}
static void cb_on_video_rate(void *cls, const float rate) {}
static void cb_on_video_stop(void *cls) {}
static void cb_on_video_acquire_playback_info(void *cls, playback_info_t *playback_video) {}
static float cb_on_video_playlist_remove(void *cls) { return 0.0f; }

/* Every member UxPlay calls without a NULL check is set (receiver/native/unguarded-callbacks.txt). */
static raop_callbacks_t g_callbacks = {
    .audio_process = cb_audio_process,
    .video_process = cb_video_process,
    .video_pause = cb_video_pause,
    .video_resume = cb_video_resume,
    .conn_feedback = cb_conn_feedback,
    .conn_reset = cb_conn_reset,
    .video_reset = cb_video_reset,
    .video_set_codec_and_size = cb_video_set_codec_and_size,
    .conn_init = cb_conn_init,
    .conn_destroy = cb_conn_destroy,
    .audio_set_client_volume = cb_audio_set_client_volume,
    .report_client_request = cb_report_client_request,
    .on_video_play = cb_on_video_play,
    .on_video_scrub = cb_on_video_scrub,
    .on_video_rate = cb_on_video_rate,
    .on_video_stop = cb_on_video_stop,
    .on_video_acquire_playback_info = cb_on_video_acquire_playback_info,
    .on_video_playlist_remove = cb_on_video_playlist_remove,
};

static void cb_log(void *cls, int level, const char *msg)
{
    if (msg) ring_push(ring_generation(), PC_EV_LOG, level, 0, 0, msg, NULL, 0);
}

static void cb_refused(void *cls, int kind)
{
    const char *what = kind == RAOP_REFUSED_URL ? "url" : kind == RAOP_REFUSED_HLS ? "hls"
                     : kind == RAOP_REFUSED_DRM ? "drm" : "other";
    hlog(LOG_LEVEL_DEBUG - 1, "refused %s", what);
}

/* ------------------------------------------------------------------------------------------------------ */
/* Registrations                                                                                          */

static void advertise_lost(void)
{
    if (atomic_exchange(&g_lost_sent, 1) == 0)
        ring_push(ring_generation(), PC_EV_ADVERTISE_LOST, 0, 0, 0, NULL, NULL, 0);
}

static void cb_register_reply(void *cls, int service, int error_code)
{
    if (service < 0 || service > 1) return;
    atomic_store(&g_reply[service], error_code);
    atomic_store(&g_seen[service], 1);
    if (error_code != 0 && atomic_load(&g_registered)) {
        hlog(3, "registration reply %d", error_code);
        advertise_lost();
    }
}

static void *watch_main(void *arg)
{
    dnssd_t *d = arg;
    for (;;) {
        struct pollfd p[3];
        int svc[3], n = 0;
        p[n].fd = g_wake[0]; p[n].events = POLLIN; p[n].revents = 0; svc[n++] = -1;
        for (int s = DNSSD_SERVICE_RAOP; s <= DNSSD_SERVICE_AIRPLAY; s++) {
            int fd = dnssd_get_service_fd(d, s);
            if (fd >= 0) { p[n].fd = fd; p[n].events = POLLIN; p[n].revents = 0; svc[n++] = s; }
        }
        if (n == 1) {
            hlog(3, "registration sockets gone");
            advertise_lost();
            return NULL;
        }
        if (poll(p, (nfds_t)n, -1) < 0) {
            if (errno == EINTR) continue;
            advertise_lost();
            return NULL;
        }
        if (p[0].revents) return NULL; /* stop requested */
        for (int i = 1; i < n; i++) {
            if (p[i].revents & (POLLHUP | POLLERR | POLLNVAL)) {
                hlog(3, "registration socket closed");
                advertise_lost();
                return NULL;
            }
            if (p[i].revents & POLLIN) {
                int r = dnssd_process_service(d, svc[i]);
                if (r != 0) {
                    hlog(3, "registration processing failed %d", r);
                    advertise_lost();
                    return NULL;
                }
            }
        }
    }
}

static void watch_stop(void)
{
    if (!g_watching) return;
    char b = 1;
    ssize_t w;
    do { w = write(g_wake[1], &b, 1); } while (w < 0 && errno == EINTR);
    pthread_join(g_watch, NULL);
    g_watching = false;
    close(g_wake[0]);
    close(g_wake[1]);
    g_wake[0] = g_wake[1] = -1;
}

static void registrations_stop(void)
{
    watch_stop();
    atomic_store(&g_registered, 0);
    if (g_dnssd) {
        dnssd_unregister_raop(g_dnssd);
        dnssd_unregister_airplay(g_dnssd);
        dnssd_destroy(g_dnssd);
        g_dnssd = NULL;
    }
    g_probed = false;
}

/* Waits for both register replies, servicing the dns_sd sockets. 0 when both are 0, else the first
   nonzero reply, or 1 on timeout. */
static int await_replies(void)
{
    int64_t deadline = now_ms() + REGISTER_WAIT_MS;
    for (;;) {
        for (int s = 0; s < 2; s++)
            if (atomic_load(&g_seen[s]) && atomic_load(&g_reply[s]) != 0) return atomic_load(&g_reply[s]);
        if (atomic_load(&g_seen[0]) && atomic_load(&g_seen[1])) return 0;
        int64_t left = deadline - now_ms();
        if (left <= 0) return 1;
        struct pollfd p[2];
        int svc[2], n = 0;
        for (int s = DNSSD_SERVICE_RAOP; s <= DNSSD_SERVICE_AIRPLAY; s++) {
            int fd = dnssd_get_service_fd(g_dnssd, s);
            if (fd >= 0 && !atomic_load(&g_seen[s])) {
                p[n].fd = fd; p[n].events = POLLIN; p[n].revents = 0; svc[n++] = s;
            }
        }
        if (n == 0) return 1;
        int r = poll(p, (nfds_t)n, (int)left);
        if (r < 0 && errno != EINTR) return 1;
        for (int i = 0; i < n && r > 0; i++) {
            if (p[i].revents & (POLLHUP | POLLERR | POLLNVAL)) return 1;
            if ((p[i].revents & POLLIN) && dnssd_process_service(g_dnssd, svc[i]) != 0) return 1;
        }
    }
}

/* ------------------------------------------------------------------------------------------------------ */
/* Start stages                                                                                           */

static void stage_keycheck(const char *keyfile, char *result, int result_len)
{
    if (!g_stdio_done) {
        /* Step 0: UxPlay and OpenSSL may write to stdout/stderr; nothing of theirs reaches a console. */
        int fd = open("/dev/null", O_RDWR);
        if (fd < 0 || dup2(fd, 1) < 0 || dup2(fd, 2) < 0) {
            if (fd >= 0) close(fd);
            result_set(result, result_len, false, "stdio-failed");
            return;
        }
        if (fd > 2) close(fd);
        g_stdio_done = true;
    }
    if (g_raop) { result_set(result, result_len, false, "state"); return; }
    if (ring_start() != 0) { result_set(result, result_len, false, "init"); return; }

    const char *key = (keyfile && *keyfile) ? keyfile : NULL;
    bool existed = key && access(key, F_OK) == 0;

    g_raop = raop_init(&g_callbacks);
    if (!g_raop) { result_set(result, result_len, false, "init"); return; }
    raop_set_log_callback(g_raop, cb_log, NULL);
    raop_set_log_level(g_raop, LOG_LEVEL_DEBUG);
    raop_set_refusal_callback(g_raop, cb_refused, NULL);

    int rc = raop_init2(g_raop, 0, DEVICE_ID, key);
    if (rc != 0) {
        raop_destroy(g_raop);
        g_raop = NULL;
        result_set(result, result_len, false, "raop_init2 %d", rc);
        return;
    }
    result_set(result, result_len, true, existed ? "key loaded" : "key created");
}

static void stage_bind(const char *address, int width, int height, int fps, char *result, int result_len)
{
    if (!g_raop) { result_set(result, result_len, false, "state"); return; }
    if (g_bound) { result_set(result, result_len, true, "bound"); return; }

    raop_set_plist(g_raop, "width", width > 0 ? width : 1920);
    raop_set_plist(g_raop, "height", height > 0 ? height : 720);
    raop_set_plist(g_raop, "refreshRate", fps > 0 ? fps : 60);
    raop_set_plist(g_raop, "maxFPS", fps > 0 ? fps : 60);

    if (!address || !*address || netutils_set_bind_address(address) != 0) {
        result_set(result, result_len, false, "address");
        return;
    }
    unsigned short tcp[2] = { 35000, 35001 };
    unsigned short udp[3] = { 35000, 35001, 35002 };
    raop_set_tcp_ports(g_raop, tcp);
    raop_set_udp_ports(g_raop, udp);
    raop_set_port(g_raop, RTSP_PORT);

    for (int i = 0; i < 2; i++) {
        if (netutils_hold_port(tcp[i], 0) != 0) {
            hlog(3, "hold tcp %u failed, errno %d", tcp[i], errno);
            netutils_release_ports();
            result_set(result, result_len, false, "tcp %u", tcp[i]);
            return;
        }
    }
    for (int i = 0; i < 3; i++) {
        if (netutils_hold_port(udp[i], 1) != 0) {
            hlog(3, "hold udp %u failed, errno %d", udp[i], errno);
            netutils_release_ports();
            result_set(result, result_len, false, "udp %u", udp[i]);
            return;
        }
    }
    g_bound = true;
    result_set(result, result_len, true, "bound");
}

/* One connect attempt to the mdnsd socket; core repeats it every 250 ms. */
static void advertise_probe(char *result, int result_len)
{
    char buf[24];
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) { result_set(result, result_len, false, "%s", errno_name(errno, buf, sizeof buf)); return; }
    fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | O_NONBLOCK);
    struct sockaddr_un sa;
    memset(&sa, 0, sizeof sa);
    sa.sun_family = AF_UNIX;
    memcpy(sa.sun_path, MDNSD_SOCKET, sizeof MDNSD_SOCKET);
    int rc = connect(fd, (struct sockaddr *)&sa, (socklen_t)sizeof sa);
    int e = errno;
    close(fd);
    if (rc != 0) { result_set(result, result_len, false, "%s", errno_name(e, buf, sizeof buf)); return; }
    g_probed = true;
    result_set(result, result_len, true, "mdnsd up");
}

static void advertise_register(char *result, int result_len)
{
    int err = 0;
    g_dnssd = dnssd_init(SERVICE_NAME, (int)strlen(SERVICE_NAME), HW_ADDR, (int)sizeof HW_ADDR, 0, &err);
    if (!g_dnssd || err) {
        hlog(3, "dnssd_init error %d", err);
        if (g_dnssd) { dnssd_destroy(g_dnssd); g_dnssd = NULL; }
        result_set(result, result_len, false, "txt-build");
        return;
    }
    /* No HLS video (bits 0 and 4) and no H.265 (bit 42): screen mirroring in H.264 only. */
    dnssd_set_airplay_features(g_dnssd, 0, 0);
    dnssd_set_airplay_features(g_dnssd, 4, 0);
    dnssd_set_airplay_features(g_dnssd, 42, 0);
    raop_set_dnssd(g_raop, g_dnssd);
    for (int s = 0; s < 2; s++) { atomic_store(&g_reply[s], 0); atomic_store(&g_seen[s], 0); }
    atomic_store(&g_lost_sent, 0);
    dnssd_set_register_callback(g_dnssd, cb_register_reply, NULL);

    int rc = dnssd_register_airplay(g_dnssd, RTSP_PORT);
    if (rc == 0) {
        rc = dnssd_register_raop(g_dnssd, RTSP_PORT);
        if (rc != 0) dnssd_unregister_airplay(g_dnssd);
    }
    if (rc == -1 || rc == -2) { registrations_stop(); g_probed = true; result_set(result, result_len, false, "txt-build"); return; }
    if (rc != 0) { registrations_stop(); g_probed = true; result_set(result, result_len, false, "register %d", rc); return; }

    int reply = await_replies();
    if (reply != 0) {
        registrations_stop();
        g_probed = true;
        if (reply == NAME_CONFLICT) result_set(result, result_len, false, "name-conflict %d", NAME_CONFLICT);
        else if (reply == 1) result_set(result, result_len, false, "register timeout");
        else result_set(result, result_len, false, "register %d", reply);
        return;
    }

    atomic_store(&g_registered, 1);
    if (pipe(g_wake) == 0) {
        if (pthread_create(&g_watch, NULL, watch_main, g_dnssd) == 0) g_watching = true;
        else { close(g_wake[0]); close(g_wake[1]); g_wake[0] = g_wake[1] = -1; }
    }
    if (!g_watching) hlog(3, "registration watch not started");
    result_set(result, result_len, true, "registered");
}

static void stage_advertise(char *result, int result_len)
{
    if (!g_raop || !g_bound || g_dnssd) { result_set(result, result_len, false, "state"); return; }
    if (!g_probed) advertise_probe(result, result_len);
    else advertise_register(result, result_len);
}

static void stage_accept(char *result, int result_len)
{
    if (!g_raop || !g_dnssd || !g_bound) { result_set(result, result_len, false, "state"); return; }
    if (atomic_load(&g_accepting)) { result_set(result, result_len, true, "accepting"); return; }
    g_need_csd = false;
    unsigned short port = RTSP_PORT;
    int rc = raop_start_httpd(g_raop, &port);
    if (rc < 0) { result_set(result, result_len, false, "httpd %d", rc); return; }
    atomic_store(&g_accepting, 1);
    result_set(result, result_len, true, "accepting");
}

void host_start(int stage, const char *keyfile, const char *address, int width, int height, int fps,
                char *result, int result_len)
{
    pthread_mutex_lock(&g_api);
    switch (stage) {
    case PC_STAGE_KEYCHECK: stage_keycheck(keyfile, result, result_len); break;
    case PC_STAGE_BIND: stage_bind(address, width, height, fps, result, result_len); break;
    case PC_STAGE_ADVERTISE: stage_advertise(result, result_len); break;
    case PC_STAGE_ACCEPT: stage_accept(result, result_len); break;
    default: result_set(result, result_len, false, "state"); break;
    }
    pthread_mutex_unlock(&g_api);
}

/* ------------------------------------------------------------------------------------------------------ */
/* Stop phases                                                                                            */

static void accept_stop(void)
{
    if (g_raop && atomic_load(&g_accepting)) raop_stop_httpd(g_raop);
    atomic_store(&g_accepting, 0);
}

void host_stop(int phase)
{
    pthread_mutex_lock(&g_api);
    switch (phase) {
    case PC_PHASE_ACCEPT:
        accept_stop();
        break;
    case PC_PHASE_SESSION:
        /* The generation moves on first, so nothing the closing session still pushes is delivered. */
        ring_next_generation();
        if (g_raop && atomic_load(&g_accepting)) raop_remove_known_connections(g_raop);
        ring_close();
        if (atomic_load(&g_accepting) && ring_start() != 0) hlog(3, "delivery restart failed");
        break;
    case PC_PHASE_REGISTRATIONS:
        /* raop keeps a pointer to the dns_sd state for GET /info, so it stops accepting first. */
        accept_stop();
        registrations_stop();
        break;
    case PC_PHASE_PORTS:
        accept_stop();
        registrations_stop();
        netutils_release_ports();
        if (g_raop) { raop_destroy(g_raop); g_raop = NULL; }
        g_bound = false;
        g_need_csd = false;
        atomic_store(&g_conns, 0);
        ring_close();
        break;
    default:
        break;
    }
    pthread_mutex_unlock(&g_api);
}

int host_accepting(void) { return atomic_load(&g_accepting); }

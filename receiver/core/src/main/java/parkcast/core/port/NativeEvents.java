package parkcast.core.port;

/** Events native code passes to Java (AIR-055(a)), delivered on the event thread after the generation check. */
public interface NativeEvents {
    void sessionStarted(int generation);

    /** {@code video_set_codec_and_size}: the stream's codec ("h264" or "h265") and size. */
    void codecConfig(int generation, String codec, int width, int height);

    void accessUnit(int generation, long ptsMs);

    void sessionEnded(int generation);

    /** The mdnsd connection closed after {@code advertise ok} (AIR-050(e)). */
    void advertiseLost(int generation);

    /** One native log record, before AIR-073's filter. */
    void log(int generation, String record);

    /** UxPlay accepted the iPhone's connection; {@code model} is its model identifier, never its name. */
    void connectionAccepted(int generation, String model);
}

package parkcast.gate.port;

/** The mirroring session: audio and the iPhone connection. The gate passes nothing to it. */
public interface MirrorSession {
    void muteAudio();

    void stopAudio();

    void disconnectAudio();

    void stopConnection();

    void disconnect();

    void reconnect();
}

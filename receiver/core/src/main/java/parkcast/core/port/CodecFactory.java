package parkcast.core.port;

/** {@code MediaCodec.createByCodecName}; no codec-list lookup and no fallback (AIR-056(a)). */
public interface CodecFactory {
    Codec create(String name) throws Exception;
}

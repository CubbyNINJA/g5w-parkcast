package parkcast.receiver;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;
import java.nio.ByteBuffer;
import parkcast.core.port.Codec;
import parkcast.core.port.CodecFactory;

/**
 * {@link CodecFactory} over MediaCodec in synchronous mode (API 19 has no async callback). Every dequeue uses
 * a zero timeout, so nothing blocks the event thread.
 */
final class AndroidCodecFactory implements CodecFactory {
    @Override
    public Codec create(String name) throws Exception {
        return new AndroidCodec(MediaCodec.createByCodecName(name));
    }

    static final class AndroidCodec implements Codec {
        private final MediaCodec codec;
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        private ByteBuffer[] inputs;

        AndroidCodec(MediaCodec codec) {
            this.codec = codec;
        }

        @Override
        public void configure(String mime, int width, int height, byte[] csd, Object surface) throws Exception {
            MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
            if (csd != null && csd.length > 0) format.setByteBuffer("csd-0", ByteBuffer.wrap(csd));
            codec.configure(format, (Surface) surface, null, 0);
        }

        @Override
        public void start() throws Exception {
            codec.start();
            inputs = codec.getInputBuffers();
        }

        @Override
        public boolean queue(byte[] au, long ptsUs) throws Exception {
            int index = codec.dequeueInputBuffer(0);
            if (index < 0) return false;
            ByteBuffer b = inputs[index];
            b.clear();
            b.put(au);
            codec.queueInputBuffer(index, 0, au.length, ptsUs, 0);
            return true;
        }

        @Override
        public int dequeue() throws Exception {
            while (true) {
                int index = codec.dequeueOutputBuffer(info, 0);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
                        || index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) continue;
                return index < 0 ? -1 : index;
            }
        }

        @Override
        public void release(int index, boolean render) throws Exception {
            codec.releaseOutputBuffer(index, render);
        }

        @Override
        public void release() {
            try {
                codec.stop();
            } catch (RuntimeException e) {
                // Not started, or already in an error state: release below still frees it.
            }
            try {
                codec.release();
            } catch (RuntimeException e) {
                // Nothing more can be done with a codec that will not release.
            }
        }
    }
}

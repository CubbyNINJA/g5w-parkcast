package parkcast.core;

/**
 * Fits a stream into the 1920x720 screen whole and centred (AIR-051(b)): the largest box of the stream's
 * aspect ratio that fits, with black bars on the sides or above and below.
 */
final class Letterbox {
    static final int SCREEN_W = 1920, SCREEN_H = 720;

    final int x, y, width, height;

    private Letterbox(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** The box for a {@code w} by {@code h} stream; a size that is not positive fills the screen. */
    static Letterbox fit(int w, int h) {
        if (w <= 0 || h <= 0) return new Letterbox(0, 0, SCREEN_W, SCREEN_H);
        long bw = Math.round((double) w * SCREEN_H / h);
        long bh = SCREEN_H;
        if (bw > SCREEN_W) {
            bw = SCREEN_W;
            bh = Math.round((double) h * SCREEN_W / w);
        }
        return new Letterbox((int) ((SCREEN_W - bw) / 2), (int) ((SCREEN_H - bh) / 2), (int) bw, (int) bh);
    }
}

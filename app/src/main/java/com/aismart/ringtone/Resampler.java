package com.aismart.ringtone;

/**
 * Bộ đổi tần số lấy mẫu dạng luồng (windowed-sinc, Lanczos a=8) cho PCM 16-bit xen kẽ các kênh.
 * Gọi [process] nhiều lần rồi [flush] một lần ở cuối.
 */
public final class Resampler {
    private static final int A = 8;
    private static final int PHASES = 512;

    private final int channels;
    private final double step;
    private final int support;
    private final int taps;
    private final float[] table;

    private float[] buf = new float[0];
    private int bufFrames = 0;
    private long baseIndex = 0;      // chỉ số tuyệt đối của khung đầu trong buf
    private long totalIn = 0;
    private double pos = 0;          // vị trí (theo khung đầu vào) của mẫu ra kế tiếp

    public Resampler(int inRate, int outRate, int channels) {
        if (inRate <= 0 || outRate <= 0 || channels < 1) throw new IllegalArgumentException();
        this.channels = channels;
        this.step = (double) inRate / outRate;
        double scale = Math.min(1.0, (double) outRate / inRate);
        this.support = (int) Math.ceil(A / scale);
        this.taps = 2 * support;
        this.table = new float[(PHASES + 1) * taps];
        for (int p = 0; p <= PHASES; p++) {
            double frac = (double) p / PHASES;
            double sum = 0;
            for (int t = 0; t < taps; t++) {
                int k = t - support + 1;
                double x = (k - frac) * scale;
                double w = 0;
                if (Math.abs(x) < A) w = sinc(x) * sinc(x / A);
                table[p * taps + t] = (float) w;
                sum += w;
            }
            if (sum != 0) for (int t = 0; t < taps; t++) table[p * taps + t] /= (float) sum;
        }
    }

    private static double sinc(double x) {
        if (Math.abs(x) < 1e-9) return 1.0;
        double px = Math.PI * x;
        return Math.sin(px) / px;
    }

    /** Nhận [frames] khung đầu vào, trả về các mẫu ra (xen kẽ các kênh). */
    public short[] process(short[] in, int frames) {
        append(in, frames, false);
        return drain(false);
    }

    /** Xả phần còn lại ở cuối luồng. */
    public short[] flush() {
        append(null, support + 2, true);
        return drain(true);
    }

    private void append(short[] in, int frames, boolean zeros) {
        int need = (bufFrames + frames) * channels;
        if (buf.length < need) buf = java.util.Arrays.copyOf(buf, Math.max(need, buf.length * 2));
        int base = bufFrames * channels;
        if (zeros) {
            for (int i = 0; i < frames * channels; i++) buf[base + i] = 0f;
        } else {
            for (int i = 0; i < frames * channels; i++) buf[base + i] = in[i] / 32768f;
            totalIn += frames;
        }
        bufFrames += frames;
    }

    private short[] drain(boolean finishing) {
        long endIndex = baseIndex + bufFrames;
        int estimate = (int) Math.max(0, (endIndex - pos) / step) + 2;
        short[] out = new short[estimate * channels];
        int produced = 0;
        while (true) {
            long center = (long) Math.floor(pos);
            if (center + support >= endIndex) break;
            if (finishing && pos >= totalIn) break;
            double frac = pos - center;
            int phase = (int) Math.round(frac * PHASES);
            int tableBase = phase * taps;
            if (produced >= estimate) break;
            for (int c = 0; c < channels; c++) {
                double acc = 0;
                for (int t = 0; t < taps; t++) {
                    long idx = center + (t - support + 1) - baseIndex;
                    if (idx < 0 || idx >= bufFrames) continue;
                    acc += buf[(int) idx * channels + c] * table[tableBase + t];
                }
                int v = (int) Math.round(acc * 32768.0);
                if (v > 32767) v = 32767;
                if (v < -32768) v = -32768;
                out[produced * channels + c] = (short) v;
            }
            produced++;
            pos += step;
        }
        // Bỏ các khung đã không còn cần
        long keepFrom = (long) Math.floor(pos) - support;
        int drop = (int) Math.max(0, Math.min(bufFrames, keepFrom - baseIndex));
        if (drop > 0) {
            System.arraycopy(buf, drop * channels, buf, 0, (bufFrames - drop) * channels);
            bufFrames -= drop;
            baseIndex += drop;
        }
        return java.util.Arrays.copyOf(out, produced * channels);
    }
}

package com.aismart.ringtone;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Bộ mã hóa FLAC không mất dữ liệu (PCM 16-bit, 1-2 kênh) viết bằng Java thuần.
 * Dùng khối cố định 4096 mẫu, bộ dự đoán cố định bậc 0-4, mã Rice phân vùng và
 * ghép kênh stereo tự động (trái/phải, trái/hiệu, hiệu/phải, giữa/hiệu).
 */
public final class FlacEncoder {
    private static final int BLOCK = 4096;
    private static final int BPS = 16;

    private final RandomAccessFile file;
    private final int sampleRate;
    private final int channels;
    private final short[] pending;
    private int pendingFrames = 0;
    private long frameNumber = 0;
    private long totalSamples = 0;
    private int minBlock = Integer.MAX_VALUE;
    private int maxBlock = 0;
    private int minFrameBytes = Integer.MAX_VALUE;
    private int maxFrameBytes = 0;
    private boolean closed = false;

    public FlacEncoder(RandomAccessFile file, int sampleRate, int channels) throws IOException {
        if (channels < 1 || channels > 2) throw new IllegalArgumentException("FLAC: chỉ hỗ trợ 1 hoặc 2 kênh");
        if (sampleRate < 1 || sampleRate > 655350) throw new IllegalArgumentException("FLAC: tần số không hợp lệ");
        this.file = file;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.pending = new short[BLOCK * channels];
        file.setLength(0);
        file.write(new byte[]{'f', 'L', 'a', 'C'});
        file.write(streamInfoBlock(true));
    }

    /** Ghi khối STREAMINFO (đã có tiêu đề khối). */
    private byte[] streamInfoBlock(boolean provisional) {
        BitWriter w = new BitWriter(64);
        w.write(1, 1);        // last metadata block
        w.write(0, 7);        // STREAMINFO
        w.write(34, 24);
        int min = (minBlock == Integer.MAX_VALUE) ? BLOCK : minBlock;
        w.write(min, 16);
        w.write(maxBlock == 0 ? BLOCK : maxBlock, 16);
        w.write(minFrameBytes == Integer.MAX_VALUE ? 0 : minFrameBytes, 24);
        w.write(maxFrameBytes, 24);
        w.write(sampleRate, 20);
        w.write(channels - 1, 3);
        w.write(BPS - 1, 5);
        w.write((int) (totalSamples >>> 32), 4);
        w.write((int) (totalSamples & 0xFFFFFFFFL), 32);
        for (int i = 0; i < 4; i++) w.write(0, 32); // MD5 = 0 (không biết)
        return w.toByteArray();
    }

    /** Nhận [frames] khung PCM xen kẽ các kênh. */
    public void encode(short[] pcm, int frames) throws IOException {
        int offset = 0;
        while (frames > 0) {
            int take = Math.min(frames, BLOCK - pendingFrames);
            System.arraycopy(pcm, offset * channels, pending, pendingFrames * channels, take * channels);
            pendingFrames += take;
            offset += take;
            frames -= take;
            if (pendingFrames == BLOCK) writeFrame();
        }
    }

    public void finish() throws IOException {
        if (pendingFrames > 0) writeFrame();
        file.seek(4);
        file.write(streamInfoBlock(false));
        file.close();
        closed = true;
    }

    public void abort() {
        if (closed) return;
        try {
            file.close();
        } catch (IOException ignored) {
        }
        closed = true;
    }

    private void writeFrame() throws IOException {
        int n = pendingFrames;
        int[][] ch = new int[channels][n];
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < channels; c++) ch[c][i] = pending[i * channels + c];
        }

        BitWriter body = new BitWriter(n * channels * 2 + 64);
        int assignment;
        if (channels == 1) {
            assignment = 0;
            Subframe s = encodeSubframe(ch[0], n, BPS);
            s.writeTo(body);
        } else {
            int[] left = ch[0];
            int[] right = ch[1];
            int[] mid = new int[n];
            int[] side = new int[n];
            for (int i = 0; i < n; i++) {
                mid[i] = (left[i] + right[i]) >> 1;
                side[i] = left[i] - right[i];
            }
            Subframe l = encodeSubframe(left, n, BPS);
            Subframe r = encodeSubframe(right, n, BPS);
            Subframe m = encodeSubframe(mid, n, BPS);
            Subframe s = encodeSubframe(side, n, BPS + 1);
            long lr = l.bits + r.bits;
            long ls = l.bits + s.bits;
            long rs = s.bits + r.bits;
            long ms = m.bits + s.bits;
            long best = Math.min(Math.min(lr, ls), Math.min(rs, ms));
            if (best == lr) {
                assignment = 1;
                l.writeTo(body);
                r.writeTo(body);
            } else if (best == ls) {
                assignment = 8;
                l.writeTo(body);
                s.writeTo(body);
            } else if (best == rs) {
                assignment = 9;
                s.writeTo(body);
                r.writeTo(body);
            } else {
                assignment = 10;
                m.writeTo(body);
                s.writeTo(body);
            }
        }
        body.alignToByte();

        BitWriter frame = new BitWriter(body.byteLength() + 32);
        frame.write(0x3FFE, 14);  // sync
        frame.write(0, 1);        // reserved
        frame.write(0, 1);        // fixed block size
        frame.write(n == BLOCK ? 12 : 7, 4);
        frame.write(0, 4);        // sample rate: lấy từ STREAMINFO
        frame.write(assignment, 4);
        frame.write(4, 3);        // 16 bit
        frame.write(0, 1);
        writeUtf8(frame, frameNumber);
        if (n != BLOCK) frame.write(n - 1, 16);
        byte[] header = frame.toByteArray();
        int crc8 = crc8(header, header.length);
        frame.write(crc8, 8);

        byte[] headerBytes = frame.toByteArray();
        byte[] bodyBytes = body.toByteArray();
        byte[] all = new byte[headerBytes.length + bodyBytes.length + 2];
        System.arraycopy(headerBytes, 0, all, 0, headerBytes.length);
        System.arraycopy(bodyBytes, 0, all, headerBytes.length, bodyBytes.length);
        int crc16 = crc16(all, all.length - 2);
        all[all.length - 2] = (byte) (crc16 >> 8);
        all[all.length - 1] = (byte) crc16;
        file.write(all);

        minBlock = Math.min(minBlock, n);
        maxBlock = Math.max(maxBlock, n);
        minFrameBytes = Math.min(minFrameBytes, all.length);
        maxFrameBytes = Math.max(maxFrameBytes, all.length);
        totalSamples += n;
        frameNumber++;
        pendingFrames = 0;
    }

    // ---- Subframe ----

    private static final class Subframe {
        int type;      // mã 6 bit
        int order;
        int bps;
        int[] samples;
        int n;
        int partitionOrder;
        int[] riceParams;
        int[] residual;
        long bits;

        void writeTo(BitWriter w) {
            w.write(0, 1);
            w.write(type, 6);
            w.write(0, 1);  // không có wasted bits
            if (type == 0) {
                w.write(samples[0], bps);
            } else if (type == 1) {
                for (int i = 0; i < n; i++) w.write(samples[i], bps);
            } else {
                for (int i = 0; i < order; i++) w.write(samples[i], bps);
                w.write(0, 2);                    // Rice 4 bit
                w.write(partitionOrder, 4);
                int parts = 1 << partitionOrder;
                int perPart = n >> partitionOrder;
                int idx = order;
                for (int p = 0; p < parts; p++) {
                    int count = (p == 0) ? perPart - order : perPart;
                    int k = riceParams[p];
                    w.write(k, 4);
                    for (int i = 0; i < count; i++) {
                        int v = residual[idx++];
                        int u = (v << 1) ^ (v >> 31);
                        int q = u >>> k;
                        w.writeUnary(q);
                        if (k > 0) w.write(u & ((1 << k) - 1), k);
                    }
                }
            }
        }
    }

    private static Subframe encodeSubframe(int[] x, int n, int bps) {
        Subframe sf = new Subframe();
        sf.samples = x;
        sf.n = n;
        sf.bps = bps;

        boolean constant = true;
        for (int i = 1; i < n; i++) {
            if (x[i] != x[0]) {
                constant = false;
                break;
            }
        }
        if (constant) {
            sf.type = 0;
            sf.bits = 8 + bps;
            return sf;
        }

        // Verbatim là phương án dự phòng
        sf.type = 1;
        sf.bits = 8L + (long) n * bps;

        int maxOrder = Math.min(4, n - 1);
        int bestOrder = -1;
        long bestSum = Long.MAX_VALUE;
        int[] work = new int[n];
        int[] bestResidual = null;
        for (int order = 0; order <= maxOrder; order++) {
            long sum = 0;
            for (int i = order; i < n; i++) {
                int r = predictResidual(x, i, order);
                work[i] = r;
                sum += Math.abs((long) r);
            }
            if (sum < bestSum) {
                bestSum = sum;
                bestOrder = order;
                bestResidual = work.clone();
            }
        }
        if (bestOrder < 0) return sf;

        long bestBits = Long.MAX_VALUE;
        int bestPo = 0;
        int[] bestParams = null;
        for (int po = 0; po <= 4; po++) {
            if ((n & ((1 << po) - 1)) != 0) break;
            int perPart = n >> po;
            if (perPart <= bestOrder) break;
            int parts = 1 << po;
            int[] params = new int[parts];
            long bits = 0;
            int idx = bestOrder;
            for (int p = 0; p < parts; p++) {
                int count = (p == 0) ? perPart - bestOrder : perPart;
                long sumU = 0;
                for (int i = 0; i < count; i++) {
                    int v = bestResidual[idx + i];
                    sumU += (v << 1) ^ (v >> 31);
                }
                int guess = 0;
                if (count > 0) {
                    long mean = sumU / count;
                    while ((1L << (guess + 1)) <= mean && guess < 14) guess++;
                }
                long bestPart = Long.MAX_VALUE;
                int bestK = 0;
                for (int k = Math.max(0, guess - 1); k <= Math.min(14, guess + 1); k++) {
                    long cost = 4;
                    for (int i = 0; i < count; i++) {
                        int v = bestResidual[idx + i];
                        long u = (v << 1) ^ (v >> 31) & 0xFFFFFFFFL;
                        u = ((long) ((v << 1) ^ (v >> 31))) & 0xFFFFFFFFL;
                        cost += (u >> k) + 1 + k;
                    }
                    if (cost < bestPart) {
                        bestPart = cost;
                        bestK = k;
                    }
                }
                params[p] = bestK;
                bits += bestPart;
                idx += count;
            }
            bits += 8L + (long) bestOrder * bps + 2 + 4;
            if (bits < bestBits) {
                bestBits = bits;
                bestPo = po;
                bestParams = params;
            }
        }
        if (bestParams != null && bestBits < sf.bits) {
            sf.type = 8 | bestOrder;
            sf.order = bestOrder;
            sf.partitionOrder = bestPo;
            sf.riceParams = bestParams;
            sf.residual = bestResidual;
            sf.bits = bestBits;
        }
        return sf;
    }

    private static int predictResidual(int[] x, int i, int order) {
        switch (order) {
            case 0: return x[i];
            case 1: return x[i] - x[i - 1];
            case 2: return x[i] - 2 * x[i - 1] + x[i - 2];
            case 3: return x[i] - 3 * x[i - 1] + 3 * x[i - 2] - x[i - 3];
            default: return x[i] - 4 * x[i - 1] + 6 * x[i - 2] - 4 * x[i - 3] + x[i - 4];
        }
    }

    // ---- Tiện ích bit ----

    private static void writeUtf8(BitWriter w, long v) {
        if (v < 0x80) {
            w.write((int) v, 8);
        } else if (v < 0x800) {
            w.write(0xC0 | (int) (v >> 6), 8);
            w.write(0x80 | (int) (v & 0x3F), 8);
        } else if (v < 0x10000) {
            w.write(0xE0 | (int) (v >> 12), 8);
            w.write(0x80 | (int) ((v >> 6) & 0x3F), 8);
            w.write(0x80 | (int) (v & 0x3F), 8);
        } else if (v < 0x200000) {
            w.write(0xF0 | (int) (v >> 18), 8);
            w.write(0x80 | (int) ((v >> 12) & 0x3F), 8);
            w.write(0x80 | (int) ((v >> 6) & 0x3F), 8);
            w.write(0x80 | (int) (v & 0x3F), 8);
        } else if (v < 0x4000000) {
            w.write(0xF8 | (int) (v >> 24), 8);
            w.write(0x80 | (int) ((v >> 18) & 0x3F), 8);
            w.write(0x80 | (int) ((v >> 12) & 0x3F), 8);
            w.write(0x80 | (int) ((v >> 6) & 0x3F), 8);
            w.write(0x80 | (int) (v & 0x3F), 8);
        } else {
            w.write(0xFC | (int) (v >> 30), 8);
            w.write(0x80 | (int) ((v >> 24) & 0x3F), 8);
            w.write(0x80 | (int) ((v >> 18) & 0x3F), 8);
            w.write(0x80 | (int) ((v >> 12) & 0x3F), 8);
            w.write(0x80 | (int) ((v >> 6) & 0x3F), 8);
            w.write(0x80 | (int) (v & 0x3F), 8);
        }
    }

    private static int crc8(byte[] data, int len) {
        int crc = 0;
        for (int i = 0; i < len; i++) {
            crc ^= data[i] & 0xFF;
            for (int b = 0; b < 8; b++) {
                crc = ((crc & 0x80) != 0) ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
            }
        }
        return crc;
    }

    private static int crc16(byte[] data, int len) {
        int crc = 0;
        for (int i = 0; i < len; i++) {
            crc ^= (data[i] & 0xFF) << 8;
            for (int b = 0; b < 8; b++) {
                crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x8005) & 0xFFFF : (crc << 1) & 0xFFFF;
            }
        }
        return crc;
    }

    /** Bộ ghi bit đơn giản (MSB trước). */
    private static final class BitWriter {
        private byte[] buf;
        private int len = 0;       // số byte đã đầy
        private long acc = 0;
        private int accBits = 0;

        BitWriter(int capacity) {
            buf = new byte[Math.max(16, capacity)];
        }

        void write(int value, int bits) {
            if (bits == 0) return;
            if (bits > 32) throw new IllegalArgumentException();
            long v = bits == 32 ? (value & 0xFFFFFFFFL) : (value & ((1L << bits) - 1));
            acc = (acc << bits) | v;
            accBits += bits;
            while (accBits >= 8) {
                accBits -= 8;
                put((byte) (acc >> accBits));
            }
            acc &= (1L << accBits) - 1;
        }

        void writeUnary(int zeros) {
            while (zeros >= 24) {
                write(0, 24);
                zeros -= 24;
            }
            write(1, zeros + 1);
        }

        void alignToByte() {
            if (accBits > 0) write(0, 8 - accBits);
        }

        int byteLength() {
            return len + (accBits > 0 ? 1 : 0);
        }

        byte[] toByteArray() {
            int total = byteLength();
            byte[] out = new byte[total];
            System.arraycopy(buf, 0, out, 0, len);
            if (accBits > 0) out[len] = (byte) (acc << (8 - accBits));
            return out;
        }

        private void put(byte b) {
            if (len == buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2);
            buf[len++] = b;
        }
    }
}

package com.aismart.ringtone;

import net.sourceforge.lame.mp3.Lame;
import net.sourceforge.lame.mp3.MPEGMode;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Bộ mã hóa MP3 (CBR) dựa trên bản Java của LAME 3.98.4.
 * Nhận PCM 16-bit xen kẽ các kênh (mono hoặc stereo) và ghi dữ liệu MP3 ra OutputStream.
 */
public final class Mp3Encoder {
    private static final int[] SUPPORTED_RATES =
            {8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000};

    private final Lame lame = new Lame();
    private final int channels;
    private float[] left = new float[0];
    private float[] right = new float[0];
    private byte[] buffer = new byte[0];

    public static boolean isSupportedSampleRate(int sampleRate) {
        for (int rate : SUPPORTED_RATES) {
            if (rate == sampleRate) return true;
        }
        return false;
    }

    public Mp3Encoder(int sampleRate, int channels, int bitrateKbps) {
        if (channels < 1 || channels > 2) {
            throw new IllegalArgumentException("MP3 chỉ hỗ trợ 1 hoặc 2 kênh");
        }
        if (!isSupportedSampleRate(sampleRate)) {
            throw new IllegalArgumentException("Tần số lấy mẫu " + sampleRate + " Hz không được MP3 hỗ trợ");
        }
        this.channels = channels;

        lame.getFlags().setInNumChannels(channels);
        lame.getFlags().setInSampleRate(sampleRate);
        lame.getFlags().setMode(channels == 1 ? MPEGMode.MONO : MPEGMode.JOINT_STEREO);
        // MPEG-2 / 2.5 (tần số thấp) chỉ cho phép tối đa 160 kbps
        int bitrate = (sampleRate < 32000 && bitrateKbps > 160) ? 160 : bitrateKbps;
        lame.getFlags().setBitRate(bitrate);
        lame.getFlags().setQuality(Lame.QUALITY_MIDDLE);
        // Không ghi khung Xing/Info (cần ghi đè đầu file sau khi mã hóa xong)
        lame.getFlags().bWriteVbrTag = false;
        lame.getId3().init(lame.getFlags());
        lame.getFlags().setWriteId3tagAutomatic(false);

        int result = lame.initParams();
        if (result < 0) {
            throw new IllegalArgumentException("LAME không nhận tham số này (mã " + result + ")");
        }
    }

    /** Mã hóa [frames] khung PCM (mỗi khung gồm [channels] mẫu) và ghi ra [out]. */
    public void encode(short[] pcm, int frames, OutputStream out) throws IOException {
        if (frames <= 0) return;
        if (left.length < frames) {
            left = new float[frames];
            right = new float[frames];
        }
        for (int i = 0; i < frames; i++) {
            // LAME nhận mẫu theo thang 32-bit (mẫu 16-bit nhân 65536)
            float l = pcm[i * channels] * 65536f;
            left[i] = l;
            right[i] = channels == 2 ? pcm[i * 2 + 1] * 65536f : l;
        }
        int needed = (int) (1.25 * frames) + 7200;
        if (buffer.length < needed) buffer = new byte[needed];
        int written = lame.encodeBuffer(left, right, frames, buffer);
        if (written < 0) throw new IllegalStateException("LAME lỗi mã " + written);
        if (written > 0) out.write(buffer, 0, written);
    }

    /** Ghi nốt phần dữ liệu còn trong bộ đệm. */
    public void finish(OutputStream out) throws IOException {
        if (buffer.length < 7200) buffer = new byte[7200];
        int written = lame.encodeFlush(buffer);
        if (written < 0) throw new IllegalStateException("LAME lỗi mã " + written);
        if (written > 0) out.write(buffer, 0, written);
    }

    public void close() {
        lame.close();
    }
}

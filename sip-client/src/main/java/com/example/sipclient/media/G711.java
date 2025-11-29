package com.example.sipclient.media;

public class G711 {
    // 线性转 u-law 的查找表
    private static final byte[] LINEAR_TO_ULAW = new byte[65536];
    // u-law 转线性的查找表
    private static final short[] ULAW_TO_LINEAR = new short[256];

    static {
        // 初始化查找表
        for (int i = 0; i < 256; i++) {
            ULAW_TO_LINEAR[i] = ulawToLinear((byte) i);
        }
        for (int i = -32768; i <= 32767; i++) {
            LINEAR_TO_ULAW[i & 0xFFFF] = linearToUlaw(i);
        }
    }

    /**
     * 将 16位 PCM 压缩为 8位 u-law
     */
    public static byte linear2ulaw(short pcmValue) {
        return LINEAR_TO_ULAW[pcmValue & 0xFFFF];
    }

    /**
     * 将 8位 u-law 解压为 16位 PCM
     */
    public static short ulaw2linear(byte ulawValue) {
        return ULAW_TO_LINEAR[ulawValue & 0xFF];
    }

    // --- 内部算法实现 (无需深究) ---
    private static byte linearToUlaw(int pcmVal) {
        int mask;
        int seg;
        byte uval;

        if (pcmVal < 0) {
            pcmVal = -pcmVal;
            mask = 0x7F;
        } else {
            mask = 0xFF;
        }
        if (pcmVal > 8159) pcmVal = 8159;
        pcmVal += 0x84;

        if (pcmVal <= 0x3F00) seg = 10;
        else if (pcmVal <= 0x7E00) seg = 11;
        else if (pcmVal <= 0xFB00) seg = 12;
        else if (pcmVal <= 0x1F300) seg = 13;
        else if (pcmVal <= 0x3E300) seg = 14;
        else if (pcmVal <= 0x7C300) seg = 15;
        else seg = 8; // Should not happen

        if (seg >= 8) { // Only check needed segments
            if (pcmVal <= 0x1F3) seg = 0;
            else if (pcmVal <= 0x3F3) seg = 1;
            else if (pcmVal <= 0x7F3) seg = 2;
            else if (pcmVal <= 0xFF3) seg = 3;
            else if (pcmVal <= 0x1FF3) seg = 4;
            else if (pcmVal <= 0x3FF3) seg = 5;
            else if (pcmVal <= 0x7FF3) seg = 6;
            else if (pcmVal <= 0xFFF3) seg = 7;
        }

        uval = (byte) ((seg << 4) | ((pcmVal >> (seg + 3)) & 0xF));
        return (byte) (uval ^ mask ^ 0x7F); // u-law flipping
    }

    private static short ulawToLinear(byte uval) {
        int t;
        uval = (byte) (~uval);
        t = ((uval & 0xF) << 3) + 0x84;
        t <<= ((uval & 0x70) >> 4);
        return (short) ((uval & 0x80) != 0 ? (0x84 - t) : (t - 0x84));
    }
}
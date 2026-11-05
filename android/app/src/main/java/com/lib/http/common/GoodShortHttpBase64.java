package com.lib.http.common;

/** Port of com.lib.http.common.Base64.encode (sign header output). */
public final class GoodShortHttpBase64 {
    private static final int ASCII_SI = 15;
    private static final int HIGH_BIT = 0x80;
    private static byte[] decodeTable = new byte[128];
    private static char[] encodeTable = new char[64];

    static {
        int r12 = 0;
        for (int r22 = 0; r22 < 128; r22++) {
            decodeTable[r22] = -1;
        }
        for (int r02 = 90; r02 >= 65; r02--) {
            decodeTable[r02] = (byte) (r02 - 65);
        }
        for (int r03 = 122; r03 >= 97; r03--) {
            decodeTable[r03] = (byte) (r03 - 71);
        }
        for (int r04 = 57; r04 >= 48; r04--) {
            decodeTable[r04] = (byte) (r04 + 4);
        }
        decodeTable[43] = 62;
        decodeTable[47] = 63;
        for (int r05 = 0; r05 <= 25; r05++) {
            encodeTable[r05] = (char) (r05 + 65);
        }
        int r06 = 26;
        int r62 = 0;
        while (r06 <= 51) {
            encodeTable[r06] = (char) (r62 + 97);
            r06++;
            r62++;
        }
        int r07 = 52;
        while (r07 <= 61) {
            encodeTable[r07] = (char) (r12 + 48);
            r07++;
            r12++;
        }
        encodeTable[62] = '+';
        encodeTable[63] = '/';
    }

    private GoodShortHttpBase64() {}

    private static char enc(byte idx) {
        return encodeTable[idx & 0xFF];
    }

    public static String encode(byte[] bArr) {
        int r32;
        if (bArr == null) {
            return null;
        }
        int length = bArr.length * 8;
        if (length == 0) {
            return "";
        }
        int r22 = length % 24;
        int r02 = length / 24;
        if (r22 != 0) {
            r32 = r02 + 1;
        } else {
            r32 = r02;
        }
        char[] cArr = new char[r32 * 4];
        int r52 = 0;
        int r62 = 0;
        for (int r42 = 0; r42 < r02; r42++) {
            byte b10 = bArr[r52];
            int r92 = r52 + 2;
            byte b11 = bArr[r52 + 1];
            r52 += 3;
            byte b12 = bArr[r92];
            byte b13 = (byte) (b11 & ASCII_SI);
            byte b14 = (byte) (b10 & 3);
            int r122 = b10 & HIGH_BIT;
            int r82 = b10 >> 2;
            if (r122 != 0) {
                r82 ^= 192;
            }
            byte b15 = (byte) r82;
            int r123 = b11 & HIGH_BIT;
            int r72 = b11 >> 4;
            if (r123 != 0) {
                r72 ^= 240;
            }
            byte b16 = (byte) r72;
            int r12;
            if ((b12 & HIGH_BIT) == 0) {
                r12 = b12 >> 6;
            } else {
                r12 = (b12 >> 6) ^ 252;
            }
            byte b17 = (byte) r12;
            cArr[r62] = enc(b15);
            cArr[r62 + 1] = enc((byte) (b16 | (b14 << 4)));
            int r73 = r62 + 3;
            cArr[r62 + 2] = enc((byte) ((b13 << 2) | b17));
            r62 += 4;
            cArr[r73] = enc((byte) (b12 & 63));
        }
        if (r22 == 8) {
            byte b18 = bArr[r52];
            byte b19 = (byte) (b18 & 3);
            int r23 = b18 & HIGH_BIT;
            int r15 = b18 >> 2;
            if (r23 != 0) {
                r15 ^= 192;
            }
            byte b20 = (byte) r15;
            cArr[r62] = enc(b20);
            cArr[r62 + 1] = enc((byte) (b19 << 4));
            cArr[r62 + 2] = '=';
            cArr[r62 + 3] = '=';
        } else if (r22 == 16) {
            byte b21 = bArr[r52];
            byte b22 = bArr[r52 + 1];
            byte b23 = (byte) (b22 & ASCII_SI);
            byte b24 = (byte) (b21 & 3);
            int r53 = b21 & HIGH_BIT;
            int r13 = b21 >> 2;
            if (r53 != 0) {
                r13 ^= 192;
            }
            byte b25 = (byte) r13;
            int r54 = b22 & HIGH_BIT;
            int r152 = b22 >> 4;
            if (r54 != 0) {
                r152 ^= 240;
            }
            byte b26 = (byte) r152;
            cArr[r62] = enc(b25);
            cArr[r62 + 1] = enc((byte) (b26 | (b24 << 4)));
            cArr[r62 + 2] = enc((byte) (b23 << 2));
            cArr[r62 + 3] = '=';
        }
        return new String(cArr);
    }
}

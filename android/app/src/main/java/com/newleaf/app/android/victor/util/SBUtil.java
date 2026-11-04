package com.newleaf.app.android.victor.util;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

/** JNI stubs required by libstupid.so (ReelShort signing/crypto). */
public class SBUtil {
    public static int PRIVATE_KEY_OLD_VERSION;
    public static int PRIVATE_KEY_VERSION;
    public static int feedbackSignKey;
    public static int normalSignKey;
    public static int reportSignKey;

    static {
        System.loadLibrary("stupid");
        normalSignKey = 1;
        reportSignKey = 2;
        feedbackSignKey = 3;
        PRIVATE_KEY_OLD_VERSION = 1;
        PRIVATE_KEY_VERSION = 2;
    }

    public static native byte[] base64Decode(String str);

    public static native byte[] decryptByte(byte[] bArr, int r1) throws IOException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException;

    public static native String decryptStr(String str, int r1) throws IOException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException;

    public static native byte[] encryptByte(byte[] bArr, int r1) throws UnsupportedEncodingException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException;

    public static native String encryptSrc(String str, int r1) throws UnsupportedEncodingException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException;

    public static native int getCpuCoreNumber();

    public static native int getCpuLdCaches();

    public static native int getCpuMaxFreq();

    public static native String getDbPwd();

    public static native String getRequestLaunchTime(String str);

    public static native String getSystemLaunchTime(String str);

    public static native void setLogHookLogEnabled(boolean z);

    public static native void setLogTagFilterEnabled(boolean z);

    public static native void setThreadCreateLogEnabled(boolean z);

    public static native void setThreadStackShrinkEnabled(boolean z);

    public static native String sha256Encrypt(String str, int r1);
}

/*
 * Copyright (c) 2017. Kaede <kidhaibara@gmail.com>.
 *
 */

package com.meituan.android.walle.sample;


import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Message digest (md5, sha-256...) utils.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class DigestUtils {

    private static final int BUFFER_SIZE = 4 * 1024; // 4KB

    /**
     * md5 digest text with 'utf-8' encoding.
     */
    public static String md5(String plainText) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        digest.reset();
        digest.update(plainText.getBytes(Charset.forName("UTF-8")));
        byte[] hash = digest.digest();
        return toHexString(hash);
    }

    /**
     * digest file's md5.
     */
    public static String md5(File file) throws IOException, NoSuchAlgorithmException {
        if (file != null && file.exists()) {
            InputStream in = null;
            try {
                in = new BufferedInputStream(new FileInputStream(file));
                DigestInputStream dis = new DigestInputStream(in, MessageDigest.getInstance("MD5"));
                in = dis;
                byte[] buffer = new byte[BUFFER_SIZE];
                while (dis.read(buffer) > 0) ;
                return DigestUtils.toHexString(dis.getMessageDigest().digest());

            } finally {
                IOUtils.closeQuietly(in);
            }
        } else {
            throw new IOException("File is null or not exists.");
        }
    }

    /**
     * sha-256 digest text with 'utf-8' encoding.
     */
    public static String sha256(String plainText) throws NoSuchAlgorithmException {
        return sha256(plainText.getBytes(Charset.forName("UTF-8")), null);
    }

    public static String sha256(byte[] bytes, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.reset();
        digest.update(bytes);
        if (salt != null)
            digest.update(salt);
        byte[] hash = digest.digest();
        return toHexString(hash);
    }

    public static String toHexString(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            int intVal = b & 0xff;
            if (intVal < 0x10)
                hexString.append('0');

            hexString.append(Integer.toHexString(intVal));
        }
        return hexString.toString();
    }
}

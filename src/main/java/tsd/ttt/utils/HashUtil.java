package tsd.ttt.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {
    private HashUtil() {
    }

    public static byte[] sha1Bytes(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            try (FileInputStream input = new FileInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is unavailable", ex);
        }
    }

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }

        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(HEX_DIGITS[(b >> 4) & 0xF]);
            out.append(HEX_DIGITS[b & 0xF]);
        }
        return out.toString();
    }
}

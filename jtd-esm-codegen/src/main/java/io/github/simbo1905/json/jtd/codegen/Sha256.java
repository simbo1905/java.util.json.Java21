package io.github.simbo1905.json.jtd.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/// SHA-256 helpers for deterministic output naming.
final class Sha256 {
    private Sha256() {}

    static byte[] digest(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return digest(in);
        }
    }

    static byte[] digest(InputStream in) throws IOException {
        final MessageDigest md = messageDigest();
        final byte[] buf = new byte[16 * 1024];
        for (int r; (r = in.read(buf)) >= 0; ) {
            if (r > 0) {
                md.update(buf, 0, r);
            }
        }
        return md.digest();
    }

    static String hex(byte[] digest) {
        final var out = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            out.append(HEX[(b >>> 4) & 0x0F]).append(HEX[b & 0x0F]);
        }
        return out.toString();
    }

    static String hexPrefix8(byte[] digest) {
        // 8 hex chars == 4 bytes.
        if (digest.length < 4) {
            throw new IllegalArgumentException("digest too short: " + digest.length);
        }
        final var out = new StringBuilder(8);
        for (int i = 0; i < 4; i++) {
            final byte b = digest[i];
            out.append(HEX[(b >>> 4) & 0x0F]).append(HEX[b & 0x0F]);
        }
        return out.toString();
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the Java platform.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();
}


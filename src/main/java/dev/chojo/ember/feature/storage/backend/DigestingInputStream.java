/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * An {@link InputStream} that computes the SHA-256 of the bytes as they flow through. The digest is
 * read once the stream has been drained, typically right after a backend has stored it.
 */
public final class DigestingInputStream extends FilterInputStream {
    private final MessageDigest digest;

    /**
     * Wraps a stream.
     *
     * @param delegate the bytes to digest
     */
    public DigestingInputStream(InputStream delegate) {
        super(delegate);
        try {
            this.digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) digest.update((byte) b);
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) digest.update(b, off, n);
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) return 0;
        byte[] buffer = new byte[(int) Math.min(n, 8192)];
        long skipped = 0;
        while (skipped < n) {
            int read = read(buffer, 0, (int) Math.min(buffer.length, n - skipped));
            if (read < 0) break;
            skipped += read;
        }
        return skipped;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    /** The hex SHA-256 of everything read so far. */
    public String hexDigest() {
        return HexFormat.of().formatHex(digest.digest());
    }
}

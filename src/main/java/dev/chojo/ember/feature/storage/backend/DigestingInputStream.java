/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

import dev.chojo.ember.util.Sha256;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Computes the SHA-256 of the bytes read through it; skipped bytes are read, so they count too. */
public final class DigestingInputStream extends FilterInputStream {
    private final MessageDigest digest = Sha256.digest();

    public DigestingInputStream(InputStream delegate) {
        super(delegate);
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

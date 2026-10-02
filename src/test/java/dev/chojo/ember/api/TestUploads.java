/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.UploadedFile;
import jakarta.servlet.http.Part;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uploaded files for tests of services that take one as it arrived in a request.
 */
public final class TestUploads {
    private TestUploads() {}

    /**
     * A file as a multipart request hands it over.
     *
     * @param fileName the name it was uploaded under
     * @param type     the type it was declared as, or null
     * @param data     its bytes
     * @return the uploaded file
     */
    public static UploadedFile of(String fileName, String type, byte[] data) {
        return of(fileName, type, data.length, new ByteArrayInputStream(data));
    }

    /**
     * A file that claims a size and whose bytes cannot be read.
     *
     * @param fileName the name it was uploaded under
     * @param size     the size it claims
     * @return the uploaded file
     */
    public static UploadedFile unreadable(String fileName, long size) {
        return of(fileName, "application/pdf", size, new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("the connection dropped");
            }
        });
    }

    private static UploadedFile of(String fileName, String type, long size, InputStream content) {
        var part = mock(Part.class);
        when(part.getSubmittedFileName()).thenReturn(fileName);
        when(part.getContentType()).thenReturn(type);
        when(part.getSize()).thenReturn(size);
        try {
            when(part.getInputStream()).thenReturn(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new UploadedFile(part);
    }
}

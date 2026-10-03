/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.UploadedFile;
import io.javalin.testtools.Request;
import jakarta.servlet.http.Part;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

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

    /**
     * A request sending one file the way a browser's form does, for a route test going over HTTP.
     *
     * <p>Sent with {@code client.request(path, ...)} after the session's own consumer, since the
     * client's {@code post} would put its own body over this one.
     *
     * @param fileName the name it is uploaded under
     * @param data     its bytes
     * @return what turns a request into the upload
     */
    public static Consumer<Request.Builder> multipart(String fileName, byte[] data) {
        return multipart(fileName, data, Map.of());
    }

    /**
     * The same, with text fields sent beside the file, as a form with inputs next to its file sends them.
     *
     * @param fileName the name it is uploaded under
     * @param data     its bytes
     * @param fields   the text fields by name
     * @return what turns a request into the upload
     */
    public static Consumer<Request.Builder> multipart(String fileName, byte[] data, Map<String, String> fields) {
        String boundary = "ember-test-boundary";
        var body = new ByteArrayOutputStream();
        fields.forEach((name, value) -> body.writeBytes(
                ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                        .getBytes(StandardCharsets.UTF_8)));
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                        + "\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.writeBytes(data);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return builder -> builder.header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .post(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
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

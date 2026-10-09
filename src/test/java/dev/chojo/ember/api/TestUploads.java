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
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uploaded files for tests of services that take one as it arrived in a request.
 */
public final class TestUploads {
    private static final String BOUNDARY = "ember-test-boundary";
    private static final byte[] FORM_TAIL = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8);
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

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
        byte[] body = multipartBody(fileName, data, fields);
        return builder -> builder.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .post(HttpRequest.BodyPublishers.ofByteArray(body));
    }

    /**
     * The same upload sent in chunks, without announcing its length, as a client streaming a file does.
     *
     * @param fileName the name it is uploaded under
     * @param data     its bytes
     * @return what turns a request into the upload
     */
    public static Consumer<Request.Builder> chunkedMultipart(String fileName, byte[] data) {
        byte[] body = multipartBody(fileName, data, Map.of());
        return builder -> builder.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .post(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)));
    }

    /**
     * An upload of a file of the given size that the test never holds in memory: a PDF header followed by
     * zeros, streamed as it is sent, with its length announced.
     *
     * @param fileName the name it is uploaded under
     * @param size     the file's size in bytes
     * @return what turns a request into the upload
     */
    public static Consumer<Request.Builder> streamedMultipart(String fileName, long size) {
        long length = formHead(fileName, Map.of()).length + size + FORM_TAIL.length;
        return builder -> builder.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .post(HttpRequest.BodyPublishers.fromPublisher(
                        HttpRequest.BodyPublishers.ofInputStream(() -> streamedBody(fileName, size)), length));
    }

    /**
     * The same streamed upload sent in chunks, without announcing its length.
     *
     * @param fileName the name it is uploaded under
     * @param size     the file's size in bytes
     * @return what turns a request into the upload
     */
    public static Consumer<Request.Builder> streamedChunkedMultipart(String fileName, long size) {
        return builder -> builder.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .post(HttpRequest.BodyPublishers.ofInputStream(() -> streamedBody(fileName, size)));
    }

    private static InputStream streamedBody(String fileName, long size) {
        return new SequenceInputStream(Collections.enumeration(List.of(
                new ByteArrayInputStream(formHead(fileName, Map.of())),
                new ByteArrayInputStream(PDF_HEADER),
                new Zeros(size - PDF_HEADER.length),
                new ByteArrayInputStream(FORM_TAIL))));
    }

    private static byte[] multipartBody(String fileName, byte[] data, Map<String, String> fields) {
        var body = new ByteArrayOutputStream();
        body.writeBytes(formHead(fileName, fields));
        body.writeBytes(data);
        body.writeBytes(FORM_TAIL);
        return body.toByteArray();
    }

    private static byte[] formHead(String fileName, Map<String, String> fields) {
        var head = new StringBuilder();
        fields.forEach((name, value) -> head.append("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\""
                + name + "\"\r\n\r\n" + value + "\r\n"));
        head.append("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n");
        return head.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** A stream of the given number of zero bytes, made up as it is read. */
    private static final class Zeros extends InputStream {
        private long left;

        private Zeros(long count) {
            this.left = count;
        }

        @Override
        public int read() {
            if (left <= 0) return -1;
            left--;
            return 0;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (length == 0) return 0;
            if (left <= 0) return -1;
            int count = (int) Math.min(length, left);
            Arrays.fill(buffer, offset, offset + count, (byte) 0);
            left -= count;
            return count;
        }
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

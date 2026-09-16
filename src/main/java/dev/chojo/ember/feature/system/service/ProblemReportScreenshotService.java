/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.media.service.MediaStorageService;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

/**
 * The picture a report carries: what is accepted, where it is kept, and how it is let go of.
 *
 * <p>A picture of a page holds whatever was on that page, which on most screens of this product is
 * the data of people who never agreed to appear in a bug report. Everything here is arranged around
 * that: only what the browser says it sent is accepted, only what it actually is is believed, and
 * the bytes travel no further than the instance until somebody says so.
 *
 * <p>Kept as a file of the instance rather than of the station. A station's library is a place
 * people browse, and a picture attached to a bug report has no business appearing in it.
 */
@Singleton
public class ProblemReportScreenshotService {
    private static final Logger log = LoggerFactory.getLogger(ProblemReportScreenshotService.class);

    /** What a picture of a page may weigh. Generous for a screen, far short of what a page may hold. */
    private static final int MAX_BYTES = 3 * 1024 * 1024;

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MAGIC = {'W', 'E', 'B', 'P'};

    private final MediaLibraryService media;

    @Inject
    public ProblemReportScreenshotService(MediaLibraryService media) {
        this.media = media;
    }

    /**
     * Takes the picture a report was sent with and keeps it, or nothing where none was sent.
     *
     * <p>What arrives is text, so it is decoded, weighed and then read: a type somebody wrote in a
     * request says nothing, and the first bytes of the picture say everything. Anything that is not
     * a PNG or a WebP is refused rather than stored and puzzled over later.
     *
     * @param encoded  the picture as base64, with or without the {@code data:} preamble a browser
     *                 writes, or null where the report carries none
     * @param memberId who sent it, for the record the library keeps of its uploads
     * @return the identifier of the stored file, or empty where nothing was sent
     */
    public Optional<Integer> store(String encoded, Integer memberId) {
        if (encoded == null || encoded.isBlank()) return Optional.empty();
        byte[] picture = decode(encoded);
        String type = typeOf(picture);
        try {
            var stored = media.upload(null, null, memberId, fileName(type), type, picture);
            return Optional.of(stored.id());
        } catch (IOException e) {
            log.warn("The picture of a problem report could not be kept", e);
            throw new BadRequestResponse("the picture could not be stored");
        }
    }

    /** The picture of a report, for the screen that shows it and for the delivery that carries it. */
    public Optional<MediaStorageService.FileData> read(int fileId) {
        return media.readById(fileId);
    }

    /**
     * Lets go of a picture, which happens when its report is deleted or swept.
     *
     * <p>Failure is logged and swallowed: a report the operator asked to be rid of goes whether or
     * not its bytes could be reached, and a file left behind is a smaller wrong than a report that
     * refuses to be deleted.
     */
    public void forget(Integer fileId) {
        if (fileId == null) return;
        try {
            media.deleteFile(fileId);
        } catch (RuntimeException e) {
            log.warn("The picture of a problem report could not be removed", e);
        }
    }

    private static byte[] decode(String encoded) {
        String payload = encoded;
        int comma = payload.indexOf(',');
        if (payload.startsWith("data:") && comma > 0) payload = payload.substring(comma + 1);
        byte[] picture;
        try {
            picture = Base64.getDecoder().decode(payload.strip());
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("the picture is not readable");
        }
        if (picture.length == 0) throw new BadRequestResponse("the picture is empty");
        if (picture.length > MAX_BYTES) throw new BadRequestResponse("the picture is too large");
        return picture;
    }

    /** What the bytes say they are, rather than what the request claimed. */
    private static String typeOf(byte[] picture) {
        if (startsWith(picture, PNG_MAGIC, 0)) return "image/png";
        if (startsWith(picture, RIFF_MAGIC, 0) && startsWith(picture, WEBP_MAGIC, 8)) return "image/webp";
        throw new BadRequestResponse("the picture is neither a PNG nor a WebP");
    }

    private static boolean startsWith(byte[] picture, byte[] magic, int offset) {
        if (picture.length < offset + magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (picture[offset + i] != magic[i]) return false;
        }
        return true;
    }

    private static String fileName(String type) {
        return "problem-report" + ("image/png".equals(type) ? ".png" : ".webp");
    }
}

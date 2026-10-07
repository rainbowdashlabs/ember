/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.media.image.MediaTypes;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import io.javalin.http.UploadedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/**
 * The one way a file is taken in, whoever hands it over: somebody at the station, an association
 * manager, the mail import, or a member reporting a loss with evidence attached.
 *
 * <p>Every file passes the same checks. It may weigh no more than the station takes for one file, the
 * station must have room for it, and where the bytes say what kind of file it is, the bytes decide
 * rather than the name or the type the sender declared. A file whose name claims one kind and whose
 * bytes are another is refused; one whose bytes say nothing is kept under the type it was declared as,
 * unless that type is one the bytes would have shown, in which case it is kept as plain bytes and never
 * shown inline as something it is not.
 *
 * <p>Who hands a file over decides only which refusal says no, which is what {@link Refusals} carries.
 */
@Singleton
public class DocumentIntake {
    private final StorageQuotaService quota;

    @Inject
    public DocumentIntake(StorageQuotaService quota) {
        this.quota = quota;
    }

    /**
     * Reads an upload, refusing one that is missing, larger than the station takes for one file, or
     * cannot be read. The size is checked before the bytes are, so a file that is refused anyway is
     * never held in memory.
     *
     * @param stationId the station the file is for
     * @param file      the uploaded file, or null where the request carried none
     * @param refusals  what to refuse with
     * @return the file as it arrived
     */
    public Upload read(int stationId, @Nullable UploadedFile file, Refusals refusals) {
        return readAtMost(quota.perFileLimitBytes(stationId), file, refusals);
    }

    /**
     * Reads an upload, refusing one that is missing, larger than the limit given, or cannot be read, for
     * a file whose limit is not the station's. The size is checked before the bytes are.
     *
     * @param maxBytes the largest file taken
     * @param file     the uploaded file, or null where the request carried none
     * @param refusals what to refuse with
     * @return the file as it arrived
     */
    public static Upload readAtMost(long maxBytes, @Nullable UploadedFile file, Refusals refusals) {
        if (file == null) throw refusals.missing().raise();
        if (file.size() > maxBytes) throw refusals.tooLarge().raise();
        try (var in = file.content()) {
            return new Upload(file.filename(), file.contentType(), in.readAllBytes());
        } catch (IOException e) {
            throw refusals.unreadable().raise();
        }
    }

    /**
     * Decides whether a file may be kept, and as what.
     *
     * @param stationId the station the file is for
     * @param category  what the station keeps it as, which decides the room it is measured against
     * @param upload    the file
     * @return the type it is kept as, or why it is not kept
     */
    public Verdict judge(int stationId, StorageCategory category, Upload upload) {
        long size = upload.data().length;
        long ceiling = quota.perFileLimitBytes(stationId);
        if (size > ceiling) {
            return new Verdict.Refused(
                    Reason.TOO_LARGE, "%d bytes, over the station's limit of %d".formatted(size, ceiling));
        }
        String sniffed = ContentSniffer.sniff(upload.data());
        if (sniffed != null && !ContentSniffer.nameAgrees(upload.fileName(), sniffed)) {
            return new Verdict.Refused(
                    Reason.NOT_WHAT_IT_IS_CALLED,
                    "Named as one kind of file and made of another (%s)".formatted(sniffed));
        }
        try {
            quota.checkQuota(stationId, category, size);
        } catch (StorageQuotaService.StorageQuotaExceededException e) {
            return new Verdict.Refused(Reason.NO_ROOM, "The station has no room left for it");
        }
        if (sniffed != null) return new Verdict.Taken(sniffed, true);
        return new Verdict.Taken(unrecognisedType(upload), false);
    }

    /**
     * Takes a file in, refusing it with the caller's own refusal where it may not be kept.
     *
     * @param stationId the station the file is for
     * @param category  what the station keeps it as
     * @param upload    the file
     * @param refusals  what to refuse with
     * @return the type it is kept as
     */
    public String take(int stationId, StorageCategory category, Upload upload, Refusals refusals) {
        return switch (judge(stationId, category, upload)) {
            case Verdict.Taken taken -> taken.mimeType();
            case Verdict.Refused refused -> throw refusals.of(refused.reason()).raise();
        };
    }

    /**
     * Reads an upload and takes it in, in one go, for a caller that keeps the file itself.
     *
     * @param stationId the station the file is for
     * @param category  what the station keeps it as
     * @param file      the uploaded file, or null where the request carried none
     * @param refusals  what to refuse with
     * @return the file, carrying the type it is kept as in place of the one it was declared as
     */
    public Upload admit(int stationId, StorageCategory category, @Nullable UploadedFile file, Refusals refusals) {
        var upload = read(stationId, file, refusals);
        return new Upload(upload.fileName(), take(stationId, category, upload, refusals), upload.data());
    }

    /**
     * What a document is called: the title given, or the name of its file where none was.
     *
     * @param title  the title the uploader typed, or null
     * @param upload the file
     * @return the title, stripped
     */
    public static String titleOf(@Nullable String title, Upload upload) {
        String chosen = title == null || title.isBlank() ? upload.fileName() : title;
        return chosen.strip();
    }

    private static String unrecognisedType(Upload upload) {
        if (ContentSniffer.claimsAKnownKind(upload.fileName(), upload.declaredType())) return MediaTypes.UNTYPED;
        return Objects.requireNonNullElse(upload.declaredType(), MediaTypes.UNTYPED);
    }

    /**
     * A file as it arrived.
     *
     * @param fileName     the name it arrived under
     * @param declaredType the type the sender declared, or null
     * @param data         its bytes
     */
    public record Upload(String fileName, @Nullable String declaredType, byte[] data) {}

    /** Why a file was not taken. */
    public enum Reason {
        /** Larger than the station takes for one file. */
        TOO_LARGE,
        /** Called one kind of file and made of another. */
        NOT_WHAT_IT_IS_CALLED,
        /** More than the station has room for. */
        NO_ROOM
    }

    /** What became of a file offered for keeping. */
    public sealed interface Verdict {
        /**
         * Kept.
         *
         * @param mimeType   the type it is kept as
         * @param recognised whether the bytes said what it is, rather than the sender
         */
        record Taken(String mimeType, boolean recognised) implements Verdict {}

        /**
         * Not kept.
         *
         * @param reason why
         * @param detail the reason in words, for a log
         */
        record Refused(Reason reason, String detail) implements Verdict {}
    }

    /**
     * The refusals one way in answers with.
     *
     * @param missing    the request carried no file
     * @param tooLarge   the file is larger than the station takes for one
     * @param unreadable the bytes could not be read
     * @param noRoom     the station has no room left for it
     * @param misnamed   the file is called one kind and made of another
     */
    public record Refusals(Refusal missing, Refusal tooLarge, Refusal unreadable, Refusal noRoom, Refusal misnamed) {

        Refusal of(Reason reason) {
            return switch (reason) {
                case TOO_LARGE -> tooLarge;
                case NOT_WHAT_IT_IS_CALLED -> misnamed;
                case NO_ROOM -> noRoom;
            };
        }
    }
}

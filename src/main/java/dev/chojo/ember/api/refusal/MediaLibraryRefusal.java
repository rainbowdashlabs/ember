/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#MEDIA_LIBRARY}: the media library.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum MediaLibraryRefusal implements Refusal {
    /** A file withdrawn by somebody who did not upload it and does not manage the library. */
    FILE_NOT_YOURS_TO_REMOVE(18, HttpStatus.FORBIDDEN, "Only the members who uploaded a file can remove it"),

    /** A folder written down without a name. */
    FOLDER_NEEDS_A_NAME(19, HttpStatus.BAD_REQUEST, "A folder needs a name, so nothing was saved"),

    /** A tag written down without a name. */
    FILE_TAG_NEEDS_A_NAME(20, HttpStatus.BAD_REQUEST, "A tag needs a name, so nothing was saved"),

    /** A file of the public site that is gone, addressed by the hash of its bytes. */
    PUBLIC_FILE_NOT_HERE(21, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** The station a public file was addressed through, which no address of this instance names. */
    STATION_NOT_HERE_BEHIND_PUBLIC_FILE(22, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /**
     * A file in the media library that is gone, or one held back from this reader. The two are one
     * code deliberately: telling them apart would say that the file is there and withheld.
     */
    FILE_NOT_HERE(1, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /**
     * A picture in the media library that is gone, or one held back from this reader. One code for
     * the same reason as the file above.
     */
    PICTURE_NOT_HERE(2, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** An upload to the library that arrived without the file itself. */
    UPLOAD_MISSING_FILE(4, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A file bigger than this instance takes. */
    UPLOAD_TOO_LARGE(5, HttpStatus.CONTENT_TOO_LARGE, "That file is larger than this instance accepts"),

    /** An upload that was read and then refused for what it held. */
    UPLOAD_NOT_SAVED(6, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** An upload that broke on the way in, which is Ember's to look into rather than the reader's. */
    UPLOAD_NOT_PROCESSED(7, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A file that went between being read and being deleted. */
    FILE_NOT_DELETED(8, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file whose description or alternative text could not be written. */
    FILE_NOT_HERE_ON_DETAIL_CHANGE(9, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file that could not be moved into another folder. */
    FILE_NOT_HERE_ON_MOVE(10, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A folder that went before its details could be written. */
    FOLDER_NOT_HERE_ON_CHANGE(11, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** A folder that was already gone when its deletion was asked for. */
    FOLDER_NOT_HERE_ON_DELETE(12, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** A tag that went before its name or colour could be written. */
    FILE_TAG_NOT_HERE_ON_CHANGE(13, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /** A tag that was already gone when its deletion was asked for. */
    FILE_TAG_NOT_HERE_ON_DELETE(14, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /** A tag that could not be put on a file. */
    FILE_TAG_NOT_HERE_ON_ASSIGNMENT(15, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /** A tag that could not be taken off a file. */
    FILE_TAG_NOT_HERE_ON_REMOVAL(16, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /** A file in the media library deleted while news entries or appointments still hand it out, naming them. */
    MEDIA_FILE_STILL_ATTACHED(
            23,
            HttpStatus.BAD_REQUEST,
            "That file is still attached to news entries or appointments, so it was not deleted. "
                    + "Detach it there first. Places it is attached to");

    private final Definition definition;

    MediaLibraryRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.MEDIA_LIBRARY, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

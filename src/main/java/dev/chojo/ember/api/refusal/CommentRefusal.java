/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#COMMENTS}: comments and notes.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum CommentRefusal implements Refusal {
    /** A list of comments asked for on a day that is not a date. */
    COMMENT_DAY_NOT_A_DATE(1, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A comment left with nothing written in it. */
    COMMENT_NEEDS_TEXT(2, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment being changed that is gone. */
    COMMENT_NOT_HERE_ON_CHANGE(3, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** Somebody else's comment, being changed. */
    COMMENT_NOT_YOURS_TO_CHANGE(4, HttpStatus.FORBIDDEN, "You can only change comments you wrote yourself"),

    /** A comment changed to nothing at all. */
    COMMENT_CHANGE_NEEDS_TEXT(5, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment that went between being changed and being read back. */
    COMMENT_NOT_HERE_AFTER_CHANGE(6, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /**
     * A comment being deleted that is gone, or whose station could not be read behind it. The two
     * are one code deliberately: telling them apart would say that the comment sits at a station
     * the caller may not see.
     */
    COMMENT_NOT_HERE_ON_DELETE(7, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** Somebody else's comment, being deleted by somebody who does not moderate. */
    COMMENT_NOT_YOURS_TO_DELETE(8, HttpStatus.FORBIDDEN, "You can only delete comments you wrote yourself"),

    /** A comment that went between being read and being deleted. */
    COMMENT_NOT_DELETED(9, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /**
     * An answer to a comment that is not on the same appointment, entry, file or ticket, or not
     * there at all. Answered as a comment that is not here, which from where the answer was written
     * it is not.
     */
    COMMENT_PARENT_ELSEWHERE(13, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** Notes of a kind this reader may neither read nor write. */
    NOTES_NOT_YOURS(10, HttpStatus.FORBIDDEN, "You may not read or write notes of this kind"),

    /** A note saved with nothing written in it. */
    NOTE_NEEDS_TEXT(11, HttpStatus.BAD_REQUEST, "A note needs something written in it"),

    /** A history asked for where nothing has been noted yet. */
    NOTE_NOT_HERE(12, HttpStatus.NOT_FOUND, "There is nothing noted here yet");

    private final Definition definition;

    CommentRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.COMMENTS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

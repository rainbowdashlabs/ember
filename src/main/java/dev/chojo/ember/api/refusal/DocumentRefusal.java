/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#DOCUMENTS}: documents.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum DocumentRefusal implements Refusal {
    /** Documents at all, at a station that has switched them off. */
    DOCUMENTS_SWITCHED_OFF(1, HttpStatus.NOT_FOUND, "This station does not keep documents"),

    /** The documents of a member the reader neither manages nor answers for. */
    DOCUMENT_LIST_NOT_YOURS(2, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS),

    /** A hidden document being written by somebody who may not hide things. */
    DOCUMENT_HIDING_NOT_ALLOWED(3, HttpStatus.FORBIDDEN, "You may not mark a document as hidden"),

    /** An upload naming members, by somebody who may not file documents against one. */
    DOCUMENT_MEMBERS_NOT_YOURS_TO_NAME(5, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** A document whose stored file is gone. */
    DOCUMENT_CONTENT_NOT_HERE(6, HttpStatus.NOT_FOUND, Sentences.DOCUMENT_NOT_HERE),

    /** A document whose tile picture could not be had. */
    DOCUMENT_THUMBNAIL_NOT_HERE(7, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A document marked hidden, read by somebody who may not see hidden ones. */
    DOCUMENT_HIDDEN_FROM_YOU(8, HttpStatus.NOT_FOUND, Sentences.DOCUMENT_NOT_HERE),

    /** A document of somebody the reader does not answer for. */
    DOCUMENT_NOT_YOURS_TO_READ(9, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS),

    /** A change to a document by somebody who may read it but not write it. */
    DOCUMENT_NOT_YOURS_TO_CHANGE(10, HttpStatus.FORBIDDEN, "You may not change this document"),

    /** A document filed against a member by somebody who may not file one. */
    DOCUMENT_NOT_YOURS_TO_ADD(11, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** A mailbox rule filing attachments against members, written by somebody who may not file one. */
    DOCUMENT_NOT_YOURS_TO_FILE(12, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** An upload to the document store that arrived with no file in it. */
    DOCUMENT_UPLOAD_MISSING_FILE(13, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A document heavier than the store takes. */
    DOCUMENT_UPLOAD_TOO_LARGE(14, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_TOO_LARGE);

    private final Definition definition;

    DocumentRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.DOCUMENTS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

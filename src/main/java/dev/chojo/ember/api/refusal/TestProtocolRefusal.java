/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#TEST_PROTOCOLS}: test protocols.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum TestProtocolRefusal implements Refusal {
    /** A section of a protocol that is gone, or belongs to another station. */
    PROTOCOL_SECTION_NOT_HERE(1, HttpStatus.NOT_FOUND, "That section is not here any more"),

    /** A point in a protocol that is gone, or belongs to another station. */
    PROTOCOL_ITEM_NOT_HERE(2, HttpStatus.NOT_FOUND, "That point is not here any more"),

    /** A protocol written down without a name. */
    PROTOCOL_NEEDS_A_NAME(3, HttpStatus.BAD_REQUEST, "A protocol needs a name, so nothing was saved"),

    /** A protocol that went before the change to it could be written. */
    PROTOCOL_NOT_CHANGED(4, HttpStatus.NOT_FOUND, "That protocol is not here any more, so nothing was changed"),

    /** A member taken up for testing while another tester already holds them. */
    PROTOCOL_MEMBER_HELD_BY_ANOTHER_TESTER(
            5, HttpStatus.BAD_REQUEST, "Somebody else is testing this member right now, so nothing was changed"),

    /** The protocol behind a test run, gone while the run was being worked out. */
    PROTOCOL_NOT_HERE_BEHIND_RUN_TO_EVALUATE(6, HttpStatus.NOT_FOUND, Sentences.PROTOCOL_NOT_HERE_BEHIND_RUN),

    /** The protocol behind a test run, gone while every sheet for it was being gathered. */
    PROTOCOL_NOT_HERE_BEHIND_RUN_TO_EXPORT(7, HttpStatus.NOT_FOUND, Sentences.PROTOCOL_NOT_HERE_BEHIND_RUN),

    /** A test run whose sheets could not be gathered into one file. */
    PROTOCOL_RUN_NOT_EXPORTED(
            8, HttpStatus.INTERNAL_SERVER_ERROR, "That test run could not be put into a file. Trying again may work"),

    /** The protocol behind a test run, gone while the result table was being made. */
    PROTOCOL_NOT_HERE_BEHIND_RUN_FOR_TABLE(9, HttpStatus.NOT_FOUND, Sentences.PROTOCOL_NOT_HERE_BEHIND_RUN),

    /** The protocol behind a test run, gone while one member's sheet was being made. */
    PROTOCOL_NOT_HERE_BEHIND_RUN_FOR_MEMBER_SHEET(10, HttpStatus.NOT_FOUND, Sentences.PROTOCOL_NOT_HERE_BEHIND_RUN),

    /**
     * A protocol asked for by a paired instance that is not here, belongs to a third station or is
     * not among the ones shared with the asker. One code deliberately: telling the three apart
     * would let a partner count its way through protocols it was never given.
     */
    REMOTE_PROTOCOL_NOT_SHARED(11, HttpStatus.NOT_FOUND, "No protocol here is shared with you under that number"),

    /** A protocol that could not be read back after being changed, with the change already in. */
    PROTOCOL_NOT_HERE_AFTER_CHANGE(12, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A test run that could not be read back after being changed, with the change already in. */
    PROTOCOL_RUN_NOT_HERE_AFTER_CHANGE(13, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A test run that could not be read back after being closed, with the closing already in. */
    PROTOCOL_RUN_NOT_HERE_AFTER_CLOSING(14, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A member on a test run that could not be read back after their test was taken over. */
    PROTOCOL_MEMBER_NOT_HERE_AFTER_LOCKING(
            15, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A member on a test run that could not be read back after their test was let go of. */
    PROTOCOL_MEMBER_NOT_HERE_AFTER_UNLOCKING(
            16, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A member on a test run that could not be read back after their test was marked finished. */
    PROTOCOL_MEMBER_NOT_HERE_AFTER_COMPLETION(
            17, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /**
     * A new order for sections or points that does not name exactly those on one level, because one
     * was added or removed in the meantime or belongs elsewhere.
     */
    PROTOCOL_ORDER_OUT_OF_DATE(
            18,
            HttpStatus.CONFLICT,
            "The order no longer matches the protocol, so nothing was moved. Reload and try again"),

    /** A new section placed under a section of another protocol, or under one that is gone. */
    PROTOCOL_SECTION_PARENT_ELSEWHERE_ON_CREATE(
            19, HttpStatus.BAD_REQUEST, "That section is not part of this protocol, so nothing was added"),

    /** A section moved under a section of another protocol, or under one that is gone. */
    PROTOCOL_SECTION_PARENT_ELSEWHERE_ON_MOVE(
            20, HttpStatus.BAD_REQUEST, "That section is not part of this protocol, so nothing was moved"),

    /** A section moved into itself or into one of its own subsections. */
    PROTOCOL_SECTION_MOVED_INTO_ITSELF(
            21,
            HttpStatus.BAD_REQUEST,
            "A section cannot be moved into itself or one of its subsections, so nothing was moved"),

    /** Someone who is not among the examiners of a run with examiners, asking to grade it. */
    PROTOCOL_RUN_GRADED_BY_ITS_EXAMINERS(22, HttpStatus.FORBIDDEN, "Only the examiners of this test run may grade it"),

    /** Examiners named for a section that is not part of the run's protocol. */
    PROTOCOL_EXAMINER_SECTION_ELSEWHERE(
            23, HttpStatus.BAD_REQUEST, "That section is not part of this run's protocol, so no examiners were saved"),

    /** Examiners named who may not grade protocols. */
    PROTOCOL_EXAMINER_NOT_A_TESTER(
            24,
            HttpStatus.BAD_REQUEST,
            "Only members who may grade protocols can be examiners, so no examiners were saved"),

    /** A section marked finished by an examiner it is not assigned to. */
    PROTOCOL_SECTION_NOT_YOURS_TO_FINISH(
            25, HttpStatus.FORBIDDEN, "That section belongs to other examiners, so it was not marked");

    private final Definition definition;

    TestProtocolRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.TEST_PROTOCOLS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

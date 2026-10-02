/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#BOARDS}: boards and tickets.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum BoardRefusal implements Refusal {
    /**
     * A board that is not here, or one the reader may not see.
     *
     * <p>One code for both on purpose. An invisible board answers exactly as a missing one, so a
     * member cannot run through board keys to learn which boards their station keeps.
     */
    BOARD_NOT_HERE_OR_NOT_YOURS(1, HttpStatus.NOT_FOUND, "That board is not here, or it is not yours to open"),

    /** A ticket that is not on the board its key names. */
    BOARD_TICKET_NOT_HERE(2, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A board changed by somebody who may read it but not write to it. */
    BOARD_NOT_YOURS_TO_EDIT(4, HttpStatus.FORBIDDEN, "This board is not yours to change"),

    /** A board created without a name. */
    BOARD_NEEDS_A_NAME(6, HttpStatus.BAD_REQUEST, "A board needs a name, so nothing was saved"),

    /** A board created without the short key its tickets are numbered under. */
    BOARD_NEEDS_A_SHORT_KEY(7, HttpStatus.BAD_REQUEST, "A board needs a short key of its own, so nothing was saved"),

    /** A board opened by a member it is not shown to. */
    BOARD_NOT_YOURS_TO_OPEN(8, HttpStatus.FORBIDDEN, "This board is not yours to open"),

    /** A board that went between being changed and being read back. */
    BOARD_NOT_HERE_AFTER_CHANGE(9, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A board that went while it was being deleted. */
    BOARD_NOT_DELETED(10, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A label created without a name. */
    BOARD_LABEL_NEEDS_A_NAME(11, HttpStatus.BAD_REQUEST, "A label needs a name, so nothing was saved"),

    /** A label of this board, changed after it had gone. */
    BOARD_LABEL_NOT_HERE_ON_CHANGE(12, HttpStatus.NOT_FOUND, Sentences.BOARD_LABEL_NOT_HERE),

    /** A label of this board, deleted after it had gone. */
    BOARD_LABEL_NOT_HERE_ON_DELETE(13, HttpStatus.NOT_FOUND, Sentences.BOARD_LABEL_NOT_HERE),

    /** A ticket created without a title. */
    TICKET_NEEDS_A_TITLE(14, HttpStatus.BAD_REQUEST, "A ticket needs a title, so nothing was saved"),

    /** A ticket that went while it was being deleted. */
    TICKET_NOT_DELETED(15, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A ticket moved to another lane after it had gone. */
    TICKET_NOT_HERE_ON_MOVE(16, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A ticket that went between being changed and being read back. */
    TICKET_NOT_HERE_AFTER_CHANGE(17, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A comment left on a ticket with nothing written in it. */
    TICKET_COMMENT_NEEDS_TEXT(18, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A checklist entry added without a title. */
    CHECKLIST_ITEM_NEEDS_A_TITLE(19, HttpStatus.BAD_REQUEST, "A checklist entry needs a title, so nothing was saved"),

    /** A field of the board, filled in after it had gone. */
    TICKET_FIELD_NOT_HERE(20, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** A field filled in with something the field does not take. */
    TICKET_FIELD_VALUE_NOT_ACCEPTED(
            21, HttpStatus.BAD_REQUEST, "That value does not suit this field, so nothing was saved"),

    /** A comment addressed on a ticket it does not hang on. */
    TICKET_COMMENT_NOT_HERE(22, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** An attachment upload on a ticket, arriving with no file in it. */
    TICKET_UPLOAD_MISSING_FILE(23, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** An attachment upload on a ticket, larger than the instance takes. */
    TICKET_UPLOAD_TOO_LARGE(24, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_TOO_LARGE),

    /** An attachment upload on a ticket that could not be read in. */
    TICKET_UPLOAD_NOT_READ(25, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_SAVED),

    /** An attachment of a ticket whose stored file is gone. */
    TICKET_ATTACHMENT_CONTENT_NOT_HERE(26, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** An attachment of a ticket that could not be handed out. */
    TICKET_ATTACHMENT_NOT_READ(
            27, HttpStatus.INTERNAL_SERVER_ERROR, "That attachment could not be read out. Trying again may work"),

    /** An attachment that went while it was being deleted. */
    TICKET_ATTACHMENT_NOT_DELETED(28, HttpStatus.NOT_FOUND, Sentences.ATTACHMENT_NOT_HERE),

    /**
     * An attachment that is not here, or one hanging on a different ticket.
     *
     * <p>One code for both on purpose. Tying the attachment to the ticket in the path is what
     * scopes it to the caller's station, so an attachment of another ticket has to look absent.
     */
    TICKET_ATTACHMENT_NOT_HERE(29, HttpStatus.NOT_FOUND, Sentences.ATTACHMENT_NOT_HERE),

    /** A second ticket named in the body of a link request, which is not here. */
    LINKED_TICKET_NOT_HERE(30, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** The board a ticket named for linking sits on, which is not here. */
    LINKED_TICKET_BOARD_NOT_HERE(31, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A wiki file named for linking, which is not here. */
    LINKED_WIKI_FILE_NOT_HERE(32, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A link added to a ticket without an address to point at. */
    WEBLINK_NEEDS_AN_ADDRESS(33, HttpStatus.BAD_REQUEST, "A link needs an address, so nothing was saved"),

    /** A link of a ticket, deleted after it had gone. */
    WEBLINK_NOT_HERE(34, HttpStatus.NOT_FOUND, "That link is not here any more"),

    /** A shared board addressed through a partner this station does not know. */
    FEDERATION_PARTNER_NOT_HERE_FOR_BOARD(35, HttpStatus.NOT_FOUND, Sentences.FEDERATION_PARTNER_NOT_HERE_FOR_BOARDS),

    /** A shared board opened by a member it is not shown to. */
    FEDERATED_BOARD_NOT_YOURS_TO_VIEW(36, HttpStatus.FORBIDDEN, "This shared board is not yours to open"),

    /** A shared board changed by a member who may read it but not write to it. */
    FEDERATED_BOARD_NOT_YOURS_TO_EDIT(37, HttpStatus.FORBIDDEN, "This shared board is not yours to change"),

    /** A bookmark on a shared board of a partner this station does not know. */
    FEDERATION_PARTNER_NOT_HERE_FOR_BOOKMARK(
            38, HttpStatus.NOT_FOUND, Sentences.FEDERATION_PARTNER_NOT_HERE_FOR_BOARDS),

    /** The local access rules of a shared board that is not here. */
    FEDERATED_BOARD_NOT_HERE_ON_OVERRIDE_READ(39, HttpStatus.NOT_FOUND, Sentences.FEDERATED_BOARD_NOT_HERE),

    /** Local access rules written for a shared board that is not here. */
    FEDERATED_BOARD_NOT_HERE_ON_OVERRIDE_WRITE(40, HttpStatus.NOT_FOUND, Sentences.FEDERATED_BOARD_NOT_HERE),

    /** A board addressed by another instance under a key this station does not use. */
    REMOTE_BOARD_NOT_HERE(42, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A ticket addressed by another instance under a number that board does not use. */
    REMOTE_BOARD_TICKET_NOT_HERE(43, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A board addressed by an instance it is not shared with. */
    REMOTE_BOARD_NOT_SHARED(44, HttpStatus.FORBIDDEN, "That board is not shared with this instance"),

    /** A board written to by an instance it is only shared with for reading. */
    REMOTE_BOARD_NOT_WRITABLE(45, HttpStatus.FORBIDDEN, "That board is shared with this instance for reading only"),

    /** A board that went between the share check and the read another instance asked for. */
    REMOTE_BOARD_NOT_HERE_ON_READ(46, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A board that went while another instance was reading who its members are. */
    REMOTE_BOARD_NOT_HERE_FOR_MEMBERS(47, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A ticket that went between the share check and the read another instance asked for. */
    REMOTE_TICKET_NOT_HERE_ON_READ(48, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A ticket that went between being changed by another instance and being read back. */
    REMOTE_TICKET_NOT_HERE_AFTER_UPDATE(51, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A ticket moved by another instance after it had gone. */
    REMOTE_TICKET_NOT_HERE_ON_MOVE(52, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A ticket that went between being moved by another instance and being read back. */
    REMOTE_TICKET_NOT_HERE_AFTER_MOVE(53, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A comment addressed by another instance on a ticket it does not hang on. */
    REMOTE_TICKET_COMMENT_NOT_HERE(54, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A ticket named by number in a request from another instance, which that board does not use. */
    REMOTE_TICKET_NOT_HERE_BY_NUMBER(55, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A field of a board given a type a board does not offer. */
    BOARD_FIELD_TYPE_NOT_OFFERED(
            56, HttpStatus.BAD_REQUEST, "Boards do not offer that type of field, so nothing was saved"),

    /** The value of a required ticket field, being cleared. */
    TICKET_FIELD_VALUE_REQUIRED(57, HttpStatus.BAD_REQUEST, "This field has to be filled in, so it was not cleared"),

    /** The partner a shared board is reached through, not a partner of this station any more. */
    BOARD_PARTNER_NOT_HERE(58, HttpStatus.NOT_FOUND, Sentences.PARTNER_STATION_NOT_HERE),

    /** A ticket handed to somebody who is not a member of the board's station. */
    BOARD_TICKET_ASSIGNEE_NOT_A_MEMBER(
            59,
            HttpStatus.BAD_REQUEST,
            "The person the ticket is handed to is not a member of the board's station, so nothing was saved"),

    /** A ticket handed to somebody who may not work on the board. */
    BOARD_TICKET_ASSIGNEE_MAY_NOT_EDIT(
            60,
            HttpStatus.BAD_REQUEST,
            "The person the ticket is handed to cannot work on this board, so nothing was saved");

    private final Definition definition;

    BoardRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.BOARDS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

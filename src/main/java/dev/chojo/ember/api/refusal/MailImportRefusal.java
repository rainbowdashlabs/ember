/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#MAIL_IMPORT}: mail import.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum MailImportRefusal implements Refusal {
    /** Mail import at all, at a station that keeps no documents for it to file into. */
    MAIL_IMPORT_SWITCHED_OFF(1, HttpStatus.NOT_FOUND, "This station does not read mail into its documents"),

    /**
     * A mailbox that is gone, or belongs to another station. One code deliberately: the lookup and
     * the station check right behind it answer alike, because telling them apart would say that the
     * mailbox is at a station the reader may not see.
     */
    MAILBOX_NOT_HERE(2, HttpStatus.NOT_FOUND, Sentences.MAILBOX_NOT_HERE),

    /**
     * A rule that is gone, or hangs off a mailbox of another station. One code deliberately, for the
     * same reason the mailbox has one: the lookup and the station check behind it answer alike, and
     * telling them apart would say that the rule is at a station the reader may not see.
     */
    MAILBOX_RULE_NOT_HERE(3, HttpStatus.NOT_FOUND, Sentences.MAILBOX_RULE_NOT_HERE),

    /** A mailbox, gone between being saved and being read back. */
    MAILBOX_NOT_HERE_AFTER_SAVE(4, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A mailbox rule, gone between being saved and being read back. */
    MAILBOX_RULE_NOT_HERE_AFTER_SAVE(5, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A mailbox set up without a password. */
    MAILBOX_PASSWORD_MISSING(6, HttpStatus.BAD_REQUEST, "A mailbox needs a password, so nothing was saved"),

    /** A mailbox password changed to nothing. */
    MAILBOX_PASSWORD_MISSING_ON_CHANGE(7, HttpStatus.BAD_REQUEST, "A mailbox needs a password, so nothing was changed"),

    /** A mailbox run by hand on an instance that has reading mail switched off. */
    MAILBOX_RUN_SWITCHED_OFF(8, HttpStatus.BAD_REQUEST, "Reading mail into documents is switched off on this instance"),

    /** A mailbox rule saved without a name. */
    MAILBOX_RULE_NAME_MISSING(9, HttpStatus.BAD_REQUEST, "A rule needs a name, so nothing was saved"),

    /** A mailbox rule saved with a name longer than a rule keeps. */
    MAILBOX_RULE_NAME_TOO_LONG(10, HttpStatus.BAD_REQUEST, "The name of the rule is too long, so nothing was saved"),

    /** A mailbox rule saved without a sender it trusts, which would accept nothing. */
    MAILBOX_RULE_SENDERS_MISSING(
            11,
            HttpStatus.BAD_REQUEST,
            "A rule has to say which senders it trusts, or it accepts nothing, so nothing was saved"),

    /** A sender on a mailbox rule that is neither an address nor a domain. */
    MAILBOX_RULE_SENDER_NOT_A_PATTERN(
            12,
            HttpStatus.BAD_REQUEST,
            "A sender has to be an address or a domain written as *@domain, so nothing was saved"),

    /** A mailbox rule saved without a kind of file it takes. */
    MAILBOX_RULE_FILE_KINDS_MISSING(
            13, HttpStatus.BAD_REQUEST, "A rule has to say which kinds of file it takes, so nothing was saved"),

    /** A kind of file on a mailbox rule that the import cannot recognise. */
    MAILBOX_RULE_FILE_KIND_UNKNOWN(
            14, HttpStatus.BAD_REQUEST, "That is not a kind of file the import can recognise, so nothing was saved"),

    /** A mailbox rule that moves messages without naming the folder to move them to. */
    MAILBOX_RULE_FOLDER_MISSING(
            15, HttpStatus.BAD_REQUEST, "Moving a message needs a folder to move it to, so nothing was saved"),

    /** A mailbox whose host this instance may not connect to, or not in the way it was set up. */
    MAILBOX_HOST_NOT_REACHABLE(
            16, HttpStatus.BAD_REQUEST, "This instance will not connect to that mailbox, so nothing was saved"),

    /** A mailbox password given on an instance with no key to keep it safe with. */
    MAILBOX_PASSWORD_CANNOT_BE_KEPT(
            17,
            HttpStatus.BAD_REQUEST,
            "This instance has no encryption key set up, so a mailbox password cannot be kept safely "
                    + "and nothing was saved"),

    /** A mailbox saved without a name. */
    MAILBOX_NAME_MISSING(18, HttpStatus.BAD_REQUEST, "A mailbox needs a name, so nothing was saved"),

    /** A mailbox saved with a name longer than a mailbox keeps. */
    MAILBOX_NAME_TOO_LONG(19, HttpStatus.BAD_REQUEST, "The name of the mailbox is too long, so nothing was saved"),

    /** A mailbox saved without the server it is read from. */
    MAILBOX_HOST_MISSING(20, HttpStatus.BAD_REQUEST, "A mailbox needs a server, so nothing was saved"),

    /** A mailbox saved with a server name longer than a mailbox keeps. */
    MAILBOX_HOST_TOO_LONG(21, HttpStatus.BAD_REQUEST, "The server name is too long, so nothing was saved"),

    /** A mailbox saved without the user it signs in as. */
    MAILBOX_USER_MISSING(22, HttpStatus.BAD_REQUEST, "A mailbox needs a user name, so nothing was saved"),

    /** A mailbox saved with a user name longer than a mailbox keeps. */
    MAILBOX_USER_TOO_LONG(23, HttpStatus.BAD_REQUEST, "The user name is too long, so nothing was saved");

    private final Definition definition;

    MailImportRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.MAIL_IMPORT, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

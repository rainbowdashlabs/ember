/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#WAITING_LISTS}: waiting lists.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum WaitingListRefusal implements Refusal {
    /** An invite that is used up or past its date. */
    WAITING_LIST_INVITE_NO_LONGER_VALID(22, HttpStatus.FORBIDDEN, "This invite can no longer be used"),

    /** A sign-up by invite that named neither the invite nor a first name. */
    WAITING_LIST_REGISTRATION_INCOMPLETE(
            23, HttpStatus.BAD_REQUEST, "An invite and a first name are needed, so nothing was saved"),

    /** A sign-up by invite refused for what it said. */
    WAITING_LIST_REGISTRATION_REFUSED(
            24, HttpStatus.BAD_REQUEST, "You could not be put on the waiting list, so nothing was saved"),

    /** A sign-up by invite the list is not in a state to take. */
    WAITING_LIST_CLOSED_TO_THIS_REGISTRATION(
            25,
            HttpStatus.FORBIDDEN,
            "This waiting list cannot be signed up for as it now stands, so nothing was saved"),

    /** An answer to an invitation that said nothing. */
    WAITING_LIST_ANSWER_MISSING(26, HttpStatus.BAD_REQUEST, "Say what your answer is"),

    /** An answer to an invitation that is none of the ones it offers. */
    WAITING_LIST_ANSWER_UNKNOWN(27, HttpStatus.BAD_REQUEST, "That is not an answer this invitation takes"),

    /** An entry that went before it could be invited. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_INVITE(28, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that is not waiting, so there is nothing to invite. */
    WAITING_LIST_ENTRY_NOT_INVITED(
            29, HttpStatus.BAD_REQUEST, "This entry cannot be invited as it now stands, so nothing was changed"),

    /** An entry that went before it could be put back on the list. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_RETURN(30, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that is not in a state to go back to waiting. */
    WAITING_LIST_ENTRY_NOT_RETURNED(
            31,
            HttpStatus.BAD_REQUEST,
            "This entry cannot be put back on the waiting list as it now stands, so nothing was changed"),

    /** The appointment an invitation names, gone or belonging to another station. */
    APPOINTMENT_NOT_HERE_FOR_INVITATION(32, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** An appointment the person inviting cannot see themselves. */
    APPOINTMENT_NOT_YOURS_TO_INVITE_TO(
            33, HttpStatus.FORBIDDEN, "You cannot see that appointment, so nothing was saved"),

    /** An invitation to an appointment that did not say which occurrence of it. */
    INVITATION_NEEDS_A_DATE(34, HttpStatus.BAD_REQUEST, "An invitation needs the date of the occurrence"),

    /** A date on an invitation that is not one. */
    INVITATION_DATE_NOT_A_DATE(35, HttpStatus.BAD_REQUEST, "That is not a date"),

    /** A time on an invitation that is not one. */
    INVITATION_TIME_NOT_A_TIME(36, HttpStatus.BAD_REQUEST, "That is not a time"),

    /** An entry that went before its trial period could begin. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_TESTING(37, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that has not been invited, so its trial period cannot begin. */
    WAITING_LIST_ENTRY_NOT_MOVED_TO_TESTING(
            38,
            HttpStatus.BAD_REQUEST,
            "This entry cannot begin its trial period as it now stands, so nothing was changed"),

    /** An entry that went before it could be marked as joined. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_JOIN(39, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that is not in its trial period, so it cannot be marked as joined. */
    WAITING_LIST_ENTRY_NOT_JOINED(
            40,
            HttpStatus.BAD_REQUEST,
            "This entry cannot be marked as joined as it now stands, so nothing was changed"),

    /** An entry that went before it could be withdrawn. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_WITHDRAWAL(41, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that has joined, or is withdrawn already. */
    WAITING_LIST_ENTRY_NOT_WITHDRAWN(
            42, HttpStatus.BAD_REQUEST, "This entry cannot be withdrawn as it now stands, so nothing was changed"),

    /** A formula for working out points that could not be made sense of. */
    SCORING_FORMULA_NOT_READ(
            43, HttpStatus.BAD_REQUEST, "The formula for working out points could not be read, so nothing was saved"),

    /** A public sign-up without a first name. */
    PUBLIC_REGISTRATION_NEEDS_A_FIRST_NAME(44, HttpStatus.BAD_REQUEST, "A first name is needed, so nothing was saved"),

    /** A public sign-up to a list that writes to people, with no address to write to. */
    PUBLIC_REGISTRATION_NEEDS_AN_ADDRESS(
            45, HttpStatus.BAD_REQUEST, "An email address is needed, so nothing was saved"),

    /** A confirmation link that names no sign-up, which covers one that has run out. */
    WAITING_LIST_CONFIRMATION_LINK_UNKNOWN(46, HttpStatus.BAD_REQUEST, "This confirmation link is no longer good"),

    /** A waiting list whose testing group does not take somebody on trial. */
    WAITING_LIST_TESTING_GROUP_WRONG_USER_TYPE(
            47,
            HttpStatus.BAD_REQUEST,
            "The group for the trial period does not take people on trial, so nothing was saved"),

    /** A waiting list whose join group does not take members. */
    WAITING_LIST_JOIN_GROUP_WRONG_USER_TYPE(
            48, HttpStatus.BAD_REQUEST, "The group for joining does not take members, so nothing was saved"),

    /** A waiting list whose testing group belongs to another station, or is gone. */
    WAITING_LIST_TESTING_GROUP_NOT_HERE(49, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE_NOTHING_SAVED),

    /** A waiting list whose join group belongs to another station, or is gone. */
    WAITING_LIST_JOIN_GROUP_NOT_HERE(50, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE_NOTHING_SAVED),

    /** A question of a waiting list given a type a waiting list does not offer. */
    WAITING_LIST_FIELD_TYPE_NOT_OFFERED(
            51, HttpStatus.BAD_REQUEST, "Waiting lists do not offer that type of question, so nothing was saved"),

    /** A question paired with a waiting list it is not on. */
    WAITING_LIST_FIELD_NOT_IN_LIST(1, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_FIELD_NOT_HERE),

    /** An invite paired with a waiting list it does not belong to. */
    WAITING_LIST_INVITE_NOT_IN_LIST(2, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_INVITE_UNKNOWN),

    /**
     * An entry that is gone, or belongs to another waiting list. The two are one code deliberately:
     * telling them apart would say that the entry exists on a list the caller may not see.
     */
    WAITING_LIST_ENTRY_NOT_IN_LIST(3, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An invite code no waiting list is reached by. */
    WAITING_LIST_INVITE_UNKNOWN(4, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_INVITE_UNKNOWN),

    /** The waiting list an invite names, which is gone. */
    WAITING_LIST_NOT_HERE_BEHIND_INVITE(5, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** An entry token that names no entry. */
    WAITING_LIST_ENTRY_TOKEN_UNKNOWN(6, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** The waiting list an entry belongs to, which is gone. */
    WAITING_LIST_NOT_HERE_BEHIND_ENTRY(7, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** The station whose invitation a family is reading, which is gone. */
    STATION_NOT_HERE_BEHIND_INVITATION(8, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** An invitation answered with a token that names no entry any more. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_ANSWER(9, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** Signing up for the same waiting list far more often than a person could. */
    WAITING_LIST_TOO_OFTEN(10, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A waiting list that went between the ownership check and being read. */
    WAITING_LIST_NOT_HERE(11, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** A waiting list that went before the change to it could be written. */
    WAITING_LIST_NOT_CHANGED(12, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** A waiting list that went before its visible questions could be written. */
    WAITING_LIST_NOT_HERE_ON_VISIBLE_QUESTIONS(13, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** A question on a waiting list that went before the change to it could be written. */
    WAITING_LIST_FIELD_NOT_CHANGED(14, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_FIELD_NOT_HERE),

    /** A waiting list that went between its entries being read and its own details being read. */
    WAITING_LIST_NOT_HERE_ON_ENTRIES(15, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** An entry that went between being changed and being read back. */
    WAITING_LIST_ENTRY_NOT_HERE_AFTER_CHANGE(16, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that went between its date being changed and being read back. */
    WAITING_LIST_ENTRY_NOT_HERE_AFTER_DATE_CHANGE(17, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** A station that is not here, asked for by a public waiting list address. */
    STATION_NOT_HERE_BEHIND_PUBLIC_LIST(18, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Public waiting lists at all, at a station that has switched them off. */
    PUBLIC_WAITING_LISTS_SWITCHED_OFF(19, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /**
     * A waiting list that is gone, belongs to another station, or is not open to the public. One
     * code deliberately: telling them apart would say that a list nobody outside may see is there.
     */
    PUBLIC_WAITING_LIST_NOT_HERE(20, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** The same, at the moment a public sign-up is sent rather than when the form is drawn. */
    PUBLIC_WAITING_LIST_NOT_HERE_ON_REGISTRATION(21, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** A second date of birth question added to a waiting list that already asks one. */
    WAITING_LIST_SECOND_BIRTH_DATE_ON_CREATE(52, HttpStatus.BAD_REQUEST, Sentences.WAITING_LIST_HAS_A_BIRTH_DATE),

    /** A question of a waiting list changed into a second date of birth question. */
    WAITING_LIST_SECOND_BIRTH_DATE_ON_CHANGE(53, HttpStatus.BAD_REQUEST, Sentences.WAITING_LIST_HAS_A_BIRTH_DATE),

    /** Somebody taking their own entry off a list after it has moved past waiting or being invited. */
    WAITING_LIST_ENTRY_NO_LONGER_REMOVABLE(
            54, HttpStatus.CONFLICT, "This entry can no longer be taken off the list, so nothing was changed"),

    /** An answer to a waiting list question that it does not take, given through an invite code. */
    WAITING_LIST_ANSWER_NOT_ACCEPTED_ON_INVITE(55, HttpStatus.BAD_REQUEST, Sentences.WAITING_LIST_ANSWER_NOT_ACCEPTED),

    /** An answer to a waiting list question that it does not take, given as an entry is written down. */
    WAITING_LIST_ANSWER_NOT_ACCEPTED_ON_CREATE(56, HttpStatus.BAD_REQUEST, Sentences.WAITING_LIST_ANSWER_NOT_ACCEPTED),

    /** An answer to a waiting list question that it does not take, given as an entry is changed. */
    WAITING_LIST_ANSWER_NOT_ACCEPTED_ON_CHANGE(57, HttpStatus.BAD_REQUEST, Sentences.WAITING_LIST_ANSWER_NOT_ACCEPTED),

    /** An answer to an invitation the entry no longer holds, because it has moved on. */
    WAITING_LIST_INVITATION_NO_LONGER_OPEN(
            58, HttpStatus.CONFLICT, "This invitation can no longer be answered, so nothing was saved"),

    /** An answer that names an appointment or a date the entry is not invited to. */
    WAITING_LIST_INVITATION_ANSWER_FOR_ANOTHER(
            59,
            HttpStatus.CONFLICT,
            "This answer is about a different appointment than the invitation, so nothing was saved"),

    /** A sign-up from somebody younger than the list takes registrations from. */
    WAITING_LIST_REGISTRANT_TOO_YOUNG(
            60, HttpStatus.BAD_REQUEST, "This list does not take registrations at this age yet, so nothing was saved");

    private final Definition definition;

    WaitingListRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.WAITING_LISTS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

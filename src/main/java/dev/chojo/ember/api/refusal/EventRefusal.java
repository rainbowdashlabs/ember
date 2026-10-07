/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#EVENTS}: appointments and registrations.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum EventRefusal implements Refusal {
    /** A registration whose answers were to be changed and is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_FIELD_CHANGE(1, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** The station a registration spreadsheet is headed with, gone between the two reads. */
    STATION_NOT_HERE_FOR_REGISTRATION_CSV(2, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The station a registration sheet is headed with, gone between the two reads. */
    STATION_NOT_HERE_FOR_REGISTRATION_SHEET(3, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The station a registration table is drawn for, gone between the two reads. */
    STATION_NOT_HERE_FOR_REGISTRATION_TABLE(4, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A registration whose status was to be set and is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_STATUS_CHANGE(5, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that went between being read and having its status written. */
    EVENT_REGISTRATION_STATUS_NOT_CHANGED(6, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration being answered for that is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_ANSWER(7, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that could not be marked as not coming. */
    EVENT_REGISTRATION_NOT_REFUSED(8, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that could not be marked as coming. */
    EVENT_REGISTRATION_NOT_CONFIRMED(9, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration being taken back that is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_WITHDRAWAL(10, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that went between being read and being withdrawn. */
    EVENT_REGISTRATION_NOT_WITHDRAWN(11, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A withdrawal being undone whose registration is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_UNDO(12, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** An appointment that went between the screen being opened and the change being saved. */
    EVENT_NOT_HERE_ON_CHANGE(13, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** An appointment that was already gone when its deletion was asked for. */
    EVENT_NOT_HERE_ON_DELETE(14, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** An appointment that was already gone when its cancellation was asked for. */
    EVENT_NOT_HERE_ON_CANCELLATION(15, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A registration made for somebody the caller neither is nor looks after. */
    MEMBER_NOT_YOURS_TO_REGISTER(17, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** The answers of somebody the caller neither is nor looks after. */
    REGISTRATION_ANSWERS_NOT_YOURS(19, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** A list of who is coming that could not be drawn as a sheet. */
    REGISTRATION_SHEET_NOT_DRAWN(20, HttpStatus.BAD_REQUEST, "This list cannot be turned into a sheet"),

    /** A list of who is coming asked for without saying which day it covers. */
    REGISTRATION_TABLE_NEEDS_A_DAY(21, HttpStatus.BAD_REQUEST, "A list of who is coming needs the day it is about"),

    /** A registration for an appointment that takes none. */
    EVENT_TAKES_NO_REGISTRATIONS(22, HttpStatus.BAD_REQUEST, "This appointment does not take registrations"),

    /** A registration sent after the deadline by somebody who does not keep the list. */
    REGISTRATION_CLOSED(23, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_CLOSED),

    /** A registration for somebody this appointment is not open to. */
    MEMBER_NOT_INVITED_TO_EVENT(24, HttpStatus.BAD_REQUEST, "This appointment is not open to that member"),

    /** A decision on a registration that is neither accepting it nor turning it down. */
    REGISTRATION_DECISION_UNKNOWN(25, HttpStatus.BAD_REQUEST, "A registration can only be accepted or turned down"),

    /** An answer given for somebody the caller may not answer for. */
    ANSWER_NOT_YOURS_TO_GIVE(26, HttpStatus.FORBIDDEN, Sentences.CANNOT_ANSWER_FOR_MEMBER),

    /** An answer changed after the deadline by somebody who does not keep the list. */
    REGISTRATION_CLOSED_ON_ANSWER_CHANGE(27, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_CLOSED),

    /** A withdrawal older than the few minutes it may be taken back in. */
    WITHDRAWAL_NO_LONGER_UNDONE(28, HttpStatus.BAD_REQUEST, Sentences.NO_LONGER_TAKEN_BACK),

    /** A withdrawal, or an undo of one, for somebody the caller may not answer for. */
    WITHDRAWAL_NOT_YOURS_TO_ANSWER(29, HttpStatus.FORBIDDEN, Sentences.CANNOT_ANSWER_FOR_MEMBER),

    /** A registration for an appointment that happens once and carries no time of its own. */
    EVENT_HAS_NO_START_TIME(30, HttpStatus.BAD_REQUEST, "This appointment has no start time, so nothing was saved"),

    /** A registration for an appointment that comes round again, without saying for which day. */
    REGISTRATION_NEEDS_A_DAY(
            31, HttpStatus.BAD_REQUEST, "A repeating appointment needs the day you are signing up for"),

    /** A registration for a day this appointment does not fall on. */
    REGISTRATION_DAY_NOT_AN_OCCURRENCE(32, HttpStatus.BAD_REQUEST, "This appointment does not fall on that day"),

    /** A shared appointment that went between being cleared for a partner and being read. */
    SHARED_EVENT_NOT_HERE(33, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /**
     * A file of a shared appointment that is gone, hangs on another appointment, or is one the
     * appointment keeps back. One code deliberately: telling them apart would say that a file kept
     * back is there.
     */
    SHARED_EVENT_FILE_NOT_HERE(34, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** The appointment a shared file hangs on, gone between the two reads. */
    EVENT_NOT_HERE_BEHIND_SHARED_FILE(35, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A file of a shared appointment whose stored bytes are gone. */
    SHARED_EVENT_FILE_CONTENT_NOT_HERE(36, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A partner confirming its own members where this station never handed that over. */
    PARTNER_DOES_NOT_CONFIRM_ITS_OWN(
            37, HttpStatus.FORBIDDEN, "This station decides the registrations for this appointment"),

    /** A registration a partner is confirming that is gone. */
    PARTNER_REGISTRATION_NOT_HERE(38, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A partner confirming one member more than the places it was given. */
    NO_PLACES_LEFT_FOR_PARTNER(39, HttpStatus.BAD_REQUEST, Sentences.NO_PLACES_LEFT),

    /** A withdrawal a partner asks to undo, past the few minutes it may be undone in. */
    PARTNER_WITHDRAWAL_NO_LONGER_UNDONE(40, HttpStatus.BAD_REQUEST, Sentences.NO_LONGER_TAKEN_BACK),

    /** A comment arriving from a partner with nothing written in it. */
    PARTNER_COMMENT_NEEDS_TEXT(41, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment arriving from a partner tied to a day that is not a date. */
    PARTNER_COMMENT_DAY_NOT_A_DATE(42, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A change to a comment arriving from a partner with nothing written in it. */
    PARTNER_COMMENT_CHANGE_NEEDS_TEXT(43, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment a partner asks to delete that is gone, or was never that partner's to delete. */
    PARTNER_COMMENT_NOT_DELETED(44, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /**
     * An appointment a partner names that this station does not share with it, which covers one
     * that is not here at all. One code deliberately: telling them apart would let a partner learn
     * which appointments exist by trying identifiers.
     */
    EVENT_NOT_SHARED_WITH_PARTNER(45, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A category being made without a name. */
    EVENT_CATEGORY_NEEDS_A_NAME(46, HttpStatus.BAD_REQUEST, "A category needs a name"),

    /** A category that went before the change to it could be written. */
    EVENT_CATEGORY_NOT_CHANGED(47, HttpStatus.NOT_FOUND, Sentences.EVENT_CATEGORY_NOT_HERE),

    /** A category that was already gone when its deletion was asked for. */
    EVENT_CATEGORY_NOT_DELETED(48, HttpStatus.NOT_FOUND, Sentences.EVENT_CATEGORY_NOT_HERE),

    /** A break being made without a name. */
    EVENT_BREAK_NEEDS_A_NAME(49, HttpStatus.BAD_REQUEST, "A break needs a name"),

    /** A break that went before the change to it could be written. */
    EVENT_BREAK_NOT_CHANGED(50, HttpStatus.NOT_FOUND, Sentences.EVENT_BREAK_NOT_HERE),

    /** A break that was already gone when its deletion was asked for. */
    EVENT_BREAK_NOT_DELETED(51, HttpStatus.NOT_FOUND, Sentences.EVENT_BREAK_NOT_HERE),

    /** A day an appointment's questions are asked about that is not a date. */
    EVENT_DAY_NOT_A_DATE(52, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** An answer kept per day, written without saying which day it belongs to. */
    EVENT_FIELD_VALUE_NEEDS_A_DAY(53, HttpStatus.BAD_REQUEST, "An answer kept per day needs the day it belongs to"),

    /** A file of a partner's appointment that the partner answered nothing for. */
    FEDERATED_FILE_NOT_HERE(54, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file whose holder answered with something no file could be made of. */
    FEDERATED_FILE_UNREADABLE(
            55, HttpStatus.BAD_GATEWAY, "The station holding this file answered with something that could not be read"),

    /** A registration the station holding the appointment did not take. */
    FEDERATED_REGISTRATION_NOT_TAKEN(
            56,
            HttpStatus.BAD_REQUEST,
            "The station holding this appointment did not take the registration, so nothing was saved"),

    /** A withdrawal the holding station will no longer put back. */
    FEDERATED_WITHDRAWAL_NO_LONGER_UNDONE(57, HttpStatus.BAD_REQUEST, Sentences.NO_LONGER_TAKEN_BACK),

    /** A confirmation that would go past the places the holding station handed over. */
    NO_PLACES_LEFT_AT_HOLDER(58, HttpStatus.BAD_REQUEST, Sentences.NO_PLACES_LEFT),

    /** A member of ours confirmed where the holding station never handed that choice over. */
    EVENT_DECIDED_BY_ITS_HOLDER(
            59, HttpStatus.FORBIDDEN, "That station decides the registrations for this appointment itself"),

    /** A registration of ours at a partner's appointment that is gone. */
    FEDERATED_REGISTRATION_NOT_HERE(60, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A station addressed as a partner that this station has no partnership with. */
    PARTNER_NOT_HERE(61, HttpStatus.NOT_FOUND, "That station is not a partner of this one"),

    /** A comment on a partner's appointment with nothing written in it. */
    FEDERATED_COMMENT_NEEDS_TEXT(62, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A change to a comment on a partner's appointment with nothing written in it. */
    FEDERATED_COMMENT_CHANGE_NEEDS_TEXT(63, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A listing asked for between two days, one of which is not a date. */
    EVENT_LIST_BOUNDS_NOT_DATES(64, HttpStatus.BAD_REQUEST, "The first and last day of the list must both be dates"),

    /** An appointment being written without a name. */
    EVENT_NEEDS_A_NAME(65, HttpStatus.BAD_REQUEST, "An appointment needs a name"),

    /** An appointment being written without the times it runs between. */
    EVENT_NEEDS_A_TIME(66, HttpStatus.BAD_REQUEST, "An appointment needs a time it starts and a time it ends"),

    /** An appointment being written without saying whether it happens once or comes round again. */
    EVENT_NEEDS_A_KIND(
            67, HttpStatus.BAD_REQUEST, "An appointment needs to say whether it happens once or comes round again"),

    /** Several appointments being written at once, with none of them named. */
    BATCH_NEEDS_ROWS(68, HttpStatus.BAD_REQUEST, "Making several appointments at once needs at least one of them"),

    /** A list of appointments that broke while being drawn, which is Ember's to look into. */
    EVENT_LIST_NOT_DRAWN(
            69,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The list of appointments could not be turned into a sheet. Trying again may work"),

    /** An appointment only some of the station may see, handed to partner stations. */
    RESTRICTED_EVENT_NOT_SHARED(
            70,
            HttpStatus.BAD_REQUEST,
            "An appointment meant for only some of the station cannot be shared with partners"),

    /** A partner's registration being decided on that is gone. */
    PARTNER_REGISTRATION_NOT_HERE_ON_DECISION(71, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A partner's registration decided here although the partner was handed that decision. */
    PARTNER_DECIDES_ITS_OWN(
            72, HttpStatus.FORBIDDEN, "This partner decides its own registrations for this appointment"),

    /** One more of a partner's members accepted than the places that partner was given. */
    NO_PLACES_LEFT_FOR_THIS_PARTNER(73, HttpStatus.BAD_REQUEST, "There are no places left for this partner"),

    /** A number of places written as less than none. */
    PLACES_CANNOT_BE_NEGATIVE(74, HttpStatus.BAD_REQUEST, "A number of places cannot be less than zero"),

    /**
     * A file of an appointment that is gone, hangs on another appointment, or is one this reader
     * may not be handed. One code deliberately: telling them apart would say that a file held back
     * is there.
     */
    EVENT_FILE_NOT_HERE(75, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file of an appointment whose stored bytes are gone. */
    EVENT_FILE_CONTENT_NOT_HERE(76, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** The same as the download refuses, asked for by the tile that shows a file. */
    EVENT_FILE_NOT_HERE_FOR_PICTURE(77, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file of an appointment that has no picture to show. */
    EVENT_FILE_PICTURE_NOT_HERE(78, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A file hung on an appointment without saying which file of the library it is. */
    EVENT_FILE_NOT_CHOSEN(79, HttpStatus.BAD_REQUEST, "Say which file of the library is to be attached"),

    /** A file of an appointment that went before the change to it could be written. */
    EVENT_FILE_NOT_CHANGED(80, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file that was already off the appointment when its removal was asked for. */
    EVENT_FILE_NOT_REMOVED(81, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /**
     * An appointment this reader is not among the people it was meant for. Answered exactly like one
     * that does not exist, so its id cannot be used to learn that a hidden appointment is there.
     */
    EVENT_NOT_YOURS_TO_SEE(82, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A file being written or taken off that is gone, or hangs on another appointment. */
    EVENT_FILE_NOT_HERE_ON_WRITE(83, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A station that is not here, asked for by a public calendar address. */
    STATION_NOT_HERE_BEHIND_PUBLIC_CALENDAR(84, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A public calendar at a station that has switched it off. */
    PUBLIC_CALENDAR_SWITCHED_OFF(85, HttpStatus.NOT_FOUND, "This station does not publish its appointments"),

    /**
     * An appointment that is gone, belongs to another station, or is not one the station publishes.
     * One code deliberately: telling them apart would say that an appointment nobody outside may
     * see is there.
     */
    PUBLIC_EVENT_NOT_HERE(86, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A template being made without a name. */
    EVENT_TEMPLATE_NEEDS_A_NAME(87, HttpStatus.BAD_REQUEST, "An appointment template needs a name"),

    /** A template that went before the change to it could be written. */
    EVENT_TEMPLATE_NOT_CHANGED(88, HttpStatus.NOT_FOUND, Sentences.EVENT_TEMPLATE_NOT_HERE),

    /** A template that was already gone when its deletion was asked for. */
    EVENT_TEMPLATE_NOT_DELETED(89, HttpStatus.NOT_FOUND, Sentences.EVENT_TEMPLATE_NOT_HERE),

    /** A template that could not be read back after being changed, with the change already in. */
    EVENT_TEMPLATE_NOT_HERE_AFTER_CHANGE(
            90, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /**
     * The station the caller is signed in at, taken from their session and not there, while a
     * registration across stations is being worked out.
     */
    SESSION_STATION_NOT_HERE(
            91,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Your sign-in could not be matched to a station, so nothing was done. Sign in again"),

    /**
     * An event block naming an appointment that is gone, belongs to another station or is hidden
     * from the reader. All three answer alike, so a block cannot tell a hidden one from a missing one.
     */
    EVENT_BLOCK_APPOINTMENT_NOT_HERE(92, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /**
     * An appointment an event block could not name: it has no public id to be named by, or it is
     * kept to part of the station, which an article read by every member may not show.
     */
    EVENT_BLOCK_REFERENCE_NOT_HERE(93, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A whole series called off for an appointment that is not a series and is called off by its date. */
    ONE_TIME_EVENT_CANCELLED_AS_SERIES(
            94,
            HttpStatus.BAD_REQUEST,
            "A one-time appointment is cancelled by its date, not as a series, so nothing was changed"),

    /** A series called off a second time. */
    SERIES_ALREADY_CANCELLED(95, HttpStatus.CONFLICT, "This series is already cancelled"),

    /** A date to call off or bring back that does not read as a date. */
    CANCELLATION_DAY_NOT_A_DATE(96, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A date to call off that the appointment does not fall on, or that a break of the station takes out. */
    DATE_TO_CANCEL_NOT_A_DATE_OF_THE_EVENT(
            97,
            HttpStatus.BAD_REQUEST,
            "The appointment does not take place on that date, so there is nothing to cancel"),

    /** A date to call off that is already behind the station. */
    DATE_TO_CANCEL_IN_THE_PAST(
            98, HttpStatus.BAD_REQUEST, "That date is already past, so it can no longer be cancelled"),

    /** A date called off that already is, on its own or with the whole series. */
    DATE_ALREADY_CANCELLED(99, HttpStatus.CONFLICT, "That date is already cancelled"),

    /** A date to bring back that is not called off. */
    DATE_TO_RESTORE_NOT_CANCELLED(
            100, HttpStatus.CONFLICT, "That date is not cancelled, so there is nothing to restore"),

    /** A date to bring back that is already behind the station. */
    DATE_TO_RESTORE_IN_THE_PAST(
            101, HttpStatus.BAD_REQUEST, "That date is already past, so it can no longer be restored"),

    /** A date to bring back of a series that was called off as a whole, which stays final. */
    DATE_OF_CANCELLED_SERIES_NOT_RESTORED(
            102, HttpStatus.CONFLICT, "The whole series is cancelled, so none of its dates can be restored"),

    /** An attendance sheet taken for a date of an appointment that was called off. */
    ATTENDANCE_DAY_CANCELLED(
            103,
            HttpStatus.BAD_REQUEST,
            "That date of the appointment was cancelled, so no attendance is taken for it"),

    /** A registration or a decline for a day a break of the station takes out of the series. */
    REGISTRATION_DAY_IN_A_BREAK(
            104,
            HttpStatus.BAD_REQUEST,
            "The station takes a break on that day, so the appointment does not take place"),

    /**
     * A registration or a decline, local or from a partner station, for a date that was called off,
     * on its own or with the whole series.
     */
    REGISTRATION_DAY_CANCELLED(105, HttpStatus.BAD_REQUEST, "That date was cancelled, so it takes no registrations"),

    /** An appointment that is gone, or belongs to another station. */
    EVENT_NOT_HERE(106, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /**
     * A page saved with an event block naming an appointment that is not on the public calendar. A
     * page is read by anybody, so nothing is saved rather than a block nobody outside can see.
     */
    EVENT_BLOCK_APPOINTMENT_NOT_PUBLIC(
            107,
            HttpStatus.BAD_REQUEST,
            "A page can only show a public appointment in an event block, so nothing was saved"),

    /**
     * An article saved with an event block naming an appointment that is kept to part of the station,
     * or is not the station's. An article is read by every member, so it may only show what every
     * member may see.
     */
    EVENT_BLOCK_APPOINTMENT_NOT_FOR_EVERY_MEMBER(
            108,
            HttpStatus.BAD_REQUEST,
            "An article can only show an appointment every member may see, so nothing was saved"),

    /**
     * An appointment made from a template that is gone or belongs to another station. One code for
     * both, so a guessed id says nothing about the templates of other stations.
     */
    EVENT_TEMPLATE_TO_APPLY_NOT_HERE(109, HttpStatus.NOT_FOUND, Sentences.EVENT_TEMPLATE_NOT_HERE),

    /** A registration question given a kind of answer the registration form does not offer. */
    REGISTRATION_QUESTION_TYPE_NOT_OFFERED(
            110,
            HttpStatus.BAD_REQUEST,
            "Registration questions do not take that kind of answer, so nothing was saved"),

    /** A field of an appointment given a type an appointment does not offer. */
    APPOINTMENT_FIELD_TYPE_NOT_OFFERED(
            111, HttpStatus.BAD_REQUEST, "Appointments do not offer that type of field, so nothing was saved"),

    /** A field of an appointment template given a type an appointment does not offer. */
    TEMPLATE_FIELD_TYPE_NOT_OFFERED(
            112, HttpStatus.BAD_REQUEST, "Appointments do not offer that type of field, so the template was not saved"),

    /** A field of appointments created together given a type an appointment does not offer. */
    BATCH_FIELD_TYPE_NOT_OFFERED(
            113, HttpStatus.BAD_REQUEST, "Appointments do not offer that type of field, so none was created"),

    /**
     * A question answered on one date of an appointment that is not here, or one that belongs to
     * another appointment. One code for both: the lookup reaches every question on the instance.
     */
    APPOINTMENT_FIELD_NOT_HERE_FOR_DATE_ANSWER(114, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** An answer for one date given to a question that is answered on the appointment as a whole. */
    APPOINTMENT_FIELD_NOT_PER_DATE(
            115, HttpStatus.BAD_REQUEST, "This question is not answered per date, so nothing was saved"),

    /** An answer for one date that the question does not take. */
    APPOINTMENT_DATE_ANSWER_NOT_ACCEPTED(
            116, HttpStatus.BAD_REQUEST, "That answer does not suit this question, so nothing was saved"),

    /** An answer for one date that went between being saved and being read back. */
    APPOINTMENT_DATE_ANSWER_NOT_HERE_AFTER_SAVE(
            117, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A question of an appointment filled in with something it does not take. */
    APPOINTMENT_FIELD_VALUE_NOT_ACCEPTED(
            118, HttpStatus.BAD_REQUEST, "That value does not suit this question, so nothing was saved"),

    /**
     * A question somebody put themselves in that is not here, or one that belongs to another
     * appointment. One code for both: the lookup reaches every question on the instance.
     */
    APPOINTMENT_FIELD_NOT_HERE_FOR_SELF_REGISTRATION(119, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** Somebody putting themselves in a question that names no members. */
    APPOINTMENT_FIELD_NAMES_NO_MEMBERS(
            120, HttpStatus.BAD_REQUEST, "Only a question that names members can be stood in, so nothing was changed"),

    /** Somebody putting themselves in a question nobody may put themselves in. */
    APPOINTMENT_FIELD_SELF_REGISTRATION_OFF(
            121, HttpStatus.BAD_REQUEST, "Nobody can put themselves in this question, so nothing was changed"),

    /** A member putting themselves in a question who is not here any more. */
    APPOINTMENT_SELF_REGISTRATION_MEMBER_NOT_HERE(122, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A question somebody put themselves in that went before it could be read back. */
    APPOINTMENT_FIELD_NOT_HERE_AFTER_SELF_REGISTRATION(
            123, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** Somebody putting themselves in a single-member question somebody else already stands in. */
    APPOINTMENT_FIELD_SLOT_TAKEN(
            124, HttpStatus.CONFLICT, "Somebody else already stands in this question, so nothing was changed"),

    /** Somebody putting themselves in a question narrowed to a group, user type or tag they are outside. */
    APPOINTMENT_FIELD_NOT_OPEN_TO_YOU(
            125, HttpStatus.FORBIDDEN, "This question is not open to you, so nothing was changed"),

    /** Somebody putting themselves in a question that lost the group, user type or tag it is narrowed to. */
    APPOINTMENT_FIELD_NARROWING_LOST(
            126, HttpStatus.BAD_REQUEST, "This question cannot be stood in as it is set up, so nothing was changed"),

    /** Somebody putting themselves in a question answered per date without naming the date. */
    APPOINTMENT_FIELD_DATE_MISSING(
            127, HttpStatus.BAD_REQUEST, "This question is answered per date, so give the date. Nothing was changed"),

    /** The appointment behind a question somebody put themselves in, gone in the meantime. */
    APPOINTMENT_NOT_HERE_FOR_SELF_REGISTRATION(128, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** Somebody putting themselves in a question of an appointment that has stopped taking people. */
    REGISTRATION_CLOSED_ON_SELF_REGISTRATION(129, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_CLOSED),

    /** A file too large to be handed to a partner station along with an appointment. */
    EVENT_FILE_TOO_LARGE_FOR_PARTNER(
            130, HttpStatus.BAD_REQUEST, "This file is too large to hand to a partner station"),

    /**
     * A file attached to an appointment that is not in the library, or one from another station's
     * library. One code for both: the lookup reaches every file on the instance.
     */
    EVENT_FILE_TO_ATTACH_NOT_HERE(131, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** A value an appointment writes into a field of its attendance sheet that the field does not take. */
    EVENT_FIELD_DEFAULT_NOT_ACCEPTED(
            132,
            HttpStatus.BAD_REQUEST,
            "That value does not suit the field of the attendance sheet, so nothing was saved"),

    /** An appointment a partner registered a member for that is not here any more. */
    EVENT_NOT_HERE_FOR_PARTNER_REGISTRATION(133, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A partner registering a member for an appointment that takes no registrations. */
    EVENT_TAKES_NO_PARTNER_REGISTRATIONS(134, HttpStatus.BAD_REQUEST, "This appointment does not take registrations"),

    /** A partner registering a member after the appointment stopped taking people. */
    REGISTRATION_CLOSED_TO_PARTNER(135, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_CLOSED),

    /** A comment on an appointment changed by a partner instance after it had gone. */
    REMOTE_EVENT_COMMENT_NOT_HERE_ON_UPDATE(136, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment on an appointment by another member, changed by a partner instance. */
    REMOTE_EVENT_COMMENT_NOT_YOURS_TO_EDIT(137, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_EDIT),

    /** A comment on an appointment that went while a partner instance was changing it. */
    REMOTE_EVENT_COMMENT_NOT_HERE_AFTER_UPDATE(138, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment on an appointment deleted by a partner instance after it had gone. */
    REMOTE_EVENT_COMMENT_NOT_HERE_ON_DELETE(139, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment on an appointment by another member, deleted by a partner instance. */
    REMOTE_EVENT_COMMENT_NOT_YOURS_TO_DELETE(140, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_DELETE),

    /** An answer to a registration question that the appointment does not ask. */
    REGISTRATION_ANSWER_TO_UNKNOWN_QUESTION(
            141, HttpStatus.BAD_REQUEST, "That answers a question this appointment does not ask, so nothing was saved"),

    /** A registration question left unanswered, or answered with something it does not take. */
    REGISTRATION_ANSWER_NOT_ACCEPTED(
            142,
            HttpStatus.BAD_REQUEST,
            "An answer to the registration questions is missing or does not suit its question, so nothing was saved"),

    /** A registration question of an appointment set up to start from a value it would not take. */
    REGISTRATION_QUESTION_DEFAULT_NOT_ACCEPTED(143, HttpStatus.BAD_REQUEST, Sentences.QUESTION_DEFAULT_NOT_ACCEPTED),

    /** A registration question of an appointment template set up to start from a value it would not take. */
    EVENT_TEMPLATE_REGISTRATION_DEFAULT_NOT_ACCEPTED(
            144, HttpStatus.BAD_REQUEST, Sentences.QUESTION_DEFAULT_NOT_ACCEPTED),

    /** A question of an appointment template set up to start from a value it would not take. */
    EVENT_TEMPLATE_FIELD_DEFAULT_NOT_ACCEPTED(145, HttpStatus.BAD_REQUEST, Sentences.QUESTION_DEFAULT_NOT_ACCEPTED),

    /** A new appointment that ends before it starts. */
    EVENT_ENDS_BEFORE_IT_STARTS_ON_CREATE(146, HttpStatus.BAD_REQUEST, Sentences.EVENT_ENDS_BEFORE_IT_STARTS),

    /** An appointment changed so that it ends before it starts. */
    EVENT_ENDS_BEFORE_IT_STARTS_ON_CHANGE(147, HttpStatus.BAD_REQUEST, Sentences.EVENT_ENDS_BEFORE_IT_STARTS),

    /** A series given both a last day and a number of times to end after. */
    EVENT_SERIES_END_GIVEN_TWICE(
            148,
            HttpStatus.BAD_REQUEST,
            "A series ends on a day or after a number of times, not both, so nothing was saved"),

    /** An end to its repetition given to an appointment that does not repeat. */
    EVENT_SERIES_END_ON_ONE_OFF(
            149,
            HttpStatus.BAD_REQUEST,
            "Only a repeating appointment has an end to its repetition, so nothing was saved"),

    /** A series told to take place fewer than once. */
    EVENT_SERIES_COUNT_BELOW_ONE(
            150, HttpStatus.BAD_REQUEST, "A series that repeats takes place at least once, so nothing was saved"),

    /** A series told to end on a day before its first. */
    EVENT_SERIES_ENDS_BEFORE_IT_STARTS(
            151, HttpStatus.BAD_REQUEST, "A series cannot end before it starts, so nothing was saved"),

    /** A registration given a new status while a question of the appointment holds the place. */
    REGISTRATION_HELD_BY_A_FIELD_ON_STATUS_CHANGE(152, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_HELD_BY_A_FIELD),

    /** A registration withdrawn while a question of the appointment holds the place. */
    REGISTRATION_HELD_BY_A_FIELD_ON_WITHDRAWAL(153, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_HELD_BY_A_FIELD),

    /** A registration turned down while a question of the appointment holds the place. */
    REGISTRATION_HELD_BY_A_FIELD_ON_REFUSAL(154, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_HELD_BY_A_FIELD),

    /** A member saying they will not come while a question of the appointment holds their place. */
    REGISTRATION_HELD_BY_A_FIELD_ON_DECLINE(155, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_HELD_BY_A_FIELD),

    /** The documents to bring asked for without the date of the appointment they are for. */
    EVENT_DOCUMENTS_DATE_MISSING(
            156, HttpStatus.BAD_REQUEST, "The documents to bring need the date of the appointment as a day");

    private final Definition definition;

    EventRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.EVENTS, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

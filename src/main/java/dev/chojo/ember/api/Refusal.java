/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.HttpStatus;

/**
 * Every refusal that has been given a code, with the sentence a reader is shown for it.
 *
 * <p>A reader who reports a failure can say what they were doing and what the screen said, and
 * neither of those finds the line that refused them. The code travels in the error body as
 * {@code code} and is short enough to be read out over a telephone, so a report arrives naming one
 * line rather than a kind of refusal thrown from eleven.
 *
 * <p>The code is one or two letters for the area, a hyphen and a number: {@code F-021}. The
 * prefixes are the registry in {@link Area}, which is the one place a feature claims one, so two
 * features cannot both be {@code F}. The number is this constant's own and belongs to nothing
 * else, ever: see {@link RetiredRefusals} for what happens to the number of a constant that is
 * deleted.
 *
 * <p>One constant stands for one throw site. That is what makes the code worth quoting. The
 * exception is a pair of lines that deliberately answer alike, a lookup that missed and the
 * ownership check right behind it: handing those two codes would tell a caller that the thing
 * exists somewhere else, which is the one thing the shared answer is there to withhold. Those
 * share a constant and say so in their own words below.
 *
 * <p>The code and the wording live here together on purpose. A code kept in one place and a
 * sentence written at the throw site drift apart within a release; here changing one means seeing
 * the other. Where several refusals say the same thing, they name the same {@code Sentences}
 * constant rather than repeating it, so the prose has a single home.
 */
public enum Refusal {
    /** A peer that answered as a peer would but named no key to recognise it by. */
    PEER_NAMED_NO_KEY(Area.ADMIN, 1, HttpStatus.BAD_REQUEST, Sentences.PEER_DID_NOT_ANSWER),

    /**
     * A peer whose discovery card could not be fetched at all. Named because a screen has to tell
     * this one refusal apart from every other refusal the peer form can get.
     */
    PEER_DID_NOT_ANSWER(Area.ADMIN, 2, HttpStatus.BAD_REQUEST, Sentences.PEER_DID_NOT_ANSWER),

    /** A handshake from another instance that arrived without the fields it is made of. */
    HANDSHAKE_INCOMPLETE(
            Area.FEDERATION, 1, HttpStatus.BAD_REQUEST, "The handshake arrived without everything it is made of"),

    /** A handshake from another instance whose enrollment signature was not accepted. */
    HANDSHAKE_SIGNATURE_NOT_GOOD(Area.FEDERATION, 2, HttpStatus.FORBIDDEN, "That handshake signature was not accepted"),

    /** A partner registering a callback without saying where it is to be called. */
    WEBHOOK_ADDRESS_MISSING(
            Area.FEDERATION, 3, HttpStatus.BAD_REQUEST, "Give the address the partner is to be called back on"),

    /** A callback address a partner named that this instance will not call. */
    WEBHOOK_ADDRESS_NOT_ALLOWED(Area.FEDERATION, 4, HttpStatus.BAD_REQUEST, Sentences.FEDERATION_ADDRESS_NOT_PUBLIC),

    /** A partner polling for changes without saying which moment to start from. */
    SYNC_SINCE_MISSING(Area.FEDERATION, 5, HttpStatus.BAD_REQUEST, "Say which moment the changes are wanted from"),

    /** A moment a partner polled from that does not read as a moment. */
    SYNC_SINCE_NOT_A_MOMENT(
            Area.FEDERATION, 6, HttpStatus.BAD_REQUEST, "The moment the changes were asked from is not a moment"),

    /** A station announcing it has moved without saying where it has moved to. */
    ANNOUNCED_HOST_MISSING(Area.FEDERATION, 7, HttpStatus.BAD_REQUEST, "Give the address the station has moved to"),

    /** A new address a moved station announced that this instance will not call. */
    ANNOUNCED_HOST_NOT_ALLOWED(Area.FEDERATION, 8, HttpStatus.BAD_REQUEST, Sentences.FEDERATION_ADDRESS_NOT_PUBLIC),

    /** The caller's own station, gone between the session being read and the invite being made. */
    FEDERATION_STATION_NOT_HERE(Area.FEDERATION, 9, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A pairing entered with no code in it. */
    INVITE_CODE_MISSING(Area.FEDERATION, 10, HttpStatus.BAD_REQUEST, "Type the pairing code, so nothing was saved"),

    /**
     * A pairing request that is not here, or one addressed to another station.
     *
     * <p>One code for both on purpose. The lookup asks about every request on the instance, so
     * telling them apart would say that a request exists between two stations the reader may not
     * see.
     */
    PAIR_REQUEST_NOT_HERE_TO_ACCEPT(Area.FEDERATION, 11, HttpStatus.NOT_FOUND, Sentences.PAIR_REQUEST_NOT_HERE),

    /**
     * A pairing request declined that is not here, or one addressed to another station.
     *
     * <p>One code for both on purpose, for the same reason as accepting one.
     */
    PAIR_REQUEST_NOT_HERE_TO_DECLINE(Area.FEDERATION, 12, HttpStatus.NOT_FOUND, Sentences.PAIR_REQUEST_NOT_HERE),

    /** A partner station addressed by a number nothing here uses. */
    FEDERATION_PARTNER_NOT_HERE(Area.FEDERATION, 13, HttpStatus.NOT_FOUND, "That partner station is not here any more"),

    /** A knowledge share that went before the withdrawal reached it. */
    KB_SHARE_NOT_HERE_TO_DELETE(Area.FEDERATION, 14, HttpStatus.NOT_FOUND, Sentences.FEDERATION_SHARE_NOT_HERE),

    /** A catalog share that went before the withdrawal reached it. */
    QUIZ_SHARE_NOT_HERE_TO_DELETE(Area.FEDERATION, 15, HttpStatus.NOT_FOUND, Sentences.FEDERATION_SHARE_NOT_HERE),

    /** A test protocol share that went before the withdrawal reached it. */
    PROTOCOL_SHARE_NOT_HERE_TO_DELETE(Area.FEDERATION, 16, HttpStatus.NOT_FOUND, Sentences.FEDERATION_SHARE_NOT_HERE),

    /** Gear asked for from the station already holding it. */
    LENDING_FROM_OWN_STATION(
            Area.FEDERATION, 17, HttpStatus.BAD_REQUEST, "A station cannot borrow from itself, so nothing was saved"),

    /** A lending request written down without the first day the gear is wanted for. */
    LENDING_FIRST_DAY_MISSING(
            Area.FEDERATION,
            18,
            HttpStatus.BAD_REQUEST,
            "Give the first day the gear is wanted for, so nothing was saved"),

    /**
     * A lending request that is not here, or one between two other stations.
     *
     * <p>One code for every route that loads one, and for the access check behind them. The lookup
     * asks about every request on the instance, and the check that follows is what narrows it to
     * the borrowing and the owning station. Giving each route its own code would say that the
     * request exists between two stations the reader is no part of, which is the one thing the
     * shared answer withholds.
     */
    LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS(
            Area.FEDERATION,
            19,
            HttpStatus.NOT_FOUND,
            "That lending request is not here any more, or it is not yours to open"),

    /** Gear put against a lending request that has not been agreed to yet. */
    LENDING_REQUEST_NOT_APPROVED(
            Area.FEDERATION,
            20,
            HttpStatus.BAD_REQUEST,
            "Gear can only be put against a lending request that has been agreed to"),

    /** A decision on a lending request taken by a station other than the one holding the gear. */
    LENDING_NOT_THE_OWNING_STATION(
            Area.FEDERATION, 21, HttpStatus.BAD_REQUEST, "Only the station the gear belongs to can do this"),

    /** A message on a lending request with nothing written in it. */
    LENDING_MESSAGE_NEEDS_TEXT(
            Area.FEDERATION,
            22,
            HttpStatus.BAD_REQUEST,
            "A message needs something written in it, so nothing was sent"),

    /** Gear held back over a span that is missing its first or its last day. */
    LENDING_BLOCK_SPAN_MISSING(
            Area.FEDERATION,
            23,
            HttpStatus.BAD_REQUEST,
            "Give the first and the last day the gear is held back, so nothing was saved"),

    /** An offer written without saying whether the gear goes out or is held back. */
    SHARE_GRANT_MISSING(
            Area.FEDERATION,
            24,
            HttpStatus.BAD_REQUEST,
            "Say whether the gear goes on offer or is held back, so nothing was saved"),

    /** An offer written without saying who it reaches. */
    SHARE_SCOPE_MISSING(
            Area.FEDERATION,
            25,
            HttpStatus.BAD_REQUEST,
            "Say whether the offer reaches every partner station or only the ones named, so nothing was saved"),

    /** A partner that could not be read back after being suspended, with the suspension already in. */
    FEDERATION_PARTNER_NOT_HERE_AFTER_SUSPENDING(
            Area.FEDERATION, 26, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A partner that could not be read back after being resumed, with the resumption already in. */
    FEDERATION_PARTNER_NOT_HERE_AFTER_RESUMING(
            Area.FEDERATION, 27, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A borrowing request that could not be read back after being approved, with the approval already in. */
    LENDING_REQUEST_NOT_HERE_AFTER_APPROVAL(
            Area.FEDERATION, 28, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A borrowing request that could not be read back after being declined, with the refusal already in. */
    LENDING_REQUEST_NOT_HERE_AFTER_DECLINE(
            Area.FEDERATION, 29, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A borrowing request that could not be read back after the gear was marked handed over. */
    LENDING_REQUEST_NOT_HERE_AFTER_LENDING(
            Area.FEDERATION, 30, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A borrowing request that could not be read back after the gear was marked given back. */
    LENDING_REQUEST_NOT_HERE_AFTER_RETURN(
            Area.FEDERATION, 31, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A borrowing request that could not be read back after being closed, with the closing already in. */
    LENDING_REQUEST_NOT_HERE_AFTER_CLOSING(
            Area.FEDERATION, 32, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** What a beacon has gathered, asked for on an instance that is not a beacon. */
    BEACON_NOT_RECEIVING(Area.BEACON, 1, HttpStatus.NOT_FOUND, Sentences.BEACON_NOT_RECEIVING),

    /** A fault marked as seen after it had gone. */
    BEACON_FAULT_NOT_HERE(
            Area.BEACON, 2, HttpStatus.NOT_FOUND, "That fault is not here any more, so nothing was changed"),

    /** A report marked as seen after it had gone. */
    BEACON_REPORT_NOT_ACKNOWLEDGED(
            Area.BEACON, 3, HttpStatus.NOT_FOUND, "That report is not here any more, so nothing was changed"),

    /** The report a picture was asked for, which is not here. */
    BEACON_REPORT_NOT_HERE_FOR_PICTURE(Area.BEACON, 4, HttpStatus.NOT_FOUND, Sentences.BEACON_REPORT_NOT_HERE),

    /** A picture asked for from a report that came without one. */
    BEACON_REPORT_HAS_NO_PICTURE(Area.BEACON, 5, HttpStatus.NOT_FOUND, "That report came without a picture"),

    /** A picture a report names whose stored file is gone. */
    BEACON_REPORT_PICTURE_NOT_HERE(Area.BEACON, 6, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** Something a beacon holds, addressed by something that is not a number. */
    BEACON_ID_NOT_A_NUMBER(Area.BEACON, 7, HttpStatus.BAD_REQUEST, Sentences.BEACON_ID_NOT_A_NUMBER),

    /** A problem addressed by something that is not a number. */
    BEACON_PROBLEM_ID_NOT_A_NUMBER(Area.BEACON, 8, HttpStatus.BAD_REQUEST, Sentences.BEACON_ID_NOT_A_NUMBER),

    /** A problem asked for on an instance that writes nothing down about problems. */
    PROBLEM_LOG_NOT_RUNNING(Area.BEACON, 9, HttpStatus.NOT_FOUND, Sentences.PROBLEM_LOG_NOT_RUNNING),

    /** A problem addressed by a number nothing written down here uses. */
    BEACON_PROBLEM_NOT_HERE(Area.BEACON, 10, HttpStatus.NOT_FOUND, "That problem is not here any more"),

    /** A send of several problems that ticked none of them. */
    BEACON_NOTHING_CHOSEN_TO_SEND(
            Area.BEACON, 11, HttpStatus.BAD_REQUEST, "Tick at least one problem to send, so nothing was sent"),

    /** Several problems sent on an instance that writes nothing down about problems. */
    PROBLEM_LOG_NOT_RUNNING_ON_SEND(Area.BEACON, 12, HttpStatus.NOT_FOUND, Sentences.PROBLEM_LOG_NOT_RUNNING),

    /** A report addressed by a number nothing here uses. */
    BEACON_REPORT_NOT_HERE(Area.BEACON, 13, HttpStatus.NOT_FOUND, Sentences.BEACON_REPORT_NOT_HERE),

    /** Something sent on from an instance that reports to no beacon. */
    BEACON_NOT_SET_UP(
            Area.BEACON, 14, HttpStatus.BAD_REQUEST, "This instance reports to no beacon, so nothing was sent"),

    /** A delivery to an instance that is not a beacon. */
    BEACON_INTAKE_NOT_RECEIVING(Area.BEACON, 15, HttpStatus.NOT_FOUND, Sentences.BEACON_NOT_RECEIVING),

    /** More deliveries from one address than a beacon takes. */
    BEACON_INTAKE_TOO_MANY(Area.BEACON, 16, HttpStatus.FORBIDDEN, "Too many deliveries from this address"),

    /** A delivery larger than anything honest a beacon reads. */
    BEACON_INTAKE_TOO_LARGE(Area.BEACON, 17, HttpStatus.BAD_REQUEST, "That delivery is larger than a beacon reads"),

    /** A delivery that carries neither a key nor a signature. */
    BEACON_INTAKE_NOT_SIGNED(Area.BEACON, 18, HttpStatus.FORBIDDEN, "A delivery to a beacon has to be signed"),

    /** A delivery whose signature does not match what was sent. */
    BEACON_INTAKE_SIGNATURE_NOT_GOOD(
            Area.BEACON, 19, HttpStatus.FORBIDDEN, "That signature does not match the delivery"),

    /** A delivery whose key is not a key at all. */
    BEACON_INTAKE_KEY_NOT_READ(Area.BEACON, 20, HttpStatus.FORBIDDEN, "That is not a key"),

    /** A picture delivery whose picture could not be kept. */
    BEACON_INTAKE_PICTURE_MISSING(
            Area.BEACON, 21, HttpStatus.BAD_REQUEST, "A picture delivery arrived without a picture"),

    /** A delivery of figures written to a protocol version newer than this beacon reads. */
    BEACON_INTAKE_PROTOCOL_TOO_NEW(
            Area.BEACON, 22, HttpStatus.BAD_REQUEST, "This beacon does not speak that version of the protocol yet"),

    /** The appointment a line of equipment hangs on, which is not here. */
    EQUIPMENT_APPOINTMENT_NOT_HERE(Area.EQUIPMENT, 1, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /**
     * A line of what an appointment needs that is not here, or one hanging on another appointment.
     *
     * <p>One code for both on purpose. The appointment in the path is what scopes the line to the
     * caller's station, so a line of another appointment has to look absent.
     */
    EQUIPMENT_NEED_NOT_HERE(
            Area.EQUIPMENT, 2, HttpStatus.NOT_FOUND, "That line of what the appointment needs is not here any more"),

    /** What an appointment needs, counted against what is there, and refused by the count. */
    EQUIPMENT_COVERAGE_NOT_WORKED_OUT(
            Area.EQUIPMENT,
            3,
            HttpStatus.BAD_REQUEST,
            "What the appointment needs could not be counted against what is there"),

    /** A line written onto an appointment that the station refused to take. */
    EQUIPMENT_NEED_NOT_SAVED(
            Area.EQUIPMENT,
            4,
            HttpStatus.BAD_REQUEST,
            "That line could not be written onto the appointment, so nothing was saved"),

    /** A line of an appointment changed to something the station refused to take. */
    EQUIPMENT_NEED_NOT_CHANGED(
            Area.EQUIPMENT, 5, HttpStatus.BAD_REQUEST, "That line could not be changed, so nothing was saved"),

    /** A handover written down that names no piece at all. */
    EQUIPMENT_HANDOVER_NAMES_NOTHING(
            Area.EQUIPMENT, 6, HttpStatus.BAD_REQUEST, "Name at least one piece that went out, so nothing was saved"),

    /** A piece written down as gone out that the station refused to take. */
    EQUIPMENT_HANDOVER_NOT_SAVED(
            Area.EQUIPMENT,
            7,
            HttpStatus.BAD_REQUEST,
            "That piece could not be written down as gone out, so nothing was saved"),

    /** A handover taken back after it had gone. */
    EQUIPMENT_HANDOVER_NOT_HERE_TO_UNDO(
            Area.EQUIPMENT, 8, HttpStatus.NOT_FOUND, "That handover is not here any more, so nothing was changed"),

    /** Equipment asked about without naming the day it is about. */
    EQUIPMENT_DATE_MISSING(Area.EQUIPMENT, 9, HttpStatus.BAD_REQUEST, Sentences.EQUIPMENT_DATE_MISSING),

    /** A day equipment was asked about on that does not read as a date. */
    EQUIPMENT_DATE_NOT_A_DATE(Area.EQUIPMENT, 10, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** What goes with a piece, asked for without naming the piece. */
    RECOMMENDATION_PIECE_MISSING(Area.EQUIPMENT, 11, HttpStatus.BAD_REQUEST, Sentences.EQUIPMENT_PIECE_MISSING),

    /** What goes with a piece, asked for with something that is not a number. */
    RECOMMENDATION_PIECE_NOT_A_NUMBER(Area.EQUIPMENT, 12, HttpStatus.BAD_REQUEST, Sentences.EQUIPMENT_PIECE_MISSING),

    /** A collected list counted again without the span it is wanted for. */
    COLLECTED_LIST_WINDOW_MISSING(
            Area.EQUIPMENT, 13, HttpStatus.BAD_REQUEST, "Give the first day the collected list is wanted for"),

    /** A find whose picture is not here. */
    LOST_ITEM_PICTURE_NOT_HERE(Area.LOST_AND_FOUND, 1, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A picture hung on a find, arriving with no file in it. */
    LOST_ITEM_UPLOAD_MISSING_FILE(Area.LOST_AND_FOUND, 2, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A picture hung on a find in a format that is not taken. */
    LOST_ITEM_PICTURE_KIND_NOT_TAKEN(
            Area.LOST_AND_FOUND, 3, HttpStatus.BAD_REQUEST, Sentences.KB_PICTURE_KIND_NOT_TAKEN),

    /** A picture of a find that the store would not keep. */
    LOST_ITEM_PICTURE_NOT_TAKEN(
            Area.LOST_AND_FOUND,
            4,
            HttpStatus.BAD_REQUEST,
            "That picture was not one that could be kept, so nothing was saved"),

    /** A picture of a find that could not be worked through at all. */
    LOST_ITEM_PICTURE_NOT_PROCESSED(
            Area.LOST_AND_FOUND, 5, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A find claimed for somebody the reader does not look after. */
    LOST_ITEM_CLAIM_NOT_YOURS_TO_MAKE(
            Area.LOST_AND_FOUND,
            6,
            HttpStatus.BAD_REQUEST,
            "You do not look after this member, so nothing was claimed"),

    /** A find claimed that somebody else has claimed already, or that has gone. */
    LOST_ITEM_ALREADY_CLAIMED(
            Area.LOST_AND_FOUND, 7, HttpStatus.BAD_REQUEST, "That find is claimed already, or it is not here any more"),

    /** A claim taken back off a find nobody has claimed. */
    LOST_ITEM_NOT_CLAIMED_TO_RELEASE(Area.LOST_AND_FOUND, 8, HttpStatus.BAD_REQUEST, Sentences.LOST_ITEM_NOT_CLAIMED),

    /** A claim of somebody the reader neither is nor looks after, taken back. */
    LOST_ITEM_CLAIM_NOT_YOURS_TO_RELEASE(
            Area.LOST_AND_FOUND, 9, HttpStatus.BAD_REQUEST, "That claim is not yours to take back"),

    /** A claim that went while it was being taken back. */
    LOST_ITEM_CLAIM_ALREADY_GONE(Area.LOST_AND_FOUND, 10, HttpStatus.BAD_REQUEST, Sentences.LOST_ITEM_NOT_CLAIMED),

    /** A find handed back that nobody has claimed. */
    LOST_ITEM_NOT_CLAIMED_TO_HAND_OVER(
            Area.LOST_AND_FOUND, 11, HttpStatus.BAD_REQUEST, Sentences.LOST_ITEM_NOT_CLAIMED),

    /** A find written down with a day of finding that does not read as a date. */
    LOST_ITEM_FOUND_DATE_NOT_A_DATE(
            Area.LOST_AND_FOUND, 12, HttpStatus.BAD_REQUEST, "The day the thing was found is not a date"),

    /** Figures about a station asked for by somebody who has not said which station. */
    INSIGHTS_NO_STATION_CHOSEN(Area.INSIGHTS, 1, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** Figures about a page addressed by something that is not a number. */
    INSIGHTS_PAGE_NOT_A_NUMBER(Area.INSIGHTS, 2, HttpStatus.BAD_REQUEST, "That does not name a page"),

    /** Figures asked for without the span of time they are to cover. */
    INSIGHTS_WINDOW_MISSING(
            Area.INSIGHTS, 3, HttpStatus.BAD_REQUEST, "Give the first and the last moment the figures are to cover"),

    /** A span the figures were asked for over whose ends do not read as moments. */
    INSIGHTS_WINDOW_NOT_A_MOMENT(
            Area.INSIGHTS, 4, HttpStatus.BAD_REQUEST, "The span the figures were asked for is not made of moments"),

    /** More pages asked for at once than the list hands out. */
    INSIGHTS_LIMIT_OUT_OF_RANGE(Area.INSIGHTS, 5, HttpStatus.BAD_REQUEST, "Ask for between 1 and 500 pages"),

    /** How many pages to show, given as something that is not a number. */
    INSIGHTS_LIMIT_NOT_A_NUMBER(Area.INSIGHTS, 6, HttpStatus.BAD_REQUEST, "How many pages to show has to be a number"),

    /** A span for the list of pages whose last moment falls before its first. */
    INSIGHTS_WINDOW_ENDS_BEFORE_IT_STARTS(
            Area.INSIGHTS, 7, HttpStatus.BAD_REQUEST, Sentences.INSIGHTS_WINDOW_BACKWARDS),

    /**
     * A page that is not here, or one of another station.
     *
     * <p>One code for both on purpose. These figures are a station's own, and telling the two apart
     * would say that the page exists at a station the reader may not see.
     */
    INSIGHTS_PAGE_NOT_HERE(Area.INSIGHTS, 8, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A span for one page's figures whose last moment falls before its first. */
    INSIGHTS_PAGE_WINDOW_ENDS_BEFORE_IT_STARTS(
            Area.INSIGHTS, 9, HttpStatus.BAD_REQUEST, Sentences.INSIGHTS_WINDOW_BACKWARDS),

    /**
     * An attendance sheet that is gone, whose template is gone, or that sits at another station.
     * One code for all three deliberately: the lookup asks only about this station's own sheets, and
     * telling them apart would say that the sheet exists at a station the reader may not see.
     */
    ATTENDANCE_SHEET_NOT_HERE(Area.ATTENDANCE, 1, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE),

    /**
     * A template that is gone, or belongs to another station. One code deliberately: the lookup and
     * the station check right behind it answer alike, because telling them apart would say that the
     * template exists at a station the reader may not see.
     */
    ATTENDANCE_TEMPLATE_NOT_HERE_OR_NOT_YOURS(
            Area.ATTENDANCE, 2, HttpStatus.NOT_FOUND, Sentences.EVENT_TEMPLATE_NOT_HERE),

    /** An entry asked for by a route that then checks which station its sheet is at. */
    ATTENDANCE_ENTRY_NOT_HERE(
            Area.ATTENDANCE, 3, HttpStatus.NOT_FOUND, "That entry on the attendance sheet is not here any more"),

    /**
     * A member that is gone, or belongs to another station. One code deliberately: the lookup and
     * the station check right behind it answer alike, because telling them apart would say that the
     * member is at a station the reader may not see.
     */
    ATTENDANCE_MEMBER_NOT_HERE(Area.ATTENDANCE, 4, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A template written down without a name. */
    ATTENDANCE_TEMPLATE_NEEDS_A_NAME(
            Area.ATTENDANCE, 5, HttpStatus.BAD_REQUEST, Sentences.ATTENDANCE_TEMPLATE_NEEDS_A_NAME),

    /** A template that went between the station check and the read of its fields and groups. */
    ATTENDANCE_TEMPLATE_GONE_WHILE_READ(Area.ATTENDANCE, 6, HttpStatus.NOT_FOUND, Sentences.EVENT_TEMPLATE_NOT_HERE),

    /** A template renamed to nothing. */
    ATTENDANCE_TEMPLATE_RENAME_NEEDS_A_NAME(
            Area.ATTENDANCE, 7, HttpStatus.BAD_REQUEST, Sentences.ATTENDANCE_TEMPLATE_NEEDS_A_NAME),

    /** A template that went before the rename reached it. */
    ATTENDANCE_TEMPLATE_NOT_HERE_TO_CHANGE(
            Area.ATTENDANCE, 8, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_TEMPLATE_NOT_HERE_ON_WRITE),

    /** A template that went before the deletion reached it. */
    ATTENDANCE_TEMPLATE_NOT_HERE_TO_DELETE(
            Area.ATTENDANCE, 9, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_TEMPLATE_NOT_HERE_ON_WRITE),

    /** A field added to a template without a name, without a kind, or without either. */
    ATTENDANCE_FIELD_DETAILS_MISSING(
            Area.ATTENDANCE, 10, HttpStatus.BAD_REQUEST, Sentences.ATTENDANCE_FIELD_DETAILS_MISSING),

    /** A field on a template left without a name or without a kind while it was being changed. */
    ATTENDANCE_FIELD_CHANGE_DETAILS_MISSING(
            Area.ATTENDANCE, 11, HttpStatus.BAD_REQUEST, Sentences.ATTENDANCE_FIELD_DETAILS_MISSING),

    /** A field that went before the change reached it. */
    ATTENDANCE_FIELD_NOT_HERE_TO_CHANGE(Area.ATTENDANCE, 12, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_FIELD_NOT_HERE),

    /** A field that went before the deletion reached it. */
    ATTENDANCE_FIELD_NOT_HERE_TO_DELETE(Area.ATTENDANCE, 13, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_FIELD_NOT_HERE),

    /** A day the sheet was looked for on that does not read as a date. */
    ATTENDANCE_DAY_NOT_A_DATE(Area.ATTENDANCE, 14, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A sheet that went between the station check and the read of its fields and entries. */
    ATTENDANCE_SHEET_GONE_WHILE_READ(Area.ATTENDANCE, 15, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE),

    /** A sheet that went before it could be reopened. */
    ATTENDANCE_SHEET_NOT_HERE_TO_REOPEN(
            Area.ATTENDANCE, 16, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE_ON_WRITE),

    /** A sheet that went before it could be closed. */
    ATTENDANCE_SHEET_NOT_HERE_TO_CLOSE(
            Area.ATTENDANCE, 17, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE_ON_WRITE),

    /** A sheet that went before the change reached it. */
    ATTENDANCE_SHEET_NOT_HERE_TO_CHANGE(
            Area.ATTENDANCE, 18, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE_ON_WRITE),

    /** A sheet that went before the deletion reached it. */
    ATTENDANCE_SHEET_NOT_HERE_TO_DELETE(
            Area.ATTENDANCE, 19, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_SHEET_NOT_HERE_ON_WRITE),

    /** An entry put on a sheet without saying which member it is for. */
    ATTENDANCE_ENTRY_NAMES_NO_MEMBER(
            Area.ATTENDANCE, 20, HttpStatus.BAD_REQUEST, "Name the member this entry is for, so nothing was saved"),

    /** An arrival written against an entry that went first. */
    ATTENDANCE_CHECK_IN_ENTRY_NOT_HERE(
            Area.ATTENDANCE, 21, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE),

    /** A departure written against an entry that went first. */
    ATTENDANCE_CHECK_OUT_ENTRY_NOT_HERE(
            Area.ATTENDANCE, 22, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE),

    /** An entry that went before the deletion reached it. */
    ATTENDANCE_ENTRY_NOT_HERE_TO_DELETE(
            Area.ATTENDANCE, 23, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE),

    /** An entry changed without saying what the member's attendance now is. */
    ATTENDANCE_STATUS_NOT_GIVEN(
            Area.ATTENDANCE,
            24,
            HttpStatus.BAD_REQUEST,
            "Say whether the member was present, was absent, declined or is still unconfirmed, "
                    + "so nothing was saved"),

    /** An attendance written against an entry that went first. */
    ATTENDANCE_STATUS_ENTRY_NOT_HERE(
            Area.ATTENDANCE, 25, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE),

    /** Times cleared on an entry that went first. */
    ATTENDANCE_RESET_TIMES_ENTRY_NOT_HERE(
            Area.ATTENDANCE, 26, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE),

    /** A sheet asked for as a PDF that could not be made into one. */
    ATTENDANCE_SHEET_PDF_NOT_MADE(
            Area.ATTENDANCE, 27, HttpStatus.NOT_FOUND, "That attendance sheet could not be handed out as a PDF"),

    /** A report asked for without the span of days it is to cover. */
    ATTENDANCE_REPORT_SPAN_MISSING(
            Area.ATTENDANCE, 28, HttpStatus.BAD_REQUEST, "Give the first and the last day the report is to cover"),

    /** A report asked for without naming whom it is about. */
    ATTENDANCE_REPORT_AUDIENCE_MISSING(
            Area.ATTENDANCE,
            29,
            HttpStatus.BAD_REQUEST,
            "Name at least one kind of member or one group for the report"),

    /** A report asked for as a table file, with nothing to put in it. */
    ATTENDANCE_REPORT_TABLE_EMPTY(Area.ATTENDANCE, 30, HttpStatus.NOT_FOUND, Sentences.NOTHING_TO_EXPORT),

    /** A report asked for as a PDF, with nothing to put in it. */
    ATTENDANCE_REPORT_PDF_EMPTY(Area.ATTENDANCE, 31, HttpStatus.NOT_FOUND, Sentences.NOTHING_TO_EXPORT),

    /** A set of report settings kept for later without a name. */
    ATTENDANCE_REPORT_PRESET_NEEDS_A_NAME(
            Area.ATTENDANCE, 32, HttpStatus.BAD_REQUEST, "A saved report needs a name, so nothing was saved"),

    /** A saved report of another station, asked to be thrown away. */
    ATTENDANCE_REPORT_PRESET_NOT_YOURS(
            Area.ATTENDANCE, 33, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_REPORT_PRESET_NOT_HERE),

    /** A saved report that went before the deletion reached it. */
    ATTENDANCE_REPORT_PRESET_NOT_HERE_TO_DELETE(
            Area.ATTENDANCE, 34, HttpStatus.NOT_FOUND, Sentences.ATTENDANCE_REPORT_PRESET_NOT_HERE),

    /** An absence written down without saying whom it is for. */
    ABSENCE_MEMBER_NOT_NAMED(
            Area.ATTENDANCE, 35, HttpStatus.BAD_REQUEST, "Name the member this absence is for, so nothing was saved"),

    /** An absence written down without its first or its last day. */
    ABSENCE_SPAN_MISSING(Area.ATTENDANCE, 36, HttpStatus.BAD_REQUEST, Sentences.ABSENCE_SPAN_MISSING),

    /** An absence whose last day falls before its first. */
    ABSENCE_ENDS_BEFORE_IT_STARTS(Area.ATTENDANCE, 37, HttpStatus.BAD_REQUEST, Sentences.ABSENCE_ENDS_BEFORE_IT_STARTS),

    /** An absence asked to be thrown away that is not here. */
    ABSENCE_NOT_HERE(Area.ATTENDANCE, 38, HttpStatus.NOT_FOUND, Sentences.ABSENCE_NOT_HERE),

    /** An absence that went before the deletion reached it. */
    ABSENCE_NOT_HERE_TO_DELETE(Area.ATTENDANCE, 39, HttpStatus.NOT_FOUND, Sentences.ABSENCE_NOT_HERE_ON_WRITE),

    /** An absence written down for oneself by somebody who is not a member of the station. */
    ABSENCE_NOT_A_STATION_MEMBER(Area.ATTENDANCE, 40, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** An absence written down for oneself without its first or its last day. */
    MY_ABSENCE_SPAN_MISSING(Area.ATTENDANCE, 41, HttpStatus.BAD_REQUEST, Sentences.ABSENCE_SPAN_MISSING),

    /** An absence written down for oneself whose last day falls before its first. */
    MY_ABSENCE_ENDS_BEFORE_IT_STARTS(
            Area.ATTENDANCE, 42, HttpStatus.BAD_REQUEST, Sentences.ABSENCE_ENDS_BEFORE_IT_STARTS),

    /** An absence written down for a member the reader does not look after. */
    ABSENCE_MEMBER_NOT_YOURS(Area.ATTENDANCE, 43, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** An absence of one's own asked to be taken back that is not here. */
    MY_ABSENCE_NOT_HERE(Area.ATTENDANCE, 44, HttpStatus.NOT_FOUND, Sentences.ABSENCE_NOT_HERE),

    /** An absence of somebody the reader neither is nor looks after, asked to be taken back. */
    MY_ABSENCE_NOT_YOURS_TO_DELETE(Area.ATTENDANCE, 45, HttpStatus.FORBIDDEN, "That absence is not yours to take back"),

    /** An absence of one's own that went before the deletion reached it. */
    MY_ABSENCE_NOT_HERE_TO_DELETE(Area.ATTENDANCE, 46, HttpStatus.NOT_FOUND, Sentences.ABSENCE_NOT_HERE_ON_WRITE),

    /** A body arrived that is not JSON at all. */
    BODY_NOT_JSON(Area.BODY, 1, HttpStatus.BAD_REQUEST, "The request body is not valid JSON, so nothing was saved"),

    /** A body arrived carrying a field the endpoint has no place for. */
    BODY_UNEXPECTED_FIELD(
            Area.BODY, 2, HttpStatus.BAD_REQUEST, "The request body carries a field this endpoint does not accept"),

    /** A body arrived shaped differently from what the endpoint reads. */
    BODY_DOES_NOT_MATCH(
            Area.BODY, 3, HttpStatus.BAD_REQUEST, "The request body does not match what this endpoint expects"),

    /** A body arrived whose shape was right but whose contents were refused. */
    BODY_VALUE_REJECTED(
            Area.BODY, 4, HttpStatus.BAD_REQUEST, "A value in the request body is not one this endpoint accepts"),

    /** Something in the request could not be used, and whoever refused it said nothing readable. */
    INPUT_NOT_USABLE(
            Area.BODY, 5, HttpStatus.BAD_REQUEST, "Something in what was sent could not be used, so nothing was saved"),

    /**
     * An entry that is not here, or one the reader was never among the people it was addressed to.
     *
     * <p>One code for both on purpose. Splitting them would tell a reader that an entry exists
     * which they were never meant to know about.
     */
    NEWS_NOT_HERE_OR_NOT_YOURS(Area.NEWS, 1, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** An entry written without a title. */
    NEWS_NEEDS_A_TITLE(Area.NEWS, 2, HttpStatus.BAD_REQUEST, Sentences.NEWS_NEEDS_A_TITLE),

    /** An entry corrected after it had gone. */
    NEWS_NOT_HERE_ON_UPDATE(Area.NEWS, 3, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An entry withdrawn after it had gone. */
    NEWS_NOT_DELETED(Area.NEWS, 4, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE),

    /** Blocks saved against an entry that has gone. */
    NEWS_NOT_HERE_ON_BLOCK_SAVE(Area.NEWS, 5, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An entry handed to the page editor after it had gone. */
    NEWS_NOT_HERE_ON_BLOCK_SWITCH(Area.NEWS, 6, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An attachment of an entry, addressed after it had gone. */
    NEWS_ATTACHMENT_NOT_HERE(Area.NEWS, 7, HttpStatus.NOT_FOUND, Sentences.ATTACHMENT_NOT_HERE),

    /** An attachment asked for without naming the file to hang on the entry. */
    NEWS_ATTACHMENT_FILE_NOT_NAMED(Area.NEWS, 8, HttpStatus.BAD_REQUEST, "Name the file to hang on this entry"),

    /** An attachment given a new label after it had gone. */
    NEWS_ATTACHMENT_NOT_RELABELLED(
            Area.NEWS, 9, HttpStatus.NOT_FOUND, "That attachment is not here any more, so nothing was changed"),

    /** An attachment taken off an entry after it had gone. */
    NEWS_ATTACHMENT_NOT_DETACHED(Area.NEWS, 10, HttpStatus.NOT_FOUND, Sentences.ATTACHMENT_NOT_HERE),

    /** A comment left under an entry with nothing written in it. */
    NEWS_COMMENT_NEEDS_TEXT(Area.NEWS, 11, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment changed after it had gone. */
    NEWS_COMMENT_NOT_HERE_ON_UPDATE(Area.NEWS, 12, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment somebody else wrote, changed. */
    NEWS_COMMENT_NOT_YOURS_TO_EDIT(Area.NEWS, 13, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_EDIT),

    /** A comment changed to nothing at all. */
    NEWS_COMMENT_NEEDS_TEXT_ON_UPDATE(Area.NEWS, 14, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment that went while it was being changed. */
    NEWS_COMMENT_NOT_HERE_AFTER_UPDATE(Area.NEWS, 15, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment deleted after it had gone. */
    NEWS_COMMENT_NOT_HERE_ON_DELETE(Area.NEWS, 16, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment somebody else wrote, deleted by a reader who does not moderate. */
    NEWS_COMMENT_NOT_YOURS_TO_DELETE(Area.NEWS, 17, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_DELETE),

    /** A comment that went while it was being deleted. */
    NEWS_COMMENT_NOT_DELETED(Area.NEWS, 18, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A public blog address naming a station that is not here. */
    STATION_NOT_HERE_BEHIND_BLOG(Area.NEWS, 19, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The station behind a blog listing, gone between resolving it and reading it. */
    STATION_NOT_HERE_BEHIND_BLOG_LIST(Area.NEWS, 20, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A blog listing at a station that keeps no public blog. */
    PUBLIC_BLOG_SWITCHED_OFF_FOR_LIST(Area.NEWS, 21, HttpStatus.NOT_FOUND, Sentences.PUBLIC_BLOG_NOT_HERE),

    /** The station behind a blog feed, gone between resolving it and reading it. */
    STATION_NOT_HERE_BEHIND_BLOG_FEED(Area.NEWS, 22, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A blog feed at a station that keeps no public blog. */
    PUBLIC_BLOG_SWITCHED_OFF_FOR_FEED(Area.NEWS, 23, HttpStatus.NOT_FOUND, Sentences.PUBLIC_BLOG_NOT_HERE),

    /** A blog feed that could not be written out. */
    BLOG_FEED_NOT_MADE(
            Area.NEWS,
            24,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The blog feed could not be put together. Trying again may work"),

    /** The station behind a single blog entry, gone between resolving it and reading it. */
    STATION_NOT_HERE_BEHIND_BLOG_ENTRY(Area.NEWS, 25, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A single blog entry at a station that keeps no public blog. */
    PUBLIC_BLOG_SWITCHED_OFF_FOR_ENTRY(Area.NEWS, 26, HttpStatus.NOT_FOUND, Sentences.PUBLIC_BLOG_NOT_HERE),

    /**
     * A blog entry that is not here, or one that was never put on the public blog.
     *
     * <p>One code for both on purpose. A stranger trying ids in turn learns nothing either way,
     * which is the whole point of answering a withheld entry as a missing one.
     */
    PUBLIC_BLOG_ENTRY_NOT_HERE(Area.NEWS, 27, HttpStatus.NOT_FOUND, "That blog entry is not here"),

    /** An entry written with nothing in it, where it is not built from blocks. */
    NEWS_NEEDS_SOMETHING_WRITTEN(
            Area.NEWS, 28, HttpStatus.BAD_REQUEST, "An entry needs something written in it, so nothing was saved"),

    /** An entry of the instance, published without a title. */
    SYSTEM_NEWS_NEEDS_A_TITLE(Area.NEWS, 29, HttpStatus.BAD_REQUEST, Sentences.NEWS_NEEDS_A_TITLE),

    /** An entry of the instance, corrected without a title. */
    SYSTEM_NEWS_NEEDS_A_TITLE_ON_UPDATE(Area.NEWS, 30, HttpStatus.BAD_REQUEST, Sentences.NEWS_NEEDS_A_TITLE),

    /** An entry of the instance, corrected after it had gone. */
    SYSTEM_NEWS_NOT_HERE_ON_UPDATE(Area.NEWS, 31, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An entry of the instance, withdrawn after it had gone. */
    SYSTEM_NEWS_NOT_DELETED(Area.NEWS, 32, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE),

    /** Blocks saved against an entry of the instance that has gone. */
    SYSTEM_NEWS_NOT_HERE_ON_BLOCK_SAVE(Area.NEWS, 33, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An entry of the instance, handed to the page editor after it had gone. */
    SYSTEM_NEWS_NOT_HERE_ON_BLOCK_SWITCH(Area.NEWS, 34, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE_ON_WRITE),

    /** An upload into the library the instance holds, arriving with no file in it. */
    INSTANCE_UPLOAD_MISSING_FILE(Area.NEWS, 35, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** An upload into the library the instance holds, larger than the instance takes. */
    INSTANCE_UPLOAD_TOO_LARGE(Area.NEWS, 36, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_TOO_LARGE),

    /** An upload the library refused on its own terms. */
    INSTANCE_UPLOAD_NOT_TAKEN(
            Area.NEWS, 37, HttpStatus.BAD_REQUEST, "That file is not one the library takes, so nothing was saved"),

    /** An upload into the library the instance holds, which did not come through. */
    INSTANCE_UPLOAD_NOT_SAVED(Area.NEWS, 38, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /**
     * A file of the instance that is not here, or one that belongs to a station instead.
     *
     * <p>One code for both on purpose. A station's file is that station's business, and saying so
     * would confirm it exists to somebody addressing the instance's own library.
     */
    INSTANCE_FILE_NOT_HERE(Area.NEWS, 39, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file of the instance that went while it was being removed. */
    INSTANCE_FILE_NOT_DELETED(Area.NEWS, 40, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /**
     * An entry of the instance that is not here, or one a station wrote for its own members.
     *
     * <p>One code for both on purpose. These routes answer for what the instance said and nothing
     * else, and a station's entry has to look absent rather than withheld.
     */
    SYSTEM_NEWS_NOT_HERE(Area.NEWS, 41, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE),

    /** An entry asked for by a partner instance, gone between the share check and the read. */
    REMOTE_NEWS_NOT_HERE(Area.NEWS, 42, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE),

    /** A comment from a partner instance with nothing written in it. */
    REMOTE_NEWS_COMMENT_NEEDS_TEXT(Area.NEWS, 43, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment from a partner instance, changed to nothing at all. */
    REMOTE_NEWS_COMMENT_NEEDS_TEXT_ON_UPDATE(Area.NEWS, 44, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment changed by a partner instance after it had gone. */
    REMOTE_NEWS_COMMENT_NOT_HERE_ON_UPDATE(Area.NEWS, 45, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment of another member, changed by a partner instance. */
    REMOTE_NEWS_COMMENT_NOT_YOURS_TO_EDIT(
            Area.NEWS, 46, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_EDIT),

    /** A comment that went while a partner instance was changing it. */
    REMOTE_NEWS_COMMENT_NOT_HERE_AFTER_UPDATE(Area.NEWS, 47, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment deleted by a partner instance after it had gone. */
    REMOTE_NEWS_COMMENT_NOT_HERE_ON_DELETE(Area.NEWS, 48, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment of another member, deleted by a partner instance. */
    REMOTE_NEWS_COMMENT_NOT_YOURS_TO_DELETE(
            Area.NEWS, 49, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_DELETE),

    /** A comment that went while a partner instance was deleting it. */
    REMOTE_NEWS_COMMENT_NOT_DELETED(Area.NEWS, 50, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** An entry addressed by a partner instance it was never shared with. */
    NEWS_NOT_SHARED_WITH_PARTNER(Area.NEWS, 51, HttpStatus.NOT_FOUND, Sentences.NEWS_NOT_HERE),

    /** A comment sent on to a partner instance with nothing written in it. */
    FEDERATED_NEWS_COMMENT_NEEDS_TEXT(Area.NEWS, 52, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /**
     * A board that is not here, or one the reader may not see.
     *
     * <p>One code for both on purpose. An invisible board answers exactly as a missing one, so a
     * member cannot run through board keys to learn which boards their station keeps.
     */
    BOARD_NOT_HERE_OR_NOT_YOURS(
            Area.BOARDS, 1, HttpStatus.NOT_FOUND, "That board is not here, or it is not yours to open"),

    /** A ticket that is not on the board its key names. */
    BOARD_TICKET_NOT_HERE(Area.BOARDS, 2, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A board changed by a session that stands for no member of the station. */
    NOT_A_MEMBER_FOR_BOARD_EDIT(Area.BOARDS, 3, HttpStatus.BAD_REQUEST, Sentences.NOT_A_MEMBER_FOR_BOARDS),

    /** A board changed by somebody who may read it but not write to it. */
    BOARD_NOT_YOURS_TO_EDIT(Area.BOARDS, 4, HttpStatus.FORBIDDEN, "This board is not yours to change"),

    /** A board read by a session that stands for no member of the station. */
    NOT_A_MEMBER_FOR_BOARD_VIEW(Area.BOARDS, 5, HttpStatus.BAD_REQUEST, Sentences.NOT_A_MEMBER_FOR_BOARDS),

    /** A board created without a name. */
    BOARD_NEEDS_A_NAME(Area.BOARDS, 6, HttpStatus.BAD_REQUEST, "A board needs a name, so nothing was saved"),

    /** A board created without the short key its tickets are numbered under. */
    BOARD_NEEDS_A_SHORT_KEY(
            Area.BOARDS, 7, HttpStatus.BAD_REQUEST, "A board needs a short key of its own, so nothing was saved"),

    /** A board opened by a member it is not shown to. */
    BOARD_NOT_YOURS_TO_OPEN(Area.BOARDS, 8, HttpStatus.FORBIDDEN, "This board is not yours to open"),

    /** A board that went between being changed and being read back. */
    BOARD_NOT_HERE_AFTER_CHANGE(Area.BOARDS, 9, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A board that went while it was being deleted. */
    BOARD_NOT_DELETED(Area.BOARDS, 10, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A label created without a name. */
    BOARD_LABEL_NEEDS_A_NAME(Area.BOARDS, 11, HttpStatus.BAD_REQUEST, "A label needs a name, so nothing was saved"),

    /** A label of this board, changed after it had gone. */
    BOARD_LABEL_NOT_HERE_ON_CHANGE(Area.BOARDS, 12, HttpStatus.NOT_FOUND, Sentences.BOARD_LABEL_NOT_HERE),

    /** A label of this board, deleted after it had gone. */
    BOARD_LABEL_NOT_HERE_ON_DELETE(Area.BOARDS, 13, HttpStatus.NOT_FOUND, Sentences.BOARD_LABEL_NOT_HERE),

    /** A ticket created without a title. */
    TICKET_NEEDS_A_TITLE(Area.BOARDS, 14, HttpStatus.BAD_REQUEST, "A ticket needs a title, so nothing was saved"),

    /** A ticket that went while it was being deleted. */
    TICKET_NOT_DELETED(Area.BOARDS, 15, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A ticket moved to another lane after it had gone. */
    TICKET_NOT_HERE_ON_MOVE(Area.BOARDS, 16, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A ticket that went between being changed and being read back. */
    TICKET_NOT_HERE_AFTER_CHANGE(Area.BOARDS, 17, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A comment left on a ticket with nothing written in it. */
    TICKET_COMMENT_NEEDS_TEXT(Area.BOARDS, 18, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A checklist entry added without a title. */
    CHECKLIST_ITEM_NEEDS_A_TITLE(
            Area.BOARDS, 19, HttpStatus.BAD_REQUEST, "A checklist entry needs a title, so nothing was saved"),

    /** A field of the board, filled in after it had gone. */
    TICKET_FIELD_NOT_HERE(Area.BOARDS, 20, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** A field filled in with something the field does not take. */
    TICKET_FIELD_VALUE_NOT_ACCEPTED(
            Area.BOARDS, 21, HttpStatus.BAD_REQUEST, "That value does not suit this field, so nothing was saved"),

    /** A comment addressed on a ticket it does not hang on. */
    TICKET_COMMENT_NOT_HERE(Area.BOARDS, 22, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** An attachment upload on a ticket, arriving with no file in it. */
    TICKET_UPLOAD_MISSING_FILE(Area.BOARDS, 23, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** An attachment upload on a ticket, larger than the instance takes. */
    TICKET_UPLOAD_TOO_LARGE(Area.BOARDS, 24, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_TOO_LARGE),

    /** An attachment upload on a ticket that could not be read in. */
    TICKET_UPLOAD_NOT_READ(Area.BOARDS, 25, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_SAVED),

    /** An attachment of a ticket whose stored file is gone. */
    TICKET_ATTACHMENT_CONTENT_NOT_HERE(Area.BOARDS, 26, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** An attachment of a ticket that could not be handed out. */
    TICKET_ATTACHMENT_NOT_READ(
            Area.BOARDS,
            27,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "That attachment could not be read out. Trying again may work"),

    /** An attachment that went while it was being deleted. */
    TICKET_ATTACHMENT_NOT_DELETED(Area.BOARDS, 28, HttpStatus.NOT_FOUND, Sentences.ATTACHMENT_NOT_HERE),

    /**
     * An attachment that is not here, or one hanging on a different ticket.
     *
     * <p>One code for both on purpose. Tying the attachment to the ticket in the path is what
     * scopes it to the caller's station, so an attachment of another ticket has to look absent.
     */
    TICKET_ATTACHMENT_NOT_HERE(Area.BOARDS, 29, HttpStatus.NOT_FOUND, Sentences.ATTACHMENT_NOT_HERE),

    /** A second ticket named in the body of a link request, which is not here. */
    LINKED_TICKET_NOT_HERE(Area.BOARDS, 30, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** The board a ticket named for linking sits on, which is not here. */
    LINKED_TICKET_BOARD_NOT_HERE(Area.BOARDS, 31, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A wiki file named for linking, which is not here. */
    LINKED_WIKI_FILE_NOT_HERE(Area.BOARDS, 32, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A link added to a ticket without an address to point at. */
    WEBLINK_NEEDS_AN_ADDRESS(Area.BOARDS, 33, HttpStatus.BAD_REQUEST, "A link needs an address, so nothing was saved"),

    /** A link of a ticket, deleted after it had gone. */
    WEBLINK_NOT_HERE(Area.BOARDS, 34, HttpStatus.NOT_FOUND, "That link is not here any more"),

    /** A shared board addressed through a partner this station does not know. */
    FEDERATION_PARTNER_NOT_HERE_FOR_BOARD(
            Area.BOARDS, 35, HttpStatus.NOT_FOUND, Sentences.FEDERATION_PARTNER_NOT_HERE_FOR_BOARDS),

    /** A shared board opened by a member it is not shown to. */
    FEDERATED_BOARD_NOT_YOURS_TO_VIEW(Area.BOARDS, 36, HttpStatus.FORBIDDEN, "This shared board is not yours to open"),

    /** A shared board changed by a member who may read it but not write to it. */
    FEDERATED_BOARD_NOT_YOURS_TO_EDIT(
            Area.BOARDS, 37, HttpStatus.FORBIDDEN, "This shared board is not yours to change"),

    /** A bookmark on a shared board of a partner this station does not know. */
    FEDERATION_PARTNER_NOT_HERE_FOR_BOOKMARK(
            Area.BOARDS, 38, HttpStatus.NOT_FOUND, Sentences.FEDERATION_PARTNER_NOT_HERE_FOR_BOARDS),

    /** The local access rules of a shared board that is not here. */
    FEDERATED_BOARD_NOT_HERE_ON_OVERRIDE_READ(
            Area.BOARDS, 39, HttpStatus.NOT_FOUND, Sentences.FEDERATED_BOARD_NOT_HERE),

    /** Local access rules written for a shared board that is not here. */
    FEDERATED_BOARD_NOT_HERE_ON_OVERRIDE_WRITE(
            Area.BOARDS, 40, HttpStatus.NOT_FOUND, Sentences.FEDERATED_BOARD_NOT_HERE),

    /** A board request from another instance that carries no signature this one accepts. */
    BOARD_FEDERATION_SIGNATURE_NOT_GOOD(
            Area.BOARDS, 41, HttpStatus.FORBIDDEN, "This request carries no signature this instance accepts"),

    /** A board addressed by another instance under a key this station does not use. */
    REMOTE_BOARD_NOT_HERE(Area.BOARDS, 42, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A ticket addressed by another instance under a number that board does not use. */
    REMOTE_BOARD_TICKET_NOT_HERE(Area.BOARDS, 43, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A board addressed by an instance it is not shared with. */
    REMOTE_BOARD_NOT_SHARED(Area.BOARDS, 44, HttpStatus.FORBIDDEN, "That board is not shared with this instance"),

    /** A board written to by an instance it is only shared with for reading. */
    REMOTE_BOARD_NOT_WRITABLE(
            Area.BOARDS, 45, HttpStatus.FORBIDDEN, "That board is shared with this instance for reading only"),

    /** A board that went between the share check and the read another instance asked for. */
    REMOTE_BOARD_NOT_HERE_ON_READ(Area.BOARDS, 46, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A board that went while another instance was reading who its members are. */
    REMOTE_BOARD_NOT_HERE_FOR_MEMBERS(Area.BOARDS, 47, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A ticket that went between the share check and the read another instance asked for. */
    REMOTE_TICKET_NOT_HERE_ON_READ(Area.BOARDS, 48, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A board that went while another instance was creating a ticket on it. */
    REMOTE_BOARD_NOT_HERE_ON_TICKET_CREATE(Area.BOARDS, 49, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A board that went while another instance was changing one of its tickets. */
    REMOTE_BOARD_NOT_HERE_ON_TICKET_UPDATE(Area.BOARDS, 50, HttpStatus.NOT_FOUND, Sentences.BOARD_NOT_HERE),

    /** A ticket that went between being changed by another instance and being read back. */
    REMOTE_TICKET_NOT_HERE_AFTER_UPDATE(Area.BOARDS, 51, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A ticket moved by another instance after it had gone. */
    REMOTE_TICKET_NOT_HERE_ON_MOVE(Area.BOARDS, 52, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A ticket that went between being moved by another instance and being read back. */
    REMOTE_TICKET_NOT_HERE_AFTER_MOVE(Area.BOARDS, 53, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** A comment addressed by another instance on a ticket it does not hang on. */
    REMOTE_TICKET_COMMENT_NOT_HERE(Area.BOARDS, 54, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A ticket named by number in a request from another instance, which that board does not use. */
    REMOTE_TICKET_NOT_HERE_BY_NUMBER(Area.BOARDS, 55, HttpStatus.NOT_FOUND, Sentences.BOARD_TICKET_NOT_HERE),

    /** Documents at all, at a station that has switched them off. */
    DOCUMENTS_SWITCHED_OFF(Area.DOCUMENTS, 1, HttpStatus.NOT_FOUND, "This station does not keep documents"),

    /** The documents of a member the reader neither manages nor answers for. */
    DOCUMENT_LIST_NOT_YOURS(Area.DOCUMENTS, 2, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS),

    /** A hidden document being written by somebody who may not hide things. */
    DOCUMENT_HIDING_NOT_ALLOWED(Area.DOCUMENTS, 3, HttpStatus.FORBIDDEN, "You may not mark a document as hidden"),

    /** Work on a station's documents, asked for by somebody who has not said which station. */
    NO_STATION_CHOSEN_FOR_DOCUMENTS(Area.DOCUMENTS, 4, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** An upload naming members, by somebody who may not file documents against one. */
    DOCUMENT_MEMBERS_NOT_YOURS_TO_NAME(Area.DOCUMENTS, 5, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** A document whose stored file is gone. */
    DOCUMENT_CONTENT_NOT_HERE(Area.DOCUMENTS, 6, HttpStatus.NOT_FOUND, Sentences.DOCUMENT_NOT_HERE),

    /** A document whose tile picture could not be had. */
    DOCUMENT_THUMBNAIL_NOT_HERE(Area.DOCUMENTS, 7, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A document marked hidden, read by somebody who may not see hidden ones. */
    DOCUMENT_HIDDEN_FROM_YOU(Area.DOCUMENTS, 8, HttpStatus.NOT_FOUND, Sentences.DOCUMENT_NOT_HERE),

    /** A document of somebody the reader does not answer for. */
    DOCUMENT_NOT_YOURS_TO_READ(Area.DOCUMENTS, 9, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS),

    /** A change to a document by somebody who may read it but not write it. */
    DOCUMENT_NOT_YOURS_TO_CHANGE(Area.DOCUMENTS, 10, HttpStatus.FORBIDDEN, "You may not change this document"),

    /** A document filed against a member by somebody who may not file one. */
    DOCUMENT_NOT_YOURS_TO_ADD(Area.DOCUMENTS, 11, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** A mailbox rule filing attachments against members, written by somebody who may not file one. */
    DOCUMENT_NOT_YOURS_TO_FILE(Area.DOCUMENTS, 12, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS_TO_ADD),

    /** An upload to the document store that arrived with no file in it. */
    DOCUMENT_UPLOAD_MISSING_FILE(Area.DOCUMENTS, 13, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A document heavier than the store takes. */
    DOCUMENT_UPLOAD_TOO_LARGE(Area.DOCUMENTS, 14, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_TOO_LARGE),

    /** An answer to a page form marked as seen by somebody who is no member of the station. */
    PAGE_FORM_ANSWER_NOT_YOURS_TO_MARK(Area.PAGES, 12, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** A member-list cell whose description of who to show could not be read. */
    PAGE_MEMBER_LIST_NOT_READ(
            Area.PAGES,
            13,
            HttpStatus.BAD_REQUEST,
            "What was sent could not be read, so no member list was worked out"),

    /** A page written down without a title. */
    PAGE_NEEDS_A_TITLE(Area.PAGES, 14, HttpStatus.BAD_REQUEST, Sentences.PAGE_NEEDS_A_TITLE),

    /** A page refused while it was being created, for a reason the reader cannot act on. */
    PAGE_NOT_CREATED(Area.PAGES, 15, HttpStatus.BAD_REQUEST, "The page could not be created, so nothing was saved"),

    /** A page whose title was emptied while it was being saved. */
    PAGE_TITLE_MISSING_ON_SAVE(Area.PAGES, 16, HttpStatus.BAD_REQUEST, Sentences.PAGE_NEEDS_A_TITLE),

    /** A page saved without the address it is reached at. */
    PAGE_ADDRESS_MISSING_ON_SAVE(
            Area.PAGES, 17, HttpStatus.BAD_REQUEST, "A page needs an address of its own, so nothing was saved"),

    /** A page whose visibility was changed without saying what to. */
    PAGE_VISIBILITY_MISSING(Area.PAGES, 18, HttpStatus.BAD_REQUEST, "Say who may see the page, so nothing was changed"),

    /** A share link replaced from a screen that was still showing the link before it. */
    PAGE_LINK_ALREADY_REPLACED(
            Area.PAGES,
            19,
            HttpStatus.CONFLICT,
            "This page has been given a different link since you last looked, so nothing was changed"),

    /** A page that cannot be the one the site opens on, named as the one it opens on. */
    LANDING_PAGE_NOT_SET(
            Area.PAGES,
            20,
            HttpStatus.BAD_REQUEST,
            "That page cannot be the one the site opens on, so nothing was changed"),

    /** An upload from the page editor that arrived with no file in it. */
    PAGE_UPLOAD_MISSING_FILE(Area.PAGES, 21, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A file from the page editor heavier than the instance takes. */
    PAGE_UPLOAD_TOO_LARGE(Area.PAGES, 22, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_TOO_LARGE),

    /** A file from the page editor the station had no room for. */
    PAGE_UPLOAD_NOT_SAVED(Area.PAGES, 23, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** A file from the page editor that could not be worked through at all. */
    PAGE_UPLOAD_NOT_PROCESSED(Area.PAGES, 24, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_PROCESSED),

    /** A page that could not be read back after being saved, with the title, link and cells already in. */
    PAGE_NOT_HERE_AFTER_SAVE(
            Area.PAGES, 25, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A page that could not be read back after how far it reaches was changed, with the change already in. */
    PAGE_NOT_HERE_AFTER_VISIBILITY_CHANGE(
            Area.PAGES, 26, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A procedure template written down without a name. */
    PROCEDURE_TEMPLATE_NEEDS_A_NAME(
            Area.PROCEDURES, 3, HttpStatus.BAD_REQUEST, Sentences.PROCEDURE_TEMPLATE_NEEDS_A_NAME),

    /** A procedure template renamed to nothing. */
    PROCEDURE_TEMPLATE_RENAME_NEEDS_A_NAME(
            Area.PROCEDURES, 4, HttpStatus.BAD_REQUEST, Sentences.PROCEDURE_TEMPLATE_NEEDS_A_NAME),

    /** A procedure template that went before the rename reached it. */
    PROCEDURE_TEMPLATE_NOT_HERE_TO_CHANGE(
            Area.PROCEDURES,
            5,
            HttpStatus.NOT_FOUND,
            "That procedure template is not here any more, so nothing was changed"),

    /** A step added to a procedure template without a title. */
    PROCEDURE_TEMPLATE_STEP_NEEDS_A_TITLE(
            Area.PROCEDURES, 6, HttpStatus.BAD_REQUEST, Sentences.PROCEDURE_STEP_NEEDS_A_TITLE),

    /** A step of a template that went before the change reached it. */
    PROCEDURE_TEMPLATE_STEP_NOT_HERE_TO_CHANGE(
            Area.PROCEDURES, 7, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** A step of a template that went before the deletion reached it. */
    PROCEDURE_TEMPLATE_STEP_NOT_HERE_TO_DELETE(
            Area.PROCEDURES, 8, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** Procedures asked for by appointment without saying which day of it. */
    PROCEDURE_OCCURRENCE_DAY_MISSING(
            Area.PROCEDURES,
            9,
            HttpStatus.BAD_REQUEST,
            "Name the day of the appointment the procedures were prepared for"),

    /** A day the procedures of an appointment were asked for on that does not read as a date. */
    PROCEDURE_OCCURRENCE_DAY_NOT_A_DATE(Area.PROCEDURES, 10, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A procedure written down without a name. */
    PROCEDURE_NEEDS_A_NAME(
            Area.PROCEDURES, 11, HttpStatus.BAD_REQUEST, "A procedure needs a name, so nothing was saved"),

    /** A procedure that went before the change reached it. */
    PROCEDURE_NOT_HERE_TO_CHANGE(
            Area.PROCEDURES, 12, HttpStatus.NOT_FOUND, "That procedure is not here any more, so nothing was changed"),

    /** A procedure marked done that was done already. */
    PROCEDURE_ALREADY_DONE(
            Area.PROCEDURES, 13, HttpStatus.BAD_REQUEST, "This procedure is already done, so nothing was changed"),

    /** A procedure reopened that was open already. */
    PROCEDURE_ALREADY_OPEN(
            Area.PROCEDURES, 14, HttpStatus.BAD_REQUEST, "This procedure is already open, so nothing was changed"),

    /** A procedure handed to nobody at all. */
    PROCEDURE_NAMES_NOBODY(
            Area.PROCEDURES,
            15,
            HttpStatus.BAD_REQUEST,
            "Name at least one member to hand the procedure to, so nothing was saved"),

    /** A step added to a procedure without a title. */
    PROCEDURE_STEP_NEEDS_A_TITLE(Area.PROCEDURES, 16, HttpStatus.BAD_REQUEST, Sentences.PROCEDURE_STEP_NEEDS_A_TITLE),

    /** A step of a procedure that went before the change reached it. */
    PROCEDURE_STEP_NOT_HERE_TO_CHANGE(
            Area.PROCEDURES, 17, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** A step of a procedure that went before the deletion reached it. */
    PROCEDURE_STEP_NOT_HERE_TO_DELETE(
            Area.PROCEDURES, 18, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** A step of a procedure that went before the tick reached it. */
    PROCEDURE_STEP_NOT_HERE_TO_TICK(
            Area.PROCEDURES, 19, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** A step nobody was meant to tick off themselves, ticked off by somebody it was handed to. */
    PROCEDURE_STEP_NOT_YOURS_TO_TICK(
            Area.PROCEDURES, 20, HttpStatus.FORBIDDEN, "This step is ticked off by whoever runs the procedure"),

    /** A step ticked off by somebody the procedure was never handed to. */
    PROCEDURE_NOT_HANDED_TO_YOU(Area.PROCEDURES, 21, HttpStatus.FORBIDDEN, Sentences.PROCEDURE_NOT_YOURS),

    /** A step that waits on another one, or is ticked off already. */
    PROCEDURE_STEP_NOT_READY_TO_TICK(
            Area.PROCEDURES,
            22,
            HttpStatus.BAD_REQUEST,
            "This step cannot be ticked off yet: a step it waits on is still open, or it is ticked off already"),

    /** A tick taken back by somebody who does not run the procedure. */
    PROCEDURE_STEP_NOT_YOURS_TO_UNTICK(
            Area.PROCEDURES, 23, HttpStatus.FORBIDDEN, "Only whoever runs the procedure can take a tick back"),

    /** A procedure that could not be read back after being changed, with the change already in. */
    PROCEDURE_NOT_HERE_AFTER_CHANGE(
            Area.PROCEDURES, 24, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A procedure that could not be read back after being marked done, with the marking already in. */
    PROCEDURE_NOT_HERE_AFTER_RESOLVING(
            Area.PROCEDURES, 25, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A procedure that could not be read back after being reopened, with the reopening already in. */
    PROCEDURE_NOT_HERE_AFTER_REOPENING(
            Area.PROCEDURES, 26, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A checklist written down without a name. */
    CHECKLIST_NEEDS_A_NAME(Area.CHECKLISTS, 1, HttpStatus.BAD_REQUEST, Sentences.CHECKLIST_NEEDS_A_NAME),

    /** A checklist written down with no column to tick anything off in. */
    CHECKLIST_NEEDS_A_COLUMN(
            Area.CHECKLISTS, 2, HttpStatus.BAD_REQUEST, "A checklist needs at least one column, so nothing was saved"),

    /** A checklist renamed to nothing. */
    CHECKLIST_RENAME_NEEDS_A_NAME(Area.CHECKLISTS, 3, HttpStatus.BAD_REQUEST, Sentences.CHECKLIST_NEEDS_A_NAME),

    /** A checklist told to follow a filter and an appointment at once. */
    CHECKLIST_FOLLOWS_ONE_THING(
            Area.CHECKLISTS,
            4,
            HttpStatus.BAD_REQUEST,
            "A checklist follows either a filter or an appointment, never both, so nothing was changed"),

    /** Columns reordered without saying what the new order is. */
    CHECKLIST_COLUMN_ORDER_MISSING(
            Area.CHECKLISTS,
            5,
            HttpStatus.BAD_REQUEST,
            "Name the columns in the order they are to stand in, so nothing was changed"),

    /** A new order of columns that leaves one out, or names one twice. */
    CHECKLIST_COLUMN_ORDER_INCOMPLETE(
            Area.CHECKLISTS,
            6,
            HttpStatus.BAD_REQUEST,
            "A new order has to name every column of this checklist exactly once, so nothing was changed"),

    /** Members put on a checklist without naming any of them. */
    CHECKLIST_NAMES_NO_MEMBERS(
            Area.CHECKLISTS,
            7,
            HttpStatus.BAD_REQUEST,
            "Name the members to put on the checklist, so nothing was saved"),

    /** A whole column ticked off without naming the rows it is to be ticked off on. */
    CHECKLIST_NAMES_NO_ROWS(
            Area.CHECKLISTS,
            8,
            HttpStatus.BAD_REQUEST,
            "Name the rows the column is to be set on, so nothing was changed"),

    /** A checklist that could not be drawn as a PDF. */
    CHECKLIST_PDF_NOT_MADE(Area.CHECKLISTS, 9, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHECKLIST_PDF_NOT_MADE),

    /** A checklist whose PDF was still being drawn when the work was cut short. */
    CHECKLIST_PDF_INTERRUPTED(Area.CHECKLISTS, 10, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHECKLIST_PDF_NOT_MADE),

    /** A column of a checklist that is gone. */
    CHECKLIST_COLUMN_NOT_HERE(Area.CHECKLISTS, 11, HttpStatus.NOT_FOUND, "That column is not here any more"),

    /** A column of one checklist reached through another. */
    CHECKLIST_COLUMN_ON_ANOTHER_LIST(
            Area.CHECKLISTS, 12, HttpStatus.FORBIDDEN, "That column belongs to another checklist"),

    /** A row of a checklist that is gone. */
    CHECKLIST_ROW_NOT_HERE(Area.CHECKLISTS, 13, HttpStatus.NOT_FOUND, "That row is not here any more"),

    /** A row of one checklist reached through another. */
    CHECKLIST_ROW_ON_ANOTHER_LIST(Area.CHECKLISTS, 14, HttpStatus.FORBIDDEN, "That row belongs to another checklist"),

    /** A checklist told to follow an appointment without saying which day of it. */
    CHECKLIST_OCCURRENCE_DAY_MISSING(
            Area.CHECKLISTS,
            15,
            HttpStatus.BAD_REQUEST,
            "Name the day of the appointment the checklist is to follow, so nothing was saved"),

    /** An appointment a checklist was told to follow that is gone, or belongs to another station. */
    CHECKLIST_APPOINTMENT_NOT_HERE(Area.CHECKLISTS, 16, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** An appointment the reader may not see at all, named as the one a checklist is to follow. */
    CHECKLIST_APPOINTMENT_NOT_YOURS_TO_FOLLOW(
            Area.CHECKLISTS,
            17,
            HttpStatus.FORBIDDEN,
            "You cannot see that appointment, so a checklist cannot follow it"),

    /** A day a checklist was told to follow that does not read as a date. */
    CHECKLIST_DAY_NOT_A_DATE(Area.CHECKLISTS, 18, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A filter told to combine its parts in a way there is none. */
    CHECKLIST_FILTER_MODE_UNKNOWN(
            Area.CHECKLISTS, 19, HttpStatus.BAD_REQUEST, "That is not a way of putting the filter together"),

    /** A column of a checklist written down without a label. */
    CHECKLIST_COLUMN_NEEDS_A_LABEL(
            Area.CHECKLISTS, 20, HttpStatus.BAD_REQUEST, "A column needs a label, so nothing was saved"),

    /** The media library, reached by somebody signed in who is no member of the station. */
    LIBRARY_NOT_YOURS_WITHOUT_MEMBERSHIP(
            Area.MEDIA_LIBRARY, 17, HttpStatus.FORBIDDEN, "Only a member of this station can use its media library"),

    /** A file withdrawn by somebody who did not upload it and does not manage the library. */
    FILE_NOT_YOURS_TO_REMOVE(
            Area.MEDIA_LIBRARY, 18, HttpStatus.FORBIDDEN, "Only the members who uploaded a file can remove it"),

    /** A folder written down without a name. */
    FOLDER_NEEDS_A_NAME(Area.MEDIA_LIBRARY, 19, HttpStatus.BAD_REQUEST, "A folder needs a name, so nothing was saved"),

    /** A tag written down without a name. */
    FILE_TAG_NEEDS_A_NAME(Area.MEDIA_LIBRARY, 20, HttpStatus.BAD_REQUEST, "A tag needs a name, so nothing was saved"),

    /** A file of the public site that is gone, addressed by the hash of its bytes. */
    PUBLIC_FILE_NOT_HERE(Area.MEDIA_LIBRARY, 21, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** The station a public file was addressed through, which no address of this instance names. */
    STATION_NOT_HERE_BEHIND_PUBLIC_FILE(Area.MEDIA_LIBRARY, 22, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Mail import at all, at a station that keeps no documents for it to file into. */
    MAIL_IMPORT_SWITCHED_OFF(
            Area.MAIL_IMPORT, 1, HttpStatus.NOT_FOUND, "This station does not read mail into its documents"),

    /**
     * A mailbox that is gone, or belongs to another station. One code deliberately: the lookup and
     * the station check right behind it answer alike, because telling them apart would say that the
     * mailbox is at a station the reader may not see.
     */
    MAILBOX_NOT_HERE(Area.MAIL_IMPORT, 2, HttpStatus.NOT_FOUND, "That mailbox is not here any more"),

    /**
     * A rule that is gone, or hangs off a mailbox of another station. One code deliberately, for the
     * same reason the mailbox has one: the lookup and the station check behind it answer alike, and
     * telling them apart would say that the rule is at a station the reader may not see.
     */
    MAILBOX_RULE_NOT_HERE(Area.MAIL_IMPORT, 3, HttpStatus.NOT_FOUND, "That mailbox rule is not here any more"),

    /** A piece of gear asked for by a route that then checks which station it is at. */
    ITEM_NOT_HERE(Area.INVENTORY, 1, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** The inventory a piece of gear was written down in, gone while the piece is still here. */
    INVENTORY_NOT_HERE_BEHIND_ITEM(Area.INVENTORY, 2, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE_BEHIND_ITEM),

    /** Gear belonging to an owner the reader may write nothing down for. */
    GEAR_OWNER_NOT_YOURS_TO_CREATE(
            Area.INVENTORY, 3, HttpStatus.FORBIDDEN, "You may not write down gear that belongs to that owner"),

    /** An inventory asked for by a route that then checks which station it is at. */
    INVENTORY_NOT_HERE(Area.INVENTORY, 4, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /**
     * A requirement that is gone, or belongs to another station. One code deliberately: the one
     * lookup asks only about this station's own, and telling the two apart would say that the
     * requirement exists at a station the reader may not see.
     */
    REQUIREMENT_NOT_HERE(Area.INVENTORY, 5, HttpStatus.NOT_FOUND, Sentences.REQUIREMENT_NOT_HERE),

    /** The inventory a piece was to be taken into and handed over from, which is gone. */
    INVENTORY_NOT_HERE_ON_HAND_OUT(Area.INVENTORY, 6, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A member whose gear was asked about and who is not at this station. */
    MEMBER_NOT_HERE_FOR_GEAR(Area.INVENTORY, 7, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** An inventory being written down without a name. */
    INVENTORY_NEEDS_A_NAME(Area.INVENTORY, 8, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NEEDS_A_NAME),

    /** An inventory being written down without saying what kind of thing it holds. */
    INVENTORY_NEEDS_A_KIND(Area.INVENTORY, 9, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NEEDS_A_KIND),

    /** An inventory that went between the list being drawn and it being opened. */
    INVENTORY_NOT_HERE_ON_READ(Area.INVENTORY, 10, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A change to an inventory that would leave it without a name. */
    INVENTORY_NEEDS_A_NAME_ON_CHANGE(Area.INVENTORY, 11, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NEEDS_A_NAME),

    /** A change to an inventory that would leave it without a kind. */
    INVENTORY_NEEDS_A_KIND_ON_CHANGE(Area.INVENTORY, 12, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NEEDS_A_KIND),

    /** An inventory that went before the change to it could be written. */
    INVENTORY_NOT_CHANGED(Area.INVENTORY, 13, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** An inventory that was already gone when its deletion was asked for. */
    INVENTORY_NOT_DELETED(Area.INVENTORY, 14, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A size being written down without a name. */
    SIZE_NEEDS_A_NAME(Area.INVENTORY, 15, HttpStatus.BAD_REQUEST, Sentences.SIZE_NEEDS_A_NAME),

    /** A change to a size that would leave it without a name. */
    SIZE_NEEDS_A_NAME_ON_CHANGE(Area.INVENTORY, 16, HttpStatus.BAD_REQUEST, Sentences.SIZE_NEEDS_A_NAME),

    /** A size that went before the change to it could be written. */
    SIZE_NOT_CHANGED(Area.INVENTORY, 17, HttpStatus.NOT_FOUND, Sentences.SIZE_NOT_HERE),

    /** A size that was already gone when its deletion was asked for. */
    SIZE_NOT_DELETED(Area.INVENTORY, 18, HttpStatus.NOT_FOUND, Sentences.SIZE_NOT_HERE),

    /** A piece of gear being written down without a name. */
    ITEM_NEEDS_A_NAME(Area.INVENTORY, 19, HttpStatus.BAD_REQUEST, Sentences.ITEM_NEEDS_A_NAME),

    /** A stock-taking that hands pieces to members, sent by somebody who may hand none out. */
    HANDING_OUT_NOT_ALLOWED(Area.INVENTORY, 20, HttpStatus.FORBIDDEN, "You may not hand a piece of gear to a member"),

    /** A search for a piece of gear that names no code to search by. */
    NO_CODE_GIVEN_FOR_ITEM(Area.INVENTORY, 21, HttpStatus.BAD_REQUEST, Sentences.NO_CODE_GIVEN),

    /** A code no piece of gear at this station carries. */
    ITEM_NOT_HERE_BY_CODE(Area.INVENTORY, 22, HttpStatus.NOT_FOUND, "No piece of gear here carries that code"),

    /** A piece of gear that went between the list being drawn and it being opened. */
    ITEM_NOT_HERE_ON_READ(Area.INVENTORY, 23, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A change to a piece of gear that would leave it without a name. */
    ITEM_NEEDS_A_NAME_ON_CHANGE(Area.INVENTORY, 24, HttpStatus.BAD_REQUEST, Sentences.ITEM_NEEDS_A_NAME),

    /** A piece of gear that went before the change to it could be written. */
    ITEM_NOT_CHANGED(Area.INVENTORY, 25, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear that went before it could be moved into another inventory. */
    ITEM_NOT_MOVED(Area.INVENTORY, 26, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear that went before it could be handed over or taken back. */
    ITEM_NOT_ASSIGNED(Area.INVENTORY, 27, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear whose whereabouts were asked for and which is gone. */
    ITEM_NOT_HERE_ON_LOCATION(Area.INVENTORY, 28, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear that went before it could be put into a container. */
    ITEM_NOT_PUT_IN_CONTAINER(Area.INVENTORY, 29, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear and a container that do not go together. */
    ITEM_NOT_FOR_THIS_CONTAINER(
            Area.INVENTORY,
            30,
            HttpStatus.BAD_REQUEST,
            "That piece of gear cannot be put in that container, so nothing was saved"),

    /** A piece of gear being reported missing that is gone. */
    ITEM_NOT_HERE_ON_LOSS(Area.INVENTORY, 31, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A loss reported without a note at a station that asks for one. */
    LOSS_NEEDS_A_NOTE(
            Area.INVENTORY,
            32,
            HttpStatus.BAD_REQUEST,
            "This station asks for a note when gear goes missing, so nothing was saved"),

    /** A piece of gear that went between being read and being marked missing. */
    ITEM_NOT_MARKED_LOST(Area.INVENTORY, 33, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A loss reported for gear in nobody's hands, or by a session that stands for no member. */
    LOSS_NOT_YOURS_TO_REPORT(Area.INVENTORY, 34, HttpStatus.FORBIDDEN, Sentences.LOSS_NOT_YOURS_TO_REPORT),

    /** A loss reported for gear held by somebody the reader does not answer for. */
    LOSS_NOT_YOURS_TO_REPORT_FOR_THEM(Area.INVENTORY, 35, HttpStatus.FORBIDDEN, Sentences.LOSS_NOT_YOURS_TO_REPORT),

    /** A loss report whose attached file could not be read. */
    LOSS_REPORT_FILE_UNREADABLE(
            Area.INVENTORY, 36, HttpStatus.BAD_REQUEST, "That file could not be read, so nothing was saved"),

    /** A piece of gear that went before it could be marked as found again. */
    ITEM_NOT_MARKED_FOUND(Area.INVENTORY, 37, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear that was already gone when its deletion was asked for. */
    ITEM_NOT_DELETED(Area.INVENTORY, 38, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A requirement written down without saying which inventory it is about. */
    REQUIREMENT_NEEDS_AN_INVENTORY(
            Area.INVENTORY,
            39,
            HttpStatus.BAD_REQUEST,
            "A requirement has to say which inventory it is about, so nothing was saved"),

    /** A requirement written down without saying who it applies to. */
    REQUIREMENT_NEEDS_SOMEBODY_TO_APPLY_TO(
            Area.INVENTORY,
            40,
            HttpStatus.BAD_REQUEST,
            "A requirement has to say which kind of member or which group it applies to, so nothing was saved"),

    /** A requirement that went before the change to it could be written. */
    REQUIREMENT_NOT_CHANGED(Area.INVENTORY, 41, HttpStatus.NOT_FOUND, Sentences.REQUIREMENT_NOT_HERE),

    /** A requirement that went before it could be moved up or down the list. */
    REQUIREMENT_NOT_MOVED(Area.INVENTORY, 42, HttpStatus.NOT_FOUND, Sentences.REQUIREMENT_NOT_HERE),

    /** A requirement that was already gone when its deletion was asked for. */
    REQUIREMENT_NOT_DELETED(Area.INVENTORY, 43, HttpStatus.NOT_FOUND, Sentences.REQUIREMENT_NOT_HERE),

    /** A list of members and their gear that came out with nothing in it. */
    MEMBER_GEAR_LIST_EMPTY(Area.INVENTORY, 44, HttpStatus.BAD_REQUEST, Sentences.NOTHING_TO_EXPORT),

    /** A movement whose ends name no chain of steps to walk. */
    NO_FLOW_FOR_THIS_MOVEMENT(Area.INVENTORY, 45, HttpStatus.NOT_FOUND, Sentences.NO_FLOW_FOR_THIS_MOVEMENT),

    /** The chain of steps a movement is bound to, which is gone. */
    FLOW_NOT_HERE_BEHIND_BINDING(Area.INVENTORY, 46, HttpStatus.NOT_FOUND, Sentences.NO_FLOW_FOR_THIS_MOVEMENT),

    /** A question about which chain would be walked that does not say what the movement is for. */
    MOVEMENT_PURPOSE_MISSING(Area.INVENTORY, 47, HttpStatus.BAD_REQUEST, Sentences.MOVEMENT_PURPOSE_MISSING),

    /** A purpose spelled in a way no movement can have. */
    MOVEMENT_PURPOSE_NOT_KNOWN(
            Area.INVENTORY, 48, HttpStatus.BAD_REQUEST, "That is not something a movement can be for"),

    /** Something in the address that has to be a number and is not one. */
    NUMBER_EXPECTED_IN_ADDRESS(
            Area.INVENTORY, 49, HttpStatus.BAD_REQUEST, "A number was expected here and this is not one"),

    /** A chain of steps being written down without saying what it is for. */
    FLOW_NEEDS_A_PURPOSE(
            Area.INVENTORY,
            50,
            HttpStatus.BAD_REQUEST,
            "A chain of steps has to say what it is for, so nothing was saved"),

    /** A chain of steps that went before its new name could be written. */
    FLOW_NOT_RENAMED(Area.INVENTORY, 51, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A chain of steps that went between being renamed and being read back. */
    FLOW_NOT_HERE_AFTER_RENAME(Area.INVENTORY, 52, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A chain of steps that was already gone when its retirement was asked for. */
    FLOW_NOT_ARCHIVED(Area.INVENTORY, 53, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A step that went before the change to it could be written. */
    FLOW_STEP_NOT_CHANGED(Area.INVENTORY, 54, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A step that was already gone when its retirement was asked for. */
    FLOW_STEP_NOT_ARCHIVED(Area.INVENTORY, 55, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A chain pointed at a combination that names neither an owner nor a purpose. */
    BINDING_NEEDS_AN_OWNER_AND_A_PURPOSE(
            Area.INVENTORY,
            56,
            HttpStatus.BAD_REQUEST,
            "Say whose gear this is about and what the movement is for, so nothing was saved"),

    /** An order for the steps of a chain that names no step at all. */
    STEP_ORDER_NAMES_NO_STEPS(
            Area.INVENTORY,
            57,
            HttpStatus.BAD_REQUEST,
            "Name the steps in the order they are to be walked, so nothing was saved"),

    /** A step written down without saying who confirms it, what it is about, or where it leaves the gear. */
    STEP_NEEDS_ITS_PARTS(
            Area.INVENTORY,
            58,
            HttpStatus.BAD_REQUEST,
            "A step has to say who confirms it, what it is about and where the gear is left, so nothing was saved"),

    /**
     * A chain of steps that is gone, or belongs to another station. The two are one code
     * deliberately: telling them apart would say that the chain exists at a station the reader may
     * not see.
     */
    FLOW_NOT_HERE(Area.INVENTORY, 59, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A step asked for by a route that then checks whose chain it belongs to. */
    FLOW_STEP_NOT_HERE(Area.INVENTORY, 60, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A chain of steps that went between being changed and being read back. */
    FLOW_NOT_HERE_AFTER_CHANGE(Area.INVENTORY, 61, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A container asked for by a route that then checks which station it is at. */
    CONTAINER_NOT_HERE(Area.INVENTORY, 62, HttpStatus.NOT_FOUND, Sentences.CONTAINER_NOT_HERE),

    /** A kind of container that was read and then refused for what it said. */
    CONTAINER_KIND_NOT_CREATED(Area.INVENTORY, 63, HttpStatus.BAD_REQUEST, Sentences.CONTAINER_KIND_NOT_CREATED),

    /** A kind of container that is gone, or belongs to another station. */
    CONTAINER_KIND_NOT_HERE(Area.INVENTORY, 64, HttpStatus.NOT_FOUND, Sentences.CONTAINER_KIND_NOT_HERE),

    /** A kind of container that went before the change to it could be written. */
    CONTAINER_KIND_NOT_CHANGED(Area.INVENTORY, 65, HttpStatus.NOT_FOUND, Sentences.CONTAINER_KIND_NOT_HERE),

    /** A change to a kind of container that would leave it without a name. */
    CONTAINER_KIND_NEEDS_A_NAME(
            Area.INVENTORY, 66, HttpStatus.BAD_REQUEST, "A kind of container needs a name, so nothing was saved"),

    /** A kind of container that was already gone when its deletion was asked for. */
    CONTAINER_KIND_NOT_HERE_ON_DELETE(Area.INVENTORY, 67, HttpStatus.NOT_FOUND, Sentences.CONTAINER_KIND_NOT_HERE),

    /** A kind of container that went between being read and being deleted. */
    CONTAINER_KIND_NOT_DELETED(Area.INVENTORY, 68, HttpStatus.NOT_FOUND, Sentences.CONTAINER_KIND_NOT_HERE),

    /** A container that was read and then refused for what it said. */
    CONTAINER_NOT_CREATED(Area.INVENTORY, 69, HttpStatus.BAD_REQUEST, Sentences.CONTAINER_NOT_SAVED),

    /** A container that went before the change to it could be written. */
    CONTAINER_NOT_HERE_ON_CHANGE(Area.INVENTORY, 70, HttpStatus.NOT_FOUND, Sentences.CONTAINER_NOT_HERE),

    /** A change to a container that was read and then refused for what it said. */
    CONTAINER_NOT_CHANGED(Area.INVENTORY, 71, HttpStatus.BAD_REQUEST, Sentences.CONTAINER_NOT_SAVED),

    /** A container that was already gone when its deletion was asked for. */
    CONTAINER_NOT_DELETED(Area.INVENTORY, 72, HttpStatus.NOT_FOUND, Sentences.CONTAINER_NOT_HERE),

    /** A search for a container that names no code to search by. */
    NO_CODE_GIVEN_FOR_CONTAINER(Area.INVENTORY, 73, HttpStatus.BAD_REQUEST, Sentences.NO_CODE_GIVEN),

    /** A code no container at this station carries. */
    CONTAINER_NOT_HERE_BY_CODE(Area.INVENTORY, 74, HttpStatus.NOT_FOUND, "No container here carries that code"),

    /** A movement started without saying what it is for. */
    MOVEMENT_NEEDS_A_PURPOSE(Area.INVENTORY, 75, HttpStatus.BAD_REQUEST, Sentences.MOVEMENT_PURPOSE_MISSING),

    /** A movement raised for a member the reader neither is nor answers for. */
    MEMBER_NOT_YOURS_TO_ACT_FOR(Area.INVENTORY, 76, HttpStatus.FORBIDDEN, "You do not answer for this member"),

    /** A piece written down as newly arrived on a movement that is about no inventory. */
    ARRIVAL_HAS_NOWHERE_TO_GO(
            Area.INVENTORY,
            77,
            HttpStatus.BAD_REQUEST,
            "This movement is about no inventory, so a new piece of gear has nowhere to go and nothing was saved"),

    /** A piece written down as newly arrived where the owner already says what it sent. */
    ARRIVAL_NAMED_BY_THE_OWNER(
            Area.INVENTORY,
            78,
            HttpStatus.BAD_REQUEST,
            "The owner says what it sends, so pick the piece that arrived rather than writing down a new one"),

    /** A piece written down as newly arrived without a name. */
    ARRIVAL_NEEDS_A_NAME(Area.INVENTORY, 79, HttpStatus.BAD_REQUEST, Sentences.ITEM_NEEDS_A_NAME),

    /** Everything a member holds asked back without saying which member. */
    RETURN_OF_EVERYTHING_NEEDS_A_MEMBER(
            Area.INVENTORY, 80, HttpStatus.BAD_REQUEST, "Say which member is to hand everything back"),

    /** A member named for a return who is not at this station. */
    MEMBER_NOT_AT_THIS_STATION(Area.INVENTORY, 81, HttpStatus.BAD_REQUEST, "That member is not at this station"),

    /** A movement whose attached file was never written down. */
    MOVEMENT_DOCUMENT_NOT_HERE(Area.INVENTORY, 82, HttpStatus.NOT_FOUND, Sentences.MOVEMENT_DOCUMENT_NOT_HERE),

    /** A movement whose attached file is written down but whose stored copy is gone. */
    MOVEMENT_DOCUMENT_NOT_READ(Area.INVENTORY, 83, HttpStatus.NOT_FOUND, Sentences.MOVEMENT_DOCUMENT_NOT_HERE),

    /** A list of movements that came out with nothing in it. */
    MOVEMENT_LIST_EMPTY(Area.INVENTORY, 84, HttpStatus.NOT_FOUND, Sentences.NOTHING_TO_EXPORT),

    /** A correction that would leave a piece of gear somewhere a movement cannot put it. */
    CUSTODY_NOT_ONE_OF_THESE(
            Area.INVENTORY,
            85,
            HttpStatus.BAD_REQUEST,
            "A movement can put a piece of gear with its owner, at a station, with a member or in the post"),

    /** A movement that went between being read and being deleted. */
    MOVEMENT_NOT_DELETED(Area.INVENTORY, 86, HttpStatus.NOT_FOUND, "That movement is not here any more"),

    /**
     * A movement that is gone, or one this reader may not see. The two are one code deliberately:
     * telling them apart would say that the movement exists somewhere they are not shown.
     */
    MOVEMENT_NOT_HERE_OR_NOT_YOURS(Area.INVENTORY, 87, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** The inventory a field is written on, asked for before the field itself. */
    INVENTORY_NOT_HERE_BEHIND_FIELD(Area.INVENTORY, 88, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A field that is gone, or belongs to another inventory. */
    FIELD_NOT_IN_THIS_INVENTORY(Area.INVENTORY, 89, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** A piece of gear whose fields were asked for and which is gone. */
    ITEM_NOT_HERE_ON_FIELDS(Area.INVENTORY, 90, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A field that was read and then refused for what it said. */
    FIELD_NOT_CREATED(
            Area.INVENTORY,
            91,
            HttpStatus.BAD_REQUEST,
            "That field could not be saved: it needs a short key of its own, a name and a kind of answer, "
                    + "and it belongs either to a kind of gear or to one piece, never to both"),

    /** A field that went before the change to it could be written. */
    FIELD_NOT_HERE_ON_CHANGE(Area.INVENTORY, 92, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** A change to a field that was read and then refused for what it said. */
    FIELD_NOT_CHANGED(
            Area.INVENTORY,
            93,
            HttpStatus.BAD_REQUEST,
            "A field needs a name, and its settings have to match the kind of answer it takes, so nothing was saved"),

    /** A field that was already gone when its deletion was asked for. */
    FIELD_NOT_DELETED(Area.INVENTORY, 94, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** A self-check saved with no answers in it at all. */
    SELF_CHECK_WITHOUT_ANSWERS(
            Area.INVENTORY,
            95,
            HttpStatus.BAD_REQUEST,
            "The check arrived without any answers in it, so nothing was saved"),

    /** A loss or a swap held back for review that says neither which it is nor about what. */
    HELD_REPORT_INCOMPLETE(
            Area.INVENTORY,
            96,
            HttpStatus.BAD_REQUEST,
            "Say what is being reported and about which piece of gear, so nothing was saved"),

    /** A day something is due that cannot be read as a day. */
    DUE_DAY_NOT_A_DATE(
            Area.INVENTORY, 97, HttpStatus.BAD_REQUEST, "The day this is due is not a date, so nothing was saved"),

    /** A container whose contents were to be checked and which is gone. */
    CONTAINER_NOT_HERE_ON_CHECK(Area.INVENTORY, 98, HttpStatus.NOT_FOUND, Sentences.CONTAINER_NOT_HERE),

    /** A piece of gear named in a check that is gone. */
    ITEM_NOT_HERE_ON_CHECK(Area.INVENTORY, 99, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** The inventory a checked piece of gear belongs to, gone while the piece is still here. */
    INVENTORY_NOT_HERE_BEHIND_CHECKED_ITEM(
            Area.INVENTORY, 100, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE_BEHIND_ITEM),

    /** An inventory whose stock was to be checked and which is gone. */
    INVENTORY_NOT_HERE_ON_CHECK(Area.INVENTORY, 101, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A member whose gear was to be checked and who is not at this station. */
    MEMBER_NOT_HERE_ON_CHECK(Area.INVENTORY, 102, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A check of a container saved with no gear in it at all. */
    CONTAINER_CHECK_WITHOUT_ITEMS(Area.INVENTORY, 103, HttpStatus.BAD_REQUEST, Sentences.CHECK_WITHOUT_ITEMS),

    /** A check of a member saved with no gear in it at all. */
    MEMBER_CHECK_WITHOUT_ITEMS(Area.INVENTORY, 104, HttpStatus.BAD_REQUEST, Sentences.CHECK_WITHOUT_ITEMS),

    /** The last check of a member who has never been checked. */
    NO_CHECK_YET_FOR_MEMBER(Area.INVENTORY, 105, HttpStatus.NOT_FOUND, "This member has not been checked yet"),

    /** The inventory a kind of gear belongs to, asked for before the kind itself. */
    INVENTORY_NOT_HERE_BEHIND_KIND(Area.INVENTORY, 106, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A kind of gear that is gone, or belongs to another inventory. */
    ITEM_KIND_NOT_IN_INVENTORY(Area.INVENTORY, 107, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** A kind of gear that went before the change to it could be written. */
    ITEM_KIND_NOT_CHANGED(Area.INVENTORY, 108, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** A kind of gear that was already gone when its deletion was asked for. */
    ITEM_KIND_NOT_DELETED(Area.INVENTORY, 109, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** The inventory something was to be ordered for, which is gone. */
    INVENTORY_NOT_HERE_ON_PROCUREMENT(Area.INVENTORY, 110, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** An order that went before it could be marked as delivered. */
    PROCUREMENT_NOT_FULFILLED(Area.INVENTORY, 111, HttpStatus.NOT_FOUND, Sentences.PROCUREMENT_NOT_HERE),

    /** An order that was already gone when its deletion was asked for. */
    PROCUREMENT_NOT_DELETED(Area.INVENTORY, 112, HttpStatus.NOT_FOUND, Sentences.PROCUREMENT_NOT_HERE),

    /** A movement that could not be read back after being moved onto another chain of steps. */
    MOVEMENT_NOT_HERE_AFTER_RECHAIN(
            Area.INVENTORY, 113, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A registration whose answers were to be changed and is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_FIELD_CHANGE(
            Area.EVENTS, 1, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** The station a registration spreadsheet is headed with, gone between the two reads. */
    STATION_NOT_HERE_FOR_REGISTRATION_CSV(Area.EVENTS, 2, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The station a registration sheet is headed with, gone between the two reads. */
    STATION_NOT_HERE_FOR_REGISTRATION_SHEET(Area.EVENTS, 3, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The station a registration table is drawn for, gone between the two reads. */
    STATION_NOT_HERE_FOR_REGISTRATION_TABLE(Area.EVENTS, 4, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A registration whose status was to be set and is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_STATUS_CHANGE(
            Area.EVENTS, 5, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that went between being read and having its status written. */
    EVENT_REGISTRATION_STATUS_NOT_CHANGED(Area.EVENTS, 6, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration being answered for that is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_ANSWER(Area.EVENTS, 7, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that could not be marked as not coming. */
    EVENT_REGISTRATION_NOT_REFUSED(Area.EVENTS, 8, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that could not be marked as coming. */
    EVENT_REGISTRATION_NOT_CONFIRMED(Area.EVENTS, 9, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration being taken back that is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_WITHDRAWAL(
            Area.EVENTS, 10, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A registration that went between being read and being withdrawn. */
    EVENT_REGISTRATION_NOT_WITHDRAWN(Area.EVENTS, 11, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A withdrawal being undone whose registration is gone. */
    EVENT_REGISTRATION_NOT_HERE_ON_UNDO(Area.EVENTS, 12, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** An appointment that went between the screen being opened and the change being saved. */
    EVENT_NOT_HERE_ON_CHANGE(Area.EVENTS, 13, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** An appointment that was already gone when its deletion was asked for. */
    EVENT_NOT_HERE_ON_DELETE(Area.EVENTS, 14, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** An appointment that was already gone when its cancellation was asked for. */
    EVENT_NOT_HERE_ON_CANCELLATION(Area.EVENTS, 15, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A registration or a decline sent by a session that stands for no member of this station. */
    NOT_A_MEMBER_ON_REGISTRATION(Area.EVENTS, 16, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** A registration made for somebody the caller neither is nor looks after. */
    MEMBER_NOT_YOURS_TO_REGISTER(Area.EVENTS, 17, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** Answers being changed by a session that stands for no member of this station. */
    NOT_A_MEMBER_ON_ANSWER_CHANGE(Area.EVENTS, 18, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** The answers of somebody the caller neither is nor looks after. */
    REGISTRATION_ANSWERS_NOT_YOURS(Area.EVENTS, 19, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** A list of who is coming that could not be drawn as a sheet. */
    REGISTRATION_SHEET_NOT_DRAWN(Area.EVENTS, 20, HttpStatus.BAD_REQUEST, "This list cannot be turned into a sheet"),

    /** A list of who is coming asked for without saying which day it covers. */
    REGISTRATION_TABLE_NEEDS_A_DAY(
            Area.EVENTS, 21, HttpStatus.BAD_REQUEST, "A list of who is coming needs the day it is about"),

    /** A registration for an appointment that takes none. */
    EVENT_TAKES_NO_REGISTRATIONS(
            Area.EVENTS, 22, HttpStatus.BAD_REQUEST, "This appointment does not take registrations"),

    /** A registration sent after the deadline by somebody who does not keep the list. */
    REGISTRATION_CLOSED(Area.EVENTS, 23, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_CLOSED),

    /** A registration for somebody this appointment is not open to. */
    MEMBER_NOT_INVITED_TO_EVENT(Area.EVENTS, 24, HttpStatus.BAD_REQUEST, "This appointment is not open to that member"),

    /** A decision on a registration that is neither accepting it nor turning it down. */
    REGISTRATION_DECISION_UNKNOWN(
            Area.EVENTS, 25, HttpStatus.BAD_REQUEST, "A registration can only be accepted or turned down"),

    /** An answer given for somebody the caller may not answer for. */
    ANSWER_NOT_YOURS_TO_GIVE(Area.EVENTS, 26, HttpStatus.FORBIDDEN, Sentences.CANNOT_ANSWER_FOR_MEMBER),

    /** An answer changed after the deadline by somebody who does not keep the list. */
    REGISTRATION_CLOSED_ON_ANSWER_CHANGE(Area.EVENTS, 27, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_CLOSED),

    /** A withdrawal older than the few minutes it may be taken back in. */
    WITHDRAWAL_NO_LONGER_UNDONE(Area.EVENTS, 28, HttpStatus.BAD_REQUEST, Sentences.NO_LONGER_TAKEN_BACK),

    /** A withdrawal, or an undo of one, for somebody the caller may not answer for. */
    WITHDRAWAL_NOT_YOURS_TO_ANSWER(Area.EVENTS, 29, HttpStatus.FORBIDDEN, Sentences.CANNOT_ANSWER_FOR_MEMBER),

    /** A registration for an appointment that happens once and carries no time of its own. */
    EVENT_HAS_NO_START_TIME(
            Area.EVENTS, 30, HttpStatus.BAD_REQUEST, "This appointment has no start time, so nothing was saved"),

    /** A registration for an appointment that comes round again, without saying for which day. */
    REGISTRATION_NEEDS_A_DAY(
            Area.EVENTS, 31, HttpStatus.BAD_REQUEST, "A repeating appointment needs the day you are signing up for"),

    /** A registration for a day this appointment does not fall on. */
    REGISTRATION_DAY_NOT_AN_OCCURRENCE(
            Area.EVENTS, 32, HttpStatus.BAD_REQUEST, "This appointment does not fall on that day"),

    /** A shared appointment that went between being cleared for a partner and being read. */
    SHARED_EVENT_NOT_HERE(Area.EVENTS, 33, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /**
     * A file of a shared appointment that is gone, hangs on another appointment, or is one the
     * appointment keeps back. One code deliberately: telling them apart would say that a file kept
     * back is there.
     */
    SHARED_EVENT_FILE_NOT_HERE(Area.EVENTS, 34, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** The appointment a shared file hangs on, gone between the two reads. */
    EVENT_NOT_HERE_BEHIND_SHARED_FILE(Area.EVENTS, 35, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A file of a shared appointment whose stored bytes are gone. */
    SHARED_EVENT_FILE_CONTENT_NOT_HERE(Area.EVENTS, 36, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A partner confirming its own members where this station never handed that over. */
    PARTNER_DOES_NOT_CONFIRM_ITS_OWN(
            Area.EVENTS, 37, HttpStatus.FORBIDDEN, "This station decides the registrations for this appointment"),

    /** A registration a partner is confirming that is gone. */
    PARTNER_REGISTRATION_NOT_HERE(Area.EVENTS, 38, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A partner confirming one member more than the places it was given. */
    NO_PLACES_LEFT_FOR_PARTNER(Area.EVENTS, 39, HttpStatus.BAD_REQUEST, Sentences.NO_PLACES_LEFT),

    /** A withdrawal a partner asks to undo, past the few minutes it may be undone in. */
    PARTNER_WITHDRAWAL_NO_LONGER_UNDONE(Area.EVENTS, 40, HttpStatus.BAD_REQUEST, Sentences.NO_LONGER_TAKEN_BACK),

    /** A comment arriving from a partner with nothing written in it. */
    PARTNER_COMMENT_NEEDS_TEXT(Area.EVENTS, 41, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment arriving from a partner tied to a day that is not a date. */
    PARTNER_COMMENT_DAY_NOT_A_DATE(Area.EVENTS, 42, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A change to a comment arriving from a partner with nothing written in it. */
    PARTNER_COMMENT_CHANGE_NEEDS_TEXT(Area.EVENTS, 43, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment a partner asks to delete that is gone, or was never that partner's to delete. */
    PARTNER_COMMENT_NOT_DELETED(Area.EVENTS, 44, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /**
     * An appointment a partner names that this station does not share with it, which covers one
     * that is not here at all. One code deliberately: telling them apart would let a partner learn
     * which appointments exist by trying identifiers.
     */
    EVENT_NOT_SHARED_WITH_PARTNER(Area.EVENTS, 45, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A category being made without a name. */
    EVENT_CATEGORY_NEEDS_A_NAME(Area.EVENTS, 46, HttpStatus.BAD_REQUEST, "A category needs a name"),

    /** A category that went before the change to it could be written. */
    EVENT_CATEGORY_NOT_CHANGED(Area.EVENTS, 47, HttpStatus.NOT_FOUND, Sentences.EVENT_CATEGORY_NOT_HERE),

    /** A category that was already gone when its deletion was asked for. */
    EVENT_CATEGORY_NOT_DELETED(Area.EVENTS, 48, HttpStatus.NOT_FOUND, Sentences.EVENT_CATEGORY_NOT_HERE),

    /** A break being made without a name. */
    EVENT_BREAK_NEEDS_A_NAME(Area.EVENTS, 49, HttpStatus.BAD_REQUEST, "A break needs a name"),

    /** A break that went before the change to it could be written. */
    EVENT_BREAK_NOT_CHANGED(Area.EVENTS, 50, HttpStatus.NOT_FOUND, Sentences.EVENT_BREAK_NOT_HERE),

    /** A break that was already gone when its deletion was asked for. */
    EVENT_BREAK_NOT_DELETED(Area.EVENTS, 51, HttpStatus.NOT_FOUND, Sentences.EVENT_BREAK_NOT_HERE),

    /** A day an appointment's questions are asked about that is not a date. */
    EVENT_DAY_NOT_A_DATE(Area.EVENTS, 52, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** An answer kept per day, written without saying which day it belongs to. */
    EVENT_FIELD_VALUE_NEEDS_A_DAY(
            Area.EVENTS, 53, HttpStatus.BAD_REQUEST, "An answer kept per day needs the day it belongs to"),

    /** A file of a partner's appointment that the partner answered nothing for. */
    FEDERATED_FILE_NOT_HERE(Area.EVENTS, 54, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file whose holder answered with something no file could be made of. */
    FEDERATED_FILE_UNREADABLE(
            Area.EVENTS,
            55,
            HttpStatus.BAD_GATEWAY,
            "The station holding this file answered with something that could not be read"),

    /** A registration the station holding the appointment did not take. */
    FEDERATED_REGISTRATION_NOT_TAKEN(
            Area.EVENTS,
            56,
            HttpStatus.BAD_REQUEST,
            "The station holding this appointment did not take the registration, so nothing was saved"),

    /** A withdrawal the holding station will no longer put back. */
    FEDERATED_WITHDRAWAL_NO_LONGER_UNDONE(Area.EVENTS, 57, HttpStatus.BAD_REQUEST, Sentences.NO_LONGER_TAKEN_BACK),

    /** A confirmation that would go past the places the holding station handed over. */
    NO_PLACES_LEFT_AT_HOLDER(Area.EVENTS, 58, HttpStatus.BAD_REQUEST, Sentences.NO_PLACES_LEFT),

    /** A member of ours confirmed where the holding station never handed that choice over. */
    EVENT_DECIDED_BY_ITS_HOLDER(
            Area.EVENTS,
            59,
            HttpStatus.FORBIDDEN,
            "That station decides the registrations for this appointment itself"),

    /** A registration of ours at a partner's appointment that is gone. */
    FEDERATED_REGISTRATION_NOT_HERE(Area.EVENTS, 60, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A station addressed as a partner that this station has no partnership with. */
    PARTNER_NOT_HERE(Area.EVENTS, 61, HttpStatus.NOT_FOUND, "That station is not a partner of this one"),

    /** A comment on a partner's appointment with nothing written in it. */
    FEDERATED_COMMENT_NEEDS_TEXT(Area.EVENTS, 62, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A change to a comment on a partner's appointment with nothing written in it. */
    FEDERATED_COMMENT_CHANGE_NEEDS_TEXT(Area.EVENTS, 63, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A listing asked for between two days, one of which is not a date. */
    EVENT_LIST_BOUNDS_NOT_DATES(
            Area.EVENTS, 64, HttpStatus.BAD_REQUEST, "The first and last day of the list must both be dates"),

    /** An appointment being written without a name. */
    EVENT_NEEDS_A_NAME(Area.EVENTS, 65, HttpStatus.BAD_REQUEST, "An appointment needs a name"),

    /** An appointment being written without the times it runs between. */
    EVENT_NEEDS_A_TIME(
            Area.EVENTS, 66, HttpStatus.BAD_REQUEST, "An appointment needs a time it starts and a time it ends"),

    /** An appointment being written without saying whether it happens once or comes round again. */
    EVENT_NEEDS_A_KIND(
            Area.EVENTS,
            67,
            HttpStatus.BAD_REQUEST,
            "An appointment needs to say whether it happens once or comes round again"),

    /** Several appointments being written at once, with none of them named. */
    BATCH_NEEDS_ROWS(
            Area.EVENTS, 68, HttpStatus.BAD_REQUEST, "Making several appointments at once needs at least one of them"),

    /** A list of appointments that broke while being drawn, which is Ember's to look into. */
    EVENT_LIST_NOT_DRAWN(
            Area.EVENTS,
            69,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The list of appointments could not be turned into a sheet. Trying again may work"),

    /** An appointment only some of the station may see, handed to partner stations. */
    RESTRICTED_EVENT_NOT_SHARED(
            Area.EVENTS,
            70,
            HttpStatus.BAD_REQUEST,
            "An appointment meant for only some of the station cannot be shared with partners"),

    /** A partner's registration being decided on that is gone. */
    PARTNER_REGISTRATION_NOT_HERE_ON_DECISION(
            Area.EVENTS, 71, HttpStatus.NOT_FOUND, Sentences.EVENT_REGISTRATION_NOT_HERE),

    /** A partner's registration decided here although the partner was handed that decision. */
    PARTNER_DECIDES_ITS_OWN(
            Area.EVENTS, 72, HttpStatus.FORBIDDEN, "This partner decides its own registrations for this appointment"),

    /** One more of a partner's members accepted than the places that partner was given. */
    NO_PLACES_LEFT_FOR_THIS_PARTNER(
            Area.EVENTS, 73, HttpStatus.BAD_REQUEST, "There are no places left for this partner"),

    /** A number of places written as less than none. */
    PLACES_CANNOT_BE_NEGATIVE(Area.EVENTS, 74, HttpStatus.BAD_REQUEST, "A number of places cannot be less than zero"),

    /**
     * A file of an appointment that is gone, hangs on another appointment, or is one this reader
     * may not be handed. One code deliberately: telling them apart would say that a file held back
     * is there.
     */
    EVENT_FILE_NOT_HERE(Area.EVENTS, 75, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file of an appointment whose stored bytes are gone. */
    EVENT_FILE_CONTENT_NOT_HERE(Area.EVENTS, 76, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** The same as the download refuses, asked for by the tile that shows a file. */
    EVENT_FILE_NOT_HERE_FOR_PICTURE(Area.EVENTS, 77, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file of an appointment that has no picture to show. */
    EVENT_FILE_PICTURE_NOT_HERE(Area.EVENTS, 78, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A file hung on an appointment without saying which file of the library it is. */
    EVENT_FILE_NOT_CHOSEN(Area.EVENTS, 79, HttpStatus.BAD_REQUEST, "Say which file of the library is to be attached"),

    /** A file of an appointment that went before the change to it could be written. */
    EVENT_FILE_NOT_CHANGED(Area.EVENTS, 80, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file that was already off the appointment when its removal was asked for. */
    EVENT_FILE_NOT_REMOVED(Area.EVENTS, 81, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** An appointment this reader is not among the people it was meant for. */
    EVENT_NOT_YOURS_TO_SEE(Area.EVENTS, 82, HttpStatus.FORBIDDEN, "This appointment is not yours to see"),

    /** A file being written or taken off that is gone, or hangs on another appointment. */
    EVENT_FILE_NOT_HERE_ON_WRITE(Area.EVENTS, 83, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A station that is not here, asked for by a public calendar address. */
    STATION_NOT_HERE_BEHIND_PUBLIC_CALENDAR(Area.EVENTS, 84, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A public calendar at a station that has switched it off. */
    PUBLIC_CALENDAR_SWITCHED_OFF(
            Area.EVENTS, 85, HttpStatus.NOT_FOUND, "This station does not publish its appointments"),

    /**
     * An appointment that is gone, belongs to another station, or is not one the station publishes.
     * One code deliberately: telling them apart would say that an appointment nobody outside may
     * see is there.
     */
    PUBLIC_EVENT_NOT_HERE(Area.EVENTS, 86, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A template being made without a name. */
    EVENT_TEMPLATE_NEEDS_A_NAME(Area.EVENTS, 87, HttpStatus.BAD_REQUEST, "An appointment template needs a name"),

    /** A template that went before the change to it could be written. */
    EVENT_TEMPLATE_NOT_CHANGED(Area.EVENTS, 88, HttpStatus.NOT_FOUND, Sentences.EVENT_TEMPLATE_NOT_HERE),

    /** A template that was already gone when its deletion was asked for. */
    EVENT_TEMPLATE_NOT_DELETED(Area.EVENTS, 89, HttpStatus.NOT_FOUND, Sentences.EVENT_TEMPLATE_NOT_HERE),

    /** A template that could not be read back after being changed, with the change already in. */
    EVENT_TEMPLATE_NOT_HERE_AFTER_CHANGE(
            Area.EVENTS, 90, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /**
     * The station the caller is signed in at, taken from their session and not there, while a
     * registration across stations is being worked out.
     */
    SESSION_STATION_NOT_HERE(
            Area.EVENTS,
            91,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Your sign-in could not be matched to a station, so nothing was done. Sign in again"),

    /** A list of comments asked for on a day that is not a date. */
    COMMENT_DAY_NOT_A_DATE(Area.COMMENTS, 1, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A comment left with nothing written in it. */
    COMMENT_NEEDS_TEXT(Area.COMMENTS, 2, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment being changed that is gone. */
    COMMENT_NOT_HERE_ON_CHANGE(Area.COMMENTS, 3, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** Somebody else's comment, being changed. */
    COMMENT_NOT_YOURS_TO_CHANGE(
            Area.COMMENTS, 4, HttpStatus.FORBIDDEN, "You can only change comments you wrote yourself"),

    /** A comment changed to nothing at all. */
    COMMENT_CHANGE_NEEDS_TEXT(Area.COMMENTS, 5, HttpStatus.BAD_REQUEST, Sentences.COMMENT_NEEDS_TEXT),

    /** A comment that went between being changed and being read back. */
    COMMENT_NOT_HERE_AFTER_CHANGE(Area.COMMENTS, 6, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /**
     * A comment being deleted that is gone, or whose station could not be read behind it. The two
     * are one code deliberately: telling them apart would say that the comment sits at a station
     * the caller may not see.
     */
    COMMENT_NOT_HERE_ON_DELETE(Area.COMMENTS, 7, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** Somebody else's comment, being deleted by somebody who does not moderate. */
    COMMENT_NOT_YOURS_TO_DELETE(
            Area.COMMENTS, 8, HttpStatus.FORBIDDEN, "You can only delete comments you wrote yourself"),

    /** A comment that went between being read and being deleted. */
    COMMENT_NOT_DELETED(Area.COMMENTS, 9, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** Notes of a kind this reader may neither read nor write. */
    NOTES_NOT_YOURS(Area.COMMENTS, 10, HttpStatus.FORBIDDEN, "You may not read or write notes of this kind"),

    /** A note saved with nothing written in it. */
    NOTE_NEEDS_TEXT(Area.COMMENTS, 11, HttpStatus.BAD_REQUEST, "A note needs something written in it"),

    /** A history asked for where nothing has been noted yet. */
    NOTE_NOT_HERE(Area.COMMENTS, 12, HttpStatus.NOT_FOUND, "There is nothing noted here yet"),

    /**
     * A form asked for by a route that then checks whose it is. The ownership check behind it
     * answers {@link #NOT_YOURS_TO_OPEN}, which is the shared answer for a thing that is somebody
     * else's.
     */
    FORM_NOT_HERE(Area.FORMS, 1, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went between an act on it succeeding and the answer being drawn. */
    FORM_NOT_HERE_ON_REREAD(Area.FORMS, 2, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went between the screen being opened and the change being saved. */
    FORM_NOT_HERE_ON_CHANGE(Area.FORMS, 3, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that was already gone when its deletion was asked for. */
    FORM_NOT_HERE_ON_DELETE(Area.FORMS, 4, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went before how far it reaches could be written. */
    FORM_NOT_HERE_ON_VISIBILITY_CHANGE(Area.FORMS, 5, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form that went before it could be closed. */
    FORM_NOT_HERE_ON_CLOSE(Area.FORMS, 6, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** The station an export of answers is headed with, gone between the two reads. */
    STATION_NOT_HERE_FOR_FORM_EXPORT(Area.FORMS, 7, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** The station a shared form belongs to, asked for to draw its name and colours. */
    STATION_NOT_HERE_BEHIND_FORM_LINK(Area.FORMS, 8, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A link that no form is reached by, which covers a link that has since been replaced. */
    FORM_LINK_UNKNOWN(Area.FORMS, 9, HttpStatus.NOT_FOUND, "No form is reached by this link"),

    /** A form that is closed, not yet open, or never published. */
    FORM_NOT_TAKING_ANSWERS(Area.FORMS, 10, HttpStatus.GONE, "This form is not taking answers"),

    /** Answering the same public form far more often than a person could. */
    FORM_ANSWERED_TOO_OFTEN(
            Area.FORMS,
            11,
            HttpStatus.TOO_MANY_REQUESTS,
            "This form has been answered too often from here. Try again shortly"),

    /** A form somebody has already answered once, where once is all it takes. */
    FORM_ALREADY_ANSWERED(Area.FORMS, 12, HttpStatus.CONFLICT, "You have already answered this form"),

    /** Answers that were read and then refused for what they said. */
    FORM_ANSWER_REFUSED(Area.FORMS, 13, HttpStatus.BAD_REQUEST, "The answers could not be saved"),

    /** Answers that arrived in a shape nothing could be made of. */
    FORM_ANSWER_UNREADABLE(
            Area.FORMS,
            14,
            HttpStatus.BAD_REQUEST,
            "The answers could not be read, so nothing was saved. Fill the form in again and send it once more"),

    /** The station a public form address names, which is not here. */
    STATION_NOT_HERE_BEHIND_PUBLIC_FORM(Area.FORMS, 15, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /**
     * A public form that is gone, or belongs to a station other than the one in the address. The
     * two are one code deliberately: telling them apart would say that the form exists elsewhere.
     */
    PUBLIC_FORM_NOT_HERE(Area.FORMS, 16, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** A form of a kind strangers may not answer. */
    FORM_NOT_ANSWERED_FROM_OUTSIDE(Area.FORMS, 17, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_ANSWERED_FROM_OUTSIDE),

    /** A form that reaches less far than an open address needs it to. */
    FORM_NOT_OPENLY_ADDRESSED(Area.FORMS, 18, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_ANSWERED_FROM_OUTSIDE),

    /** A cell on a page pointing at a form that is gone or is of the wrong kind. */
    PAGE_FORM_NOT_HERE(
            Area.FORMS, 19, HttpStatus.NOT_FOUND, "The form this part of the page shows is not here any more"),

    /**
     * An answer that was to be acknowledged and is gone, or belongs to another form. The two are
     * one code deliberately: telling them apart would say that the answer exists elsewhere.
     */
    FORM_ANSWER_NOT_HERE(Area.FORMS, 20, HttpStatus.NOT_FOUND, "That answer is not here any more"),

    /** A kind of form asked for by a name that names none. */
    FORM_KIND_UNKNOWN(Area.FORMS, 21, HttpStatus.BAD_REQUEST, Sentences.FORM_KIND_UNKNOWN),

    /** A search for forms that did not say which kind it wants. */
    FORM_KIND_NOT_NAMED(Area.FORMS, 22, HttpStatus.BAD_REQUEST, "Say which kind of form to look for"),

    /** The same kind that names none, at the picker rather than at the list. */
    FORM_KIND_UNKNOWN_ON_SEARCH(Area.FORMS, 23, HttpStatus.BAD_REQUEST, Sentences.FORM_KIND_UNKNOWN),

    /** A form offered without a title. */
    FORM_NEEDS_A_TITLE(Area.FORMS, 24, HttpStatus.BAD_REQUEST, "A form needs a title, so nothing was saved"),

    /** Making a form while belonging to no station. */
    NOT_A_MEMBER_ON_FORM_CREATION(Area.FORMS, 25, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** Publishing a form that is past being a draft. */
    FORM_NOT_A_DRAFT(Area.FORMS, 26, HttpStatus.BAD_REQUEST, "Only a form that is still a draft can be published"),

    /** How far a form reaches, left unsaid where it had to be said. */
    FORM_REACH_NOT_SAID(Area.FORMS, 27, HttpStatus.BAD_REQUEST, "Say how far the form is to reach"),

    /** The link of a form the station's own members answer, which is not sent by one. */
    INTERNAL_FORM_HAS_NO_LINK(
            Area.FORMS, 28, HttpStatus.BAD_REQUEST, "A form for the station's own members is not sent by link"),

    /** Replacing a link that has been replaced by somebody else in the meantime. */
    FORM_LINK_ALREADY_REPLACED(
            Area.FORMS, 29, HttpStatus.CONFLICT, "This form has been given a different link since you last looked"),

    /** Questions of a kind the form's own kind does not ask. */
    QUESTIONS_NOT_FOR_THIS_KIND_OF_FORM(
            Area.FORMS,
            30,
            HttpStatus.BAD_REQUEST,
            "Some of these questions cannot be asked on a form of this kind, so nothing was saved"),

    /** Reading one's own answer while belonging to no station. */
    NOT_A_MEMBER_READING_OWN_ANSWER(Area.FORMS, 31, HttpStatus.BAD_REQUEST, "You are not a member of this station"),

    /** Answering a form while belonging to no station. */
    NOT_A_MEMBER_ANSWERING_FORM(Area.FORMS, 32, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** A form that is closed, not yet open or never published, answered by a member. */
    FORM_TAKES_NO_ANSWERS(Area.FORMS, 33, HttpStatus.BAD_REQUEST, Sentences.FORM_TAKES_NO_ANSWERS),

    /** A form the member answering is not among the people it was put to. */
    FORM_NOT_YOURS_TO_ANSWER(Area.FORMS, 34, HttpStatus.FORBIDDEN, Sentences.FORM_NOT_YOURS_TO_ANSWER),

    /** Answers that were read and then refused for what they said. */
    FORM_ANSWERS_NOT_SAVED(Area.FORMS, 35, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWERS_NOT_SAVED),

    /** Changing one's own answer while belonging to no station. */
    NOT_A_MEMBER_CHANGING_FORM_ANSWER(Area.FORMS, 36, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** A form that takes an answer once and does not take it again. */
    FORM_ANSWER_NOT_CHANGEABLE(Area.FORMS, 37, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWER_NOT_CHANGEABLE),

    /** The same form put to somebody else, at the moment an answer to it is changed. */
    FORM_NOT_YOURS_TO_CHANGE_ANSWER(Area.FORMS, 38, HttpStatus.FORBIDDEN, Sentences.FORM_NOT_YOURS_TO_ANSWER),

    /** A changed answer that was read and then refused for what it said. */
    FORM_ANSWER_CHANGE_NOT_SAVED(Area.FORMS, 39, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWERS_NOT_SAVED),

    /** Answering for somebody who is looked after, while belonging to no station. */
    NOT_A_MEMBER_ANSWERING_FOR_MEMBER(Area.FORMS, 40, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** A form that takes no answers, at the moment one is given for somebody looked after. */
    FORM_TAKES_NO_ANSWERS_FOR_MEMBER(Area.FORMS, 41, HttpStatus.BAD_REQUEST, Sentences.FORM_TAKES_NO_ANSWERS),

    /** A form whose answer stands, at the moment it is changed for somebody looked after. */
    FORM_ANSWER_NOT_CHANGEABLE_FOR_MEMBER(Area.FORMS, 42, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWER_NOT_CHANGEABLE),

    /** A form that was not put to the member it is being answered for. */
    FORM_NOT_FOR_THIS_MEMBER(
            Area.FORMS, 43, HttpStatus.FORBIDDEN, "This form was not put to the member you are answering for"),

    /** Answers given for somebody looked after, read and then refused for what they said. */
    FORM_ANSWERS_FOR_MEMBER_NOT_SAVED(Area.FORMS, 44, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWERS_NOT_SAVED),

    /** Answering for a member nobody has put in the caller's care. */
    MEMBER_NOT_YOURS_TO_ANSWER_FOR(Area.FORMS, 45, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** Grouping results by who answered, on a form that is answered without signing in. */
    ONLY_INTERNAL_FORM_GROUPED_BY_WHO_ANSWERED(
            Area.FORMS,
            46,
            HttpStatus.BAD_REQUEST,
            "Only the results of an internal form can be grouped by who answered"),

    /** An export of answers that fell over while it was being written, which is ours to look into. */
    FORM_ANSWERS_NOT_EXPORTED(
            Area.FORMS, 47, HttpStatus.INTERNAL_SERVER_ERROR, "The answers could not be made into a file"),

    /** A form that could not be read back after how far it reaches was changed, with the change already in. */
    FORM_NOT_HERE_AFTER_VISIBILITY_CHANGE(
            Area.FORMS, 48, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** An invite that is used up or past its date. */
    WAITING_LIST_INVITE_NO_LONGER_VALID(
            Area.WAITING_LISTS, 22, HttpStatus.FORBIDDEN, "This invite can no longer be used"),

    /** A sign-up by invite that named neither the invite nor a first name. */
    WAITING_LIST_REGISTRATION_INCOMPLETE(
            Area.WAITING_LISTS,
            23,
            HttpStatus.BAD_REQUEST,
            "An invite and a first name are needed, so nothing was saved"),

    /** A sign-up by invite refused for what it said. */
    WAITING_LIST_REGISTRATION_REFUSED(
            Area.WAITING_LISTS,
            24,
            HttpStatus.BAD_REQUEST,
            "You could not be put on the waiting list, so nothing was saved"),

    /** A sign-up by invite the list is not in a state to take. */
    WAITING_LIST_CLOSED_TO_THIS_REGISTRATION(
            Area.WAITING_LISTS,
            25,
            HttpStatus.FORBIDDEN,
            "This waiting list cannot be signed up for as it now stands, so nothing was saved"),

    /** An answer to an invitation that said nothing. */
    WAITING_LIST_ANSWER_MISSING(Area.WAITING_LISTS, 26, HttpStatus.BAD_REQUEST, "Say what your answer is"),

    /** An answer to an invitation that is none of the ones it offers. */
    WAITING_LIST_ANSWER_UNKNOWN(
            Area.WAITING_LISTS, 27, HttpStatus.BAD_REQUEST, "That is not an answer this invitation takes"),

    /** An entry that went before it could be invited. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_INVITE(
            Area.WAITING_LISTS, 28, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that is not waiting, so there is nothing to invite. */
    WAITING_LIST_ENTRY_NOT_INVITED(
            Area.WAITING_LISTS,
            29,
            HttpStatus.BAD_REQUEST,
            "This entry cannot be invited as it now stands, so nothing was changed"),

    /** An entry that went before it could be put back on the list. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_RETURN(
            Area.WAITING_LISTS, 30, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that is not in a state to go back to waiting. */
    WAITING_LIST_ENTRY_NOT_RETURNED(
            Area.WAITING_LISTS,
            31,
            HttpStatus.BAD_REQUEST,
            "This entry cannot be put back on the waiting list as it now stands, so nothing was changed"),

    /** The appointment an invitation names, gone or belonging to another station. */
    APPOINTMENT_NOT_HERE_FOR_INVITATION(Area.WAITING_LISTS, 32, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** An appointment the person inviting cannot see themselves. */
    APPOINTMENT_NOT_YOURS_TO_INVITE_TO(
            Area.WAITING_LISTS, 33, HttpStatus.FORBIDDEN, "You cannot see that appointment, so nothing was saved"),

    /** An invitation to an appointment that did not say which occurrence of it. */
    INVITATION_NEEDS_A_DATE(
            Area.WAITING_LISTS, 34, HttpStatus.BAD_REQUEST, "An invitation needs the date of the occurrence"),

    /** A date on an invitation that is not one. */
    INVITATION_DATE_NOT_A_DATE(Area.WAITING_LISTS, 35, HttpStatus.BAD_REQUEST, "That is not a date"),

    /** A time on an invitation that is not one. */
    INVITATION_TIME_NOT_A_TIME(Area.WAITING_LISTS, 36, HttpStatus.BAD_REQUEST, "That is not a time"),

    /** An entry that went before its trial period could begin. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_TESTING(
            Area.WAITING_LISTS, 37, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that has not been invited, so its trial period cannot begin. */
    WAITING_LIST_ENTRY_NOT_MOVED_TO_TESTING(
            Area.WAITING_LISTS,
            38,
            HttpStatus.BAD_REQUEST,
            "This entry cannot begin its trial period as it now stands, so nothing was changed"),

    /** An entry that went before it could be marked as joined. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_JOIN(
            Area.WAITING_LISTS, 39, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that is not in its trial period, so it cannot be marked as joined. */
    WAITING_LIST_ENTRY_NOT_JOINED(
            Area.WAITING_LISTS,
            40,
            HttpStatus.BAD_REQUEST,
            "This entry cannot be marked as joined as it now stands, so nothing was changed"),

    /** An entry that went before it could be withdrawn. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_WITHDRAWAL(
            Area.WAITING_LISTS, 41, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that has joined, or is withdrawn already. */
    WAITING_LIST_ENTRY_NOT_WITHDRAWN(
            Area.WAITING_LISTS,
            42,
            HttpStatus.BAD_REQUEST,
            "This entry cannot be withdrawn as it now stands, so nothing was changed"),

    /** A formula for working out points that could not be made sense of. */
    SCORING_FORMULA_NOT_READ(
            Area.WAITING_LISTS,
            43,
            HttpStatus.BAD_REQUEST,
            "The formula for working out points could not be read, so nothing was saved"),

    /** A public sign-up without a first name. */
    PUBLIC_REGISTRATION_NEEDS_A_FIRST_NAME(
            Area.WAITING_LISTS, 44, HttpStatus.BAD_REQUEST, "A first name is needed, so nothing was saved"),

    /** A public sign-up to a list that writes to people, with no address to write to. */
    PUBLIC_REGISTRATION_NEEDS_AN_ADDRESS(
            Area.WAITING_LISTS, 45, HttpStatus.BAD_REQUEST, "An email address is needed, so nothing was saved"),

    /** A confirmation link that names no sign-up, which covers one that has run out. */
    WAITING_LIST_CONFIRMATION_LINK_UNKNOWN(
            Area.WAITING_LISTS, 46, HttpStatus.BAD_REQUEST, "This confirmation link is no longer good"),

    /** Nobody expected this one, and the log is where it is explained. */
    UNEXPECTED_FAULT(Area.GENERAL, 1, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UNEXPECTED_FAULT),

    /** The store refused in a way nothing here has a name for, which is ours to look into. */
    UNEXPECTED_FAULT_FROM_UNKNOWN_STATE(Area.GENERAL, 2, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UNEXPECTED_FAULT),

    /** A second row with details that may only exist once. */
    ALREADY_EXISTS(
            Area.GENERAL,
            3,
            HttpStatus.CONFLICT,
            "Something with the same details is already there, so nothing was saved"),

    /** A row naming something gone, or something still depended on. */
    STILL_LINKED(
            Area.GENERAL,
            4,
            HttpStatus.CONFLICT,
            "This names something that is no longer there, or something else still depends on it, "
                    + "so nothing was saved"),

    /** A value larger, longer or otherwise different from what can be kept. */
    DOES_NOT_FIT(
            Area.GENERAL,
            5,
            HttpStatus.BAD_REQUEST,
            "Something in what was sent does not fit what can be stored here, so nothing was saved"),

    /** Two changes to the same thing at the same moment, one of which had to be undone. */
    CHANGE_COLLIDED(
            Area.GENERAL,
            6,
            HttpStatus.CONFLICT,
            "Somebody changed the same thing at the same moment, so this change was undone. "
                    + "Trying again usually works"),

    /** Work that ran long enough to be stopped before it finished. */
    TOOK_TOO_LONG(
            Area.GENERAL,
            7,
            HttpStatus.SERVICE_UNAVAILABLE,
            "This took too long and was stopped before anything was saved. Trying again may work"),

    /** The store Ember keeps its data in could not be reached. */
    STORE_UNREACHABLE(
            Area.GENERAL,
            8,
            HttpStatus.SERVICE_UNAVAILABLE,
            "Ember could not reach the place it keeps its data, so nothing was saved. "
                    + "Trying again in a moment usually works"),

    /** An address that cannot name anything, because the identifier in it is not one. */
    ADDRESS_NOT_AN_IDENTIFIER(Area.GENERAL, 9, HttpStatus.NOT_FOUND, "That address does not name anything"),

    /** Something already loaded that turns out to belong to another station. */
    NOT_YOURS_TO_OPEN(Area.GENERAL, 10, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /**
     * Gone, or another station's, from the loader that does both at once. The two are one code
     * deliberately: telling them apart would say that the thing exists at another station, which
     * is what the shared answer is there to withhold.
     */
    NOT_HERE_OR_NOT_YOURS(Area.GENERAL, 11, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** An order for a cluster's questions, sent without saying which audience the order is for. */
    CLUSTER_FIELD_ORDER_NEEDS_AN_AUDIENCE(
            Area.CLUSTERS,
            1,
            HttpStatus.BAD_REQUEST,
            "An order of questions belongs to one audience, so nothing was saved"),

    /** A cluster's questions, asked for by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN_FOR_FIELDS(Area.CLUSTERS, 2, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its questions, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_FIELDS(Area.CLUSTERS, 3, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster question given a kind of answer that is not one of the kinds on offer. */
    CLUSTER_FIELD_TYPE_UNKNOWN(
            Area.CLUSTERS, 4, HttpStatus.BAD_REQUEST, "That is not a kind of question, so nothing was saved"),

    /** A cluster question aimed at an audience that is not one of the audiences on offer. */
    CLUSTER_FIELD_AUDIENCE_UNKNOWN(
            Area.CLUSTERS,
            5,
            HttpStatus.BAD_REQUEST,
            "That is not an audience a question can be put to, so nothing was saved"),

    /** A way of standing the cluster wiki on the public web that is not one of the ways on offer. */
    CLUSTER_PUBLIC_WIKI_MODE_UNKNOWN(
            Area.CLUSTERS,
            6,
            HttpStatus.BAD_REQUEST,
            "That is not a way of putting the wiki on the public web, so nothing was saved"),

    /** A group of stations named by something that is not a number. */
    CLUSTER_STATION_GROUP_NOT_A_NUMBER(Area.CLUSTERS, 7, HttpStatus.BAD_REQUEST, "That is not a group of stations"),

    /** A part of Ember the cluster wants to withhold that is not one this instance knows. */
    CLUSTER_MODULE_UNKNOWN(
            Area.CLUSTERS,
            8,
            HttpStatus.BAD_REQUEST,
            "That is not a part of Ember that can be withheld, so nothing was saved"),

    /** The cluster's own settings, asked for by somebody who has not said which cluster. */
    NO_CLUSTER_CHOSEN_FOR_GOVERNANCE(Area.CLUSTERS, 9, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its settings, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_GOVERNANCE(Area.CLUSTERS, 10, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A look the cluster wants to hand its stations that is not one of the looks on offer. */
    CLUSTER_THEME_FEEL_UNKNOWN(
            Area.CLUSTERS,
            11,
            HttpStatus.BAD_REQUEST,
            "That is not a look a station can be given, so nothing was saved"),

    /** A new order for the steps of a chain that names no steps at all. */
    CLUSTER_CHAIN_ORDER_NEEDS_STEPS(
            Area.CLUSTERS,
            12,
            HttpStatus.BAD_REQUEST,
            "Name the steps in the order they are to be walked. Nothing was saved"),

    /** A step of a chain saved without saying who walks it, what it is about and who holds the gear after. */
    CLUSTER_STEP_DETAILS_MISSING(
            Area.CLUSTERS,
            13,
            HttpStatus.BAD_REQUEST,
            "A step needs who does it, what it is done to, and who holds the gear afterwards, "
                    + "so nothing was saved"),

    /** Gear sent out of the cluster's own store without saying which station it is going to. */
    CLUSTER_DISPATCH_NEEDS_A_STATION(
            Area.CLUSTERS, 14, HttpStatus.BAD_REQUEST, "Name the station the gear is going to. Nothing was sent"),

    /** What a loss report has to carry, saved without saying what that is. */
    CLUSTER_LOSS_REPORT_NEEDS_A_REQUIREMENT(
            Area.CLUSTERS, 15, HttpStatus.BAD_REQUEST, "Say what a loss report has to carry. Nothing was saved"),

    /** The cluster's gear, asked for by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN_FOR_CLUSTER_INVENTORY(Area.CLUSTERS, 16, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its gear, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_CLUSTER_INVENTORY(Area.CLUSTERS, 17, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A chain of steps saved without saying what it is walked for. */
    CLUSTER_CHAIN_NEEDS_A_PURPOSE(
            Area.CLUSTERS, 18, HttpStatus.BAD_REQUEST, "A chain needs a purpose, so nothing was saved"),

    /** A chain given a purpose that is not one of the purposes on offer. */
    CLUSTER_CHAIN_PURPOSE_UNKNOWN(
            Area.CLUSTERS, 19, HttpStatus.BAD_REQUEST, "That is not a purpose a chain can have, so nothing was saved"),

    /** The words a cluster recommends for its stations' gear, asked for without saying which cluster. */
    NO_CLUSTER_CHOSEN_FOR_INVENTORY_TAGS(Area.CLUSTERS, 20, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its recommended words, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_INVENTORY_TAGS(Area.CLUSTERS, 21, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A document filed about one of the cluster's people that arrived without the file itself. */
    CLUSTER_MEMBER_DOCUMENT_MISSING_FILE(
            Area.CLUSTERS, 22, HttpStatus.BAD_REQUEST, "That upload arrived without a file, so nothing was filed"),

    /** A document about one of the cluster's people that is larger than a document may be. */
    CLUSTER_MEMBER_DOCUMENT_TOO_LARGE(
            Area.CLUSTERS,
            23,
            HttpStatus.BAD_REQUEST,
            "That file is larger than a document may be, so nothing was filed"),

    /** A document about one of the cluster's people whose bytes could not be taken in. */
    CLUSTER_MEMBER_DOCUMENT_UNREADABLE(
            Area.CLUSTERS, 24, HttpStatus.BAD_REQUEST, "That file could not be read, so nothing was filed"),

    /** An answer filed against a question said to come from somewhere questions do not come from. */
    CLUSTER_FIELD_ORIGIN_UNKNOWN(
            Area.CLUSTERS,
            25,
            HttpStatus.BAD_REQUEST,
            "That is not somewhere a question can come from, so nothing was saved"),

    /** Somebody taken on at one of the cluster's stations without a first and a last name. */
    CLUSTER_NEW_MEMBER_NEEDS_A_NAME(
            Area.CLUSTERS,
            26,
            HttpStatus.BAD_REQUEST,
            "A new member needs a first name and a last name, so nobody was taken on"),

    /** Somebody taken on at one of the cluster's stations whose address is already in use here. */
    CLUSTER_MEMBER_ALREADY_TAKEN_ON(
            Area.CLUSTERS,
            27,
            HttpStatus.CONFLICT,
            "Somebody is already here with that address, so nobody was taken on"),

    /** A station named in the address of the route that takes somebody on, written as no station can be. */
    CLUSTER_NEW_MEMBER_STATION_NOT_AN_IDENTITY(
            Area.CLUSTERS, 28, HttpStatus.BAD_REQUEST, "That does not name a station, so nobody was taken on"),

    /** Somebody at a station made into something nobody can be at a station. */
    STATION_USER_TYPE_UNKNOWN_FROM_CLUSTER(
            Area.CLUSTERS,
            29,
            HttpStatus.BAD_REQUEST,
            "That is not something somebody can be at a station, so nothing was changed"),

    /** Somebody at a station allowed something that cannot be allowed at a station. */
    STATION_PERMISSION_UNKNOWN_FROM_CLUSTER(
            Area.CLUSTERS,
            30,
            HttpStatus.BAD_REQUEST,
            "That is not something that can be allowed at a station, so nothing was changed"),

    /** The cluster's people, asked for by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN_FOR_MEMBER_MANAGEMENT(Area.CLUSTERS, 31, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its people, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_MEMBER_MANAGEMENT(Area.CLUSTERS, 32, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A station used to narrow a search of the cluster's people, written as no station can be. */
    CLUSTER_MEMBER_FILTER_STATION_NOT_AN_IDENTITY(
            Area.CLUSTERS, 33, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_AN_IDENTITY),

    /**
     * A station asked for from a cluster that does not stand over it. A station nobody has ever
     * created and a station belonging to another cluster are one code deliberately: telling them
     * apart would let a cluster administrator map the stations standing outside their own cluster.
     */
    STATION_NOT_IN_THIS_CLUSTER(Area.CLUSTERS, 34, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The people who act for a cluster, asked for by somebody who has not said which cluster. */
    NO_CLUSTER_CHOSEN_FOR_CLUSTER_MEMBERS(Area.CLUSTERS, 35, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind the people who act for it, gone between the session and the request. */
    CLUSTER_NOT_HERE_FOR_CLUSTER_MEMBERS(Area.CLUSTERS, 36, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** Somebody in a cluster made into something nobody can be in a cluster. */
    CLUSTER_USER_TYPE_UNKNOWN(
            Area.CLUSTERS,
            37,
            HttpStatus.BAD_REQUEST,
            "That is not something somebody can be in a cluster, so nothing was saved"),

    /** Somebody in a cluster allowed something that cannot be allowed in a cluster. */
    CLUSTER_PERMISSION_UNKNOWN(
            Area.CLUSTERS,
            38,
            HttpStatus.BAD_REQUEST,
            "That is not something that can be allowed in a cluster, so nothing was saved"),

    /** The cluster's notices, asked for by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN_FOR_NOTIFICATIONS(Area.CLUSTERS, 39, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster just renamed, gone before it could be read back. */
    CLUSTER_NOT_HERE_AFTER_RENAME(Area.CLUSTERS, 40, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster asked to be deleted that is not here any more. */
    CLUSTER_NOT_HERE_ON_DELETE(Area.CLUSTERS, 41, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster being handed its first person to act for it that is not here any more. */
    CLUSTER_NOT_HERE_ON_APPOINTMENT(Area.CLUSTERS, 42, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster handed its first person without saying who that person is. */
    CLUSTER_APPOINTMENT_NEEDS_AN_ACCOUNT(
            Area.CLUSTERS,
            43,
            HttpStatus.BAD_REQUEST,
            "Name the account that is to act for this cluster. Nobody was appointed"),

    /** The account being appointed to act for a cluster, gone before it could be appointed. */
    ACCOUNT_NOT_HERE_ON_CLUSTER_APPOINTMENT(Area.CLUSTERS, 44, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** A cluster acted on by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN(Area.CLUSTERS, 45, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster of the session, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE(Area.CLUSTERS, 46, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster named in an address, written as no cluster can be. */
    CLUSTER_NOT_AN_IDENTITY(Area.CLUSTERS, 47, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NOT_AN_IDENTITY),

    /** The way a cluster files its stations, asked for without saying which cluster. */
    NO_CLUSTER_CHOSEN_FOR_STATION_GROUPS(Area.CLUSTERS, 48, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its groups of stations, gone between the session and the request. */
    CLUSTER_NOT_HERE_FOR_STATION_GROUPS(Area.CLUSTERS, 49, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A station a cluster is letting go that is not here any more. */
    STATION_NOT_HERE_ON_CLUSTER_RELEASE(Area.CLUSTERS, 50, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The cluster's stations, asked for by somebody who has not said which cluster they act for. */
    NO_CLUSTER_CHOSEN_FOR_CLUSTER_STATIONS(Area.CLUSTERS, 51, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind its stations, gone between the session being opened and the request. */
    CLUSTER_NOT_HERE_FOR_CLUSTER_STATIONS(Area.CLUSTERS, 52, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A station a cluster is letting go, named in a way no station can be named. */
    STATION_NOT_AN_IDENTITY_ON_CLUSTER_RELEASE(
            Area.CLUSTERS, 53, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_AN_IDENTITY),

    /** The reach of a cluster's storage, saved without saying how far it reaches. */
    CLUSTER_STORAGE_POLICY_NEEDS_A_REACH(
            Area.CLUSTERS,
            54,
            HttpStatus.BAD_REQUEST,
            "Say how far the cluster's storage reaches, so nothing was saved"),

    /** Storage the cluster has not set up, asked whether it answers. */
    CLUSTER_KEEPS_NO_STORAGE(
            Area.CLUSTERS,
            55,
            HttpStatus.BAD_REQUEST,
            "This cluster keeps no storage of its own, so there was nothing to try"),

    /** A station whose files are being carried to the cluster's storage that is not here any more. */
    STATION_NOT_HERE_ON_CLUSTER_STORAGE_MOVE(Area.CLUSTERS, 56, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Files that could not be carried to the storage the cluster chose, so they stayed where they were. */
    CLUSTER_STORAGE_MOVE_FAILED(
            Area.CLUSTERS,
            57,
            HttpStatus.BAD_REQUEST,
            "That station's files could not be carried over, so they were left where they are"),

    /** A station whose files are being carried over, named in a way no station can be named. */
    STATION_NOT_AN_IDENTITY_ON_CLUSTER_STORAGE_MOVE(
            Area.CLUSTERS, 58, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_AN_IDENTITY),

    /** The storage a cluster stands on, asked for by somebody who has not said which cluster. */
    NO_CLUSTER_CHOSEN_FOR_STORAGE_BACKEND(Area.CLUSTERS, 59, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind the storage it stands on, gone between the session and the request. */
    CLUSTER_NOT_HERE_FOR_STORAGE_BACKEND(Area.CLUSTERS, 60, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** Files carried between stores by a session that carries no account to record the move against. */
    NO_ACCOUNT_IN_SESSION_FOR_STORAGE_MOVE(
            Area.CLUSTERS, 61, HttpStatus.FORBIDDEN, "Sign in again before doing this, so nothing was changed"),

    /** A cluster being granted the room it may hand out that is not here any more. */
    CLUSTER_NOT_HERE_ON_POOL(Area.CLUSTERS, 62, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** The room a cluster hands out, asked for by somebody who has not said which cluster. */
    NO_CLUSTER_CHOSEN_FOR_STORAGE(Area.CLUSTERS, 63, HttpStatus.BAD_REQUEST, Sentences.NO_CLUSTER_CHOSEN),

    /** The cluster behind the room it hands out, gone between the session and the request. */
    CLUSTER_NOT_HERE_FOR_STORAGE(Area.CLUSTERS, 64, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A station or a cluster named in a storage address, written as neither can be. */
    NOT_AN_IDENTITY_IN_CLUSTER_STORAGE(
            Area.CLUSTERS, 65, HttpStatus.BAD_REQUEST, "That does not name a station or a cluster"),

    /** A cluster a station is asking to join that is not here any more. */
    CLUSTER_NOT_HERE_ON_APPLICATION(Area.CLUSTERS, 66, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A station asking to join a cluster without having said which station is asking. */
    NO_STATION_CHOSEN_FOR_CLUSTER_APPLICATION(Area.CLUSTERS, 67, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** A station asking to join a cluster from a session that holds no membership to ask with. */
    NO_MEMBERSHIP_FOR_CLUSTER_APPLICATION(Area.CLUSTERS, 68, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** A cluster a station is asking to join, named in a way no cluster can be named. */
    CLUSTER_NOT_AN_IDENTITY_ON_APPLICATION(
            Area.CLUSTERS, 69, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NOT_AN_IDENTITY),

    /**
     * A file in the media library that is gone, or one held back from this reader. The two are one
     * code deliberately: telling them apart would say that the file is there and withheld.
     */
    FILE_NOT_HERE(Area.MEDIA_LIBRARY, 1, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /**
     * A picture in the media library that is gone, or one held back from this reader. One code for
     * the same reason as the file above.
     */
    PICTURE_NOT_HERE(Area.MEDIA_LIBRARY, 2, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** The library of a station, asked for by somebody who has not said which station. */
    NO_STATION_CHOSEN_FOR_LIBRARY(Area.MEDIA_LIBRARY, 3, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** An upload to the library that arrived without the file itself. */
    UPLOAD_MISSING_FILE(Area.MEDIA_LIBRARY, 4, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A file bigger than this instance takes. */
    UPLOAD_TOO_LARGE(
            Area.MEDIA_LIBRARY, 5, HttpStatus.CONTENT_TOO_LARGE, "That file is larger than this instance accepts"),

    /** An upload that was read and then refused for what it held. */
    UPLOAD_NOT_SAVED(Area.MEDIA_LIBRARY, 6, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** An upload that broke on the way in, which is Ember's to look into rather than the reader's. */
    UPLOAD_NOT_PROCESSED(Area.MEDIA_LIBRARY, 7, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A file that went between being read and being deleted. */
    FILE_NOT_DELETED(Area.MEDIA_LIBRARY, 8, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file whose description or alternative text could not be written. */
    FILE_NOT_HERE_ON_DETAIL_CHANGE(Area.MEDIA_LIBRARY, 9, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file that could not be moved into another folder. */
    FILE_NOT_HERE_ON_MOVE(Area.MEDIA_LIBRARY, 10, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A folder that went before its details could be written. */
    FOLDER_NOT_HERE_ON_CHANGE(Area.MEDIA_LIBRARY, 11, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** A folder that was already gone when its deletion was asked for. */
    FOLDER_NOT_HERE_ON_DELETE(Area.MEDIA_LIBRARY, 12, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** A tag that went before its name or colour could be written. */
    FILE_TAG_NOT_HERE_ON_CHANGE(Area.MEDIA_LIBRARY, 13, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /** A tag that was already gone when its deletion was asked for. */
    FILE_TAG_NOT_HERE_ON_DELETE(Area.MEDIA_LIBRARY, 14, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /** A tag that could not be put on a file. */
    FILE_TAG_NOT_HERE_ON_ASSIGNMENT(Area.MEDIA_LIBRARY, 15, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /** A tag that could not be taken off a file. */
    FILE_TAG_NOT_HERE_ON_REMOVAL(Area.MEDIA_LIBRARY, 16, HttpStatus.NOT_FOUND, Sentences.FILE_TAG_NOT_HERE),

    /**
     * A folder or an article of the wiki that the reader's reach does not cover. Answered as though
     * it were not there, because saying it is there and withheld is already saying it is there.
     */
    KB_ENTRY_NOT_YOURS_TO_OPEN(Area.KNOWLEDGE_BASE, 1, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** An upload to the wiki that arrived without the file itself. */
    KB_UPLOAD_MISSING_FILE(Area.KNOWLEDGE_BASE, 2, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A file bigger than the wiki takes. */
    KB_UPLOAD_TOO_LARGE(Area.KNOWLEDGE_BASE, 3, HttpStatus.BAD_REQUEST, "That file is larger than the wiki accepts"),

    /** A folder being made with nothing in the name box. */
    KB_FOLDER_NEEDS_A_NAME(
            Area.KNOWLEDGE_BASE, 4, HttpStatus.BAD_REQUEST, "A folder needs a name, so nothing was saved"),

    /** A folder that went before the change to it could be written. */
    KB_FOLDER_NOT_CHANGED(Area.KNOWLEDGE_BASE, 5, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** A folder that went between being changed and being read back. */
    KB_FOLDER_NOT_HERE_AFTER_CHANGE(Area.KNOWLEDGE_BASE, 6, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** A folder that was already gone when it was to be put in the trash. */
    KB_FOLDER_NOT_TRASHED(Area.KNOWLEDGE_BASE, 7, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** The folder a move aims at, which is not one the reader may put anything into. */
    KB_MOVE_TARGET_NOT_USABLE(
            Area.KNOWLEDGE_BASE, 8, HttpStatus.NOT_FOUND, "Nothing can be put into that folder, so nothing was moved"),

    /** A move being looked ahead at without saying what is to be moved. */
    KB_MOVE_PREVIEW_NEEDS_AN_ENTRY(
            Area.KNOWLEDGE_BASE, 9, HttpStatus.BAD_REQUEST, "Say which folder or article the move is for"),

    /** A folder being thrown away for good that is no longer in the trash. */
    KB_FOLDER_NOT_PURGED(Area.KNOWLEDGE_BASE, 10, HttpStatus.NOT_FOUND, "That folder is not in the trash any more"),

    /** An article being thrown away for good that is no longer in the trash. */
    KB_ARTICLE_NOT_PURGED(Area.KNOWLEDGE_BASE, 11, HttpStatus.NOT_FOUND, "That article is not in the trash any more"),

    /** An article that went before the change to its details could be written. */
    KB_ARTICLE_NOT_CHANGED(Area.KNOWLEDGE_BASE, 12, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** An article that went between being changed and being read back. */
    KB_ARTICLE_NOT_HERE_AFTER_CHANGE(Area.KNOWLEDGE_BASE, 13, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** An article that was already gone when it was to be put in the trash. */
    KB_ARTICLE_NOT_TRASHED(Area.KNOWLEDGE_BASE, 14, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** A written article being made with nothing in the name box. */
    KB_ARTICLE_NEEDS_A_NAME(Area.KNOWLEDGE_BASE, 15, HttpStatus.BAD_REQUEST, Sentences.KB_ARTICLE_NEEDS_A_NAME),

    /** A video entry being made with nothing in the name box. */
    KB_VIDEO_NEEDS_A_NAME(Area.KNOWLEDGE_BASE, 16, HttpStatus.BAD_REQUEST, Sentences.KB_ARTICLE_NEEDS_A_NAME),

    /** A video entry being made without the address of the video. */
    KB_VIDEO_NEEDS_AN_ADDRESS(
            Area.KNOWLEDGE_BASE,
            17,
            HttpStatus.BAD_REQUEST,
            "A video entry needs the address of the video, so nothing was saved"),

    /** A link entry being made without the address it is to lead to. */
    KB_LINK_NEEDS_AN_ADDRESS(
            Area.KNOWLEDGE_BASE, 18, HttpStatus.BAD_REQUEST, "A link entry needs an address, so nothing was saved"),

    /** An upload whose contents could not be read off the request at all. */
    KB_UPLOAD_NOT_READ(
            Area.KNOWLEDGE_BASE, 19, HttpStatus.BAD_REQUEST, "That file could not be read, so nothing was saved"),

    /** A document offered for reading in that is of a kind the wiki cannot take apart. */
    KB_IMPORT_KIND_UNKNOWN(
            Area.KNOWLEDGE_BASE,
            20,
            HttpStatus.BAD_REQUEST,
            "That kind of document cannot be read in here, so nothing was saved"),

    /** A document that was of a readable kind and still could not be turned into an article. */
    KB_IMPORT_FAILED(
            Area.KNOWLEDGE_BASE,
            21,
            HttpStatus.BAD_REQUEST,
            "That document could not be turned into an article, so nothing was saved"),

    /** A written article whose text is gone. */
    KB_ARTICLE_TEXT_NOT_HERE(Area.KNOWLEDGE_BASE, 22, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_HAS_NOTHING_TO_SHOW),

    /** An uploaded entry whose stored file is gone. */
    KB_FILE_CONTENT_NOT_HERE(Area.KNOWLEDGE_BASE, 23, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A presentation asked for before it had been turned into something a browser can show. */
    KB_PRESENTATION_NOT_READY(
            Area.KNOWLEDGE_BASE,
            24,
            HttpStatus.NOT_FOUND,
            "That presentation is still being prepared. Try again shortly"),

    /** A written article whose text is gone, asked for as a drawn page. */
    KB_ARTICLE_TEXT_NOT_HERE_AS_PAGE(
            Area.KNOWLEDGE_BASE, 25, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_HAS_NOTHING_TO_SHOW),

    /** An article that went before its blocks could be written. */
    KB_BLOCKS_NOT_SAVED(Area.KNOWLEDGE_BASE, 26, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** An article that went before it could be turned into one built from blocks. */
    KB_BLOCKS_NOT_ENABLED(Area.KNOWLEDGE_BASE, 27, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** An entry asked for as a PDF that holds no text to set. */
    KB_NOT_A_PDF_TO_MAKE(Area.KNOWLEDGE_BASE, 28, HttpStatus.BAD_REQUEST, Sentences.KB_ONLY_WRITTEN_AS_PDF),

    /** A PDF whose setting was stopped before it finished. */
    KB_PDF_STOPPED(Area.KNOWLEDGE_BASE, 29, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** A PDF that broke while it was being set, which is Ember's to look into. */
    KB_PDF_NOT_MADE(Area.KNOWLEDGE_BASE, 30, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** The file an entry was made from, asked for on an entry that was not made from one. */
    KB_ORIGINAL_NOT_KEPT(Area.KNOWLEDGE_BASE, 31, HttpStatus.BAD_REQUEST, Sentences.KB_ORIGINAL_ONLY_FOR_PRESENTATIONS),

    /** A presentation whose stored file is gone. */
    KB_ORIGINAL_NOT_HERE(Area.KNOWLEDGE_BASE, 32, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A replacement file sent for an entry that was not made from one. */
    KB_ORIGINAL_NOT_REPLACEABLE(
            Area.KNOWLEDGE_BASE, 33, HttpStatus.BAD_REQUEST, Sentences.KB_ORIGINAL_ONLY_FOR_PRESENTATIONS),

    /** A presentation that broke while it was being replaced. */
    KB_PRESENTATION_NOT_REPLACED(
            Area.KNOWLEDGE_BASE,
            34,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "That presentation could not be replaced, so nothing was saved. Trying again may work"),

    /** An earlier state of an article that is gone. */
    KB_VERSION_NOT_HERE(
            Area.KNOWLEDGE_BASE, 35, HttpStatus.NOT_FOUND, "That version of the article is not here any more"),

    /** An article whose tile picture could not be had. */
    KB_ARTICLE_PICTURE_NOT_HERE(Area.KNOWLEDGE_BASE, 36, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A folder icon sent without the picture itself. */
    KB_FOLDER_ICON_MISSING(Area.KNOWLEDGE_BASE, 37, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A folder icon of a kind the wiki does not take. */
    KB_FOLDER_ICON_KIND_NOT_TAKEN(Area.KNOWLEDGE_BASE, 38, HttpStatus.BAD_REQUEST, Sentences.KB_PICTURE_KIND_NOT_TAKEN),

    /** A folder icon that was read and then refused for what it held. */
    KB_FOLDER_ICON_NOT_SAVED(Area.KNOWLEDGE_BASE, 39, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** A folder icon that broke on the way in, which is Ember's to look into. */
    KB_FOLDER_ICON_NOT_PROCESSED(
            Area.KNOWLEDGE_BASE, 40, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A picture for an article sent without the picture itself. */
    KB_ARTICLE_IMAGE_MISSING(Area.KNOWLEDGE_BASE, 41, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A picture for an article of a kind the wiki does not take. */
    KB_ARTICLE_IMAGE_KIND_NOT_TAKEN(
            Area.KNOWLEDGE_BASE, 42, HttpStatus.BAD_REQUEST, Sentences.KB_PICTURE_KIND_NOT_TAKEN),

    /** A picture for an article that was read and then refused for what it held. */
    KB_ARTICLE_IMAGE_NOT_SAVED(Area.KNOWLEDGE_BASE, 43, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** A picture for an article that broke on the way in, which is Ember's to look into. */
    KB_ARTICLE_IMAGE_NOT_PROCESSED(
            Area.KNOWLEDGE_BASE, 44, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A comment on an article of this station with nothing written in it. */
    KB_COMMENT_EMPTY(Area.KNOWLEDGE_BASE, 45, HttpStatus.BAD_REQUEST, Sentences.KB_COMMENT_EMPTY),

    /** Somebody else's comment, being reworded. */
    KB_COMMENT_NOT_YOURS_TO_CHANGE(
            Area.KNOWLEDGE_BASE, 46, HttpStatus.FORBIDDEN, "You may only change your own comments"),

    /** A comment that went between being reworded and being read back. */
    KB_COMMENT_NOT_HERE_AFTER_CHANGE(Area.KNOWLEDGE_BASE, 47, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** Somebody else's comment, being deleted by a reader who does not look after the wiki. */
    KB_COMMENT_NOT_YOURS_TO_DELETE(
            Area.KNOWLEDGE_BASE, 48, HttpStatus.FORBIDDEN, "You may only delete your own comments"),

    /** A comment that was already gone when its deletion was asked for. */
    KB_COMMENT_NOT_DELETED(Area.KNOWLEDGE_BASE, 49, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** A comment that is gone, asked for by a route that then checks whose article it hangs on. */
    KB_COMMENT_NOT_HERE(Area.KNOWLEDGE_BASE, 50, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** Favourites, asked for by a session that holds station rights without being a member. */
    KB_FAVOURITES_NEED_A_MEMBER(
            Area.KNOWLEDGE_BASE, 51, HttpStatus.NOT_FOUND, "This sign-in keeps no favourites of its own"),

    /** A favourite being marked without saying what kind of thing it is. */
    KB_FAVOURITE_NEEDS_A_TARGET(
            Area.KNOWLEDGE_BASE,
            52,
            HttpStatus.BAD_REQUEST,
            "Nothing was named to mark as a favourite, so nothing was saved"),

    /** A favourite at a partner station, marked without saying which partner. */
    KB_FAVOURITE_NEEDS_A_PARTNER(
            Area.KNOWLEDGE_BASE,
            53,
            HttpStatus.BAD_REQUEST,
            "A favourite at a partner station needs the station named, so nothing was saved"),

    /** A favourite that was already gone when its mark was to be taken off. */
    KB_FAVOURITE_NOT_HERE(Area.KNOWLEDGE_BASE, 54, HttpStatus.NOT_FOUND, "That favourite is not here any more"),

    /** A public wiki address whose station part is not an identifier at all. */
    PUBLIC_KB_ADDRESS_NOT_A_STATION(
            Area.KNOWLEDGE_BASE, 55, HttpStatus.BAD_REQUEST, "That address does not name a station"),

    /** A station that is not here, asked for by a public wiki address. */
    STATION_NOT_HERE_BEHIND_PUBLIC_KB(Area.KNOWLEDGE_BASE, 56, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /**
     * A station whose public wiki is switched off, and a station whose wiki is switched off
     * altogether. One code deliberately: the reader is not signed in here and is owed nothing about
     * which of the two switches stands where.
     */
    PUBLIC_KB_SWITCHED_OFF(Area.KNOWLEDGE_BASE, 57, HttpStatus.NOT_FOUND, "This station has no public wiki"),

    /**
     * A folder or an article of a public wiki that is gone, belongs to another station, or was
     * never published. One code deliberately: telling them apart would say that the entry is there
     * and held back, to a reader who is not signed in to anything.
     */
    PUBLIC_KB_ENTRY_NOT_HERE(
            Area.KNOWLEDGE_BASE, 58, HttpStatus.NOT_FOUND, "That is not on this station's public wiki"),

    /** A published entry whose stored file is gone. */
    PUBLIC_KB_FILE_CONTENT_NOT_HERE(Area.KNOWLEDGE_BASE, 59, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A published article whose tile picture could not be had. */
    PUBLIC_KB_ARTICLE_PICTURE_NOT_HERE(Area.KNOWLEDGE_BASE, 60, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A published entry asked for as a drawn page that is not a written article. */
    PUBLIC_KB_NOT_A_WRITTEN_ARTICLE(
            Area.KNOWLEDGE_BASE, 61, HttpStatus.BAD_REQUEST, "That entry is not a written article"),

    /** A published entry asked for as a PDF that holds no text to set. */
    PUBLIC_KB_NOT_A_PDF_TO_MAKE(Area.KNOWLEDGE_BASE, 62, HttpStatus.BAD_REQUEST, Sentences.KB_ONLY_WRITTEN_AS_PDF),

    /** A PDF of a published article whose setting was stopped before it finished. */
    PUBLIC_KB_PDF_STOPPED(Area.KNOWLEDGE_BASE, 63, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** A PDF of a published article that broke while it was being set. */
    PUBLIC_KB_PDF_NOT_MADE(Area.KNOWLEDGE_BASE, 64, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** A comment on a partner station's article with nothing written in it. */
    PARTNER_KB_COMMENT_EMPTY(Area.KNOWLEDGE_BASE, 65, HttpStatus.BAD_REQUEST, Sentences.KB_COMMENT_EMPTY),

    /** A PDF of a partner station's article whose setting was stopped before it finished. */
    PARTNER_KB_PDF_STOPPED(Area.KNOWLEDGE_BASE, 66, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** A PDF of a partner station's article that broke while it was being set. */
    PARTNER_KB_PDF_NOT_MADE(Area.KNOWLEDGE_BASE, 67, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.KB_PDF_NOT_MADE),

    /** A comment at a partner station that was already gone when its deletion was asked for. */
    PARTNER_KB_COMMENT_NOT_DELETED(Area.KNOWLEDGE_BASE, 68, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** A comment sent by a partner instance with nothing written in it. */
    REMOTE_KB_COMMENT_EMPTY(Area.KNOWLEDGE_BASE, 69, HttpStatus.BAD_REQUEST, Sentences.KB_COMMENT_EMPTY),

    /** A comment a partner instance asked to delete that was already gone. */
    REMOTE_KB_COMMENT_NOT_DELETED(Area.KNOWLEDGE_BASE, 70, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** A presentation that could not be read back after the file behind it was replaced. */
    KB_FILE_NOT_HERE_AFTER_REUPLOAD(
            Area.KNOWLEDGE_BASE, 71, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** An avatar asked for by somebody who may not see it, or of somebody who has none. */
    AVATAR_NOT_HERE(Area.MEMBERS, 1, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** An avatar upload that arrived without the picture itself. */
    AVATAR_UPLOAD_MISSING_FILE(Area.MEMBERS, 2, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A picture that could not be read as one, or is of a kind not taken here. */
    AVATAR_NOT_A_PICTURE(Area.MEMBERS, 3, HttpStatus.BAD_REQUEST, "That file could not be taken as a picture"),

    /** An avatar that was read and then refused for what it held. */
    AVATAR_NOT_SAVED(Area.MEMBERS, 4, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_NOT_SAVED),

    /** An avatar that broke on the way in, which is Ember's to look into rather than the reader's. */
    AVATAR_NOT_PROCESSED(Area.MEMBERS, 5, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A session that is signed in to a station but stands for no account. */
    SESSION_HAS_NO_ACCOUNT(Area.MEMBERS, 6, HttpStatus.BAD_REQUEST, Sentences.SESSION_HAS_NO_ACCOUNT),

    /** A session whose account could not be resolved to the one an avatar is kept under. */
    SESSION_ACCOUNT_NOT_RESOLVED(Area.MEMBERS, 7, HttpStatus.BAD_REQUEST, Sentences.SESSION_HAS_NO_ACCOUNT),

    /** An account that is not a member of the station the reader is signed in to. */
    MEMBER_NOT_HERE(Area.MEMBERS, 8, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** An account being sent through joining again that is gone. */
    ACCOUNT_NOT_HERE_ON_ONBOARDING_AGAIN(Area.MEMBERS, 9, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account being given a passkey code that is gone. */
    ACCOUNT_NOT_HERE_ON_PASSKEY_CODE(Area.MEMBERS, 10, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account whose details were to be changed and is gone. */
    ACCOUNT_NOT_HERE_ON_CHANGE(Area.MEMBERS, 11, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account that went between being read and having its details written. */
    MEMBER_NOT_HERE_ON_CHANGE(Area.MEMBERS, 12, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** An account whose password was to be reset and is gone. */
    ACCOUNT_NOT_HERE_ON_PASSWORD_RESET(Area.MEMBERS, 13, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An account the password reset mail could not be sent for. */
    ACCOUNT_NOT_HERE_ON_PASSWORD_RESET_MAIL(Area.MEMBERS, 14, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** Profile questions of a station, asked for by somebody who has not said which station. */
    NO_STATION_CHOSEN_FOR_PROFILE_QUESTIONS(Area.MEMBERS, 15, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** A profile question that went before the change to it could be written. */
    PROFILE_FIELD_NOT_HERE_ON_CHANGE(Area.MEMBERS, 16, HttpStatus.NOT_FOUND, Sentences.PROFILE_FIELD_NOT_HERE),

    /** A profile question that was already gone when its deletion was asked for. */
    PROFILE_FIELD_NOT_HERE_ON_DELETE(Area.MEMBERS, 17, HttpStatus.NOT_FOUND, Sentences.PROFILE_FIELD_NOT_HERE),

    /** A registration that named no name, no address or no password to sign in with. */
    REGISTRATION_DETAILS_MISSING(
            Area.MEMBERS,
            18,
            HttpStatus.BAD_REQUEST,
            "Give a first name, a last name, an address and a password to register"),

    /**
     * A self-registration the sign-up refused, whether over the registration code it named or the
     * address it chose. One code deliberately: an endpoint nobody has signed in to must not say
     * which addresses are already registered here, and telling the reasons apart would.
     */
    REGISTRATION_REFUSED(
            Area.MEMBERS,
            19,
            HttpStatus.CONFLICT,
            "You could not be registered with these details, so no account was made"),

    /** A confirmation of an address that arrived with nothing to confirm. */
    EMAIL_VERIFICATION_TOKEN_MISSING(Area.MEMBERS, 20, HttpStatus.BAD_REQUEST, Sentences.LINK_CARRIES_NOTHING),

    /**
     * A confirmation link that is unknown, has run out, or has already been spent. One code
     * deliberately: telling them apart would say whether an address was ever asked to confirm
     * itself here, which is the thing this endpoint withholds from anybody guessing links.
     */
    EMAIL_VERIFICATION_LINK_NOT_GOOD(
            Area.MEMBERS,
            21,
            HttpStatus.BAD_REQUEST,
            "That confirmation link is no longer good, so the address was not confirmed"),

    /** A second confirmation mail asked for without saying where to send it. */
    RESEND_VERIFICATION_ADDRESS_MISSING(Area.MEMBERS, 22, HttpStatus.BAD_REQUEST, Sentences.ADDRESS_MISSING),

    /** A password being set without the link that allows it, or without the password itself. */
    PASSWORD_SETUP_DETAILS_MISSING(
            Area.MEMBERS, 23, HttpStatus.BAD_REQUEST, "A link and a password are both needed, so nothing was saved"),

    /** A chosen password shorter than this instance accepts. */
    NEW_PASSWORD_TOO_SHORT(Area.MEMBERS, 24, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_TOO_SHORT),

    /** A chosen password that is known to have leaked elsewhere. */
    NEW_PASSWORD_BREACHED(Area.MEMBERS, 25, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_BREACHED),

    /** A setup or reset link that no account answers to. */
    PASSWORD_SETUP_LINK_UNKNOWN(
            Area.MEMBERS, 26, HttpStatus.BAD_REQUEST, "That link is not one a password can be set with"),

    /** A setup or reset link that was good and has since run out. */
    PASSWORD_SETUP_LINK_EXPIRED(
            Area.MEMBERS, 27, HttpStatus.BAD_REQUEST, "That link has run out, so nothing was saved"),

    /** A password being set on an instance that has stopped giving them out. */
    PASSWORDS_SWITCHED_OFF(
            Area.MEMBERS, 28, HttpStatus.FORBIDDEN, "This instance gives out no passwords: a passkey is the way in"),

    /** An address being put on an account without the link that allows it, or without the address. */
    ADDRESS_SETUP_DETAILS_MISSING(
            Area.MEMBERS, 29, HttpStatus.BAD_REQUEST, "A link and an address are both needed, so nothing was saved"),

    /** A sign-in that was stopped for an address, carried on with something that stands for no such stop. */
    ADDRESS_SETUP_LINK_UNKNOWN(
            Area.MEMBERS, 30, HttpStatus.BAD_REQUEST, "That step cannot be carried on with. Sign in again"),

    /** The same stop, carried on with long enough after it that it no longer stands. */
    ADDRESS_SETUP_LINK_EXPIRED(Area.MEMBERS, 31, HttpStatus.BAD_REQUEST, "That step has run out. Sign in again"),

    /** An address that is not shaped like one. */
    ADDRESS_MALFORMED(Area.MEMBERS, 32, HttpStatus.BAD_REQUEST, "That is not an address anything can be sent to"),

    /** An address shaped like one that nothing can actually be delivered to. */
    ADDRESS_UNREACHABLE(
            Area.MEMBERS, 33, HttpStatus.BAD_REQUEST, "Nothing can be delivered to that address, so nothing was saved"),

    /** An address another account on this instance already carries, given at the sign-in stop. */
    ADDRESS_TAKEN_ON_SETUP(Area.MEMBERS, 34, HttpStatus.BAD_REQUEST, Sentences.ADDRESS_BELONGS_TO_ANOTHER),

    /** A forgotten password asked about without saying whose. */
    FORGOTTEN_PASSWORD_ADDRESS_MISSING(Area.MEMBERS, 35, HttpStatus.BAD_REQUEST, Sentences.ADDRESS_MISSING),

    /** A sign-in that left one of the two fields empty. */
    SIGN_IN_DETAILS_MISSING(Area.MEMBERS, 36, HttpStatus.BAD_REQUEST, "Give both a sign-in name and a password"),

    /**
     * A sign-in that did not work, for every reason a sign-in does not work: no such account, the
     * wrong password, an address nobody has confirmed, an account whose password has been switched
     * off. One code and one sentence deliberately, because that is the whole point of this line:
     * anybody trying addresses at this endpoint must be told the same thing every time, or the
     * endpoint becomes a list of who has an account here.
     */
    SIGN_IN_REFUSED(
            Area.MEMBERS,
            37,
            HttpStatus.UNAUTHORIZED,
            "Signing in did not work. Check what you typed, and whether your address is confirmed"),

    /** A quick sign-in on a demo instance that said nobody to sign in as. */
    DEMO_SIGN_IN_ADDRESS_MISSING(Area.MEMBERS, 38, HttpStatus.BAD_REQUEST, Sentences.ADDRESS_MISSING),

    /** A quick sign-in that did not work, on the demo instances where one is offered at all. */
    DEMO_SIGN_IN_REFUSED(Area.MEMBERS, 39, HttpStatus.UNAUTHORIZED, "That quick sign-in did not work"),

    /** A session being carried on that named no session. */
    SESSION_RENEWAL_TOKEN_MISSING(Area.MEMBERS, 40, HttpStatus.BAD_REQUEST, Sentences.SESSION_TOKEN_MISSING),

    /**
     * A session that could not be carried on, whether unknown, run out or already ended. One code
     * deliberately: the three say nothing a reader can act on beyond signing in again.
     */
    SESSION_NOT_RENEWED(
            Area.MEMBERS, 41, HttpStatus.UNAUTHORIZED, "This session could not be carried on. Sign in again"),

    /** A sign-out that named no session to end. */
    SIGN_OUT_TOKEN_MISSING(Area.MEMBERS, 42, HttpStatus.BAD_REQUEST, Sentences.SESSION_TOKEN_MISSING),

    /** A password change that left the old or the new one empty. */
    PASSWORD_CHANGE_DETAILS_MISSING(
            Area.MEMBERS, 43, HttpStatus.BAD_REQUEST, "Give both the current password and the new one"),

    /** A new password shorter than this instance accepts. */
    CHANGED_PASSWORD_TOO_SHORT(Area.MEMBERS, 44, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_TOO_SHORT),

    /** A new password that is known to have leaked elsewhere. */
    CHANGED_PASSWORD_BREACHED(Area.MEMBERS, 45, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_BREACHED),

    /** A password change on an account that has never had one. */
    ACCOUNT_HAS_NO_PASSWORD(
            Area.MEMBERS,
            46,
            HttpStatus.BAD_REQUEST,
            "This account carries no password to change. Sign in with your passkey and set one"),

    /** A password change where the password given as the current one is not it. */
    CURRENT_PASSWORD_WRONG(
            Area.MEMBERS, 47, HttpStatus.BAD_REQUEST, "The current password is not right, so nothing was saved"),

    /** An address change being confirmed with nothing to confirm. */
    EMAIL_CHANGE_TOKEN_MISSING(Area.MEMBERS, 48, HttpStatus.BAD_REQUEST, Sentences.LINK_CARRIES_NOTHING),

    /** An address change onto an address another account has taken meanwhile. */
    EMAIL_CHANGE_ADDRESS_TAKEN(Area.MEMBERS, 49, HttpStatus.BAD_REQUEST, Sentences.ADDRESS_BELONGS_TO_ANOTHER),

    /**
     * A link confirming an address change that is unknown or has run out. One code deliberately,
     * for the same reason the confirmation of a new address has one.
     */
    EMAIL_CHANGE_LINK_NOT_GOOD(
            Area.MEMBERS,
            50,
            HttpStatus.BAD_REQUEST,
            "That confirmation link is no longer good, so the address was not changed"),

    /**
     * A member asked about by a session that stands at no station. Answered as a missing member
     * rather than as a missing station, which is what the ownership check right behind it answers.
     */
    MEMBER_NOT_HERE_WITHOUT_STATION(Area.MEMBERS, 51, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A member named by the identifier a member menu hands over, who is gone or has left. */
    MEMBER_NOT_HERE_BY_UID(Area.MEMBERS, 52, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A membership being made without saying whose account it is for. */
    MEMBER_ACCOUNT_NOT_NAMED(Area.MEMBERS, 53, HttpStatus.BAD_REQUEST, "Name the account to add as a member"),

    /** A membership being made by somebody who has not said which station. */
    NO_STATION_CHOSEN_FOR_NEW_MEMBER(Area.MEMBERS, 54, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** A member who cannot yet be written off the register. */
    MEMBER_NOT_MARKED_FORMER(
            Area.MEMBERS,
            55,
            HttpStatus.BAD_REQUEST,
            "This member cannot be marked as having left: they may still hold equipment, or a role"),

    /** An invitation being sent again to a member who has no account behind them. */
    MEMBER_HAS_NO_ACCOUNT(Area.MEMBERS, 56, HttpStatus.BAD_REQUEST, "This member has no account to write to"),

    /** The account behind a member, gone between the member being read and the mail being sent. */
    ACCOUNT_NOT_HERE_ON_SETUP_MAIL(Area.MEMBERS, 57, HttpStatus.BAD_REQUEST, Sentences.ACCOUNT_NOT_HERE),

    /** An invitation being sent again to somebody who has already taken their account over. */
    ACCOUNT_ALREADY_SET_UP(
            Area.MEMBERS, 58, HttpStatus.BAD_REQUEST, "This account is already set up, so no invitation was sent"),

    /** An invitation for an account that nothing can be delivered about, to anybody. */
    ACCOUNT_NOBODY_TO_WRITE_TO(
            Area.MEMBERS,
            59,
            HttpStatus.BAD_REQUEST,
            "Nobody can be written to about this account, so no invitation was sent"),

    /** A member's kind being set without saying which kind. */
    MEMBER_USER_TYPE_NOT_NAMED(Area.MEMBERS, 60, HttpStatus.BAD_REQUEST, "Name the kind of member this should be"),

    /** A member's joining date being set without saying which date. */
    MEMBER_JOIN_DATE_NOT_NAMED(Area.MEMBERS, 61, HttpStatus.BAD_REQUEST, "Name the date this member joined"),

    /** An account that administers the instance, acted on from a permission below that. */
    ACCOUNT_ABOVE_YOU(
            Area.MEMBERS,
            62,
            HttpStatus.FORBIDDEN,
            "An account that administers the instance can only be acted on by one that does too"),

    /** A fresh way in being made for somebody, without saying whose account. */
    ACCOUNT_NOT_NAMED_ON_ONBOARDING_AGAIN(Area.MEMBERS, 63, HttpStatus.BAD_REQUEST, Sentences.ACCOUNT_NOT_NAMED),

    /** A passkey code being handed out, without saying whose account it is for. */
    ACCOUNT_NOT_NAMED_ON_PASSKEY_CODE(Area.MEMBERS, 64, HttpStatus.BAD_REQUEST, Sentences.ACCOUNT_NOT_NAMED),

    /** A passkey code asked for on behalf of somebody who can be written to directly. */
    MEMBER_HAS_OWN_ADDRESS(
            Area.MEMBERS, 65, HttpStatus.FORBIDDEN, "This member has an address of their own: the way in is by mail"),

    /** Somebody else's account being changed by a reader who may only change their own. */
    ACCOUNT_NOT_YOURS_TO_CHANGE(
            Area.MEMBERS, 66, HttpStatus.FORBIDDEN, "Changing another account takes the right to edit members"),

    /** An address change onto an address another account already carries. */
    ACCOUNT_ADDRESS_TAKEN(Area.MEMBERS, 67, HttpStatus.BAD_REQUEST, Sentences.ADDRESS_BELONGS_TO_ANOTHER),

    /** An invitation with nobody's name on it. */
    INVITE_NAME_MISSING(Area.MEMBERS, 68, HttpStatus.BAD_REQUEST, "Give a first name and a last name"),

    /**
     * An invitation the provisioning refused, whether because the address is spoken for or because
     * the station would not take the membership. One code: the reader is invited to correct the
     * row in front of them, and which half refused does not change what they do about it.
     */
    MEMBER_NOT_PROVISIONED(
            Area.MEMBERS, 69, HttpStatus.CONFLICT, "That member could not be set up, so nothing was saved"),

    /** A password reset asked for without saying whose account. */
    ACCOUNT_NOT_NAMED_ON_PASSWORD_RESET(Area.MEMBERS, 70, HttpStatus.BAD_REQUEST, Sentences.ACCOUNT_NOT_NAMED),

    /** A profile question being made with no name or no kind. */
    PROFILE_FIELD_DETAILS_MISSING_ON_CREATE(
            Area.MEMBERS, 71, HttpStatus.BAD_REQUEST, Sentences.PROFILE_FIELD_DETAILS_MISSING),

    /** A profile question being put to an audience named as both a kind of member and a group, or as neither. */
    PROFILE_FIELD_AUDIENCE_AMBIGUOUS_ON_ASSIGN(
            Area.MEMBERS, 72, HttpStatus.BAD_REQUEST, Sentences.PROFILE_FIELD_AUDIENCE_AMBIGUOUS),

    /** The same, where an audience is being stopped from being asked rather than started. */
    PROFILE_FIELD_AUDIENCE_AMBIGUOUS_ON_UNASSIGN(
            Area.MEMBERS, 73, HttpStatus.BAD_REQUEST, Sentences.PROFILE_FIELD_AUDIENCE_AMBIGUOUS),

    /** A profile question being changed to have no name or no kind. */
    PROFILE_FIELD_DETAILS_MISSING_ON_CHANGE(
            Area.MEMBERS, 74, HttpStatus.BAD_REQUEST, Sentences.PROFILE_FIELD_DETAILS_MISSING),

    /** Profile questions being put in order without saying whose order it is. */
    PROFILE_FIELD_ORDER_AUDIENCE_MISSING(
            Area.MEMBERS, 75, HttpStatus.BAD_REQUEST, "Name the kind of member this order is for"),

    /** A group being made without a name. */
    GROUP_NAME_MISSING_ON_CREATE(Area.MEMBERS, 76, HttpStatus.BAD_REQUEST, Sentences.GROUP_NAME_MISSING),

    /** A group that went between the ownership check and being read. */
    GROUP_NOT_HERE_ON_READ(Area.MEMBERS, 77, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE),

    /** A group being renamed to nothing. */
    GROUP_NAME_MISSING_ON_CHANGE(Area.MEMBERS, 78, HttpStatus.BAD_REQUEST, Sentences.GROUP_NAME_MISSING),

    /** A group that went before the change to it could be written. */
    GROUP_NOT_HERE_ON_CHANGE(Area.MEMBERS, 79, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE),

    /** A group that was already gone when its deletion was asked for. */
    GROUP_NOT_HERE_ON_DELETE(Area.MEMBERS, 80, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE),

    /** A tag being made without a name. */
    TAG_NAME_MISSING_ON_CREATE(Area.MEMBERS, 81, HttpStatus.BAD_REQUEST, Sentences.TAG_NAME_MISSING),

    /** A tag being renamed to nothing. */
    TAG_NAME_MISSING_ON_CHANGE(Area.MEMBERS, 82, HttpStatus.BAD_REQUEST, Sentences.TAG_NAME_MISSING),

    /** A tag that went before the change to it could be written. */
    MEMBER_TAG_NOT_HERE_ON_CHANGE(Area.MEMBERS, 83, HttpStatus.NOT_FOUND, Sentences.MEMBER_TAG_NOT_HERE),

    /** A tag that was already gone when its deletion was asked for. */
    MEMBER_TAG_NOT_HERE_ON_DELETE(Area.MEMBERS, 84, HttpStatus.NOT_FOUND, Sentences.MEMBER_TAG_NOT_HERE),

    /** The list of what a station transfer carries, asked for with a transfer that is over. */
    TRANSFER_TOKEN_NOT_GOOD_ON_TABLES(Area.MEMBERS, 85, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** One part of a station transfer, asked for with a transfer that is over. */
    TRANSFER_TOKEN_NOT_GOOD_ON_TABLE(Area.MEMBERS, 86, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** A part of a station transfer asked for by a name no transfer carries. */
    TRANSFER_PART_UNKNOWN(
            Area.MEMBERS, 87, HttpStatus.BAD_REQUEST, "That is not one of the parts a station transfer carries"),

    /** A transfer being called off that is over already. */
    TRANSFER_TOKEN_NOT_GOOD_ON_ABORT(Area.MEMBERS, 88, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** A transfer being signed off that is over already. */
    TRANSFER_TOKEN_NOT_GOOD_ON_COMPLETE(Area.MEMBERS, 89, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** A station being taken in without the code the other instance handed out. */
    TRANSFER_TOKEN_MISSING(Area.MEMBERS, 90, HttpStatus.BAD_REQUEST, "Give the transfer code, so nothing was started"),

    /** A transfer code that nothing can be read out of. */
    TRANSFER_TOKEN_UNREADABLE(
            Area.MEMBERS, 91, HttpStatus.BAD_REQUEST, "That transfer code could not be read, so nothing was started"),

    /** A transfer code that carries no instance to fetch from, where none was named beside it. */
    TRANSFER_SOURCE_MISSING(
            Area.MEMBERS,
            92,
            HttpStatus.BAD_REQUEST,
            "That transfer code names no instance to fetch from, so name one yourself"),

    /** Progress asked about for a station nothing is being taken in for. */
    TRANSFER_IMPORT_NOT_RUNNING(
            Area.MEMBERS, 93, HttpStatus.NOT_FOUND, "No transfer into this instance is running for that station"),

    /** A batch of invitations with nobody in it. */
    INVITES_MISSING(Area.MEMBERS, 94, HttpStatus.BAD_REQUEST, "Name at least one person to invite"),

    /** An invitation naming a kind of member this instance has none of. */
    INVITE_USER_TYPE_UNKNOWN(
            Area.MEMBERS, 95, HttpStatus.BAD_REQUEST, "That is not a kind of member anybody can be invited as"),

    /** One row of a batch of invitations with a name or an address left out. */
    INVITE_ENTRY_DETAILS_MISSING(
            Area.MEMBERS, 96, HttpStatus.BAD_REQUEST, "Every invitation needs both names and an address"),

    /** A guardian on a row of a batch of invitations with a name or an address left out. */
    INVITE_GUARDIAN_DETAILS_MISSING(
            Area.MEMBERS, 97, HttpStatus.BAD_REQUEST, "Every guardian needs both names and an address"),

    /** A registration code being made without the text people would type. */
    REGISTRATION_CODE_TEXT_MISSING(Area.MEMBERS, 98, HttpStatus.BAD_REQUEST, "Give the registration code its text"),

    /** A registration code that went between the list being drawn and it being opened. */
    REGISTRATION_CODE_NOT_HERE(Area.MEMBERS, 99, HttpStatus.NOT_FOUND, Sentences.REGISTRATION_CODE_NOT_HERE),

    /** A registration code that was already gone when its deletion was asked for. */
    REGISTRATION_CODE_NOT_DELETED(Area.MEMBERS, 100, HttpStatus.NOT_FOUND, Sentences.REGISTRATION_CODE_NOT_HERE),

    /** A saved filter being kept without a name, a list or anything to filter by. */
    SAVED_FILTER_DETAILS_MISSING(
            Area.MEMBERS, 101, HttpStatus.BAD_REQUEST, "Give the saved filter a name and something to filter by"),

    /** A saved filter that was already gone when its deletion was asked for. */
    SAVED_FILTER_NOT_HERE_ON_DELETE(Area.MEMBERS, 102, HttpStatus.NOT_FOUND, "That saved filter is not here any more"),

    /** A column selection being kept under no name. */
    MEMBER_TABLE_PRESET_NAME_MISSING(Area.MEMBERS, 103, HttpStatus.BAD_REQUEST, "A saved selection needs a name"),

    /** A member list that could not be drawn as a sheet to print. */
    MEMBER_TABLE_NOT_A_SHEET(Area.MEMBERS, 104, HttpStatus.BAD_REQUEST, "This list could not be turned into a sheet"),

    /** The station a member list is headed with, gone between the two reads. */
    STATION_NOT_HERE_FOR_MEMBER_TABLE(Area.MEMBERS, 105, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** An import that arrived without the file to read members out of. */
    IMPORT_FILE_MISSING(Area.MEMBERS, 106, HttpStatus.BAD_REQUEST, "The import arrived without a file in it"),

    /** An import file bigger than one request carries. */
    IMPORT_FILE_TOO_LARGE(Area.MEMBERS, 107, HttpStatus.BAD_REQUEST, "That file is larger than an import takes"),

    /**
     * A member whose change history was asked about and who is not here. Answered the same for a
     * member of another station, so that a member id cannot be probed for existence from outside.
     */
    MEMBER_NOT_HERE_ON_CHANGE_HISTORY(Area.MEMBERS, 108, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** Changes to somebody a guardian does not look after. */
    MEMBER_CHANGES_NOT_YOURS(Area.MEMBERS, 109, HttpStatus.FORBIDDEN, "You may only see the members you look after"),

    /** A change being acknowledged that is gone. */
    PROFILE_FIELD_CHANGE_NOT_HERE(Area.MEMBERS, 110, HttpStatus.NOT_FOUND, "That change is not here any more"),

    /** Somebody acted on by a guardian who does not look after them. */
    MEMBER_NOT_YOURS_TO_LOOK_AFTER(Area.MEMBERS, 111, HttpStatus.FORBIDDEN, "You do not look after this member"),

    /** A looked-after member whose profile was asked for and who is gone. */
    MEMBER_NOT_HERE_ON_MANAGED_PROFILE(Area.MEMBERS, 112, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A looked-after member who went between the profile being opened and the answers being written. */
    MEMBER_NOT_HERE_ON_MANAGED_PROFILE_CHANGE(Area.MEMBERS, 113, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A looked-after member whose equipment list was asked for and who is gone. */
    MEMBER_NOT_HERE_ON_MANAGED_EQUIPMENT(Area.MEMBERS, 114, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** An account being deleted that still administers a station. */
    ACCOUNT_STILL_ADMINISTERS_STATION(
            Area.MEMBERS,
            115,
            HttpStatus.BAD_REQUEST,
            "This account still administers a station, so it cannot be deleted. Hand the station on first"),

    /** A password set for a looked-after member that is shorter than this instance takes. */
    MANAGED_PASSWORD_TOO_SHORT(Area.MEMBERS, 116, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_TOO_SHORT),

    /** A password set for a looked-after member that is known to have leaked. */
    MANAGED_PASSWORD_BREACHED(Area.MEMBERS, 117, HttpStatus.BAD_REQUEST, Sentences.PASSWORD_BREACHED),

    /** A password set for a looked-after member at an instance that signs in without passwords. */
    MANAGED_PASSWORD_NOT_TAKEN(
            Area.MEMBERS,
            118,
            HttpStatus.FORBIDDEN,
            "This instance signs in without passwords, so none was saved for this member"),

    /** A password set for a looked-after member who has an address and sets their own. */
    MANAGED_MEMBER_SETS_THEIR_OWN_PASSWORD(
            Area.MEMBERS,
            119,
            HttpStatus.FORBIDDEN,
            "This member has an address of their own and sets their own password"),

    /** Asking to set an instance up far more often than a person could. */
    SETUP_TOO_OFTEN(Area.INSTALLATION, 1, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A language asked for in a path that is not written the way a language is. */
    SETTINGS_LOCALE_NOT_A_LANGUAGE(Area.SYSTEM, 1, HttpStatus.BAD_REQUEST, Sentences.LOCALE_NOT_GOOD),

    /** A language whose folder of texts would sit outside the one the instance keeps them in. */
    SETTINGS_LOCALE_OUT_OF_PLACE(Area.SYSTEM, 2, HttpStatus.BAD_REQUEST, Sentences.LOCALE_NOT_GOOD),

    /** The same check where the folder is resolved, for a caller that came past the first one. */
    SETTINGS_LOCALE_FOLDER_OUT_OF_PLACE(Area.SYSTEM, 3, HttpStatus.BAD_REQUEST, Sentences.LOCALE_NOT_GOOD),

    /** A setting given a number outside the range it allows. */
    SETTING_OUT_OF_RANGE(
            Area.SYSTEM,
            4,
            HttpStatus.BAD_REQUEST,
            "That setting was given a number outside what it allows, so nothing was saved"),

    /** A language chosen for outgoing mail that no mail is written in here. */
    NO_MAIL_WRITTEN_IN_THAT_LANGUAGE(
            Area.SYSTEM,
            5,
            HttpStatus.BAD_REQUEST,
            "No mail is written in that language on this instance, so nothing was saved"),

    /** A session on an untrusted device set to last longer than one on a trusted device. */
    UNTRUSTED_SESSION_OUTLASTS_TRUSTED(
            Area.SYSTEM,
            6,
            HttpStatus.BAD_REQUEST,
            "An untrusted device may not keep a session longer than a trusted one, so nothing was saved"),

    /** A fresh pepper asked for where one is already set, which would lock every session out. */
    TOKEN_PEPPER_ALREADY_SET(
            Area.SYSTEM,
            7,
            HttpStatus.BAD_REQUEST,
            "auth.tokenPepper is already set, and it is not replaced from here"),

    /** The password leak check saved without the address of the service that answers it. */
    PASSWORD_LEAK_CHECK_NEEDS_AN_ADDRESS(
            Area.SYSTEM,
            8,
            HttpStatus.BAD_REQUEST,
            "Give the address of the service that checks passwords against known leaks"),

    /** A fresh second-factor key asked for where one is already set. */
    TWO_FACTOR_SECRET_KEY_ALREADY_SET(
            Area.SYSTEM,
            9,
            HttpStatus.BAD_REQUEST,
            "The second-factor secret key is already set, and it is not replaced from here"),

    /** Authenticator settings saved without the name an authenticator app would show. */
    AUTHENTICATOR_NEEDS_AN_ISSUER(
            Area.SYSTEM,
            10,
            HttpStatus.BAD_REQUEST,
            "Give the name an authenticator app should show for this instance, so nothing was saved"),

    /** An authenticator algorithm that is not one of the three an authenticator app can do. */
    AUTHENTICATOR_ALGORITHM_UNKNOWN(
            Area.SYSTEM,
            11,
            HttpStatus.BAD_REQUEST,
            "The algorithm has to be SHA1, SHA256 or SHA512, so nothing was saved"),

    /** A security key attestation setting that is not one of the three the standard has. */
    SECURITY_KEY_ATTESTATION_UNKNOWN(
            Area.SYSTEM,
            12,
            HttpStatus.BAD_REQUEST,
            "Attestation has to be none, indirect or direct, so nothing was saved"),

    /** A test mail asked for on an instance that has set up nothing to send it with. */
    INSTANCE_HAS_NO_MAIL_PROVIDER(
            Area.SYSTEM, 13, HttpStatus.BAD_REQUEST, "This instance has no mail provider set up, so nothing was sent"),

    /** A place in the instance list of mail providers that is not a number. */
    INSTANCE_MAIL_PROVIDER_POSITION_NOT_A_NUMBER(
            Area.SYSTEM, 14, HttpStatus.BAD_REQUEST, "That is not a place in the list of mail providers"),

    /** A block lifted for a kind of mail provider this instance does not know. */
    MAIL_PROVIDER_KIND_UNKNOWN(
            Area.SYSTEM, 15, HttpStatus.BAD_REQUEST, "That is not a mail provider this instance knows"),

    /** A level the log is to be kept at that is not one of the levels there are. */
    LOG_LEVEL_UNKNOWN(Area.SYSTEM, 16, HttpStatus.BAD_REQUEST, "That is not a level the log can be kept at"),

    /** An empty list of mail providers saved over the instance list, which is how post stops silently. */
    INSTANCE_MAIL_PROVIDER_LIST_EMPTY(
            Area.SYSTEM,
            17,
            HttpStatus.BAD_REQUEST,
            "To stop sending, clear the mail settings rather than saving an empty list, so nothing was changed"),

    /** A legal document uploaded in a shape that could not be turned into text. */
    LEGAL_DOCUMENT_NOT_READ(
            Area.SYSTEM,
            18,
            HttpStatus.BAD_REQUEST,
            "That file could not be read as a document, so nothing was imported"),

    /** A legal document imported with neither a file nor any text in the request. */
    LEGAL_DOCUMENT_NEEDS_TEXT(
            Area.SYSTEM,
            19,
            HttpStatus.BAD_REQUEST,
            "Give the document to import, either as a file or as text, so nothing was imported"),

    /** A kind of legal document named in a path that is not one this instance keeps. */
    LEGAL_DOCUMENT_KIND_UNKNOWN(
            Area.SYSTEM, 20, HttpStatus.BAD_REQUEST, "That is not a kind of legal document kept here"),

    /** The figures for one endpoint asked for without saying which endpoint. */
    ENDPOINT_NOT_NAMED(
            Area.SYSTEM, 21, HttpStatus.BAD_REQUEST, "Name the method and the path of the endpoint you want"),

    /** A table written to in the data tracking inspector that it keeps no record of. */
    TRACKED_TABLE_NOT_HERE(Area.SYSTEM, 22, HttpStatus.NOT_FOUND, Sentences.TRACKED_TABLE_NOT_HERE),

    /** A table whose columns were to be checked and that data tracking keeps no record of. */
    TRACKED_TABLE_NOT_HERE_ON_CHECK(Area.SYSTEM, 23, HttpStatus.NOT_FOUND, Sentences.TRACKED_TABLE_NOT_HERE),

    /** Installer answers kept without a single answer in them. */
    INSTALL_ANSWERS_MISSING(
            Area.SYSTEM, 24, HttpStatus.BAD_REQUEST, "The installer sent no answers to keep, so nothing was kept"),

    /** An install code the installer presented that has run out or was never handed out. */
    INSTALL_CODE_NOT_GOOD(
            Area.SYSTEM, 25, HttpStatus.NOT_FOUND, "That code is not one the installer can still fetch answers with"),

    /** A problem report sent without anything written in it. */
    PROBLEM_REPORT_NEEDS_A_MESSAGE(
            Area.SYSTEM,
            26,
            HttpStatus.BAD_REQUEST,
            "Write what went wrong before sending the report, so nothing was sent"),

    /** A problem report whose picture was asked for and that is not here. */
    PROBLEM_REPORT_NOT_HERE(Area.SYSTEM, 27, HttpStatus.NOT_FOUND, "That problem report is not here any more"),

    /** A problem report that was written without a picture. */
    PROBLEM_REPORT_HAS_NO_PICTURE(
            Area.SYSTEM, 28, HttpStatus.NOT_FOUND, "That problem report was written without a picture"),

    /** A problem report that names a picture the store no longer holds. */
    PROBLEM_REPORT_PICTURE_NOT_HERE(
            Area.SYSTEM, 29, HttpStatus.NOT_FOUND, "The picture that went with that report is not here any more"),

    /** A problem in the instance list acknowledged after it had already gone from it. */
    PROBLEM_NOT_HERE(Area.SYSTEM, 30, HttpStatus.NOT_FOUND, "That problem is not here any more"),

    /**
     * A sitemap asked for a station that is not here, or that puts nothing in public. One code
     * deliberately: the two are told apart by nobody but a stranger counting stations, and this
     * answer is public.
     */
    SITEMAP_NOT_HERE(Area.SYSTEM, 31, HttpStatus.NOT_FOUND, "There is no sitemap here"),

    /** A table of rows and columns to be read where the upload carried no file. */
    CSV_UPLOAD_MISSING_FILE(Area.SYSTEM, 32, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** An upload that could not be read as a table of rows and columns. */
    CSV_NOT_READ(Area.SYSTEM, 33, HttpStatus.BAD_REQUEST, "That file could not be read as a table of rows and columns"),

    /**
     * A station's files that could not be carried to the storage it asked for.
     *
     * <p>The cause is deliberately kept out of the sentence and left in the log. Carrying files
     * over opens a connection to an address the request itself names, so saying why it failed
     * would answer "is anything listening there" for any address somebody cares to try.
     */
    STATION_STORAGE_MOVE_NOT_DONE(Area.STORAGE, 1, HttpStatus.BAD_REQUEST, Sentences.STORAGE_MOVE_NOT_DONE),

    /** A reachability test asked for at a station that keeps no storage of its own. */
    STATION_KEEPS_NO_STORAGE_OF_ITS_OWN(
            Area.STORAGE, 2, HttpStatus.BAD_REQUEST, "This station keeps no storage of its own to test"),

    /** Storage settings reached by a session that stands for no station. */
    NO_STATION_CHOSEN_FOR_STORAGE(Area.STORAGE, 3, HttpStatus.FORBIDDEN, Sentences.NO_STATION_CHOSEN),

    /** A session naming a station that is not here any more. */
    STORAGE_STATION_NOT_HERE(Area.STORAGE, 4, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_HERE),

    /** A change to a station's storage made by a session that stands for no account. */
    NO_ACCOUNT_BEHIND_STORAGE_CHANGE(Area.STORAGE, 5, HttpStatus.FORBIDDEN, Sentences.SESSION_HAS_NO_ACCOUNT),

    /** A station asking to move onto its association's storage while it belongs to no association. */
    STATION_ANSWERS_TO_NO_ASSOCIATION(
            Area.STORAGE, 6, HttpStatus.BAD_REQUEST, "This station belongs to no association, so nothing was changed"),

    /** An association that does not keep storage for the stations in it. */
    ASSOCIATION_KEEPS_NO_STORAGE_FOR_STATIONS(
            Area.STORAGE,
            7,
            HttpStatus.BAD_REQUEST,
            "This association does not keep storage for its stations, so nothing was changed"),

    /** An association that keeps storage for its stations but has named none of its own. */
    ASSOCIATION_KEEPS_NO_STORAGE_OF_ITS_OWN(
            Area.STORAGE,
            8,
            HttpStatus.BAD_REQUEST,
            "This association keeps no storage of its own, so nothing was changed"),

    /** A station picking its own storage where its association has kept that decision. */
    ASSOCIATION_DECIDES_WHERE_FILES_ARE_KEPT(
            Area.STORAGE,
            9,
            HttpStatus.FORBIDDEN,
            "This station's association decides where its files are kept, so nothing was changed"),

    /** Storage named at an address this instance is not allowed to open a connection to. */
    STORAGE_ADDRESS_NOT_ALLOWED(
            Area.STORAGE, 10, HttpStatus.BAD_REQUEST, "That is not an address this instance may reach"),

    /** Object storage saved without the pair of keys that opens it. */
    STORAGE_KEYS_MISSING(
            Area.STORAGE,
            11,
            HttpStatus.BAD_REQUEST,
            "Give the access key and the secret key for that storage, so nothing was saved"),

    /** A shared folder saved without the name and password that open it. */
    STORAGE_SIGN_IN_MISSING(
            Area.STORAGE,
            12,
            HttpStatus.BAD_REQUEST,
            "Give the user name and the password for that storage, so nothing was saved"),

    /** Storage reached over a file transfer connection given both a password and a key, or neither. */
    STORAGE_SIGN_IN_AMBIGUOUS(
            Area.STORAGE,
            13,
            HttpStatus.BAD_REQUEST,
            "Give either a password or a private key for that storage, and only one of the two"),

    /** The instance storage changed without saying what to change it to. */
    INSTANCE_STORAGE_TARGET_MISSING(
            Area.STORAGE, 14, HttpStatus.BAD_REQUEST, "Say where the files are to be kept, so nothing was changed"),

    /**
     * The instance files that could not be carried to the storage asked for.
     *
     * <p>Redacted for the same reason as {@link #STATION_STORAGE_MOVE_NOT_DONE}: the address is
     * the caller's own, so a cause would say what answers at it.
     */
    INSTANCE_STORAGE_MOVE_NOT_DONE(Area.STORAGE, 15, HttpStatus.BAD_REQUEST, Sentences.STORAGE_MOVE_NOT_DONE),

    /** A change to the instance storage made by a session that stands for no account. */
    NO_ACCOUNT_BEHIND_INSTANCE_STORAGE_CHANGE(Area.STORAGE, 16, HttpStatus.FORBIDDEN, Sentences.SESSION_HAS_NO_ACCOUNT),

    /**
     * A move of the instance files that broke while it was being committed.
     *
     * <p>Ours rather than the operator's, unlike the other move refusals: the files had already
     * been prepared and the commit is where it broke, so the move was taken back. The cause stays
     * in the log for the same reason {@link #INSTANCE_STORAGE_MOVE_NOT_DONE} keeps it there.
     */
    INSTANCE_STORAGE_MOVE_TAKEN_BACK(
            Area.STORAGE,
            17,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The move broke part way through and was taken back, so the files are still where they were. "
                    + "The reason is in the instance log"),

    /** Traffic figures for the whole instance asked for without one end of the stretch of time. */
    TRAFFIC_SPAN_MISSING(Area.TRAFFIC, 1, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_MISSING),

    /** One end of the stretch of time that cannot be read as a date and a time. */
    TRAFFIC_SPAN_NOT_A_TIME(Area.TRAFFIC, 2, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_NOT_A_TIME),

    /** A station narrowed down to by something that is not a whole number. */
    TRAFFIC_NUMBER_NOT_A_NUMBER(Area.TRAFFIC, 3, HttpStatus.BAD_REQUEST, "That has to be a whole number"),

    /** A kind of request to count that is not one of the kinds counted. */
    TRAFFIC_KIND_UNKNOWN(Area.TRAFFIC, 4, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_KIND_UNKNOWN),

    /** A stretch of time for the instance figures that ends before it starts. */
    TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS(
            Area.TRAFFIC, 5, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS),

    /** A station's own traffic figures asked for without one end of the stretch of time. */
    STATION_TRAFFIC_SPAN_MISSING(Area.TRAFFIC, 6, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_MISSING),

    /** One end of a station's stretch of time that cannot be read as a date and a time. */
    STATION_TRAFFIC_SPAN_NOT_A_TIME(Area.TRAFFIC, 7, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_NOT_A_TIME),

    /** A kind of request to count at a station that is not one of the kinds counted. */
    STATION_TRAFFIC_KIND_UNKNOWN(Area.TRAFFIC, 8, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_KIND_UNKNOWN),

    /** A station's traffic figures asked for by a session that stands for no station. */
    NO_STATION_CHOSEN_FOR_TRAFFIC(Area.TRAFFIC, 9, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** A stretch of time for a station's figures that ends before it starts. */
    STATION_TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS(
            Area.TRAFFIC, 10, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS),

    /** A piece of the map asked for without saying which piece. */
    MAP_TILE_NUMBER_MISSING(Area.MAPS, 1, HttpStatus.BAD_REQUEST, "Name the piece of the map you want"),

    /** A piece of the map tried out by an administrator and named by something that is not a number. */
    MAP_TILE_NUMBER_NOT_A_NUMBER(Area.MAPS, 2, HttpStatus.BAD_REQUEST, Sentences.MAP_TILE_NOT_A_NUMBER),

    /** A piece of the map asked for on a public page and named by something that is not a number. */
    PUBLIC_MAP_TILE_NUMBER_NOT_A_NUMBER(Area.MAPS, 3, HttpStatus.BAD_REQUEST, Sentences.MAP_TILE_NOT_A_NUMBER),

    /**
     * A delivery report from a mail provider whose key opens nothing, or whose signature does not
     * match what it carries. One code deliberately: two would tell whoever is trying the addresses
     * which of the two they got right.
     */
    MAIL_REPORT_NOT_TAKEN(Area.MAIL, 1, HttpStatus.NOT_FOUND, "Nothing here takes that report"),

    /** Consent written down without saying which version of the terms was agreed to. */
    CONSENT_VERSION_MISSING(
            Area.LEGAL,
            1,
            HttpStatus.BAD_REQUEST,
            "Say which version of the terms was agreed to, so nothing was written down"),

    /**
     * A feed link that opens nothing, or that stands for a member who has since gone. One code
     * deliberately: two would tell somebody trying links which of them are real.
     */
    FEED_LINK_NOT_GOOD(Area.FEED, 1, HttpStatus.NOT_FOUND, "That feed link does not lead anywhere any more"),

    /** The station behind a calendar feed, which has gone between the link and the reading of it. */
    FEED_STATION_NOT_HERE(Area.FEED, 2, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /**
     * A lost property entry that is not here, or that is at another station. One code deliberately:
     * the two answer alike so that a feed link cannot be used to count what other stations hold.
     */
    FEED_ITEM_NOT_HERE(Area.FEED, 3, HttpStatus.NOT_FOUND, "That entry in lost and found is not here any more"),

    /** A picture of a lost property entry that the store no longer holds. */
    FEED_ITEM_PICTURE_NOT_HERE(Area.FEED, 4, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** The station behind a notification feed, which has gone between the link and the reading of it. */
    FEED_STATION_NOT_HERE_FOR_NOTIFICATIONS(Area.FEED, 5, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A feed that could not be written out once its entries had been gathered. */
    FEED_NOT_BUILT(
            Area.FEED,
            6,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The feed could not be put together. Trying again may work; if it keeps happening, please report it"),

    /** The feed subscriptions of a station read by a session that stands for no station. */
    NO_STATION_CHOSEN_FOR_FEED_USE(Area.FEED, 7, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** A sign-in vouched for by another device that arrived without the claim to spend. */
    DEVICE_SIGN_IN_CLAIM_MISSING(Area.PASSKEYS, 1, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A sign-in vouched for by another device that did not end in a session. One code and one
     * sentence for a claim nobody ever handed out, a claim already spent, and an account the
     * sign-in was then refused for: telling them apart would say whether a guessed claim was real
     * and what state the account behind it is in.
     */
    DEVICE_SIGN_IN_NOT_GRANTED(
            Area.PASSKEYS,
            2,
            HttpStatus.UNAUTHORIZED,
            "That sign-in could not be completed. Ask the other device again"),

    /** A device asking whether it has been let in yet without saying which request it is. */
    DEVICE_POLL_SECRET_MISSING(Area.PASSKEYS, 3, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A passkey being made on a new device without the permission the handshake hands over. */
    DEVICE_ENROLMENT_TOKEN_MISSING(Area.PASSKEYS, 4, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A permission to make a passkey on a new device that is unknown, has run out or has already
     * been spent. One code deliberately: telling them apart would say which guesses were once real.
     */
    DEVICE_ENROLMENT_NOT_BEGUN(Area.PASSKEYS, 5, HttpStatus.UNAUTHORIZED, Sentences.PASSKEY_ENROLMENT_REFUSED),

    /** A passkey being finished on a new device with one of the three parts left out. */
    DEVICE_ENROLMENT_DETAILS_MISSING(Area.PASSKEYS, 6, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A passkey on a new device that the browser's answer did not complete. One code for a spent
     * permission, a challenge that does not belong to it and an answer that did not verify.
     */
    DEVICE_ENROLMENT_NOT_FINISHED(Area.PASSKEYS, 7, HttpStatus.UNAUTHORIZED, Sentences.PASSKEY_ENROLMENT_REFUSED),

    /** Reading whose account an enrolment link is for, without the link. */
    ENROLMENT_LINK_TOKEN_MISSING(Area.PASSKEYS, 8, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * An enrolment link that names nobody. One code for unknown, run out and already spent, because
     * this is the one place that would otherwise say a name, and guessing must earn nothing.
     */
    ENROLMENT_LINK_UNKNOWN(Area.PASSKEYS, 9, HttpStatus.NOT_FOUND, Sentences.ENROLMENT_LINK_NOT_GOOD),

    /** A passkey being started from an enrolment link that the request did not carry. */
    ENROLMENT_LINK_TOKEN_MISSING_ON_BEGIN(Area.PASSKEYS, 10, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** An enrolment link that could not begin a passkey, for every reason it cannot. */
    ENROLMENT_LINK_NOT_BEGUN(Area.PASSKEYS, 11, HttpStatus.UNAUTHORIZED, Sentences.PASSKEY_ENROLMENT_REFUSED),

    /** A passkey being finished from an enrolment link with one of the three parts left out. */
    ENROLMENT_LINK_DETAILS_MISSING(Area.PASSKEYS, 12, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** An enrolment link whose passkey the browser's answer did not complete. */
    ENROLMENT_LINK_NOT_FINISHED(Area.PASSKEYS, 13, HttpStatus.UNAUTHORIZED, Sentences.PASSKEY_ENROLMENT_REFUSED),

    /** Reading what a device is asking for without typing the code it is showing. */
    DEVICE_CODE_MISSING_ON_LOOKUP(Area.PASSKEYS, 14, HttpStatus.BAD_REQUEST, Sentences.DEVICE_CODE_MISSING),

    /**
     * A device code that is not open, or is open for somebody this reader does not answer for. One
     * code and one sentence deliberately: a wrong code and somebody else's code have always
     * answered alike, and splitting them would turn this into a way of asking whether a code exists.
     */
    DEVICE_CODE_NOT_YOURS_ON_LOOKUP(Area.PASSKEYS, 15, HttpStatus.NOT_FOUND, Sentences.DEVICE_CODE_NOT_GOOD),

    /** Confirming for somebody else something that is not a sign-in. */
    APPROVAL_ONLY_FOR_SIGN_IN(
            Area.PASSKEYS, 16, HttpStatus.FORBIDDEN, "Only a sign-in can be confirmed for somebody else"),

    /** Signing somebody in who is not in this reader's care. */
    APPROVAL_MEMBER_NOT_YOURS(Area.PASSKEYS, 17, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** Confirming what a device is asking for without typing the code it is showing. */
    DEVICE_CODE_MISSING_ON_APPROVAL(Area.PASSKEYS, 18, HttpStatus.BAD_REQUEST, Sentences.DEVICE_CODE_MISSING),

    /**
     * A device code being confirmed that is not open, or is open for somebody this reader does not
     * answer for. One code and one sentence, for the same reason the lookup has one.
     */
    DEVICE_CODE_NOT_YOURS_ON_APPROVAL(Area.PASSKEYS, 19, HttpStatus.NOT_FOUND, Sentences.DEVICE_CODE_NOT_GOOD),

    /** A confirmation that named none of the numbers offered. */
    DEVICE_MATCH_NUMBER_MISSING(
            Area.PASSKEYS, 20, HttpStatus.BAD_REQUEST, "Choose the number the other device is showing"),

    /** A confirmation that named the wrong one of the numbers offered. */
    DEVICE_MATCH_NUMBER_WRONG(
            Area.PASSKEYS, 21, HttpStatus.CONFLICT, "That is not the number the other device is showing"),

    /** A device request that ran out or was answered elsewhere between being read and confirmed. */
    DEVICE_APPROVAL_NOT_TAKEN(
            Area.PASSKEYS,
            22,
            HttpStatus.NOT_FOUND,
            "That request could not be confirmed any more, so nothing was granted"),

    /** Anything to do with passkeys on an instance that has switched them off. */
    PASSKEYS_SWITCHED_OFF(Area.PASSKEYS, 23, HttpStatus.FORBIDDEN, "Passkeys are switched off on this instance"),

    /** A passwordless sign-in finished without the challenge or the browser's answer. */
    PASSKEY_SIGN_IN_DETAILS_MISSING(Area.PASSKEYS, 24, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A passwordless sign-in that did not work, for every reason it does not work: an unknown
     * passkey, a signature that did not verify, a passkey that may not sign in, an address nobody
     * has confirmed. One code and one sentence deliberately, because anybody grinding this endpoint
     * must be told the same thing every time or it becomes a list of who has a passkey here.
     */
    PASSKEY_SIGN_IN_REFUSED(
            Area.PASSKEYS,
            25,
            HttpStatus.UNAUTHORIZED,
            "Signing in with that passkey did not work. Try another way in"),

    /** A passkey being made for a session whose account went in between. */
    ACCOUNT_NOT_HERE_ON_PASSKEY_CREATION(Area.PASSKEYS, 26, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** A passkey being finished without the challenge or the browser's answer. */
    PASSKEY_CREATION_DETAILS_MISSING(Area.PASSKEYS, 27, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A passkey the browser's answer did not complete, so none was saved. */
    PASSKEY_NOT_CREATED(
            Area.PASSKEYS, 28, HttpStatus.BAD_REQUEST, "That passkey could not be created, so none was saved"),

    /** A passkey that went between the list being drawn and the new name being given. */
    PASSKEY_NOT_HERE_ON_RENAME(Area.PASSKEYS, 29, HttpStatus.NOT_FOUND, Sentences.PASSKEY_NOT_HERE),

    /** A passkey that was already gone when its removal was asked for. */
    PASSKEY_NOT_HERE_ON_REMOVAL(Area.PASSKEYS, 30, HttpStatus.NOT_FOUND, Sentences.PASSKEY_NOT_HERE),

    /** The last passkey on an account that has no password to fall back on. */
    LAST_WAY_INTO_ACCOUNT(
            Area.PASSKEYS,
            31,
            HttpStatus.CONFLICT,
            "This is the only way into the account, so it was kept. Be onboarded again for a new passkey first"),

    /** Switching password sign-in off where the instance does not allow that at all. */
    PASSWORD_SIGN_IN_LOCKED_BY_INSTANCE(
            Area.PASSKEYS,
            32,
            HttpStatus.FORBIDDEN,
            "This instance does not allow switching password sign-in off, so nothing was changed"),

    /** Switching password sign-in off on an account no reset mail could reach. */
    PASSWORD_SIGN_IN_NEEDS_REACHABLE_ADDRESS(
            Area.PASSKEYS,
            33,
            HttpStatus.CONFLICT,
            "Switching password sign-in off needs an address a reset mail can reach, so nothing was changed"),

    /** Switching password sign-in off before any passkey has been shown to work. */
    PASSWORD_SIGN_IN_NEEDS_TRIED_PASSKEY(
            Area.PASSKEYS,
            34,
            HttpStatus.CONFLICT,
            "Switching password sign-in off needs a passkey that has signed in once, so nothing was changed"),

    /** Switching password sign-in on an account that holds no password to switch. */
    ACCOUNT_HOLDS_NO_PASSWORD_ON_SWITCH(Area.PASSKEYS, 35, HttpStatus.CONFLICT, Sentences.ACCOUNT_HOLDS_NO_PASSWORD),

    /** An answer to the passkey offer that is neither of the two the offer takes. */
    PASSKEY_OFFER_ANSWER_UNKNOWN(Area.PASSKEYS, 36, HttpStatus.BAD_REQUEST, "That is not an answer this offer takes"),

    /** A passkey being tried out without the challenge or the browser's answer. */
    PASSKEY_TRIAL_DETAILS_MISSING(Area.PASSKEYS, 37, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** Retiring the password of an account that holds none. */
    ACCOUNT_HOLDS_NO_PASSWORD_ON_RETIRE(Area.PASSKEYS, 38, HttpStatus.CONFLICT, Sentences.ACCOUNT_HOLDS_NO_PASSWORD),

    /** Retiring a password before any passkey on that account has been shown to work. */
    NO_TRIED_PASSKEY_ON_RETIRE(
            Area.PASSKEYS,
            39,
            HttpStatus.CONFLICT,
            "No passkey of that account has signed in yet, so the password was kept"),

    /** A passkey mode named that this instance does not have. */
    PASSKEY_MODE_UNKNOWN(Area.PASSKEYS, 40, HttpStatus.BAD_REQUEST, "That is not a passkey mode this instance knows"),

    /** Going passwordless on an instance whose mail has never been shown to work. */
    PASSWORDLESS_NEEDS_WORKING_MAIL(
            Area.PASSKEYS,
            41,
            HttpStatus.CONFLICT,
            "Going passwordless needs working mail, proven by a test mail that went out"),

    /** Lowering the passkey mode while accounts would be left with no way in. */
    PASSWORDLESS_ACCOUNTS_DEPEND(
            Area.PASSKEYS,
            42,
            HttpStatus.CONFLICT,
            "Some accounts would have no way in without a passkey, so the mode was left as it was"),

    /** Setting up an app code on an account that already has a second factor. */
    ALREADY_ENROLLED_ON_TOTP_SETUP(
            Area.TWO_FACTOR, 1, HttpStatus.BAD_REQUEST, "A second factor is already set up on this account"),

    /** An app code being confirmed without the secret, the code or the recovery codes. */
    TOTP_CONFIRMATION_DETAILS_MISSING(Area.TWO_FACTOR, 2, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** An app code that did not match while it was being set up. */
    TOTP_SETUP_CODE_WRONG(Area.TWO_FACTOR, 3, HttpStatus.BAD_REQUEST, Sentences.VERIFICATION_CODE_WRONG),

    /** Removing an app code from an account that has none set up. */
    NO_TOTP_TO_REMOVE(
            Area.TWO_FACTOR, 4, HttpStatus.BAD_REQUEST, "There is no app code set up on this account to remove"),

    /** New recovery codes asked for on an account with no second factor. */
    NOT_ENROLLED_ON_BACKUP_CODES(Area.TWO_FACTOR, 5, HttpStatus.BAD_REQUEST, Sentences.NO_SECOND_FACTOR_SET_UP),

    /** A second factor being answered without saying which sign-in it is for, or with what. */
    TWO_FACTOR_CHECK_DETAILS_MISSING(Area.TWO_FACTOR, 6, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A sign-in waiting on a second factor that is unknown, has run out, or was never waiting on
     * one. One code and one sentence deliberately: telling them apart would say whether a guessed
     * half-finished sign-in was ever real, which is exactly what this step withholds.
     */
    SIGN_IN_NOT_WAITING_ON_A_FACTOR(Area.TWO_FACTOR, 7, HttpStatus.UNAUTHORIZED, Sentences.SIGN_IN_NOT_WAITING),

    /**
     * A second factor that did not match at sign-in. One code for a wrong app code, a wrong
     * recovery code and a recovery code already spent, so no answer says which of the two was tried
     * or whether it had been used before.
     */
    TWO_FACTOR_CODE_WRONG(Area.TWO_FACTOR, 8, HttpStatus.UNAUTHORIZED, Sentences.VERIFICATION_CODE_WRONG),

    /** Withdrawing trust from a device that is not one this account trusts. */
    TRUSTED_DEVICE_NOT_HERE(
            Area.TWO_FACTOR, 9, HttpStatus.BAD_REQUEST, "That device is not one this account trusts any more"),

    /** Proving who you are again without saying with what, or with which proof. */
    STEP_UP_DETAILS_MISSING(Area.TWO_FACTOR, 10, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** Proving a second factor again on an account that has none set up. */
    NOT_ENROLLED_ON_STEP_UP(Area.TWO_FACTOR, 11, HttpStatus.BAD_REQUEST, Sentences.NO_SECOND_FACTOR_SET_UP),

    /** A second factor that did not match while proving who you are again. */
    STEP_UP_CODE_WRONG(Area.TWO_FACTOR, 12, HttpStatus.UNAUTHORIZED, Sentences.VERIFICATION_CODE_WRONG),

    /** A security key being set up without the challenge or the browser's answer. */
    SECURITY_KEY_SETUP_DETAILS_MISSING(Area.TWO_FACTOR, 13, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A security key the browser's answer did not set up, so nothing was saved. */
    SECURITY_KEY_NOT_REGISTERED(
            Area.TWO_FACTOR, 14, HttpStatus.BAD_REQUEST, "That security key could not be set up, so nothing was saved"),

    /** A second factor that was already gone when its removal was asked for. */
    FACTOR_NOT_HERE_ON_REMOVAL(Area.TWO_FACTOR, 15, HttpStatus.BAD_REQUEST, Sentences.FACTOR_NOT_HERE),

    /**
     * A second factor that could not take the new name. One code for a name the product will not
     * take and a factor that is gone or belongs to somebody else, so no answer says which.
     */
    FACTOR_NOT_RENAMED(
            Area.TWO_FACTOR,
            16,
            HttpStatus.BAD_REQUEST,
            "That name could not be given, so the second factor was left as it was"),

    /** A security key sign-in begun without saying which sign-in is waiting. */
    SECURITY_KEY_SIGN_IN_TOKEN_MISSING(Area.TWO_FACTOR, 17, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A security key sign-in finished with one of the three parts left out. */
    SECURITY_KEY_SIGN_IN_DETAILS_MISSING(Area.TWO_FACTOR, 18, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A security key that did not verify at sign-in. */
    SECURITY_KEY_SIGN_IN_REFUSED(Area.TWO_FACTOR, 19, HttpStatus.UNAUTHORIZED, Sentences.SECURITY_KEY_NOT_ACCEPTED),

    /** A security key proving who you are again, without the challenge or the browser's answer. */
    SECURITY_KEY_STEP_UP_DETAILS_MISSING(Area.TWO_FACTOR, 20, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A security key that did not verify while proving who you are again. */
    SECURITY_KEY_STEP_UP_REFUSED(Area.TWO_FACTOR, 21, HttpStatus.UNAUTHORIZED, Sentences.SECURITY_KEY_NOT_ACCEPTED),

    /**
     * A security key ceremony whose waiting sign-in is unknown, has run out, or was never waiting on
     * a factor. One code for all three, for the same reason the code path has one.
     */
    SIGN_IN_NOT_WAITING_ON_A_KEY(Area.TWO_FACTOR, 22, HttpStatus.UNAUTHORIZED, Sentences.SIGN_IN_NOT_WAITING),

    /** Asking another device to confirm where this account has no other device that could. */
    NO_OTHER_DEVICE_TO_CONFIRM(
            Area.TWO_FACTOR, 23, HttpStatus.FORBIDDEN, "No other device of this account could confirm this"),

    /** Asking whether the other device has confirmed yet without saying which request. */
    DEVICE_STEP_UP_POLL_SECRET_MISSING(Area.TWO_FACTOR, 24, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** Asking another device to confirm without saying what it would be confirming. */
    STEP_UP_CATEGORY_MISSING(Area.TWO_FACTOR, 25, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** Naming something to confirm that this instance does not ask anybody to confirm. */
    STEP_UP_CATEGORY_UNKNOWN(
            Area.TWO_FACTOR, 26, HttpStatus.BAD_REQUEST, "That is not something this instance asks you to confirm"),

    /** Confirming with a password that the request did not carry. */
    STEP_UP_PASSWORD_MISSING(Area.TWO_FACTOR, 27, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /**
     * A password offered where this account is asked for something stronger. It says no more than
     * the screen the reader is already looking at, which offers only the proofs the account has:
     * accepting a password here would let somebody holding a phished one walk past a second factor.
     */
    PASSWORD_IS_NOT_A_PROOF_HERE(
            Area.TWO_FACTOR, 28, HttpStatus.FORBIDDEN, "A password is not enough to confirm this on this account"),

    /** A password that did not match while confirming who you are. */
    STEP_UP_PASSWORD_WRONG(
            Area.TWO_FACTOR, 29, HttpStatus.UNAUTHORIZED, "That password was not right, so nothing was confirmed"),

    /**
     * A passkey offered to confirm on an account that holds none. Says no more than the screen the
     * reader is already looking at, which offers the passkey button only where there is a passkey.
     */
    NO_PASSKEY_TO_CONFIRM_WITH(
            Area.TWO_FACTOR, 30, HttpStatus.FORBIDDEN, "This account holds no passkey to confirm with"),

    /** A passkey confirming who you are, without the challenge or the browser's answer. */
    PASSKEY_STEP_UP_DETAILS_MISSING(Area.TWO_FACTOR, 31, HttpStatus.BAD_REQUEST, Sentences.REQUEST_INCOMPLETE),

    /** A passkey that did not verify while confirming who you are. */
    PASSKEY_STEP_UP_REFUSED(
            Area.TWO_FACTOR, 32, HttpStatus.UNAUTHORIZED, "That passkey did not confirm it, so nothing was confirmed"),

    /** A rule written for a kind of member this instance does not have. */
    USER_TYPE_UNKNOWN_ON_POLICY(
            Area.TWO_FACTOR, 33, HttpStatus.BAD_REQUEST, "That is not a kind of member this instance knows"),

    /** Managing a station's second-factor rules without a station chosen. */
    NO_STATION_CHOSEN_ON_POLICY(Area.TWO_FACTOR, 34, HttpStatus.FORBIDDEN, Sentences.NO_STATION_CHOSEN),

    /** An instance-wide rule that was already gone when its removal was asked for. */
    POLICY_NOT_HERE(Area.TWO_FACTOR, 35, HttpStatus.BAD_REQUEST, Sentences.POLICY_NOT_HERE),

    /**
     * A station rule that is gone, or belongs to another station. One code and one sentence
     * deliberately: splitting them would tell a station administrator that a rule with that number
     * exists on a station they cannot see.
     */
    POLICY_NOT_HERE_ON_STATION_DELETE(Area.TWO_FACTOR, 36, HttpStatus.BAD_REQUEST, Sentences.POLICY_NOT_HERE),

    /** A second factor an operator asked to clear that could not be cleared. */
    SECOND_FACTOR_NOT_RESET(Area.TWO_FACTOR, 37, HttpStatus.NOT_FOUND, Sentences.SECOND_FACTOR_NOT_RESET),

    /** Clearing a member's second factor without a station chosen. */
    NO_STATION_CHOSEN_ON_RESET(Area.TWO_FACTOR, 38, HttpStatus.FORBIDDEN, Sentences.NO_STATION_CHOSEN),

    /**
     * Somebody whose second factor a station administrator cannot clear. One code and one sentence
     * for a member of another station and an account that is not here at all, so this endpoint
     * cannot be used to find out which accounts exist.
     */
    MEMBER_NOT_YOURS_TO_RESET(
            Area.TWO_FACTOR, 39, HttpStatus.NOT_FOUND, "That member is not on your station, so nothing was changed"),

    /** A station administrator clearing the second factor of an instance administrator. */
    ADMIN_ONLY_RESET_BY_ADMIN(
            Area.TWO_FACTOR,
            40,
            HttpStatus.FORBIDDEN,
            "Only an instance administrator can reset this for another instance administrator"),

    /** A member's second factor a station administrator asked to clear that could not be cleared. */
    SECOND_FACTOR_NOT_RESET_ON_STATION(Area.TWO_FACTOR, 41, HttpStatus.NOT_FOUND, Sentences.SECOND_FACTOR_NOT_RESET),

    /** A page that went between the editor being opened and the save being asked for. */
    PAGE_NOT_HERE_ON_SAVE(Area.PAGES, 1, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A page whose contents were read and then refused for what they said. */
    PAGE_NOT_SAVED(Area.PAGES, 2, HttpStatus.BAD_REQUEST, "The page could not be saved"),

    /** A page that went before a copy of it could be made. */
    PAGE_NOT_HERE_ON_COPY(Area.PAGES, 3, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A page that was already gone when its deletion was asked for. */
    PAGE_NOT_HERE_ON_DELETE(Area.PAGES, 4, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A page that went before how far it reaches could be written. */
    PAGE_NOT_HERE_ON_VISIBILITY_CHANGE(Area.PAGES, 5, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A public address no page of that station sits at. */
    PUBLIC_PAGE_NOT_HERE(Area.PAGES, 6, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A public page that exists but has nothing drawn to serve. */
    PUBLIC_PAGE_NOT_RENDERED(Area.PAGES, 7, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A station whose public site has no page to open on. */
    PUBLIC_LANDING_PAGE_NOT_HERE(Area.PAGES, 8, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** Public pages at all, at a station that has switched them off. */
    PUBLIC_PAGES_SWITCHED_OFF(Area.PAGES, 9, HttpStatus.NOT_FOUND, Sentences.PAGE_NOT_HERE),

    /** A station that is not here, asked for by a public page address. */
    STATION_NOT_HERE_BEHIND_PUBLIC_PAGE(Area.PAGES, 10, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /**
     * A share link that leads nowhere, whether because no page answers to it or because the station
     * behind it has closed its public pages. One code deliberately: the reader holds a link and is
     * owed nothing about which of the two it was.
     */
    PAGE_LINK_UNKNOWN(Area.PAGES, 11, HttpStatus.NOT_FOUND, "No page is reached by this link"),

    /** An answer written on a paper that is no longer being sat. */
    QUIZ_ALREADY_HANDED_IN(
            Area.QUIZZES,
            1,
            HttpStatus.CONFLICT,
            "This paper has already been handed in, so the answer was not saved. "
                    + "Reload the page to see it as it stands"),

    /** A paper handed in that was not still being written, whether already in or out of time. */
    QUIZ_NOT_HANDED_IN(
            Area.QUIZZES,
            2,
            HttpStatus.CONFLICT,
            "This paper could not be handed in: it is already in, or the time for it has run out. "
                    + "Reload the page to see where it stands"),

    /** A paper that went between being handed in and being read back. */
    QUIZ_ATTEMPT_NOT_HERE_AFTER_HANDING_IN(Area.QUIZZES, 3, HttpStatus.NOT_FOUND, Sentences.QUIZ_ATTEMPT_NOT_HERE),

    /** A question that is gone, or belongs to another station's catalog. */
    QUIZ_QUESTION_NOT_HERE(Area.QUIZZES, 4, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** An attempt that is gone, asked for by a route that then checks whose test it was. */
    QUIZ_ATTEMPT_NOT_HERE(Area.QUIZZES, 5, HttpStatus.NOT_FOUND, Sentences.QUIZ_ATTEMPT_NOT_HERE),

    /** An attempt that is gone, asked for by the member sitting it. */
    QUIZ_ATTEMPT_NOT_HERE_FOR_MEMBER(Area.QUIZZES, 6, HttpStatus.NOT_FOUND, Sentences.QUIZ_ATTEMPT_NOT_HERE),

    /** A test attempt that belongs to somebody else. */
    QUIZ_ATTEMPT_NOT_YOURS(Area.QUIZZES, 7, HttpStatus.FORBIDDEN, "That attempt is somebody else's"),

    /** A catalog that went between the list being drawn and it being opened. */
    QUIZ_CATALOG_NOT_HERE(Area.QUIZZES, 8, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A catalog that went before the change to it could be written. */
    QUIZ_CATALOG_NOT_CHANGED(Area.QUIZZES, 9, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A catalog that went between being changed and being read back. */
    QUIZ_CATALOG_NOT_HERE_AFTER_CHANGE(Area.QUIZZES, 10, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A catalog that was already gone when its deletion was asked for. */
    QUIZ_CATALOG_NOT_DELETED(Area.QUIZZES, 11, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** A grouping of questions that went before the change to it could be written. */
    QUIZ_CATEGORY_NOT_CHANGED(Area.QUIZZES, 12, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATEGORY_NOT_HERE),

    /** A grouping of questions that was already gone when its deletion was asked for. */
    QUIZ_CATEGORY_NOT_DELETED(Area.QUIZZES, 13, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATEGORY_NOT_HERE),

    /** An example file asked for in a format there is no example in. */
    QUIZ_TEMPLATE_FORMAT_UNKNOWN(Area.QUIZZES, 14, HttpStatus.NOT_FOUND, "There is no example file in that format"),

    /** A test that went before the change to it could be written. */
    QUIZ_TEST_NOT_CHANGED(Area.QUIZZES, 15, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went between being changed and being read back. */
    QUIZ_TEST_NOT_HERE_AFTER_CHANGE(Area.QUIZZES, 16, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that was already gone when its deletion was asked for. */
    QUIZ_TEST_NOT_DELETED(Area.QUIZZES, 17, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went between being started and being read back. */
    QUIZ_TEST_NOT_HERE_AFTER_ACTIVATION(Area.QUIZZES, 18, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went before it could be closed. */
    QUIZ_TEST_NOT_CLOSED(Area.QUIZZES, 19, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A test that went between being closed and being read back. */
    QUIZ_TEST_NOT_HERE_AFTER_CLOSING(Area.QUIZZES, 20, HttpStatus.NOT_FOUND, Sentences.QUIZ_TEST_NOT_HERE),

    /** A catalog named in a public teaser link by something that is not a number. */
    PUBLIC_QUIZ_CATALOG_NOT_A_NUMBER(
            Area.QUIZZES, 21, HttpStatus.BAD_REQUEST, "The catalogs in that link must each be named by a number"),

    /**
     * A station named in a public teaser link by something that is not an identifier. It shares its
     * sentence with the miss below on purpose: the reader is a stranger on the open internet and is
     * owed nothing about whether a station answers to the identifier they hold.
     */
    PUBLIC_QUIZ_STATION_LINK_NOT_GOOD(
            Area.QUIZZES, 22, HttpStatus.BAD_REQUEST, Sentences.PUBLIC_QUIZ_STATION_NOT_REACHED),

    /** A public teaser link naming a station that is not here. */
    PUBLIC_QUIZ_STATION_NOT_HERE(Area.QUIZZES, 23, HttpStatus.NOT_FOUND, Sentences.PUBLIC_QUIZ_STATION_NOT_REACHED),

    /** A public teaser asking for a question without saying which catalogs to draw it from. */
    PUBLIC_QUIZ_CATALOGS_NOT_NAMED(Area.QUIZZES, 24, HttpStatus.BAD_REQUEST, Sentences.PUBLIC_QUIZ_CATALOGS_NOT_NAMED),

    /** A public teaser naming catalogs that come to nothing once read. */
    PUBLIC_QUIZ_CATALOGS_EMPTY(Area.QUIZZES, 25, HttpStatus.BAD_REQUEST, Sentences.PUBLIC_QUIZ_CATALOGS_NOT_NAMED),

    /** No public question in any of the catalogs a teaser asked for. */
    PUBLIC_QUIZ_NO_QUESTION_HERE(
            Area.QUIZZES, 26, HttpStatus.NOT_FOUND, "There is no question here to show from those catalogs"),

    /** A test whose questions or sections were being changed while it was being sat. */
    QUIZ_TEST_RUNNING_CANNOT_CHANGE(
            Area.QUIZZES, 27, HttpStatus.BAD_REQUEST, "This test is running, so nothing was changed"),

    /** An attempt reached for by a session that stands for no member of the station. */
    QUIZ_ATTEMPT_NEEDS_MEMBERSHIP(Area.QUIZZES, 28, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** A catalog written down without a name. */
    QUIZ_CATALOG_NEEDS_A_NAME(Area.QUIZZES, 29, HttpStatus.BAD_REQUEST, "A catalog needs a name, so nothing was saved"),

    /** A category written down without a name. */
    QUIZ_CATEGORY_NEEDS_A_NAME(
            Area.QUIZZES, 30, HttpStatus.BAD_REQUEST, "A category needs a name, so nothing was saved"),

    /** Training asked for on a catalog that is not handed out for training. */
    QUIZ_CATALOG_NOT_FOR_TRAINING(Area.QUIZZES, 31, HttpStatus.FORBIDDEN, "This catalog is not open for training"),

    /** A sheet sent to be read into questions with nothing in it. */
    QUIZ_SHEET_EMPTY(Area.QUIZZES, 32, HttpStatus.BAD_REQUEST, "The sheet arrived empty, so nothing was read from it"),

    /** A sheet sent to be read into questions without saying which column holds what. */
    QUIZ_SHEET_COLUMNS_NOT_MAPPED(
            Area.QUIZZES, 33, HttpStatus.BAD_REQUEST, "Say which column holds what, so nothing was read from it"),

    /** A note about a question that is gone, or belongs to another station's catalog. */
    QUIZ_QUESTION_NOTE_NOT_HERE(Area.QUIZZES, 34, HttpStatus.NOT_FOUND, Sentences.QUIZ_QUESTION_NOTE_NOT_HERE),

    /** A note about a question that was already gone when it was marked as dealt with. */
    QUIZ_QUESTION_NOTE_NOT_CLEARED(Area.QUIZZES, 35, HttpStatus.NOT_FOUND, Sentences.QUIZ_QUESTION_NOTE_NOT_HERE),

    /** A question written down without a title. */
    QUIZ_QUESTION_NEEDS_A_TITLE(
            Area.QUIZZES, 36, HttpStatus.BAD_REQUEST, "A question needs a title, so nothing was saved"),

    /** A question written down without saying what kind of question it is. */
    QUIZ_QUESTION_NEEDS_A_KIND(
            Area.QUIZZES, 37, HttpStatus.BAD_REQUEST, "A question needs a kind, so nothing was saved"),

    /** A question that went before the change to it could be written. */
    QUIZ_QUESTION_NOT_CHANGED(Area.QUIZZES, 38, HttpStatus.NOT_FOUND, Sentences.QUIZ_QUESTION_NOT_HERE_ON_WRITE),

    /** A question that went between being changed and being read back, with the change already in. */
    QUIZ_QUESTION_NOT_HERE_AFTER_CHANGE(
            Area.QUIZZES,
            39,
            HttpStatus.NOT_FOUND,
            "The change to that question was saved, but it could not be read back. "
                    + "Reload the page to see it as it stands"),

    /** A question that was already gone when its deletion was asked for. */
    QUIZ_QUESTION_NOT_DELETED(Area.QUIZZES, 40, HttpStatus.NOT_FOUND, Sentences.QUIZ_QUESTION_NOT_HERE_ON_WRITE),

    /** A picture asked for on a question that carries none. */
    QUIZ_QUESTION_PICTURE_NOT_HERE(Area.QUIZZES, 41, HttpStatus.NOT_FOUND, "That question has no picture"),

    /** A picture for a question sent without the picture in it. */
    QUIZ_PICTURE_UPLOAD_WITHOUT_FILE(Area.QUIZZES, 42, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A picture for a question sent in a format that is not taken here. */
    QUIZ_PICTURE_KIND_NOT_TAKEN(Area.QUIZZES, 43, HttpStatus.BAD_REQUEST, Sentences.KB_PICTURE_KIND_NOT_TAKEN),

    /** A picture for a question that could not be taken as it was sent. */
    QUIZ_PICTURE_NOT_SAVED(
            Area.QUIZZES,
            44,
            HttpStatus.BAD_REQUEST,
            "That picture could not be saved, so the question keeps the one it had"),

    /** A picture for a question that could not be worked through at all. */
    QUIZ_PICTURE_NOT_PROCESSED(Area.QUIZZES, 45, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A paper started by a session that stands for no member of the station. */
    QUIZ_ATTEMPT_NEEDS_MEMBERSHIP_TO_START(Area.QUIZZES, 46, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** A test started by somebody it is not open to, whether by time or by who may sit it. */
    QUIZ_TEST_NOT_OPEN_TO_YOU(
            Area.QUIZZES, 47, HttpStatus.FORBIDDEN, "This test is not open to you now, so no attempt was started"),

    /** A paper read back by a session that stands for no member of the station. */
    QUIZ_ATTEMPT_NEEDS_MEMBERSHIP_TO_READ(
            Area.QUIZZES, 48, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER_TO_READ),

    /** An answer on a paper that is gone, reached for by somebody marking it. */
    QUIZ_ANSWER_NOT_HERE(Area.QUIZZES, 49, HttpStatus.NOT_FOUND, "That answer is not here any more"),

    /** A mark given for an answer without saying how many points it is worth. */
    QUIZ_GRADE_NEEDS_POINTS(
            Area.QUIZZES, 50, HttpStatus.BAD_REQUEST, "Give the points for this answer, so no mark was saved"),

    /** A paper marked by a session that stands for no member of the station. */
    QUIZ_GRADING_NEEDS_MEMBERSHIP(Area.QUIZZES, 51, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** A paper that went between its marks being written and being read back, with the marks in. */
    QUIZ_ATTEMPT_NOT_HERE_AFTER_GRADING(
            Area.QUIZZES,
            52,
            HttpStatus.NOT_FOUND,
            "The marks were saved, but that attempt could not be read back. "
                    + "Reload the page to see it as it stands"),

    /** A test written down by a session that stands for no member of the station. */
    QUIZ_TEST_NEEDS_MEMBERSHIP(Area.QUIZZES, 53, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** A test written down without a title. */
    QUIZ_TEST_NEEDS_A_TITLE(Area.QUIZZES, 54, HttpStatus.BAD_REQUEST, "A test needs a title, so nothing was saved"),

    /** A test started that was not waiting to be started. */
    QUIZ_TEST_NOT_A_DRAFT(
            Area.QUIZZES, 55, HttpStatus.BAD_REQUEST, "This test has already been started, so nothing was changed"),

    /** Questions drawn again for a test that is being sat. */
    QUIZ_TEST_RUNNING_CANNOT_REDRAW(
            Area.QUIZZES, 56, HttpStatus.BAD_REQUEST, "This test is running, so its questions cannot be drawn again"),

    /** A test opened for somebody without saying who. */
    QUIZ_TEST_ACCESS_NEEDS_A_MEMBER(
            Area.QUIZZES, 57, HttpStatus.BAD_REQUEST, "Name the member to open this test for, so nothing was saved"),

    /** A test that could not be made into a question sheet. */
    QUIZ_TEST_PDF_NOT_MADE(
            Area.QUIZZES,
            58,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "That test could not be made into a PDF. Trying again may work"),

    /** A test that could not be made into an answer sheet. */
    QUIZ_TEST_SOLUTION_PDF_NOT_MADE(
            Area.QUIZZES,
            59,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The answer sheet could not be made into a PDF. Trying again may work"),

    /** A provider for generated questions written down without a key. */
    AI_PROVIDER_NEEDS_A_KEY(
            Area.QUIZZES, 60, HttpStatus.BAD_REQUEST, "Give the key for this provider, so nothing was saved"),

    /** A provider that turned the key or the request away when its models were asked for. */
    AI_MODELS_NOT_LISTED_KEY_NOT_GOOD(
            Area.QUIZZES, 61, HttpStatus.BAD_REQUEST, "The models could not be listed: check the key and the provider"),

    /** A provider that could not be reached at all when its models were asked for. */
    AI_MODELS_NOT_LISTED(
            Area.QUIZZES, 62, HttpStatus.BAD_REQUEST, "The models could not be listed. Trying again may work"),

    /** Answers asked to be generated without the question they belong to. */
    AI_GENERATION_NEEDS_A_QUESTION(
            Area.QUIZZES, 63, HttpStatus.BAD_REQUEST, "Give the question to work from, so nothing was generated"),

    /** Answers asked to be generated without the right answer to work around. */
    AI_GENERATION_NEEDS_THE_RIGHT_ANSWER(
            Area.QUIZZES, 64, HttpStatus.BAD_REQUEST, "Give the right answer to work from, so nothing was generated"),

    /** A provider that turned the key, the model or the request away while generating. */
    AI_GENERATION_REFUSED(
            Area.QUIZZES,
            65,
            HttpStatus.BAD_REQUEST,
            "Nothing could be generated from that: check the key, the model and what was asked for"),

    /** A provider that could not be reached at all while generating. */
    AI_GENERATION_FAILED(
            Area.QUIZZES, 66, HttpStatus.BAD_REQUEST, "Nothing could be generated this time. Trying again may work"),

    /** Questions asked to be generated without saying what to generate. */
    AI_GENERATION_NEEDS_ENTRIES(
            Area.QUIZZES, 67, HttpStatus.BAD_REQUEST, "Say what to generate and how much, so nothing was generated"),

    /** A generation whose results were already collected, or that another station started. */
    AI_GENERATION_NOT_HERE(
            Area.QUIZZES,
            68,
            HttpStatus.NOT_FOUND,
            "That generation is no longer here, so its questions cannot be collected"),

    /**
     * A catalog asked for by a paired instance that is not here, belongs to a third station or is
     * not among the ones shared with the asker. One code deliberately: telling the three apart
     * would let a partner count its way through a question bank it was never given.
     */
    REMOTE_QUIZ_CATALOG_NOT_SHARED(
            Area.QUIZZES, 69, HttpStatus.NOT_FOUND, "No catalog here is shared with you under that number"),

    /** A procedure nobody but the people it was handed to may open, read by somebody else. */
    PROCEDURE_NOT_PUBLIC(Area.PROCEDURES, 1, HttpStatus.FORBIDDEN, Sentences.PROCEDURE_NOT_YOURS),

    /** A procedure open to the station but not handed to this reader. */
    PROCEDURE_NOT_YOURS(Area.PROCEDURES, 2, HttpStatus.FORBIDDEN, Sentences.PROCEDURE_NOT_YOURS),

    /** A confirmation link for an application that has been used, has run out, or was never one. */
    STATION_APPLICATION_LINK_UNKNOWN(
            Area.STATIONS, 1, HttpStatus.NOT_FOUND, "That confirmation link is no longer good"),

    /** An application to join a station that is gone. */
    STATION_APPLICATION_NOT_HERE(Area.STATIONS, 2, HttpStatus.NOT_FOUND, Sentences.STATION_APPLICATION_NOT_HERE),

    /** An application that was gone or already answered when its acceptance was asked for. */
    STATION_APPLICATION_NOT_HERE_ON_ACCEPTANCE(
            Area.STATIONS, 3, HttpStatus.NOT_FOUND, Sentences.STATION_APPLICATION_NOT_HERE),

    /** An application that was gone or already answered when its denial was asked for. */
    STATION_APPLICATION_NOT_HERE_ON_DENIAL(
            Area.STATIONS, 4, HttpStatus.NOT_FOUND, Sentences.STATION_APPLICATION_NOT_HERE),

    /**
     * A public address another station on this instance already answers at. Named because a screen
     * has to tell this one refusal apart from every other refusal a settings form can get, and
     * matching on the English sentence to do it broke the moment anybody reworded it.
     */
    STATION_SLUG_TAKEN(Area.STATIONS, 5, HttpStatus.CONFLICT, "Another station is already reached at that address"),

    /** The station whose settings were opened, gone between signing in and reading them. */
    STATION_NOT_HERE_ON_MANAGE(Area.STATIONS, 6, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A change to a station's settings that would leave it without a name. */
    STATION_NEEDS_A_NAME_ON_CHANGE(Area.STATIONS, 7, HttpStatus.BAD_REQUEST, Sentences.STATION_NEEDS_A_NAME),

    /** A time zone chosen for a station that this instance cannot place. */
    STATION_TIME_ZONE_NOT_KNOWN(
            Area.STATIONS,
            8,
            HttpStatus.BAD_REQUEST,
            "That is not a time zone this instance knows, so nothing was saved"),

    /** A public address for a station that could not be taken, for a reason of its own. */
    STATION_ADDRESS_NOT_USABLE(
            Area.STATIONS, 9, HttpStatus.BAD_REQUEST, "That public address cannot be used, so nothing was saved"),

    /** A station that went between its settings being written and being read back. */
    STATION_NOT_HERE_AFTER_CHANGE(Area.STATIONS, 10, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A logo uploaded at a station whose cluster sets the logo for it. */
    LOGO_SET_BY_CLUSTER(
            Area.STATIONS,
            11,
            HttpStatus.BAD_REQUEST,
            "The cluster this station belongs to sets the logo, so nothing was saved"),

    /** A logo upload that arrived without a picture in it. */
    LOGO_UPLOAD_MISSING_FILE(Area.STATIONS, 12, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A logo larger than a station logo may be. */
    LOGO_TOO_LARGE(Area.STATIONS, 13, HttpStatus.BAD_REQUEST, "A logo may be at most 2 MB, so nothing was saved"),

    /** A logo in a kind of picture that is not taken here. */
    LOGO_KIND_NOT_TAKEN(
            Area.STATIONS,
            14,
            HttpStatus.BAD_REQUEST,
            "A logo has to be a PNG, JPEG, WebP or GIF picture, so nothing was saved"),

    /** A logo whose bytes could not be read off the upload. */
    LOGO_NOT_READ(Area.STATIONS, 15, HttpStatus.BAD_REQUEST, "That file could not be read, so nothing was saved"),

    /** A station whose logo was asked for by name and which is not here. */
    STATION_NOT_HERE_FOR_LOGO(Area.STATIONS, 16, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The times a station has asked its gathered mail to go out at, where it has asked for none. */
    NOTIFICATION_TIMES_NOT_SET(
            Area.STATIONS, 17, HttpStatus.NOT_FOUND, "This station has set no times of its own for notifications"),

    /** A time of day for notifications that cannot be read as one. */
    NOTIFICATION_TIME_NOT_A_TIME(
            Area.STATIONS, 18, HttpStatus.BAD_REQUEST, "That is not a time of day, so nothing was saved"),

    /** An empty list of mail providers saved over a station's own, which is how post stops silently. */
    MAIL_PROVIDER_LIST_EMPTY(
            Area.STATIONS,
            19,
            HttpStatus.BAD_REQUEST,
            "To stop sending, clear the mail settings rather than saving an empty list, so nothing was changed"),

    /** A place in the list of mail providers that is not a number. */
    MAIL_PROVIDER_POSITION_NOT_A_NUMBER(
            Area.STATIONS, 20, HttpStatus.BAD_REQUEST, "That is not a place in the list of mail providers"),

    /** A test mail asked for at a station that has set up no provider to send it. */
    NO_MAIL_PROVIDER_SET(
            Area.STATIONS, 21, HttpStatus.BAD_REQUEST, "This station has no mail provider set up, so nothing was sent"),

    /** The short way out of a station that has not in fact been moved to another instance. */
    STATION_NOT_MOVED(
            Area.STATIONS,
            22,
            HttpStatus.BAD_REQUEST,
            "This station has not been moved to another instance, so nothing was deleted"),

    /** A station handed over by a session that stands for no member of it. */
    NOT_A_MEMBER_ON_HANDOVER(Area.STATIONS, 23, HttpStatus.BAD_REQUEST, Sentences.NOT_A_STATION_MEMBER),

    /** A station handed over by somebody who does not own it. */
    ONLY_THE_OWNER_HANDS_OVER(
            Area.STATIONS, 24, HttpStatus.FORBIDDEN, "Only the owner of this station can hand it over"),

    /** A station handed to somebody who does not manage it. */
    NEW_OWNER_NOT_A_MANAGER(
            Area.STATIONS,
            25,
            HttpStatus.BAD_REQUEST,
            "Only a manager of this station can be made its owner, so nothing was changed"),

    /** An import started without the transfer code that says what is being fetched. */
    IMPORT_NEEDS_A_TRANSFER_CODE(
            Area.STATIONS, 26, HttpStatus.BAD_REQUEST, "Give the transfer code of the instance you are moving from"),

    /** A transfer code this instance cannot read or is no longer carrying out. */
    TRANSFER_CODE_NOT_GOOD_ON_IMPORT(Area.STATIONS, 27, HttpStatus.BAD_REQUEST, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** A transfer code that names no instance, sent without one alongside it. */
    IMPORT_NEEDS_A_SOURCE(
            Area.STATIONS,
            28,
            HttpStatus.BAD_REQUEST,
            "That transfer code names no instance to fetch from, so give the address as well"),

    /** The progress of an import at a station where none is running. */
    NO_IMPORT_RUNNING(Area.STATIONS, 29, HttpStatus.NOT_FOUND, "No import is running for this station"),

    /** A deletion confirmed by a link that carried nothing to confirm with. */
    STATION_DELETE_LINK_CARRIES_NOTHING(Area.STATIONS, 30, HttpStatus.BAD_REQUEST, Sentences.LINK_CARRIES_NOTHING),

    /** A confirmation link for a deletion that has been used, has run out, or was never one. */
    STATION_DELETE_LINK_UNKNOWN(
            Area.STATIONS,
            31,
            HttpStatus.BAD_REQUEST,
            "That confirmation link has been used or has run out, so nothing was deleted"),

    /** A block lifted for something this instance does not know as a mail provider. */
    MAIL_PROVIDER_NOT_KNOWN(
            Area.STATIONS, 32, HttpStatus.BAD_REQUEST, "That is not a mail provider this instance knows"),

    /** A station being made without a name. */
    STATION_NEEDS_A_NAME(Area.STATIONS, 33, HttpStatus.BAD_REQUEST, Sentences.STATION_NEEDS_A_NAME),

    /** A change to a station that would leave it without a name. */
    STATION_NEEDS_A_NAME_ON_UPDATE(Area.STATIONS, 34, HttpStatus.BAD_REQUEST, Sentences.STATION_NEEDS_A_NAME),

    /** A station that went before its name and its manager could be written. */
    STATION_NOT_HERE_ON_MANAGER_UPDATE(Area.STATIONS, 35, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station that went before its new name could be written. */
    STATION_NOT_HERE_ON_UPDATE(Area.STATIONS, 36, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station that was already gone when its deletion was asked for. */
    STATION_NOT_DELETED(Area.STATIONS, 37, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station asked for by name that is not here. */
    STATION_NOT_HERE_BY_NAME(Area.STATIONS, 38, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Something in the address that has to name a station and does not. */
    STATION_NAME_NOT_READABLE(Area.STATIONS, 39, HttpStatus.BAD_REQUEST, "That does not name a station"),

    /** An application to join sent to an instance that is taking none. */
    STATION_REGISTRATION_SWITCHED_OFF(
            Area.STATIONS,
            40,
            HttpStatus.FORBIDDEN,
            "This instance is not taking applications for new stations at the moment"),

    /** An application to join that left one of its fields empty. */
    STATION_APPLICATION_NEEDS_EVERY_FIELD(
            Area.STATIONS,
            41,
            HttpStatus.BAD_REQUEST,
            "Every field is needed: your name, your email address and the name of the station, "
                    + "so nothing was saved"),

    /** An application confirmed by a link that carried nothing to confirm with. */
    STATION_APPLICATION_LINK_CARRIES_NOTHING(Area.STATIONS, 42, HttpStatus.BAD_REQUEST, Sentences.LINK_CARRIES_NOTHING),

    /** An application that could not be turned into a station, whose own words stay in the log. */
    STATION_APPLICATION_NOT_ACCEPTED(
            Area.STATIONS,
            43,
            HttpStatus.BAD_REQUEST,
            "This application could not be accepted, so nothing was saved: it may already have been "
                    + "answered, or a role the new station needs is missing here"),

    /** An application turned down that is no longer waiting for an answer. */
    STATION_APPLICATION_NOT_DENIED(
            Area.STATIONS,
            44,
            HttpStatus.BAD_REQUEST,
            "This application is not waiting to be answered any more, so nothing was saved"),

    /**
     * A station asked about from the open web that has nothing to show there. One code for all four
     * ways of getting here deliberately: an address nobody answers at, a station that has put
     * nothing on the open web, an association's own station, and one whose wiki is switched off all
     * say the same thing, because telling them apart would let a stranger learn which stations exist
     * and which of them are merely hidden.
     */
    PUBLIC_STATION_NOTHING_TO_SHOW(
            Area.STATIONS, 45, HttpStatus.NOT_FOUND, "There is nothing here for the open web to see"),

    /** An invite asked for without saying which station it is for. */
    INVITE_NEEDS_A_STATION(Area.DISCOVERY, 1, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /**
     * A station an invite was asked for that is not among the ones this instance offers to be found.
     * One code for the miss and the visibility check deliberately: this endpoint answers strangers,
     * and telling them apart would let one learn that a station exists but keeps itself out of the
     * listing.
     */
    STATION_NOT_OPEN_TO_INVITES(Area.DISCOVERY, 2, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_FOUND_IN_DISCOVERY),

    /** A partnership asked for without saying which station it is with. */
    FEDERATION_REQUEST_NEEDS_A_STATION(Area.DISCOVERY, 3, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /**
     * A station a partnership was asked for that is not among the ones this instance offers to be
     * found. One code for the miss and the visibility check deliberately, for the same reason as the
     * invite above.
     */
    STATION_NOT_OPEN_TO_FEDERATION(Area.DISCOVERY, 4, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_FOUND_IN_DISCOVERY),

    /** A partnership asked for with a station that is already a partner. */
    ALREADY_FEDERATED(Area.DISCOVERY, 5, HttpStatus.BAD_REQUEST, "This station is already a partner of yours"),

    /** A partnership asked for a second time while the first is still unanswered. */
    FEDERATION_REQUEST_ALREADY_SENT(
            Area.DISCOVERY, 6, HttpStatus.BAD_REQUEST, "A request to this station is already waiting for an answer"),

    /** An instance knocked on without saying where it is. */
    PROBE_NEEDS_AN_ADDRESS(Area.DISCOVERY, 7, HttpStatus.BAD_REQUEST, Sentences.PEER_ADDRESS_MISSING),

    /** An instance written down without saying where it is. */
    PEER_NEEDS_AN_ADDRESS(Area.DISCOVERY, 8, HttpStatus.BAD_REQUEST, Sentences.PEER_ADDRESS_MISSING),

    /** An instance that named a key other than the one it was expected to name. */
    PEER_KEY_NOT_THE_EXPECTED_ONE(
            Area.DISCOVERY,
            9,
            HttpStatus.BAD_REQUEST,
            "That instance named a different key from the one you expected, so nothing was saved"),

    /** An instance that answers but wants no part in being found. */
    PEER_DOES_NOT_WANT_DISCOVERY(
            Area.DISCOVERY,
            10,
            HttpStatus.BAD_REQUEST,
            "That instance does not take part in discovery, so nothing was saved"),

    /** An instance being voted on or blocked that this instance has not written down. */
    PEER_NOT_HERE(Area.DISCOVERY, 11, HttpStatus.NOT_FOUND, Sentences.PEER_NOT_HERE),

    /** An instance that went between the change to it and it being read back. */
    PEER_NOT_HERE_AFTER_CHANGE(Area.DISCOVERY, 12, HttpStatus.NOT_FOUND, Sentences.PEER_NOT_HERE),

    /** An instance being pinged that this instance has not written down. */
    PEER_NOT_HERE_ON_PING(Area.DISCOVERY, 13, HttpStatus.NOT_FOUND, Sentences.PEER_NOT_HERE),

    /** A block written down without saying what is blocked or what kind of thing it is. */
    BLOCKLIST_ENTRY_INCOMPLETE(
            Area.DISCOVERY,
            14,
            HttpStatus.BAD_REQUEST,
            "Say what is being blocked and what kind of thing it is, so nothing was saved"),

    /** A section of a protocol that is gone, or belongs to another station. */
    PROTOCOL_SECTION_NOT_HERE(Area.TEST_PROTOCOLS, 1, HttpStatus.NOT_FOUND, "That section is not here any more"),

    /** A point in a protocol that is gone, or belongs to another station. */
    PROTOCOL_ITEM_NOT_HERE(Area.TEST_PROTOCOLS, 2, HttpStatus.NOT_FOUND, "That point is not here any more"),

    /** A protocol written down without a name. */
    PROTOCOL_NEEDS_A_NAME(
            Area.TEST_PROTOCOLS, 3, HttpStatus.BAD_REQUEST, "A protocol needs a name, so nothing was saved"),

    /** A protocol that went before the change to it could be written. */
    PROTOCOL_NOT_CHANGED(
            Area.TEST_PROTOCOLS, 4, HttpStatus.NOT_FOUND, "That protocol is not here any more, so nothing was changed"),

    /** A member taken up for testing while another tester already holds them. */
    PROTOCOL_MEMBER_HELD_BY_ANOTHER_TESTER(
            Area.TEST_PROTOCOLS,
            5,
            HttpStatus.BAD_REQUEST,
            "Somebody else is testing this member right now, so nothing was changed"),

    /** The protocol behind a test run, gone while the run was being worked out. */
    PROTOCOL_NOT_HERE_BEHIND_RUN_TO_EVALUATE(
            Area.TEST_PROTOCOLS, 6, HttpStatus.NOT_FOUND, Sentences.PROTOCOL_NOT_HERE_BEHIND_RUN),

    /** The protocol behind a test run, gone while every sheet for it was being gathered. */
    PROTOCOL_NOT_HERE_BEHIND_RUN_TO_EXPORT(
            Area.TEST_PROTOCOLS, 7, HttpStatus.NOT_FOUND, Sentences.PROTOCOL_NOT_HERE_BEHIND_RUN),

    /** A test run whose sheets could not be gathered into one file. */
    PROTOCOL_RUN_NOT_EXPORTED(
            Area.TEST_PROTOCOLS,
            8,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "That test run could not be put into a file. Trying again may work"),

    /** The protocol behind a test run, gone while the result table was being made. */
    PROTOCOL_NOT_HERE_BEHIND_RUN_FOR_TABLE(
            Area.TEST_PROTOCOLS, 9, HttpStatus.NOT_FOUND, Sentences.PROTOCOL_NOT_HERE_BEHIND_RUN),

    /** The protocol behind a test run, gone while one member's sheet was being made. */
    PROTOCOL_NOT_HERE_BEHIND_RUN_FOR_MEMBER_SHEET(
            Area.TEST_PROTOCOLS, 10, HttpStatus.NOT_FOUND, Sentences.PROTOCOL_NOT_HERE_BEHIND_RUN),

    /**
     * A protocol asked for by a paired instance that is not here, belongs to a third station or is
     * not among the ones shared with the asker. One code deliberately: telling the three apart
     * would let a partner count its way through protocols it was never given.
     */
    REMOTE_PROTOCOL_NOT_SHARED(
            Area.TEST_PROTOCOLS, 11, HttpStatus.NOT_FOUND, "No protocol here is shared with you under that number"),

    /** A protocol that could not be read back after being changed, with the change already in. */
    PROTOCOL_NOT_HERE_AFTER_CHANGE(
            Area.TEST_PROTOCOLS, 12, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A test run that could not be read back after being changed, with the change already in. */
    PROTOCOL_RUN_NOT_HERE_AFTER_CHANGE(
            Area.TEST_PROTOCOLS, 13, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A test run that could not be read back after being closed, with the closing already in. */
    PROTOCOL_RUN_NOT_HERE_AFTER_CLOSING(
            Area.TEST_PROTOCOLS, 14, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A member on a test run that could not be read back after their test was taken over. */
    PROTOCOL_MEMBER_NOT_HERE_AFTER_LOCKING(
            Area.TEST_PROTOCOLS, 15, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A member on a test run that could not be read back after their test was let go of. */
    PROTOCOL_MEMBER_NOT_HERE_AFTER_UNLOCKING(
            Area.TEST_PROTOCOLS, 16, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A member on a test run that could not be read back after their test was marked finished. */
    PROTOCOL_MEMBER_NOT_HERE_AFTER_COMPLETION(
            Area.TEST_PROTOCOLS, 17, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A question paired with a waiting list it is not on. */
    WAITING_LIST_FIELD_NOT_IN_LIST(Area.WAITING_LISTS, 1, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_FIELD_NOT_HERE),

    /** An invite paired with a waiting list it does not belong to. */
    WAITING_LIST_INVITE_NOT_IN_LIST(Area.WAITING_LISTS, 2, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_INVITE_UNKNOWN),

    /**
     * An entry that is gone, or belongs to another waiting list. The two are one code deliberately:
     * telling them apart would say that the entry exists on a list the caller may not see.
     */
    WAITING_LIST_ENTRY_NOT_IN_LIST(Area.WAITING_LISTS, 3, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An invite code no waiting list is reached by. */
    WAITING_LIST_INVITE_UNKNOWN(Area.WAITING_LISTS, 4, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_INVITE_UNKNOWN),

    /** The waiting list an invite names, which is gone. */
    WAITING_LIST_NOT_HERE_BEHIND_INVITE(Area.WAITING_LISTS, 5, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** An entry token that names no entry. */
    WAITING_LIST_ENTRY_TOKEN_UNKNOWN(
            Area.WAITING_LISTS, 6, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** The waiting list an entry belongs to, which is gone. */
    WAITING_LIST_NOT_HERE_BEHIND_ENTRY(Area.WAITING_LISTS, 7, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** The station whose invitation a family is reading, which is gone. */
    STATION_NOT_HERE_BEHIND_INVITATION(Area.WAITING_LISTS, 8, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** An invitation answered with a token that names no entry any more. */
    WAITING_LIST_ENTRY_NOT_HERE_ON_ANSWER(
            Area.WAITING_LISTS, 9, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** Signing up for the same waiting list far more often than a person could. */
    WAITING_LIST_TOO_OFTEN(Area.WAITING_LISTS, 10, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A waiting list that went between the ownership check and being read. */
    WAITING_LIST_NOT_HERE(Area.WAITING_LISTS, 11, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** A waiting list that went before the change to it could be written. */
    WAITING_LIST_NOT_CHANGED(Area.WAITING_LISTS, 12, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** A waiting list that went before its visible questions could be written. */
    WAITING_LIST_NOT_HERE_ON_VISIBLE_QUESTIONS(
            Area.WAITING_LISTS, 13, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** A question on a waiting list that went before the change to it could be written. */
    WAITING_LIST_FIELD_NOT_CHANGED(Area.WAITING_LISTS, 14, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_FIELD_NOT_HERE),

    /** A waiting list that went between its entries being read and its own details being read. */
    WAITING_LIST_NOT_HERE_ON_ENTRIES(Area.WAITING_LISTS, 15, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** An entry that went between being changed and being read back. */
    WAITING_LIST_ENTRY_NOT_HERE_AFTER_CHANGE(
            Area.WAITING_LISTS, 16, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** An entry that went between its date being changed and being read back. */
    WAITING_LIST_ENTRY_NOT_HERE_AFTER_DATE_CHANGE(
            Area.WAITING_LISTS, 17, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_ENTRY_NOT_HERE),

    /** A station that is not here, asked for by a public waiting list address. */
    STATION_NOT_HERE_BEHIND_PUBLIC_LIST(Area.WAITING_LISTS, 18, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Public waiting lists at all, at a station that has switched them off. */
    PUBLIC_WAITING_LISTS_SWITCHED_OFF(Area.WAITING_LISTS, 19, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /**
     * A waiting list that is gone, belongs to another station, or is not open to the public. One
     * code deliberately: telling them apart would say that a list nobody outside may see is there.
     */
    PUBLIC_WAITING_LIST_NOT_HERE(Area.WAITING_LISTS, 20, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** The same, at the moment a public sign-up is sent rather than when the form is drawn. */
    PUBLIC_WAITING_LIST_NOT_HERE_ON_REGISTRATION(
            Area.WAITING_LISTS, 21, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE);

    private final Area area;
    private final int number;
    private final String code;
    private final HttpStatus status;
    private final String message;

    Refusal(Area area, int number, HttpStatus status, String message) {
        this.area = area;
        this.number = number;
        this.code = "%s-%03d".formatted(area.prefix(), number);
        this.status = status;
        this.message = message;
    }

    /**
     * The area of the product this refusal belongs to, which is what its prefix stands for.
     *
     * @return the area
     */
    public Area area() {
        return area;
    }

    /**
     * This refusal's number within its area.
     *
     * @return the number, unique among the refusals of the same area
     */
    public int number() {
        return number;
    }

    /**
     * The code a reader quotes, which names exactly one line.
     *
     * @return the area's prefix, a hyphen and three digits
     */
    public String code() {
        return code;
    }

    /**
     * The status this refusal answers with, chosen so the reader is told truthfully whose problem
     * it is: what they sent is a {@code 400}, what they may not do is a {@code 403}, what is not
     * there is a {@code 404}, a collision is a {@code 409}, and only a fault is a {@code 500}.
     *
     * @return the HTTP status
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * The sentence the reader is shown.
     *
     * @return one sentence saying what was refused and what became of their data
     */
    public String message() {
        return message;
    }

    /**
     * The exception to throw for this refusal.
     *
     * @return an exception carrying this refusal's status, sentence and code
     */
    public RefusalResponse raise() {
        return new RefusalResponse(this, message);
    }

    /**
     * The exception to throw for this refusal, naming the particular thing it was about.
     *
     * <p>The detail is the caller's own words back at them, a field name they sent or a value they
     * chose, never anything of Ember's.
     *
     * @param detail what the refusal was about, in the reader's terms
     * @return an exception carrying this refusal's status and code, and its sentence with the
     *         detail named
     */
    public RefusalResponse raise(String detail) {
        return new RefusalResponse(this, message + ": " + detail);
    }

    /**
     * The registry of code prefixes, one per area of the product.
     *
     * <p>A prefix is claimed here and nowhere else, so two features cannot both be {@code F} and
     * nobody has to read the whole enum to find out what is free. An area with no refusals yet is
     * still listed: reserving the prefix is the point, because the alternative is two features
     * picking the same one months apart.
     *
     * <p>A prefix is one letter or two. The alphabet ran out long before the features did, and the
     * areas that had a letter when it did keep it, because a code already written down in a report
     * outlives the table that produced it. Everything added since takes two. The hyphen is what
     * makes the two widths safe to mix: a code is always the prefix, a hyphen and three digits, so
     * {@code F-001} and {@code FD-001} cannot be read for one another and no prefix can be
     * mistaken for the start of another.
     */
    public enum Area {
        /** Instance administration and the peers an instance knows of. */
        ADMIN("A", "instance administration and peers"),
        /** Attendance: who was there, and the sheets it is recorded on. */
        ATTENDANCE("AT", "attendance"),
        /** The request body, before any route has looked at it. */
        BODY("B", "the request body"),
        /** The beacon an installation reports to, and what it takes in. */
        BEACON("BC", "the beacon"),
        /** Boards, their tickets and what hangs off one. */
        BOARDS("BO", "boards and tickets"),
        /** Checklists and the runs of one. */
        CHECKLISTS("CL", "checklists"),
        /** Comments and notes, wherever they are left. */
        COMMENTS("CM", "comments and notes"),
        /** Clusters of stations and what they share. */
        CLUSTERS("CU", "clusters"),
        /** The documents a station keeps for its members. */
        DOCUMENTS("D", "documents"),
        /** Finding instances and the stations on them. */
        DISCOVERY("DC", "discovery"),
        /** Appointments and the registrations for them. */
        EVENTS("E", "appointments and registrations"),
        /** Equipment and what a station still needs. */
        EQUIPMENT("EQ", "equipment"),
        /** Forms, their answers and the links they are answered through. */
        FORMS("F", "forms and answers"),
        /** The feed a member reads, and the tokens it is read by. */
        FEED("FD", "the feed"),
        /** Anything with no area of its own: faults, the store, and the shared loaders. */
        GENERAL("G", "failures that belong to no one feature"),
        /** Inventory: items, containers, movements and checks. */
        INVENTORY("I", "inventory"),
        /** The figures a station is shown about itself. */
        INSIGHTS("IS", "insights"),
        /** The knowledge base. */
        KNOWLEDGE_BASE("K", "the knowledge base"),
        /** The media library: files, folders, tags and uploads. */
        MEDIA_LIBRARY("L", "the media library"),
        /** Things handed in that somebody lost. */
        LOST_AND_FOUND("LF", "lost and found"),
        /** Consent, and the terms somebody has agreed to. */
        LEGAL("LG", "consent and terms"),
        /** Members, accounts, avatars and profile questions. */
        MEMBERS("M", "members and accounts"),
        /** Reading members in from somewhere else. */
        MAIL_IMPORT("MI", "mail import"),
        /** Sending mail, and what a provider reports back about it. */
        MAIL("ML", "mail"),
        /** Maps and what is drawn on one. */
        MAPS("MP", "maps"),
        /** Setting an instance up, before it has any station. */
        INSTALLATION("N", "installation"),
        /** News, whether a station's own or the instance's. */
        NEWS("NW", "news"),
        /** Pages, their public addresses and their share links. */
        PAGES("P", "pages"),
        /** Passkeys, and registering or using one. */
        PASSKEYS("PK", "passkeys"),
        /** Catalogs, tests and the papers sat on them. */
        QUIZZES("Q", "quizzes"),
        /** Procedures and who they were handed to. */
        PROCEDURES("R", "procedures"),
        /** Stations and the applications to join one. */
        STATIONS("S", "stations"),
        /** Where an installation keeps its files, and reaching it. */
        STORAGE("ST", "storage"),
        /** The instance itself: its settings, its status and what it reports. */
        SYSTEM("SY", "the instance itself"),
        /** Test protocols, their sections and their points. */
        TEST_PROTOCOLS("T", "test protocols"),
        /** Second factors, and proving one. */
        TWO_FACTOR("TF", "two-factor"),
        /** What an instance counts about the requests it answers. */
        TRAFFIC("TR", "traffic"),
        /** Waiting lists, their entries and their invites. */
        WAITING_LISTS("W", "waiting lists"),
        /** Federation between instances. */
        FEDERATION("X", "federation");

        private final String prefix;
        private final String covers;

        Area(String prefix, String covers) {
            this.prefix = prefix;
            this.covers = covers;
        }

        /**
         * The letters this area's codes open with.
         *
         * @return one or two upper-case letters
         */
        public String prefix() {
            return prefix;
        }

        /**
         * What the prefix stands for, in the words somebody looking up a code would use.
         *
         * @return the area in a few words
         */
        public String covers() {
            return covers;
        }
    }

    /**
     * The sentences more than one refusal says.
     *
     * <p>Several sites refuse for the same reason in different places, and each needs its own code
     * while saying the same thing. Written out at each constant the wording drifts: one of them is
     * reworded, the others are not, and the same failure reads two ways. Named here it cannot.
     */
    private static final class Sentences {
        private static final String STATION_NOT_HERE = "That station is not here";
        private static final String NOT_HERE_OR_NOT_YOURS = "That is not here any more, or it is not yours to open";
        private static final String NO_CLUSTER_CHOSEN = "Choose a cluster before doing this";
        private static final String CLUSTER_NOT_HERE = "That cluster is not here any more";
        private static final String STATION_NOT_AN_IDENTITY = "That does not name a station";
        private static final String CLUSTER_NOT_AN_IDENTITY = "That does not name a cluster";
        private static final String UNEXPECTED_FAULT =
                "Something went wrong in Ember and the request was not carried out, so nothing was saved. "
                        + "Trying again may work; if it keeps happening, please report it";
        private static final String CHANGE_SAVED_BUT_NOT_READ_BACK =
                "The change was saved, but it could not be read back. Reload the page to see it as it stands";
        private static final String NO_STATION_CHOSEN = "Choose a station before doing this";
        private static final String STATION_NEEDS_A_NAME = "A station needs a name, so nothing was saved";
        private static final String STATION_NOT_FOUND_IN_DISCOVERY = "That station is not one that can be found here";
        private static final String PEER_ADDRESS_MISSING = "Give the address of the instance";
        private static final String PEER_NOT_HERE = "That instance is not here any more";
        private static final String TOO_MANY_ATTEMPTS = "Too many attempts from here. Try again shortly";
        private static final String LOCALE_NOT_GOOD = "That does not name a language this instance keeps text in";
        private static final String TRACKED_TABLE_NOT_HERE =
                "Data tracking keeps no record of that, so nothing was changed";
        private static final String STORAGE_MOVE_NOT_DONE =
                "The files could not be carried over, so nothing was changed and they are still where they were. "
                        + "The reason is in the instance log";
        private static final String TRAFFIC_SPAN_MISSING = "Say which stretch of time to count over";
        private static final String TRAFFIC_SPAN_NOT_A_TIME =
                "Each end of the stretch of time has to be a date and a time";
        private static final String TRAFFIC_KIND_UNKNOWN =
                "Ask for signed-in, signed-out or federated requests, or for all of them together";
        private static final String TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS =
                "The stretch of time cannot end before it starts";
        private static final String MAP_TILE_NOT_A_NUMBER = "A piece of the map is named by whole numbers";
        private static final String REQUEST_INCOMPLETE =
                "That request arrived without everything it needs, so nothing was done";
        private static final String PASSKEY_ENROLMENT_REFUSED = "That passkey could not be set up, so none was saved";
        private static final String ENROLMENT_LINK_NOT_GOOD =
                "That link is no longer good, so no passkey can be set up with it";
        private static final String DEVICE_CODE_MISSING = "Type the code the other device is showing";
        private static final String DEVICE_CODE_NOT_GOOD = "There is nothing to confirm for that code";
        private static final String ACCOUNT_HOLDS_NO_PASSWORD = "This account holds no password";
        private static final String PASSKEY_NOT_HERE = "That passkey is not here any more";
        private static final String VERIFICATION_CODE_WRONG = "That code was not right, so nothing was confirmed";
        private static final String NO_SECOND_FACTOR_SET_UP = "This account has no second factor set up";
        private static final String SIGN_IN_NOT_WAITING =
                "That sign-in is no longer waiting for a second factor. Start signing in again";
        private static final String SECURITY_KEY_NOT_ACCEPTED =
                "That security key was not accepted, so nothing was confirmed";
        private static final String FACTOR_NOT_HERE = "That second factor is not here any more";
        private static final String POLICY_NOT_HERE = "That rule is not here any more";
        private static final String SECOND_FACTOR_NOT_RESET =
                "That second factor could not be reset, so nothing was changed";
        private static final String PEER_DID_NOT_ANSWER = "That instance could not be reached";
        private static final String FEDERATION_ADDRESS_NOT_PUBLIC = "That address has to be a public HTTPS address";
        private static final String PAIR_REQUEST_NOT_HERE = "That pairing request is not here any more";
        private static final String FEDERATION_SHARE_NOT_HERE =
                "That share is not here any more, so nothing was changed";
        private static final String BEACON_NOT_RECEIVING = "This instance is not a beacon";
        private static final String BEACON_REPORT_NOT_HERE = "That report is not here any more";
        private static final String BEACON_ID_NOT_A_NUMBER = "That does not name anything a beacon holds";
        private static final String PROBLEM_LOG_NOT_RUNNING = "Nothing is being written down about problems here";
        private static final String EQUIPMENT_DATE_MISSING = "Name the day this is about";
        private static final String EQUIPMENT_PIECE_MISSING = "Name the piece of gear this is about";
        private static final String LOST_ITEM_NOT_CLAIMED = "That find has not been claimed, so nothing was changed";
        private static final String INSIGHTS_WINDOW_BACKWARDS = "The span cannot end before it starts";

        private static final String ATTENDANCE_SHEET_NOT_HERE = "That attendance sheet is not here any more";
        private static final String ATTENDANCE_SHEET_NOT_HERE_ON_WRITE =
                "That attendance sheet is not here any more, so nothing was changed";
        private static final String ATTENDANCE_ENTRY_NOT_HERE_ON_WRITE =
                "That entry on the attendance sheet is not here any more, so nothing was changed";
        private static final String ATTENDANCE_TEMPLATE_NEEDS_A_NAME = "A template needs a name, so nothing was saved";
        private static final String ATTENDANCE_TEMPLATE_NOT_HERE_ON_WRITE =
                "That template is not here any more, so nothing was changed";
        private static final String ATTENDANCE_FIELD_DETAILS_MISSING =
                "A field on the sheet needs a name and a kind, so nothing was saved";
        private static final String ATTENDANCE_FIELD_NOT_HERE = "That field on the sheet is not here any more";
        private static final String ATTENDANCE_REPORT_PRESET_NOT_HERE = "That saved report is not here any more";
        private static final String ABSENCE_SPAN_MISSING =
                "Give the first and the last day of the absence, so nothing was saved";
        private static final String ABSENCE_ENDS_BEFORE_IT_STARTS =
                "The absence cannot end before it starts, so nothing was saved";
        private static final String ABSENCE_NOT_HERE = "That absence is not here any more";
        private static final String ABSENCE_NOT_HERE_ON_WRITE =
                "That absence is not here any more, so nothing was changed";

        private static final String FORM_NOT_HERE = "That form is not here any more, so nothing was changed";
        private static final String FORM_NOT_ANSWERED_FROM_OUTSIDE =
                "That form cannot be answered from outside the station";
        private static final String FORM_KIND_UNKNOWN = "That is not a kind of form";
        private static final String FORM_TAKES_NO_ANSWERS = "This form is not taking answers, so nothing was saved";
        private static final String FORM_NOT_YOURS_TO_ANSWER = "This form is not yours to answer";
        private static final String FORM_ANSWERS_NOT_SAVED = "The answers could not be saved";
        private static final String FORM_ANSWER_NOT_CHANGEABLE =
                "This form does not let an answer be changed once it is given";

        private static final String EVENT_NOT_HERE = "That appointment is not here any more";
        private static final String EVENT_REGISTRATION_NOT_HERE = "That registration is not here any more";
        private static final String EVENT_CATEGORY_NOT_HERE = "That category is not here any more";
        private static final String EVENT_BREAK_NOT_HERE = "That break is not here any more";
        private static final String EVENT_TEMPLATE_NOT_HERE = "That template is not here any more";
        private static final String NOT_A_STATION_MEMBER = "You are not a member of this station, so nothing was saved";
        private static final String MEMBER_NOT_YOURS = "You do not look after this member";
        private static final String CANNOT_ANSWER_FOR_MEMBER = "You cannot answer for this member";
        private static final String REGISTRATION_CLOSED =
                "Registration for this appointment has closed. Ask whoever runs it";
        private static final String NO_LONGER_TAKEN_BACK = "This can no longer be taken back";
        private static final String NO_PLACES_LEFT = "There are no places left";
        private static final String DAY_NOT_A_DATE = "The day asked about is not a date";

        private static final String COMMENT_NOT_HERE = "That comment is not here any more";
        private static final String COMMENT_NEEDS_TEXT = "A comment needs something written in it";

        private static final String PAGE_NOT_HERE = "That page is not here any more";

        private static final String NEWS_NOT_HERE = "That entry is not here any more";
        private static final String NEWS_NOT_HERE_ON_WRITE = "That entry is not here any more, so nothing was changed";
        private static final String NEWS_NEEDS_A_TITLE = "An entry needs a title, so nothing was saved";
        private static final String NEWS_COMMENT_NOT_YOURS_TO_EDIT = "You can only change a comment you wrote yourself";
        private static final String NEWS_COMMENT_NOT_YOURS_TO_DELETE =
                "You can only delete a comment you wrote yourself";
        private static final String PUBLIC_BLOG_NOT_HERE = "This station keeps no public blog";
        private static final String ATTACHMENT_NOT_HERE = "That attachment is not here any more";
        private static final String UPLOAD_TOO_LARGE = "That file is bigger than this instance takes";

        private static final String BOARD_NOT_HERE = "That board is not here any more";
        private static final String BOARD_TICKET_NOT_HERE = "That ticket is not here any more";
        private static final String BOARD_LABEL_NOT_HERE = "That label is not here any more";
        private static final String NOT_A_MEMBER_FOR_BOARDS = "You are not a member of this station";
        private static final String FEDERATED_BOARD_NOT_HERE = "That shared board is not here any more";
        private static final String FEDERATION_PARTNER_NOT_HERE_FOR_BOARDS =
                "That partner instance is not one this station knows";

        private static final String FILE_NOT_HERE = "That file is not here any more";
        private static final String FOLDER_NOT_HERE = "That folder is not here any more";
        private static final String FILE_TAG_NOT_HERE = "That tag is not here any more";
        private static final String PICTURE_NOT_HERE = "That picture is not here";
        private static final String UPLOAD_MISSING_FILE = "The upload arrived without a file in it";
        private static final String UPLOAD_NOT_SAVED = "The upload could not be saved";
        private static final String UPLOAD_NOT_PROCESSED =
                "The upload could not be worked through, so nothing was saved. Trying again may work";

        private static final String KB_ARTICLE_NOT_HERE = "That article is not here any more";
        private static final String KB_ARTICLE_NEEDS_A_NAME = "An article needs a name, so nothing was saved";
        private static final String KB_ARTICLE_HAS_NOTHING_TO_SHOW = "That article has nothing to show";
        private static final String KB_ONLY_WRITTEN_AS_PDF = "Only a written article can be handed out as a PDF";
        private static final String KB_PDF_NOT_MADE =
                "That article could not be made into a PDF. Trying again may work";
        private static final String KB_ORIGINAL_ONLY_FOR_PRESENTATIONS =
                "Only a presentation keeps the file it was made from";
        private static final String KB_PICTURE_KIND_NOT_TAKEN = "Only PNG, JPEG and WebP pictures are taken here";
        private static final String KB_COMMENT_EMPTY = "A comment cannot be empty, so nothing was saved";
        private static final String KB_COMMENT_NOT_HERE = "That comment is not here any more";

        private static final String PUBLIC_QUIZ_STATION_NOT_REACHED = "No station is reached by this link";
        private static final String PUBLIC_QUIZ_CATALOGS_NOT_NAMED =
                "That link names no catalog to draw a question from";
        private static final String QUIZ_QUESTION_NOTE_NOT_HERE = "That note about a question is not here any more";
        private static final String QUIZ_QUESTION_NOT_HERE_ON_WRITE =
                "That question is not here any more, so nothing was changed";
        private static final String NOT_A_STATION_MEMBER_TO_READ = "You are not a member of this station";
        private static final String PROTOCOL_NOT_HERE_BEHIND_RUN =
                "The protocol this test run follows is not here any more";

        private static final String SESSION_HAS_NO_ACCOUNT = "This session does not stand for an account";
        private static final String MEMBER_NOT_HERE = "That member is not here any more";
        private static final String ACCOUNT_NOT_HERE = "That account is not here any more";
        private static final String PROFILE_FIELD_NOT_HERE = "That profile question is not here any more";
        private static final String ADDRESS_MISSING = "Give the address to write to";
        private static final String ADDRESS_BELONGS_TO_ANOTHER = "That address already belongs to another account";
        private static final String LINK_CARRIES_NOTHING = "That link carries nothing to act on";
        private static final String SESSION_TOKEN_MISSING = "The request named no session";
        private static final String PASSWORD_TOO_SHORT = "That password is too short, so nothing was saved";
        private static final String PASSWORD_BREACHED =
                "That password turns up in known data leaks, so choose another one";
        private static final String ACCOUNT_NOT_NAMED = "Name the account this is about";
        private static final String PROFILE_FIELD_DETAILS_MISSING = "Give the question a name and a kind";
        private static final String PROFILE_FIELD_AUDIENCE_AMBIGUOUS =
                "Name either a kind of member or a group, and only one of the two";
        private static final String GROUP_NAME_MISSING = "Give the group a name";
        private static final String GROUP_NOT_HERE = "That group is not here any more";
        private static final String TAG_NAME_MISSING = "Give the tag a name";
        private static final String MEMBER_TAG_NOT_HERE = "That tag is not here any more";
        private static final String TRANSFER_TOKEN_NOT_GOOD =
                "That transfer is not one this instance is carrying out any more";
        private static final String REGISTRATION_CODE_NOT_HERE = "That registration code is not here any more";

        private static final String DOCUMENT_NOT_HERE = "That document is not here any more";
        private static final String DOCUMENT_NOT_YOURS = "That document belongs to somebody you do not answer for";
        private static final String PAGE_NEEDS_A_TITLE = "A page needs a title, so nothing was saved";
        private static final String PROCEDURE_TEMPLATE_NEEDS_A_NAME =
                "A procedure template needs a name, so nothing was saved";
        private static final String PROCEDURE_STEP_NEEDS_A_TITLE = "A step needs a title, so nothing was saved";
        private static final String PROCEDURE_STEP_NOT_HERE_ON_WRITE =
                "That step of the procedure is not here any more, so nothing was changed";
        private static final String CHECKLIST_NEEDS_A_NAME = "A checklist needs a name, so nothing was saved";
        private static final String CHECKLIST_PDF_NOT_MADE =
                "That checklist could not be made into a PDF. Trying again may work";
        private static final String DOCUMENT_NOT_YOURS_TO_ADD = "You may not add a document for this member";

        private static final String ITEM_NOT_HERE = "That piece of gear is not here any more";
        private static final String ITEM_NEEDS_A_NAME = "A piece of gear needs a name, so nothing was saved";
        private static final String ITEM_KIND_NOT_HERE = "That kind of gear is not here any more";
        private static final String INVENTORY_NOT_HERE = "That inventory is not here any more";
        private static final String INVENTORY_NOT_HERE_BEHIND_ITEM =
                "The inventory this piece of gear belongs to is not here any more";
        private static final String INVENTORY_NEEDS_A_NAME = "An inventory needs a name, so nothing was saved";
        private static final String INVENTORY_NEEDS_A_KIND = "An inventory needs a kind, so nothing was saved";
        private static final String SIZE_NOT_HERE = "That size is not here any more";
        private static final String SIZE_NEEDS_A_NAME = "A size needs a name, so nothing was saved";
        private static final String REQUIREMENT_NOT_HERE = "That requirement is not here any more";
        private static final String NO_CODE_GIVEN = "Name the code to look for";
        private static final String LOSS_NOT_YOURS_TO_REPORT = "Only somebody holding this gear can report it missing";
        private static final String NOTHING_TO_EXPORT = "There was nothing to put in that list, so no file was made";
        private static final String NO_FLOW_FOR_THIS_MOVEMENT =
                "No chain of steps is set up for a movement like this one";
        private static final String MOVEMENT_PURPOSE_MISSING = "Say what the movement is for";
        private static final String MOVEMENT_DOCUMENT_NOT_HERE =
                "The file attached to this movement is not here any more";
        private static final String FLOW_NOT_HERE = "That chain of steps is not here any more";
        private static final String FLOW_STEP_NOT_HERE = "That step is not here any more";
        private static final String CONTAINER_NOT_HERE = "That container is not here any more";
        private static final String CONTAINER_NOT_SAVED = "The container could not be saved, so nothing was changed. "
                + "It needs a name without a slash, a place to sit that is here and not inside itself, "
                + "and a code nothing else here already uses";
        private static final String CONTAINER_KIND_NOT_HERE = "That kind of container is not here any more";
        private static final String CONTAINER_KIND_NOT_CREATED =
                "A kind of container needs a short key of its own and a name, so nothing was saved";
        private static final String FIELD_NOT_HERE = "That field is not here any more";
        private static final String CHECK_WITHOUT_ITEMS = "The check arrived with no gear in it, so nothing was saved";
        private static final String PROCUREMENT_NOT_HERE = "That procurement is not here any more";

        private static final String QUIZ_CATALOG_NOT_HERE = "That catalog is not here any more";
        private static final String QUIZ_CATEGORY_NOT_HERE = "That category is not here any more";
        private static final String QUIZ_TEST_NOT_HERE = "That test is not here any more";
        private static final String QUIZ_ATTEMPT_NOT_HERE = "That attempt is not here any more";

        private static final String PROCEDURE_NOT_YOURS = "That procedure was not handed to you";

        private static final String STATION_APPLICATION_NOT_HERE = "That application is not here any more";

        private static final String WAITING_LIST_NOT_HERE = "That waiting list is not here any more";
        private static final String WAITING_LIST_ENTRY_NOT_HERE = "That entry is not here any more";
        private static final String WAITING_LIST_FIELD_NOT_HERE = "That question is not on this waiting list any more";
        private static final String WAITING_LIST_INVITE_UNKNOWN = "No waiting list is reached by this invite";

        private Sentences() {}
    }
}

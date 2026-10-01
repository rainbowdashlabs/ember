/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.HttpStatus;
import org.jspecify.annotations.Nullable;

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

    /**
     * A partner station on this instance asked while its partner no longer holds the partnership
     * as active, which a partner on another instance is refused for at the signature check.
     */
    FEDERATION_PARTNERSHIP_NOT_ACTIVE_THERE(
            Area.FEDERATION,
            33,
            HttpStatus.FORBIDDEN,
            "The partner station does not hold this partnership as active any more"),

    /** A partner station on another instance that did not answer, or answered with nothing to read. */
    FEDERATION_PARTNER_DID_NOT_ANSWER(
            Area.FEDERATION, 34, HttpStatus.NOT_FOUND, "The partner station did not answer, or had nothing to give"),

    /** A partner asking what is free on a day that does not read as a day. */
    LENDING_DAY_NOT_A_DAY(Area.FEDERATION, 35, HttpStatus.BAD_REQUEST, "A day asked about is not a day"),

    /** A partner's request naming gear, a kind of thing or an inventory that is not this station's. */
    LENDING_LINE_NAMES_FOREIGN_GEAR(
            Area.FEDERATION,
            36,
            HttpStatus.BAD_REQUEST,
            "A line of the request names gear that is not this station's, so nothing was saved"),

    /** A sign-up for a partner's appointment naming a day that does not read as a day. */
    FEDERATED_REGISTRATION_DAY_NOT_A_DAY(Area.FEDERATION, 37, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A partner's change to a checklist item that is not on the shared ticket it names. */
    REMOTE_CHECKLIST_ITEM_NOT_ON_TICKET(
            Area.FEDERATION, 38, HttpStatus.NOT_FOUND, "That checklist item is not on this ticket"),

    /** A partner reordering a lane that is not on the shared board it names. */
    REMOTE_LANE_NOT_ON_BOARD(Area.FEDERATION, 39, HttpStatus.NOT_FOUND, "That lane is not on this board"),

    /** A partner putting a label on a shared ticket, or taking it off, that is not a label of its board. */
    REMOTE_LABEL_NOT_ON_BOARD(Area.FEDERATION, 40, HttpStatus.NOT_FOUND, "That label is not on this board"),

    /** A partner route reached without a signature this instance accepted. */
    FEDERATION_REQUEST_NOT_SIGNED(
            Area.FEDERATION, 41, HttpStatus.FORBIDDEN, "That request was not signed by a partner this instance knows"),

    /** Content asked of a partner whose partnership is not active. */
    FEDERATION_PARTNER_NOT_ACTIVE(
            Area.FEDERATION, 42, HttpStatus.BAD_REQUEST, "That partner station is not active at the moment"),

    /** A cluster's own connection paused, which would stop its content arriving. */
    FEDERATION_CLUSTER_PARTNER_NOT_PAUSABLE(
            Area.FEDERATION,
            43,
            HttpStatus.BAD_REQUEST,
            "This connection carries the cluster's own content and cannot be paused, so nothing was changed"),

    /** A cluster's connection ended by the station, when it ends only with the membership. */
    FEDERATION_CLUSTER_PARTNER_NOT_DELETABLE(
            Area.FEDERATION,
            44,
            HttpStatus.BAD_REQUEST,
            "This connection belongs to the cluster and ends when the station leaves it, so nothing was changed"),

    /**
     * An inventory an offer is written on that is not here, or is not this station's. One code for
     * both: telling them apart would say that another station keeps an inventory under that number.
     */
    SHARE_INVENTORY_NOT_HERE(Area.FEDERATION, 45, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** An offer written on an inventory that holds only gear of the body above the station. */
    SHARE_INVENTORY_NOT_THE_STATIONS(
            Area.FEDERATION,
            46,
            HttpStatus.BAD_REQUEST,
            "This inventory belongs to the body above the station, which the station cannot lend from, so "
                    + "nothing was saved"),

    /** A kind of gear an offer is written on, which is not here. */
    SHARE_ITEM_KIND_NOT_HERE(Area.FEDERATION, 47, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** A piece of gear an offer is written on, which is not here. */
    SHARE_ITEM_NOT_HERE(Area.FEDERATION, 48, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** Gear asked for from a station that does not lend to the asking one. */
    LENDING_PARTNER_DOES_NOT_LEND(
            Area.FEDERATION, 49, HttpStatus.FORBIDDEN, "That station does not lend gear to yours"),

    /** Gear set aside for a lending request that the station only holds and does not own. */
    LENDING_GEAR_NOT_THE_STATIONS(Area.FEDERATION, 50, HttpStatus.FORBIDDEN, "That gear is not this station's to lend"),

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
    BEACON_INTAKE_PROTOCOL_TOO_NEW(Area.BEACON, 22, HttpStatus.BAD_REQUEST, Sentences.BEACON_PROTOCOL_TOO_NEW),

    /** A delivery to the beacon that does not say when it was sent or carries no one-time number. */
    BEACON_DELIVERY_ENVELOPE_INCOMPLETE(
            Area.BEACON,
            23,
            HttpStatus.BAD_REQUEST,
            "A delivery has to say when it was sent and carry a one-time number"),

    /** A signed delivery written to a protocol version newer than this beacon reads. */
    BEACON_DELIVERY_PROTOCOL_TOO_NEW(Area.BEACON, 24, HttpStatus.BAD_REQUEST, Sentences.BEACON_PROTOCOL_TOO_NEW),

    /** A signed delivery sent too long before or after now to be trusted. */
    BEACON_DELIVERY_OUT_OF_TIME(
            Area.BEACON, 25, HttpStatus.FORBIDDEN, "That delivery was sent too long before or after now"),

    /** A signed delivery addressed to another beacon, or to nothing that reads as an address. */
    BEACON_DELIVERY_FOR_ANOTHER_BEACON(
            Area.BEACON, 26, HttpStatus.FORBIDDEN, "That delivery was addressed to another beacon"),

    /** A signed delivery that has already arrived once. */
    BEACON_DELIVERY_ALREADY_TAKEN(Area.BEACON, 27, HttpStatus.CONFLICT, "That delivery has already arrived once"),

    /** A fault delivered without the fingerprint it is filed under. */
    BEACON_FAULT_FINGERPRINT_MISSING(Area.BEACON, 28, HttpStatus.BAD_REQUEST, "A fault has to carry its fingerprint"),

    /** A report delivered with nothing written in it. */
    BEACON_REPORT_MESSAGE_MISSING(Area.BEACON, 29, HttpStatus.BAD_REQUEST, "A report needs something written in it"),

    /** A delivery of figures with no entries in it. */
    BEACON_FIGURES_WITHOUT_SUBJECTS(
            Area.BEACON, 30, HttpStatus.BAD_REQUEST, "A delivery of figures needs at least one entry"),

    /** A delivery of figures with more entries than a beacon takes. */
    BEACON_FIGURES_TOO_MANY_SUBJECTS(
            Area.BEACON,
            31,
            HttpStatus.BAD_REQUEST,
            "That delivery of figures carries more entries than a beacon takes"),

    /** A delivery of figures that does not name a day it is for. */
    BEACON_FIGURES_DAY_MISSING(
            Area.BEACON, 32, HttpStatus.BAD_REQUEST, "A delivery of figures has to name the day it is for"),

    /** A delivery of figures for a day that has not happened yet. */
    BEACON_FIGURES_DAY_IN_THE_FUTURE(
            Area.BEACON,
            33,
            HttpStatus.BAD_REQUEST,
            "A delivery of figures cannot be for a day that has not happened yet"),

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

    /** A field of an attendance sheet given a type the sheet does not offer. */
    ATTENDANCE_FIELD_TYPE_NOT_OFFERED(
            Area.ATTENDANCE,
            47,
            HttpStatus.BAD_REQUEST,
            "Attendance sheets do not offer that type, so nothing was saved"),

    /** A field of an attendance sheet set up to start from a value it would not take as an answer. */
    ATTENDANCE_FIELD_DEFAULT_NOT_ACCEPTED(
            Area.ATTENDANCE, 48, HttpStatus.BAD_REQUEST, Sentences.QUESTION_DEFAULT_NOT_ACCEPTED),

    /** An attendance sheet that ends before, or at the moment, it starts. */
    ATTENDANCE_SHEET_ENDS_BEFORE_IT_STARTS(
            Area.ATTENDANCE, 49, HttpStatus.BAD_REQUEST, "The sheet has to end after it starts, so nothing was saved"),

    /** An attendance sheet running longer than a sheet may, raised with the longest it may run. */
    ATTENDANCE_SHEET_TOO_LONG(
            Area.ATTENDANCE,
            50,
            HttpStatus.BAD_REQUEST,
            "The sheet runs longer than a sheet may, so nothing was saved. The longest it may run is"),

    /** The hours a sheet counts a presence as, given as a negative number. */
    ATTENDANCE_COUNTED_HOURS_NEGATIVE(
            Area.ATTENDANCE,
            51,
            HttpStatus.BAD_REQUEST,
            "The hours a sheet counts as cannot be negative, so nothing was saved"),

    /** The hours a sheet counts a presence as, given as more than a sheet may count, raised with the most. */
    ATTENDANCE_COUNTED_HOURS_TOO_MANY(
            Area.ATTENDANCE,
            52,
            HttpStatus.BAD_REQUEST,
            "The hours a sheet counts as are more than it may count, so nothing was saved. The most it may count is"),

    /** A field of a sheet filled in with something it does not take. */
    ATTENDANCE_SHEET_ANSWER_NOT_ACCEPTED(
            Area.ATTENDANCE,
            53,
            HttpStatus.BAD_REQUEST,
            "That answer does not suit this field of the sheet, so nothing was saved"),

    /**
     * Anything written to an attendance sheet that is closed. One code for every way of writing to
     * one, because a closed sheet refuses them all for the same reason.
     */
    ATTENDANCE_SHEET_CLOSED(
            Area.ATTENDANCE,
            54,
            HttpStatus.BAD_REQUEST,
            "This attendance sheet is closed and has to be reopened first, so nothing was changed"),

    /** A member put on a sheet for a day before they joined the station. */
    ATTENDANCE_MEMBER_NOT_YET_JOINED(
            Area.ATTENDANCE,
            55,
            HttpStatus.BAD_REQUEST,
            "The member had not joined the station on this date, so nothing was saved"),

    /** A saved report that names neither a user type nor a group. */
    ATTENDANCE_REPORT_PRESET_SELECTS_NOBODY(
            Area.ATTENDANCE,
            56,
            HttpStatus.BAD_REQUEST,
            "A saved report has to name at least one user type or group, so nothing was saved"),

    /** A saved report whose user types or groups include an empty entry. */
    ATTENDANCE_REPORT_PRESET_EMPTY_ENTRY(
            Area.ATTENDANCE,
            57,
            HttpStatus.BAD_REQUEST,
            "A saved report cannot name an empty entry, so nothing was saved"),

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

    /** A block of a page, news entry or article sent with settings its kind of block does not take. */
    BLOCK_SETTINGS_REJECTED(
            Area.BODY,
            6,
            HttpStatus.BAD_REQUEST,
            "The settings of a block do not fit its kind of block, so nothing was saved"),

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

    /** A news block on a page of a station that is not here. */
    STATION_NOT_HERE_BEHIND_NEWS_BLOCK(Area.NEWS, 53, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A news block on a page of a station that keeps no public blog, so no entry is shown. */
    PUBLIC_BLOG_SWITCHED_OFF_FOR_NEWS_BLOCK(Area.NEWS, 54, HttpStatus.NOT_FOUND, Sentences.PUBLIC_BLOG_NOT_HERE),

    /**
     * A news block naming an entry that is gone, belongs to another station, is not published yet,
     * is kept to part of the station or was never put on the public blog. All of them answer alike,
     * so a block cannot tell a withheld entry from a missing one.
     */
    NEWS_BLOCK_ENTRY_NOT_HERE(Area.NEWS, 55, HttpStatus.NOT_FOUND, "That news entry is not available here"),

    /**
     * A page saved with a news block naming an entry that is not on the station's public blog. A page
     * is read by anybody, so nothing is saved rather than a block nobody outside can see.
     */
    NEWS_BLOCK_ENTRY_NOT_PUBLIC(
            Area.NEWS,
            56,
            HttpStatus.BAD_REQUEST,
            "A page can only show a news entry on the station's public blog, so nothing was saved"),

    /**
     * An article saved with a news block naming an entry that is not published, is kept to part of
     * the station or is not the station's. An article is read by every member, so it may only show
     * what every member may read.
     */
    NEWS_BLOCK_ENTRY_NOT_FOR_EVERY_MEMBER(
            Area.NEWS,
            57,
            HttpStatus.BAD_REQUEST,
            "An article can only show a news entry every member may read, so nothing was saved"),

    /** A file hung on a news entry that is not in the media library. */
    NEWS_ATTACHMENT_FILE_NOT_HERE(Area.NEWS, 58, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A file hung on a news entry that belongs to another station's media library. */
    NEWS_ATTACHMENT_FILE_ELSEWHERE(
            Area.NEWS, 59, HttpStatus.BAD_REQUEST, "That file belongs to another station, so nothing was attached"),

    /** The partner whose entry is commented on, not a partner of this station any more. */
    NEWS_COMMENT_PARTNER_NOT_HERE(Area.NEWS, 60, HttpStatus.NOT_FOUND, Sentences.PARTNER_STATION_NOT_HERE),

    /** Blocks saved onto a news entry that is not built from them. */
    NEWS_ENTRY_NOT_BUILT_FROM_BLOCKS(
            Area.NEWS, 61, HttpStatus.BAD_REQUEST, "This entry is not built from blocks, so nothing was saved"),

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

    /** A board changed by somebody who may read it but not write to it. */
    BOARD_NOT_YOURS_TO_EDIT(Area.BOARDS, 4, HttpStatus.FORBIDDEN, "This board is not yours to change"),

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

    /** A field of a board given a type a board does not offer. */
    BOARD_FIELD_TYPE_NOT_OFFERED(
            Area.BOARDS, 56, HttpStatus.BAD_REQUEST, "Boards do not offer that type of field, so nothing was saved"),

    /** The value of a required ticket field, being cleared. */
    TICKET_FIELD_VALUE_REQUIRED(
            Area.BOARDS, 57, HttpStatus.BAD_REQUEST, "This field has to be filled in, so it was not cleared"),

    /** The partner a shared board is reached through, not a partner of this station any more. */
    BOARD_PARTNER_NOT_HERE(Area.BOARDS, 58, HttpStatus.NOT_FOUND, Sentences.PARTNER_STATION_NOT_HERE),

    /** A ticket handed to somebody who is not a member of the board's station. */
    BOARD_TICKET_ASSIGNEE_NOT_A_MEMBER(
            Area.BOARDS,
            59,
            HttpStatus.BAD_REQUEST,
            "The person the ticket is handed to is not a member of the board's station, so nothing was saved"),

    /** A ticket handed to somebody who may not work on the board. */
    BOARD_TICKET_ASSIGNEE_MAY_NOT_EDIT(
            Area.BOARDS,
            60,
            HttpStatus.BAD_REQUEST,
            "The person the ticket is handed to cannot work on this board, so nothing was saved"),

    /** Documents at all, at a station that has switched them off. */
    DOCUMENTS_SWITCHED_OFF(Area.DOCUMENTS, 1, HttpStatus.NOT_FOUND, "This station does not keep documents"),

    /** The documents of a member the reader neither manages nor answers for. */
    DOCUMENT_LIST_NOT_YOURS(Area.DOCUMENTS, 2, HttpStatus.FORBIDDEN, Sentences.DOCUMENT_NOT_YOURS),

    /** A hidden document being written by somebody who may not hide things. */
    DOCUMENT_HIDING_NOT_ALLOWED(Area.DOCUMENTS, 3, HttpStatus.FORBIDDEN, "You may not mark a document as hidden"),

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

    /** A step named under a procedure it does not belong to. */
    PROCEDURE_STEP_NOT_IN_PROCEDURE(
            Area.PROCEDURES, 27, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** A step named under a procedure template it does not belong to. */
    PROCEDURE_TEMPLATE_STEP_NOT_IN_TEMPLATE(
            Area.PROCEDURES, 28, HttpStatus.NOT_FOUND, Sentences.PROCEDURE_STEP_NOT_HERE_ON_WRITE),

    /** A note on a step written by somebody who does not run the procedure. */
    PROCEDURE_STEP_NOTE_NOT_YOURS(
            Area.PROCEDURES, 29, HttpStatus.FORBIDDEN, "Only whoever runs the procedure can write a note on a step"),

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

    /** A column of a checklist written down without a label. */
    CHECKLIST_COLUMN_NEEDS_A_LABEL(
            Area.CHECKLISTS, 20, HttpStatus.BAD_REQUEST, "A column needs a label, so nothing was saved"),

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
    MAILBOX_NOT_HERE(Area.MAIL_IMPORT, 2, HttpStatus.NOT_FOUND, Sentences.MAILBOX_NOT_HERE),

    /**
     * A rule that is gone, or hangs off a mailbox of another station. One code deliberately, for the
     * same reason the mailbox has one: the lookup and the station check behind it answer alike, and
     * telling them apart would say that the rule is at a station the reader may not see.
     */
    MAILBOX_RULE_NOT_HERE(Area.MAIL_IMPORT, 3, HttpStatus.NOT_FOUND, Sentences.MAILBOX_RULE_NOT_HERE),

    /** A mailbox, gone between being saved and being read back. */
    MAILBOX_NOT_HERE_AFTER_SAVE(
            Area.MAIL_IMPORT, 4, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A mailbox rule, gone between being saved and being read back. */
    MAILBOX_RULE_NOT_HERE_AFTER_SAVE(
            Area.MAIL_IMPORT, 5, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A mailbox set up without a password. */
    MAILBOX_PASSWORD_MISSING(
            Area.MAIL_IMPORT, 6, HttpStatus.BAD_REQUEST, "A mailbox needs a password, so nothing was saved"),

    /** A mailbox password changed to nothing. */
    MAILBOX_PASSWORD_MISSING_ON_CHANGE(
            Area.MAIL_IMPORT, 7, HttpStatus.BAD_REQUEST, "A mailbox needs a password, so nothing was changed"),

    /** A mailbox run by hand on an instance that has reading mail switched off. */
    MAILBOX_RUN_SWITCHED_OFF(
            Area.MAIL_IMPORT,
            8,
            HttpStatus.BAD_REQUEST,
            "Reading mail into documents is switched off on this instance"),

    /** A mailbox rule saved without a name. */
    MAILBOX_RULE_NAME_MISSING(Area.MAIL_IMPORT, 9, HttpStatus.BAD_REQUEST, "A rule needs a name, so nothing was saved"),

    /** A mailbox rule saved with a name longer than a rule keeps. */
    MAILBOX_RULE_NAME_TOO_LONG(
            Area.MAIL_IMPORT, 10, HttpStatus.BAD_REQUEST, "The name of the rule is too long, so nothing was saved"),

    /** A mailbox rule saved without a sender it trusts, which would accept nothing. */
    MAILBOX_RULE_SENDERS_MISSING(
            Area.MAIL_IMPORT,
            11,
            HttpStatus.BAD_REQUEST,
            "A rule has to say which senders it trusts, or it accepts nothing, so nothing was saved"),

    /** A sender on a mailbox rule that is neither an address nor a domain. */
    MAILBOX_RULE_SENDER_NOT_A_PATTERN(
            Area.MAIL_IMPORT,
            12,
            HttpStatus.BAD_REQUEST,
            "A sender has to be an address or a domain written as *@domain, so nothing was saved"),

    /** A mailbox rule saved without a kind of file it takes. */
    MAILBOX_RULE_FILE_KINDS_MISSING(
            Area.MAIL_IMPORT,
            13,
            HttpStatus.BAD_REQUEST,
            "A rule has to say which kinds of file it takes, so nothing was saved"),

    /** A kind of file on a mailbox rule that the import cannot recognise. */
    MAILBOX_RULE_FILE_KIND_UNKNOWN(
            Area.MAIL_IMPORT,
            14,
            HttpStatus.BAD_REQUEST,
            "That is not a kind of file the import can recognise, so nothing was saved"),

    /** A mailbox rule that moves messages without naming the folder to move them to. */
    MAILBOX_RULE_FOLDER_MISSING(
            Area.MAIL_IMPORT,
            15,
            HttpStatus.BAD_REQUEST,
            "Moving a message needs a folder to move it to, so nothing was saved"),

    /** A mailbox whose host this instance may not connect to, or not in the way it was set up. */
    MAILBOX_HOST_NOT_REACHABLE(
            Area.MAIL_IMPORT,
            16,
            HttpStatus.BAD_REQUEST,
            "This instance will not connect to that mailbox, so nothing was saved"),

    /** A mailbox password given on an instance with no key to keep it safe with. */
    MAILBOX_PASSWORD_CANNOT_BE_KEPT(
            Area.MAIL_IMPORT,
            17,
            HttpStatus.BAD_REQUEST,
            "This instance has no encryption key set up, so a mailbox password cannot be kept safely "
                    + "and nothing was saved"),

    /** A mailbox saved without a name. */
    MAILBOX_NAME_MISSING(Area.MAIL_IMPORT, 18, HttpStatus.BAD_REQUEST, "A mailbox needs a name, so nothing was saved"),

    /** A mailbox saved with a name longer than a mailbox keeps. */
    MAILBOX_NAME_TOO_LONG(
            Area.MAIL_IMPORT, 19, HttpStatus.BAD_REQUEST, "The name of the mailbox is too long, so nothing was saved"),

    /** A mailbox saved without the server it is read from. */
    MAILBOX_HOST_MISSING(
            Area.MAIL_IMPORT, 20, HttpStatus.BAD_REQUEST, "A mailbox needs a server, so nothing was saved"),

    /** A mailbox saved with a server name longer than a mailbox keeps. */
    MAILBOX_HOST_TOO_LONG(
            Area.MAIL_IMPORT, 21, HttpStatus.BAD_REQUEST, "The server name is too long, so nothing was saved"),

    /** A mailbox saved without the user it signs in as. */
    MAILBOX_USER_MISSING(
            Area.MAIL_IMPORT, 22, HttpStatus.BAD_REQUEST, "A mailbox needs a user name, so nothing was saved"),

    /** A mailbox saved with a user name longer than a mailbox keeps. */
    MAILBOX_USER_TOO_LONG(
            Area.MAIL_IMPORT, 23, HttpStatus.BAD_REQUEST, "The user name is too long, so nothing was saved"),

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

    /** The inventory something was to be ordered for, which is gone or belongs to another station. */
    INVENTORY_NOT_HERE_ON_PROCUREMENT(Area.INVENTORY, 110, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** An order that went before it could be marked as delivered. */
    PROCUREMENT_NOT_FULFILLED(Area.INVENTORY, 111, HttpStatus.NOT_FOUND, Sentences.PROCUREMENT_NOT_HERE),

    /** An order that was already gone when its deletion was asked for. */
    PROCUREMENT_NOT_DELETED(Area.INVENTORY, 112, HttpStatus.NOT_FOUND, Sentences.PROCUREMENT_NOT_HERE),

    /** A movement that could not be read back after being moved onto another chain of steps. */
    MOVEMENT_NOT_HERE_AFTER_RECHAIN(
            Area.INVENTORY, 113, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A field of an inventory given a type an inventory does not offer. */
    INVENTORY_FIELD_TYPE_NOT_OFFERED(
            Area.INVENTORY,
            114,
            HttpStatus.BAD_REQUEST,
            "Inventories do not offer that type of field, so nothing was saved"),

    /** A chain of steps that went before whether it waits for the member's receipt could be written. */
    FLOW_RECEIPT_NOT_CHANGED(Area.INVENTORY, 275, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A movement started on a chain whose steps, once those a loss report skips are left out, are none. */
    MOVEMENT_FLOW_HAS_NO_STEPS(
            Area.INVENTORY,
            115,
            HttpStatus.BAD_REQUEST,
            "That chain of steps has no steps to walk, so the movement was not started"),

    /** A correction to a movement sent without the reason it has to carry. */
    MOVEMENT_CORRECTION_NEEDS_A_REASON(
            Area.INVENTORY,
            116,
            HttpStatus.BAD_REQUEST,
            "Correcting a movement needs a reason saying why, so nothing was changed"),

    /** A correction to a movement that is not here. */
    MOVEMENT_NOT_HERE_TO_CORRECT(Area.INVENTORY, 117, HttpStatus.NOT_FOUND, Sentences.MOVEMENT_NOT_HERE),

    /** A correction to a movement whose chain of steps has since been removed. */
    MOVEMENT_FLOW_GONE_BEFORE_CORRECTION(Area.INVENTORY, 118, HttpStatus.BAD_REQUEST, Sentences.MOVEMENT_FLOW_GONE),

    /** A movement started on a piece of gear that has since been removed. */
    MOVEMENT_PIECE_NOT_HERE(
            Area.INVENTORY,
            119,
            HttpStatus.BAD_REQUEST,
            "That piece of gear is no longer recorded, so no movement was started"),

    /** A movement started on a piece that is already leaving on another open movement. */
    MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT(
            Area.INVENTORY,
            120,
            HttpStatus.BAD_REQUEST,
            "This piece of gear is already on another movement, so finish or call that one off first"),

    /** A movement started on a piece another open movement has already promised to somebody. */
    MOVEMENT_PIECE_ALREADY_PROMISED(
            Area.INVENTORY,
            121,
            HttpStatus.BAD_REQUEST,
            "This piece of gear is promised to another movement, so finish or call that one off first"),

    /** An exchange started in an inventory of different things, which has nothing to swap a piece for. */
    MOVEMENT_NOTHING_TO_SWAP_FOR(
            Area.INVENTORY,
            122,
            HttpStatus.BAD_REQUEST,
            "This inventory holds different things, so there is nothing to swap a piece for"),

    /** A step forced without the note saying why. */
    MOVEMENT_FORCE_NEEDS_A_NOTE(
            Area.INVENTORY,
            123,
            HttpStatus.BAD_REQUEST,
            "Forcing a step needs a note saying why, so nothing was changed"),

    /** A step acknowledged or forced that is not the one the movement stands on. */
    MOVEMENT_NOT_ON_THAT_STEP(
            Area.INVENTORY,
            124,
            HttpStatus.BAD_REQUEST,
            "The movement is not standing on that step any more, so nothing was changed"),

    /** The step a movement stands on, gone from its chain before it could be walked. */
    MOVEMENT_STEP_GONE(Area.INVENTORY, 125, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A step forced that belongs to the station itself, which can simply acknowledge it. */
    MOVEMENT_STATION_STEP_NOT_FORCED(
            Area.INVENTORY,
            126,
            HttpStatus.BAD_REQUEST,
            "This step is the station's own, so acknowledge it rather than forcing it"),

    /** A step that names the arriving piece, walked without naming one. */
    MOVEMENT_STEP_NEEDS_THE_ARRIVING_PIECE(
            Area.INVENTORY,
            127,
            HttpStatus.BAD_REQUEST,
            "This step names the piece of gear that arrives, so choose it first"),

    /** A movement called off by somebody whose turn it no longer is and who no longer holds the piece. */
    MOVEMENT_NOT_YOURS_TO_CANCEL(
            Area.INVENTORY,
            128,
            HttpStatus.FORBIDDEN,
            "This movement is not on your side any more, so it was not called off"),

    /** A step walked on a movement that is not here. */
    MOVEMENT_NOT_HERE_TO_WALK(Area.INVENTORY, 129, HttpStatus.NOT_FOUND, Sentences.MOVEMENT_NOT_HERE),

    /** A step walked on a movement that is already finished or called off. */
    MOVEMENT_ALREADY_CLOSED(
            Area.INVENTORY,
            130,
            HttpStatus.BAD_REQUEST,
            "This movement is already finished or called off, so nothing was changed"),

    /** A step walked on a movement whose chain of steps has since been removed. */
    MOVEMENT_FLOW_GONE(Area.INVENTORY, 131, HttpStatus.BAD_REQUEST, Sentences.MOVEMENT_FLOW_GONE),

    /** A step acknowledged by somebody it does not belong to. */
    MOVEMENT_STEP_NOT_YOUR_TURN(
            Area.INVENTORY,
            132,
            HttpStatus.FORBIDDEN,
            "This step is for somebody else to take, so nothing was changed"),

    /** A movement moved onto the chain it belongs on, which has no steps to stand on. */
    MOVEMENT_RECHAIN_FLOW_HAS_NO_STEPS(
            Area.INVENTORY,
            133,
            HttpStatus.BAD_REQUEST,
            "The chain this movement belongs on has no steps to stand on, so nothing was changed"),

    /** A movement moved onto its chain without a step, where the chain does not say on its own where it stands. */
    MOVEMENT_RECHAIN_LANDING_NOT_CLEAR(
            Area.INVENTORY,
            134,
            HttpStatus.BAD_REQUEST,
            "The chain this movement belongs on does not say where it would stand, so choose the step"),

    /** A movement moved onto its chain at a step the chain does not have. */
    MOVEMENT_RECHAIN_LANDING_OUT_OF_RANGE(
            Area.INVENTORY,
            135,
            HttpStatus.BAD_REQUEST,
            "The chain this movement belongs on has no step at that place, so nothing was changed"),

    /** A movement moved onto another chain that is not here. */
    MOVEMENT_NOT_HERE_TO_RECHAIN(Area.INVENTORY, 136, HttpStatus.NOT_FOUND, Sentences.MOVEMENT_NOT_HERE),

    /** A movement moved onto another chain after it has finished. */
    MOVEMENT_FINISHED_BEFORE_RECHAIN(
            Area.INVENTORY,
            137,
            HttpStatus.BAD_REQUEST,
            "That movement has finished, so the chain under it no longer decides anything"),

    /** A chain written again from its preset that is not here. */
    MOVEMENT_FLOW_NOT_HERE_TO_RESTORE(Area.INVENTORY, 138, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A chain written again from its preset that belongs to the association above the station. */
    MOVEMENT_FLOW_RESTORE_BELONGS_TO_ASSOCIATION(
            Area.INVENTORY,
            139,
            HttpStatus.BAD_REQUEST,
            "That chain belongs to the association above the station, so the station cannot write it again"),

    /** A chain written again from its preset while nothing is bound to it. */
    MOVEMENT_FLOW_RESTORE_NOT_BOUND(
            Area.INVENTORY,
            140,
            HttpStatus.BAD_REQUEST,
            "Nothing is bound to that chain, so there is no preset to write it from"),

    /** A chain written again from its preset where no preset covers what it is bound to. */
    MOVEMENT_FLOW_RESTORE_NO_PRESET(
            Area.INVENTORY,
            141,
            HttpStatus.BAD_REQUEST,
            "No preset covers what that chain is bound to, so there is none to write it from"),

    /** A chain written again while movements on one of its steps have nowhere to land in the new one. */
    MOVEMENT_FLOW_RESTORE_LANDING_MISSING(
            Area.INVENTORY,
            142,
            HttpStatus.BAD_REQUEST,
            "Some movements have nowhere to stand in the new chain, so say where they land"),

    /** A landing for a chain written again that names no step to land on. */
    MOVEMENT_FLOW_RESTORE_LANDING_NAMES_NO_STEP(
            Area.INVENTORY,
            143,
            HttpStatus.BAD_REQUEST,
            "Every landing has to name the step it lands on, so nothing was changed"),

    /** A landing given for a step of the chain no movement stands on. */
    MOVEMENT_FLOW_RESTORE_LANDING_FOR_EMPTY_STEP(
            Area.INVENTORY,
            144,
            HttpStatus.BAD_REQUEST,
            "No movement stands on that step, so it needs no landing and nothing was changed"),

    /** A landing on a step the preset does not write. */
    MOVEMENT_FLOW_RESTORE_LANDING_OUT_OF_RANGE(
            Area.INVENTORY,
            145,
            HttpStatus.BAD_REQUEST,
            "The preset has no step at that place to land on, so nothing was changed"),

    /** Two landings given for the movements on the same step. */
    MOVEMENT_FLOW_RESTORE_LANDING_TWICE(
            Area.INVENTORY,
            146,
            HttpStatus.BAD_REQUEST,
            "A step was given two landings, and its movements can only stand on one, so nothing was changed"),

    /** A movement asked for a combination of owner, purpose and party no chain is bound to at the station. */
    MOVEMENT_FLOW_NOT_BOUND(Area.INVENTORY, 147, HttpStatus.BAD_REQUEST, Sentences.NO_FLOW_FOR_THIS_MOVEMENT),

    /** A step of a chain changed that is not here. */
    MOVEMENT_FLOW_STEP_NOT_HERE_TO_CHANGE(Area.INVENTORY, 148, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A step of a chain archived that is not here. */
    MOVEMENT_FLOW_STEP_NOT_HERE_TO_ARCHIVE(Area.INVENTORY, 149, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A chain whose purpose was asked for, gone between being named and being read. */
    MOVEMENT_FLOW_NOT_HERE_FOR_PURPOSE(Area.INVENTORY, 150, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A chain bound to a combination that is not here. */
    MOVEMENT_FLOW_NOT_HERE_TO_BIND(Area.INVENTORY, 151, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A chain bound at a station it does not belong to. */
    MOVEMENT_FLOW_NOT_YOURS_TO_BIND(
            Area.INVENTORY, 152, HttpStatus.BAD_REQUEST, "That chain belongs to somebody else, so it was not bound"),

    /** A chain bound to a combination whose purpose is not the chain's own. */
    MOVEMENT_FLOW_BOUND_TO_OTHER_PURPOSE(
            Area.INVENTORY,
            153,
            HttpStatus.BAD_REQUEST,
            "That chain is for a different kind of movement, so it was not bound"),

    /** A piece handed out at the counter that an open movement has promised to somebody. */
    CUSTODY_PIECE_PROMISED(
            Area.INVENTORY,
            154,
            HttpStatus.BAD_REQUEST,
            "This piece of gear is promised to a movement, so hand it over there or call that one off"),

    /** A piece handed out that is missing, with a partner, or otherwise somewhere it cannot be handed out of. */
    CUSTODY_NOT_HANDED_OUT_FROM_HERE(
            Area.INVENTORY,
            155,
            HttpStatus.BAD_REQUEST,
            "This piece of gear cannot be handed out from where it is now, so nothing was changed"),

    /** Gear borrowed from a partner station marked missing here rather than on the lending request. */
    CUSTODY_BORROWED_NOT_MARKED_LOST(
            Area.INVENTORY, 156, HttpStatus.BAD_REQUEST, Sentences.BORROWED_GEAR_LOST_AT_PARTNER),

    /** A step that hands gear to a member, walked on a movement that names no member. */
    CUSTODY_STEP_NAMES_NO_MEMBER(
            Area.INVENTORY,
            157,
            HttpStatus.BAD_REQUEST,
            "This step hands the gear to a member but the movement names none, so nothing was changed"),

    /** A loss reported for a piece of gear that is not here. */
    LOSS_REPORT_PIECE_NOT_HERE(Area.INVENTORY, 158, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A loss reported for gear that is not recorded as missing. */
    LOSS_REPORT_PIECE_NOT_MISSING(
            Area.INVENTORY,
            159,
            HttpStatus.BAD_REQUEST,
            "This gear is not recorded as missing, so there is nothing to report"),

    /** A loss reported for gear the station owns itself, which has nobody above it to report to. */
    LOSS_REPORT_STATION_OWNS_IT(
            Area.INVENTORY,
            160,
            HttpStatus.BAD_REQUEST,
            "The station owns this gear itself, so there is nobody to report it to"),

    /** A loss reported for gear whose owning association is not here. */
    LOSS_REPORT_OWNER_NOT_HERE(
            Area.INVENTORY,
            161,
            HttpStatus.NOT_FOUND,
            "The association that owns this gear is not here to answer, so nothing was reported"),

    /** A loss reported without the note the owning association asks for. */
    LOSS_REPORT_NEEDS_A_NOTE(
            Area.INVENTORY,
            162,
            HttpStatus.BAD_REQUEST,
            "The association that owns this gear asks for a note with a loss report, so nothing was reported"),

    /** A loss reported without the document the owning association asks for. */
    LOSS_REPORT_NEEDS_A_DOCUMENT(
            Area.INVENTORY,
            163,
            HttpStatus.BAD_REQUEST,
            "The association that owns this gear asks for a document with a loss report, so nothing was reported"),

    /** A line of a stock-taking that names a size the inventory does not have. */
    INTAKE_SIZE_NOT_IN_INVENTORY(
            Area.INVENTORY,
            164,
            HttpStatus.BAD_REQUEST,
            "A line names a size this inventory does not have, so nothing was taken in"),

    /** A line of a stock-taking that names a member who is not at the station. */
    INTAKE_MEMBER_NOT_AT_STATION(
            Area.INVENTORY,
            165,
            HttpStatus.BAD_REQUEST,
            "A line names a member who is not at this station, so nothing was taken in"),

    /** A stock-taking that gives the same number to two of its lines. */
    INTAKE_NUMBER_TWICE(
            Area.INVENTORY, 166, HttpStatus.BAD_REQUEST, "A number appears twice in the list, so nothing was taken in"),

    /** A line of a stock-taking whose number is already on another piece of gear. */
    INTAKE_NUMBER_TAKEN(
            Area.INVENTORY,
            167,
            HttpStatus.BAD_REQUEST,
            "A number in the list is already on another piece of gear, so nothing was taken in"),

    /** The kinds sharing a key across stations, asked for a kind that is not here. */
    ART_NOT_HERE_TO_MATCH(Area.INVENTORY, 168, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** A kind created under a name the inventory already has. */
    ART_NAME_TAKEN_ON_CREATE(Area.INVENTORY, 169, HttpStatus.BAD_REQUEST, Sentences.ART_NAME_TAKEN),

    /** A kind changed to a name another kind of the inventory already has. */
    ART_NAME_TAKEN_ON_CHANGE(Area.INVENTORY, 170, HttpStatus.BAD_REQUEST, Sentences.ART_NAME_TAKEN),

    /** A kind changed that is not here. */
    ART_NOT_HERE_TO_CHANGE(Area.INVENTORY, 171, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** Kinds written for an inventory that is not here. */
    ART_INVENTORY_NOT_HERE(Area.INVENTORY, 172, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** Kinds written for an inventory of one thing in many copies, which is ordered by its sizes instead. */
    ART_INVENTORY_UNIFORM(
            Area.INVENTORY,
            173,
            HttpStatus.BAD_REQUEST,
            "Only an inventory of different things has kinds, and this one holds one thing in many copies"),

    /** A kind created or changed with no name. */
    ART_NEEDS_A_NAME(Area.INVENTORY, 174, HttpStatus.BAD_REQUEST, "A kind needs a name, so nothing was saved"),

    /** A kind assigned or merged that is not here. */
    ART_NOT_HERE(Area.INVENTORY, 175, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** A kind assigned or merged within an inventory it does not belong to. */
    ART_IN_ANOTHER_INVENTORY(
            Area.INVENTORY,
            176,
            HttpStatus.BAD_REQUEST,
            "That kind belongs to another inventory, so nothing was changed"),

    /** Gear given a kind or merged that is not here. */
    ART_PIECE_NOT_HERE(Area.INVENTORY, 177, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** Gear given a kind of an inventory it does not belong to. */
    ART_PIECE_IN_ANOTHER_INVENTORY(
            Area.INVENTORY,
            178,
            HttpStatus.BAD_REQUEST,
            "That piece of gear belongs to another inventory, so nothing was changed"),

    /** A colour for a kind or piece of gear that is not written as a hash and six hexadecimal digits. */
    GLYPH_COLOUR_NOT_READABLE(
            Area.INVENTORY,
            179,
            HttpStatus.BAD_REQUEST,
            "A colour is written as # followed by six digits or letters from a to f, so nothing was saved"),

    /** A tag created at a station with a name that is blank once trimmed. */
    INVENTORY_TAG_NAME_MISSING_ON_CREATE(Area.INVENTORY, 180, HttpStatus.BAD_REQUEST, Sentences.TAG_NAME_MISSING),

    /** A tag renamed to a name that is blank once trimmed. */
    INVENTORY_TAG_NAME_MISSING_ON_CHANGE(Area.INVENTORY, 181, HttpStatus.BAD_REQUEST, Sentences.TAG_NAME_MISSING),

    /** A tag renamed onto the name another tag of the station already carries. */
    INVENTORY_TAG_NAME_TAKEN(
            Area.INVENTORY,
            182,
            HttpStatus.BAD_REQUEST,
            "The station already has a tag of that name, so nothing was saved"),

    /** A tag that went between being renamed and being read back. */
    INVENTORY_TAG_NOT_HERE_AFTER_CHANGE(
            Area.INVENTORY, 183, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /**
     * A tag renamed that is gone or belongs to another station. One code for both, so the answer
     * does not say that a tag exists elsewhere.
     */
    INVENTORY_TAG_NOT_HERE_ON_CHANGE(Area.INVENTORY, 184, HttpStatus.NOT_FOUND, Sentences.INVENTORY_TAG_NOT_HERE),

    /**
     * A tag deleted that is gone or belongs to another station. One code for both, so the answer
     * does not say that a tag exists elsewhere.
     */
    INVENTORY_TAG_NOT_HERE_ON_DELETE(Area.INVENTORY, 185, HttpStatus.NOT_FOUND, Sentences.INVENTORY_TAG_NOT_HERE),

    /**
     * A piece of gear whose tags were asked for that is gone, sits in an inventory that is gone, or
     * belongs to another station. One code for all three, so the answer does not say it exists
     * elsewhere.
     */
    INVENTORY_TAG_ITEM_NOT_HERE_ON_READ(Area.INVENTORY, 186, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /**
     * A piece of gear given new tags that is gone, sits in an inventory that is gone, or belongs to
     * another station. One code for all three, so the answer does not say it exists elsewhere.
     */
    INVENTORY_TAG_ITEM_NOT_HERE_ON_TAGGING(Area.INVENTORY, 187, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /**
     * An inventory whose tags were asked for that is gone or belongs to another station. One code
     * for both, so the answer does not say it exists elsewhere.
     */
    INVENTORY_TAG_INVENTORY_NOT_HERE(Area.INVENTORY, 188, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A self-check answer taken as it stands that first needs the member's record putting right. */
    SELF_CHECK_REVIEW_RECORD_NEEDS_PUTTING_RIGHT(
            Area.INVENTORY,
            189,
            HttpStatus.BAD_REQUEST,
            "This answer needs the record putting right before it can be taken, so nothing was changed"),

    /** A self-check answer somebody else settled between being read and being taken. */
    SELF_CHECK_REVIEW_SETTLED_BEFORE_TAKING(
            Area.INVENTORY, 190, HttpStatus.CONFLICT, Sentences.SELF_CHECK_ANSWER_ALREADY_SETTLED),

    /** A record put right for a self-check answer that does not say the record is wrong. */
    SELF_CHECK_REVIEW_NOTHING_TO_PUT_RIGHT(
            Area.INVENTORY,
            191,
            HttpStatus.BAD_REQUEST,
            "This answer does not ask for the record to be put right, so nothing was changed"),

    /** A self-check answer somebody else settled while its record was being put right. */
    SELF_CHECK_REVIEW_SETTLED_BEFORE_CORRECTING(
            Area.INVENTORY, 192, HttpStatus.CONFLICT, Sentences.SELF_CHECK_ANSWER_ALREADY_SETTLED),

    /** A self-check answer sent back to the member without a reason. */
    SELF_CHECK_REVIEW_REASON_MISSING(
            Area.INVENTORY,
            193,
            HttpStatus.BAD_REQUEST,
            "Say why the answer cannot be settled, so nothing was sent back"),

    /** A self-check answer somebody else settled before it could be sent back. */
    SELF_CHECK_REVIEW_SETTLED_BEFORE_SENDING_BACK(
            Area.INVENTORY, 194, HttpStatus.CONFLICT, Sentences.SELF_CHECK_ANSWER_ALREADY_SETTLED),

    /** A record put right for a self-check answer without saying what the member actually holds. */
    SELF_CHECK_REVIEW_CORRECTION_MISSING(
            Area.INVENTORY,
            195,
            HttpStatus.BAD_REQUEST,
            "Say what the member is actually holding, so nothing was changed"),

    /**
     * A self-check submission about the reviewer's own gear, which they may not sign off. Also the
     * reason the review screen gives for not offering the sign-off.
     */
    SELF_CHECK_REVIEW_OF_OWN_GEAR(Area.INVENTORY, 196, HttpStatus.FORBIDDEN, "This submission is about your own gear"),

    /**
     * A self-check submission the reviewer handed in themselves, which they may not sign off. Also
     * the reason the review screen gives for not offering the sign-off.
     */
    SELF_CHECK_REVIEW_OF_OWN_SUBMISSION(
            Area.INVENTORY, 197, HttpStatus.FORBIDDEN, "You entered this submission yourself"),

    /** A self-check answer the reviewer entered themselves, which they may not settle. */
    SELF_CHECK_REVIEW_OF_OWN_ANSWER(Area.INVENTORY, 198, HttpStatus.FORBIDDEN, "You entered this answer yourself"),

    /**
     * A self-check under review that is gone or belongs to another station. One code for both, so
     * the answer does not say that it exists elsewhere.
     */
    SELF_CHECK_REVIEW_TASK_NOT_HERE(Area.INVENTORY, 199, HttpStatus.NOT_FOUND, Sentences.SELF_CHECK_NOT_HERE),

    /**
     * A self-check answer under review that is gone or belongs to another submission. One code for
     * both, so the answer does not say that it exists elsewhere.
     */
    SELF_CHECK_REVIEW_ANSWER_NOT_HERE(Area.INVENTORY, 200, HttpStatus.NOT_FOUND, "That answer is not here any more"),

    /** A self-check answer settled on a submission that has not been handed in, or is finished. */
    SELF_CHECK_REVIEW_TASK_NOT_WAITING(
            Area.INVENTORY, 201, HttpStatus.CONFLICT, "This self-check is not waiting to be read"),

    /** A self-check answer settled that was already settled when it was opened for settling. */
    SELF_CHECK_REVIEW_ANSWER_ALREADY_SETTLED(
            Area.INVENTORY, 202, HttpStatus.CONFLICT, Sentences.SELF_CHECK_ANSWER_ALREADY_SETTLED),

    /** A check of a member's gear begun while another checker already holds that member. */
    INVENTORY_CHECK_MEMBER_ALREADY_LOCKED(
            Area.INVENTORY, 203, HttpStatus.CONFLICT, Sentences.INVENTORY_CHECK_MEMBER_TAKEN),

    /** A check of a member's gear begun at the moment another checker took the same member. */
    INVENTORY_CHECK_MEMBER_LOCKED_MEANWHILE(
            Area.INVENTORY, 204, HttpStatus.CONFLICT, Sentences.INVENTORY_CHECK_MEMBER_TAKEN),

    /** A record put right against an inventory that is gone. */
    INVENTORY_CHECK_CORRECTION_INVENTORY_NOT_HERE(
            Area.INVENTORY, 205, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** A record put right with a new piece in an inventory of both owners, naming neither. */
    INVENTORY_CHECK_CORRECTION_OWNER_MISSING(
            Area.INVENTORY,
            206,
            HttpStatus.BAD_REQUEST,
            "This inventory holds gear of both owners, so say whose the new piece is"),

    /** A record put right with a new piece written down as a partner station's. */
    INVENTORY_CHECK_CORRECTION_OWNED_BY_PARTNER(
            Area.INVENTORY,
            207,
            HttpStatus.BAD_REQUEST,
            "A new piece cannot be written down as a partner station's, so nothing was changed"),

    /** A free piece picked to put a record right that is gone. */
    INVENTORY_CHECK_PICKED_ITEM_NOT_HERE(Area.INVENTORY, 208, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A free piece picked to put a record right that sits in another inventory. */
    INVENTORY_CHECK_PICKED_ITEM_IN_OTHER_INVENTORY(
            Area.INVENTORY,
            209,
            HttpStatus.BAD_REQUEST,
            "That piece of gear sits in another inventory, so nothing was changed"),

    /** A piece picked to put a record right that somebody already holds. */
    INVENTORY_CHECK_PICKED_ITEM_TAKEN(
            Area.INVENTORY,
            210,
            HttpStatus.BAD_REQUEST,
            "That piece of gear is already with somebody, so nothing was changed"),

    /** The piece a correction takes off a member's record, gone by the time it is taken off. */
    INVENTORY_CHECK_REPLACED_ITEM_NOT_HERE(Area.INVENTORY, 211, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** The piece a correction takes off a member's record, which is not on that member's record. */
    INVENTORY_CHECK_REPLACED_ITEM_NOT_THE_MEMBERS(
            Area.INVENTORY, 212, HttpStatus.BAD_REQUEST, Sentences.ITEM_NOT_ON_MEMBERS_RECORD),

    /** Self-checks handed out without naming a single member. */
    SELF_CHECK_NO_MEMBER_NAMED(
            Area.INVENTORY, 213, HttpStatus.BAD_REQUEST, "Name at least one member to ask, so nothing was handed out"),

    /** A self-check handed to a member who is not of this station. */
    SELF_CHECK_MEMBER_NOT_OF_STATION(
            Area.INVENTORY, 214, HttpStatus.BAD_REQUEST, "One of those members is not of this station"),

    /** A self-check handed to a member who has left the station. */
    SELF_CHECK_FOR_FORMER_MEMBER(
            Area.INVENTORY, 215, HttpStatus.BAD_REQUEST, "A former member cannot be asked to check their gear"),

    /** Self-check answers saved with nothing in them. */
    SELF_CHECK_NOTHING_SAID(
            Area.INVENTORY, 216, HttpStatus.BAD_REQUEST, "Give at least one answer, so nothing was saved"),

    /** A self-check handed in that was handed in at the same moment from somewhere else. */
    SELF_CHECK_ALREADY_HANDED_IN(
            Area.INVENTORY, 217, HttpStatus.CONFLICT, "This self-check has already been handed in"),

    /** A report held back on a self-check about a piece of gear that is gone. */
    SELF_CHECK_HELD_BACK_ITEM_NOT_HERE(Area.INVENTORY, 218, HttpStatus.BAD_REQUEST, Sentences.ITEM_NOT_HERE),

    /** A report held back on a self-check about a piece that is not on the member's record. */
    SELF_CHECK_HELD_BACK_ITEM_NOT_THE_MEMBERS(
            Area.INVENTORY, 219, HttpStatus.BAD_REQUEST, Sentences.ITEM_NOT_ON_MEMBERS_RECORD),

    /** A loss held back on a self-check about gear borrowed from a partner station. */
    SELF_CHECK_HELD_BACK_LOSS_OF_BORROWED_GEAR(
            Area.INVENTORY, 220, HttpStatus.BAD_REQUEST, Sentences.BORROWED_GEAR_LOST_AT_PARTNER),

    /** A report held back on a self-check before the size the member holds has been saved. */
    SELF_CHECK_HELD_BACK_WITHOUT_SAVED_SIZE(
            Area.INVENTORY,
            221,
            HttpStatus.BAD_REQUEST,
            "Save the size you are actually holding before reporting anything about this piece"),

    /** A second report of the same kind held back on a self-check about the same piece. */
    SELF_CHECK_HELD_BACK_ALREADY_REPORTED(
            Area.INVENTORY,
            222,
            HttpStatus.BAD_REQUEST,
            "This has already been reported for this piece of gear, so nothing was saved"),

    /** A swap held back on a self-check for a piece whose inventory is gone. */
    SELF_CHECK_HELD_BACK_INVENTORY_NOT_HERE(
            Area.INVENTORY, 223, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE_BEHIND_ITEM),

    /** A swap held back on a self-check for gear that is not kept by size. */
    SELF_CHECK_HELD_BACK_SWAP_WITHOUT_SIZES(
            Area.INVENTORY, 224, HttpStatus.BAD_REQUEST, "This kind of gear is not swapped by size"),

    /** A swap held back on a self-check asking for a size the gear does not come in. */
    SELF_CHECK_HELD_BACK_SIZE_NOT_OFFERED(
            Area.INVENTORY, 225, HttpStatus.BAD_REQUEST, Sentences.SELF_CHECK_SIZE_NOT_OFFERED),

    /**
     * A self-check that is gone or belongs to another station, opened by the member answering it.
     * One code for both, so the answer does not say that it exists elsewhere.
     */
    SELF_CHECK_NOT_HERE(Area.INVENTORY, 226, HttpStatus.NOT_FOUND, Sentences.SELF_CHECK_NOT_HERE),

    /** A self-check of a member the caller does not answer for. */
    SELF_CHECK_NOT_YOURS_TO_ANSWER(
            Area.INVENTORY, 227, HttpStatus.FORBIDDEN, "This self-check belongs to somebody you do not answer for"),

    /** A self-check answered or handed in after it was handed in or closed. */
    SELF_CHECK_CLOSED(
            Area.INVENTORY, 228, HttpStatus.CONFLICT, "This self-check no longer takes answers, so nothing was saved"),

    /** A self-check answer that says nothing at all. */
    SELF_CHECK_ANSWER_EMPTY(
            Area.INVENTORY, 229, HttpStatus.BAD_REQUEST, "Every answer has to say something, so nothing was saved"),

    /** A self-check answer meant for an empty place, given about a piece of gear. */
    SELF_CHECK_PLACE_ANSWER_ON_A_PIECE(
            Area.INVENTORY,
            230,
            HttpStatus.BAD_REQUEST,
            "That answer is about an empty place, not about a piece of gear"),

    /** A self-check answer about a piece of gear that is gone. */
    SELF_CHECK_ANSWERED_ITEM_NOT_HERE(Area.INVENTORY, 231, HttpStatus.BAD_REQUEST, Sentences.ITEM_NOT_HERE),

    /** A self-check answer about a piece that is not on the member's record. */
    SELF_CHECK_ANSWERED_ITEM_NOT_THE_MEMBERS(
            Area.INVENTORY, 232, HttpStatus.BAD_REQUEST, Sentences.ITEM_NOT_ON_MEMBERS_RECORD),

    /** A self-check answer saying the station's own gear is missing, which belongs with the losses. */
    SELF_CHECK_OWN_GEAR_MISSING_NOT_REPORTED_HERE(
            Area.INVENTORY,
            233,
            HttpStatus.BAD_REQUEST,
            "Report the station's own gear as missing where losses are reported"),

    /** A self-check answer saying a piece turned up that nobody reported missing. */
    SELF_CHECK_TURNED_UP_BUT_NOT_MISSING(
            Area.INVENTORY,
            234,
            HttpStatus.BAD_REQUEST,
            "This piece of gear is not recorded as missing, so it cannot have turned up"),

    /** A self-check answer meant for a piece of gear, given without naming one. */
    SELF_CHECK_PIECE_ANSWER_WITHOUT_PIECE(
            Area.INVENTORY,
            235,
            HttpStatus.BAD_REQUEST,
            "That answer is about a piece of gear, and no piece was named"),

    /** A self-check answer about an empty place that does not say which place. */
    SELF_CHECK_PLACE_NOT_NAMED(
            Area.INVENTORY, 236, HttpStatus.BAD_REQUEST, "An answer about an empty place has to say which one"),

    /** A self-check answer about gear of a kind the member is not asked to hold. */
    SELF_CHECK_KIND_NOT_ASKED_OF_MEMBER(
            Area.INVENTORY, 237, HttpStatus.BAD_REQUEST, "Nothing of this kind is asked of this member"),

    /** A self-check answer about an empty place the member does not have. */
    SELF_CHECK_PLACE_NOT_THERE(Area.INVENTORY, 238, HttpStatus.BAD_REQUEST, "This member has no such empty place"),

    /** A number typed on a self-check answer that is not about something the member holds. */
    SELF_CHECK_NUMBER_ON_WRONG_ANSWER(
            Area.INVENTORY, 239, HttpStatus.BAD_REQUEST, "Only a place you are holding something for takes a number"),

    /** A size given on a self-check answer that does not say what the member actually holds. */
    SELF_CHECK_SIZE_ON_WRONG_ANSWER(
            Area.INVENTORY,
            240,
            HttpStatus.BAD_REQUEST,
            "Only an answer that says what the member actually holds takes a size"),

    /** A size given on a self-check answer that the gear does not come in. */
    SELF_CHECK_ANSWERED_SIZE_NOT_OFFERED(
            Area.INVENTORY, 241, HttpStatus.BAD_REQUEST, Sentences.SELF_CHECK_SIZE_NOT_OFFERED),

    /** A size added to an inventory that is gone. */
    INVENTORY_NOT_HERE_FOR_SIZE(Area.INVENTORY, 242, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** A size added to an inventory that holds different things rather than one thing in many copies. */
    INVENTORY_SIZE_ON_A_COLLECTION(
            Area.INVENTORY,
            243,
            HttpStatus.BAD_REQUEST,
            "This inventory holds different things, so it keeps no size list and nothing was saved"),

    /** A requirement written for an inventory that is gone. */
    INVENTORY_NOT_HERE_FOR_REQUIREMENT(Area.INVENTORY, 244, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** A requirement written for an inventory that holds different things. */
    INVENTORY_REQUIREMENT_ON_A_COLLECTION(
            Area.INVENTORY,
            245,
            HttpStatus.BAD_REQUEST,
            "This inventory holds different things, so a requirement does not apply to it and nothing was saved"),

    /** More of something ordered for an inventory that is gone. */
    INVENTORY_NOT_HERE_FOR_ORDER(Area.INVENTORY, 246, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** More of something ordered for an inventory that holds different things. */
    INVENTORY_ORDER_ON_A_COLLECTION(
            Area.INVENTORY,
            247,
            HttpStatus.BAD_REQUEST,
            "This inventory holds different things, so more of it cannot be ordered and nothing was saved"),

    /** The shelf for borrowed gear deleted while something is still on it. */
    INVENTORY_BORROWED_SHELF_NOT_EMPTY(
            Area.INVENTORY,
            248,
            HttpStatus.BAD_REQUEST,
            "This shelf still holds gear belonging to somebody else, so it was not deleted"),

    /** A piece of gear written straight onto the shelf for borrowed gear. */
    INVENTORY_BORROWED_SHELF_ON_CREATE(
            Area.INVENTORY, 249, HttpStatus.BAD_REQUEST, Sentences.BORROWED_SHELF_ONLY_BORROWED),

    /** A piece of gear with a named owner written straight onto the shelf for borrowed gear. */
    INVENTORY_BORROWED_SHELF_ON_OWNED_CREATE(
            Area.INVENTORY, 250, HttpStatus.BAD_REQUEST, Sentences.BORROWED_SHELF_ONLY_BORROWED),

    /** A piece of gear moved onto the shelf for borrowed gear. */
    INVENTORY_BORROWED_SHELF_ON_MOVE(
            Area.INVENTORY, 251, HttpStatus.BAD_REQUEST, Sentences.BORROWED_SHELF_ONLY_BORROWED),

    /** A piece of gear written down by hand as a partner station's. */
    INVENTORY_PARTNER_GEAR_BY_HAND(
            Area.INVENTORY,
            252,
            HttpStatus.BAD_REQUEST,
            "Gear belonging to a partner station arrives by handover, not by hand, so nothing was saved"),

    /** A piece of gear created with a kind that belongs to another inventory. */
    INVENTORY_ITEM_KIND_ELSEWHERE_ON_CREATE(
            Area.INVENTORY, 253, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_KIND_ELSEWHERE),

    /** A piece of gear changed to a kind that belongs to another inventory. */
    INVENTORY_ITEM_KIND_ELSEWHERE_ON_CHANGE(
            Area.INVENTORY, 254, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_KIND_ELSEWHERE),

    /** A piece of gear created in an inventory that is gone, found while checking its owner. */
    INVENTORY_NOT_HERE_FOR_OWNER_CHECK(Area.INVENTORY, 255, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** The station's own gear written into an inventory that only holds gear of the body above it. */
    INVENTORY_HOLDS_NO_STATION_GEAR(
            Area.INVENTORY,
            256,
            HttpStatus.BAD_REQUEST,
            "This inventory does not hold the station's own gear, so nothing was saved"),

    /** Gear of the body above the station written into an inventory that only holds the station's own. */
    INVENTORY_HOLDS_NO_ASSOCIATION_GEAR(
            Area.INVENTORY,
            257,
            HttpStatus.BAD_REQUEST,
            "This inventory does not hold gear owned by the association, so nothing was saved"),

    /** A requirement naming a group of stations for an inventory that is gone. */
    INVENTORY_NOT_HERE_FOR_REQUIREMENT_GROUP(Area.INVENTORY, 258, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** A requirement naming a group of stations that is gone. */
    INVENTORY_REQUIREMENT_GROUP_NOT_HERE(
            Area.INVENTORY,
            259,
            HttpStatus.BAD_REQUEST,
            "That group of stations is not here any more, so nothing was saved"),

    /** A requirement naming a group of stations filed by another association. */
    INVENTORY_REQUIREMENT_GROUP_OF_ANOTHER_ASSOCIATION(
            Area.INVENTORY,
            260,
            HttpStatus.BAD_REQUEST,
            "A requirement can only name a group of stations of the association that wrote it"),

    /** Gear recorded as an association's in an inventory that is gone. */
    INVENTORY_NOT_HERE_FOR_ASSOCIATION_CHECK(Area.INVENTORY, 261, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** Gear recorded as belonging to an association the station does not answer to. */
    INVENTORY_GEAR_OF_ANOTHER_ASSOCIATION(
            Area.INVENTORY,
            262,
            HttpStatus.BAD_REQUEST,
            "Gear can only be recorded as belonging to the association this station answers to"),

    /** A value on a piece of gear that the field describing it does not take. */
    INVENTORY_ITEM_FIELD_VALUE_NOT_ACCEPTED(
            Area.INVENTORY, 263, HttpStatus.BAD_REQUEST, "That value does not suit this field, so nothing was saved"),

    /** A piece of gear moved that went before the move reached it. */
    INVENTORY_ITEM_NOT_HERE_TO_MOVE(Area.INVENTORY, 264, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** A piece of gear moved into an inventory that is gone. */
    INVENTORY_MOVE_TARGET_NOT_HERE(Area.INVENTORY, 265, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE),

    /** A piece of gear moved out of an inventory that is gone. */
    INVENTORY_MOVE_SOURCE_NOT_HERE(
            Area.INVENTORY, 266, HttpStatus.BAD_REQUEST, Sentences.INVENTORY_NOT_HERE_BEHIND_ITEM),

    /** A piece of gear moved into an inventory of another station. */
    INVENTORY_MOVE_TO_ANOTHER_STATION(
            Area.INVENTORY,
            267,
            HttpStatus.BAD_REQUEST,
            "A piece of gear can only be moved into an inventory of the same station"),

    /** A new piece made and handed out from an inventory that is gone. */
    INVENTORY_NOT_HERE_TO_HAND_OUT_NEW(Area.INVENTORY, 268, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** Gear borrowed from a partner station, described anew by the station holding it. */
    INVENTORY_PARTNER_GEAR_NOT_YOURS_TO_DESCRIBE(
            Area.INVENTORY,
            269,
            HttpStatus.FORBIDDEN,
            "This gear belongs to a partner station and can only be described by them"),

    /** Gear borrowed from a partner station, moved by the station holding it. */
    INVENTORY_PARTNER_GEAR_NOT_YOURS_TO_MOVE(
            Area.INVENTORY,
            270,
            HttpStatus.FORBIDDEN,
            "This gear belongs to a partner station and can only be moved by them"),

    /** Gear borrowed from a partner station, deleted by the station holding it. */
    INVENTORY_PARTNER_GEAR_NOT_YOURS_TO_DELETE(
            Area.INVENTORY,
            271,
            HttpStatus.FORBIDDEN,
            "This gear belongs to a partner station and can only be deleted by them"),

    /** Gear of the body above the station, described anew by somebody other than that body. */
    INVENTORY_ASSOCIATION_GEAR_NOT_YOURS_TO_DESCRIBE(
            Area.INVENTORY,
            272,
            HttpStatus.FORBIDDEN,
            "This gear belongs to the body above the station and can only be described by them"),

    /** Gear of the body above the station, moved by somebody other than that body. */
    INVENTORY_ASSOCIATION_GEAR_NOT_YOURS_TO_MOVE(
            Area.INVENTORY,
            273,
            HttpStatus.FORBIDDEN,
            "This gear belongs to the body above the station and can only be moved by them"),

    /** Gear of the body above the station, deleted by somebody other than that body. */
    INVENTORY_ASSOCIATION_GEAR_NOT_YOURS_TO_DELETE(
            Area.INVENTORY,
            274,
            HttpStatus.FORBIDDEN,
            "This gear belongs to the body above the station and can only be deleted by them"),

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

    /** A registration made for somebody the caller neither is nor looks after. */
    MEMBER_NOT_YOURS_TO_REGISTER(Area.EVENTS, 17, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

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

    /**
     * An appointment this reader is not among the people it was meant for. Answered exactly like one
     * that does not exist, so its id cannot be used to learn that a hidden appointment is there.
     */
    EVENT_NOT_YOURS_TO_SEE(Area.EVENTS, 82, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

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

    /**
     * An event block naming an appointment that is gone, belongs to another station or is hidden
     * from the reader. All three answer alike, so a block cannot tell a hidden one from a missing one.
     */
    EVENT_BLOCK_APPOINTMENT_NOT_HERE(Area.EVENTS, 92, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /**
     * An appointment an event block could not name: it has no public id to be named by, or it is
     * kept to part of the station, which an article read by every member may not show.
     */
    EVENT_BLOCK_REFERENCE_NOT_HERE(Area.EVENTS, 93, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A whole series called off for an appointment that is not a series and is called off by its date. */
    ONE_TIME_EVENT_CANCELLED_AS_SERIES(
            Area.EVENTS,
            94,
            HttpStatus.BAD_REQUEST,
            "A one-time appointment is cancelled by its date, not as a series, so nothing was changed"),

    /** A series called off a second time. */
    SERIES_ALREADY_CANCELLED(Area.EVENTS, 95, HttpStatus.CONFLICT, "This series is already cancelled"),

    /** A date to call off or bring back that does not read as a date. */
    CANCELLATION_DAY_NOT_A_DATE(Area.EVENTS, 96, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A date to call off that the appointment does not fall on, or that a break of the station takes out. */
    DATE_TO_CANCEL_NOT_A_DATE_OF_THE_EVENT(
            Area.EVENTS,
            97,
            HttpStatus.BAD_REQUEST,
            "The appointment does not take place on that date, so there is nothing to cancel"),

    /** A date to call off that is already behind the station. */
    DATE_TO_CANCEL_IN_THE_PAST(
            Area.EVENTS, 98, HttpStatus.BAD_REQUEST, "That date is already past, so it can no longer be cancelled"),

    /** A date called off that already is, on its own or with the whole series. */
    DATE_ALREADY_CANCELLED(Area.EVENTS, 99, HttpStatus.CONFLICT, "That date is already cancelled"),

    /** A date to bring back that is not called off. */
    DATE_TO_RESTORE_NOT_CANCELLED(
            Area.EVENTS, 100, HttpStatus.CONFLICT, "That date is not cancelled, so there is nothing to restore"),

    /** A date to bring back that is already behind the station. */
    DATE_TO_RESTORE_IN_THE_PAST(
            Area.EVENTS, 101, HttpStatus.BAD_REQUEST, "That date is already past, so it can no longer be restored"),

    /** A date to bring back of a series that was called off as a whole, which stays final. */
    DATE_OF_CANCELLED_SERIES_NOT_RESTORED(
            Area.EVENTS,
            102,
            HttpStatus.CONFLICT,
            "The whole series is cancelled, so none of its dates can be restored"),

    /** An attendance sheet taken for a date of an appointment that was called off. */
    ATTENDANCE_DAY_CANCELLED(
            Area.EVENTS,
            103,
            HttpStatus.BAD_REQUEST,
            "That date of the appointment was cancelled, so no attendance is taken for it"),

    /** A registration or a decline for a day a break of the station takes out of the series. */
    REGISTRATION_DAY_IN_A_BREAK(
            Area.EVENTS,
            104,
            HttpStatus.BAD_REQUEST,
            "The station takes a break on that day, so the appointment does not take place"),

    /**
     * A registration or a decline, local or from a partner station, for a date that was called off,
     * on its own or with the whole series.
     */
    REGISTRATION_DAY_CANCELLED(
            Area.EVENTS, 105, HttpStatus.BAD_REQUEST, "That date was cancelled, so it takes no registrations"),

    /** An appointment that is gone, or belongs to another station. */
    EVENT_NOT_HERE(Area.EVENTS, 106, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /**
     * A page saved with an event block naming an appointment that is not on the public calendar. A
     * page is read by anybody, so nothing is saved rather than a block nobody outside can see.
     */
    EVENT_BLOCK_APPOINTMENT_NOT_PUBLIC(
            Area.EVENTS,
            107,
            HttpStatus.BAD_REQUEST,
            "A page can only show a public appointment in an event block, so nothing was saved"),

    /**
     * An article saved with an event block naming an appointment that is kept to part of the station,
     * or is not the station's. An article is read by every member, so it may only show what every
     * member may see.
     */
    EVENT_BLOCK_APPOINTMENT_NOT_FOR_EVERY_MEMBER(
            Area.EVENTS,
            108,
            HttpStatus.BAD_REQUEST,
            "An article can only show an appointment every member may see, so nothing was saved"),

    /**
     * An appointment made from a template that is gone or belongs to another station. One code for
     * both, so a guessed id says nothing about the templates of other stations.
     */
    EVENT_TEMPLATE_TO_APPLY_NOT_HERE(Area.EVENTS, 109, HttpStatus.NOT_FOUND, Sentences.EVENT_TEMPLATE_NOT_HERE),

    /** A registration question given a kind of answer the registration form does not offer. */
    REGISTRATION_QUESTION_TYPE_NOT_OFFERED(
            Area.EVENTS,
            110,
            HttpStatus.BAD_REQUEST,
            "Registration questions do not take that kind of answer, so nothing was saved"),

    /** A field of an appointment given a type an appointment does not offer. */
    APPOINTMENT_FIELD_TYPE_NOT_OFFERED(
            Area.EVENTS,
            111,
            HttpStatus.BAD_REQUEST,
            "Appointments do not offer that type of field, so nothing was saved"),

    /** A field of an appointment template given a type an appointment does not offer. */
    TEMPLATE_FIELD_TYPE_NOT_OFFERED(
            Area.EVENTS,
            112,
            HttpStatus.BAD_REQUEST,
            "Appointments do not offer that type of field, so the template was not saved"),

    /** A field of appointments created together given a type an appointment does not offer. */
    BATCH_FIELD_TYPE_NOT_OFFERED(
            Area.EVENTS,
            113,
            HttpStatus.BAD_REQUEST,
            "Appointments do not offer that type of field, so none was created"),

    /**
     * A question answered on one date of an appointment that is not here, or one that belongs to
     * another appointment. One code for both: the lookup reaches every question on the instance.
     */
    APPOINTMENT_FIELD_NOT_HERE_FOR_DATE_ANSWER(Area.EVENTS, 114, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** An answer for one date given to a question that is answered on the appointment as a whole. */
    APPOINTMENT_FIELD_NOT_PER_DATE(
            Area.EVENTS, 115, HttpStatus.BAD_REQUEST, "This question is not answered per date, so nothing was saved"),

    /** An answer for one date that the question does not take. */
    APPOINTMENT_DATE_ANSWER_NOT_ACCEPTED(
            Area.EVENTS, 116, HttpStatus.BAD_REQUEST, "That answer does not suit this question, so nothing was saved"),

    /** An answer for one date that went between being saved and being read back. */
    APPOINTMENT_DATE_ANSWER_NOT_HERE_AFTER_SAVE(
            Area.EVENTS, 117, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A question of an appointment filled in with something it does not take. */
    APPOINTMENT_FIELD_VALUE_NOT_ACCEPTED(
            Area.EVENTS, 118, HttpStatus.BAD_REQUEST, "That value does not suit this question, so nothing was saved"),

    /**
     * A question somebody put themselves in that is not here, or one that belongs to another
     * appointment. One code for both: the lookup reaches every question on the instance.
     */
    APPOINTMENT_FIELD_NOT_HERE_FOR_SELF_REGISTRATION(Area.EVENTS, 119, HttpStatus.NOT_FOUND, Sentences.FIELD_NOT_HERE),

    /** Somebody putting themselves in a question that names no members. */
    APPOINTMENT_FIELD_NAMES_NO_MEMBERS(
            Area.EVENTS,
            120,
            HttpStatus.BAD_REQUEST,
            "Only a question that names members can be stood in, so nothing was changed"),

    /** Somebody putting themselves in a question nobody may put themselves in. */
    APPOINTMENT_FIELD_SELF_REGISTRATION_OFF(
            Area.EVENTS,
            121,
            HttpStatus.BAD_REQUEST,
            "Nobody can put themselves in this question, so nothing was changed"),

    /** A member putting themselves in a question who is not here any more. */
    APPOINTMENT_SELF_REGISTRATION_MEMBER_NOT_HERE(Area.EVENTS, 122, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A question somebody put themselves in that went before it could be read back. */
    APPOINTMENT_FIELD_NOT_HERE_AFTER_SELF_REGISTRATION(
            Area.EVENTS, 123, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** Somebody putting themselves in a single-member question somebody else already stands in. */
    APPOINTMENT_FIELD_SLOT_TAKEN(
            Area.EVENTS,
            124,
            HttpStatus.CONFLICT,
            "Somebody else already stands in this question, so nothing was changed"),

    /** Somebody putting themselves in a question narrowed to a group, user type or tag they are outside. */
    APPOINTMENT_FIELD_NOT_OPEN_TO_YOU(
            Area.EVENTS, 125, HttpStatus.FORBIDDEN, "This question is not open to you, so nothing was changed"),

    /** Somebody putting themselves in a question that lost the group, user type or tag it is narrowed to. */
    APPOINTMENT_FIELD_NARROWING_LOST(
            Area.EVENTS,
            126,
            HttpStatus.BAD_REQUEST,
            "This question cannot be stood in as it is set up, so nothing was changed"),

    /** Somebody putting themselves in a question answered per date without naming the date. */
    APPOINTMENT_FIELD_DATE_MISSING(
            Area.EVENTS,
            127,
            HttpStatus.BAD_REQUEST,
            "This question is answered per date, so give the date. Nothing was changed"),

    /** The appointment behind a question somebody put themselves in, gone in the meantime. */
    APPOINTMENT_NOT_HERE_FOR_SELF_REGISTRATION(Area.EVENTS, 128, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** Somebody putting themselves in a question of an appointment that has stopped taking people. */
    REGISTRATION_CLOSED_ON_SELF_REGISTRATION(Area.EVENTS, 129, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_CLOSED),

    /** A file too large to be handed to a partner station along with an appointment. */
    EVENT_FILE_TOO_LARGE_FOR_PARTNER(
            Area.EVENTS, 130, HttpStatus.BAD_REQUEST, "This file is too large to hand to a partner station"),

    /**
     * A file attached to an appointment that is not in the library, or one from another station's
     * library. One code for both: the lookup reaches every file on the instance.
     */
    EVENT_FILE_TO_ATTACH_NOT_HERE(Area.EVENTS, 131, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** A value an appointment writes into a field of its attendance sheet that the field does not take. */
    EVENT_FIELD_DEFAULT_NOT_ACCEPTED(
            Area.EVENTS,
            132,
            HttpStatus.BAD_REQUEST,
            "That value does not suit the field of the attendance sheet, so nothing was saved"),

    /** An appointment a partner registered a member for that is not here any more. */
    EVENT_NOT_HERE_FOR_PARTNER_REGISTRATION(Area.EVENTS, 133, HttpStatus.NOT_FOUND, Sentences.EVENT_NOT_HERE),

    /** A partner registering a member for an appointment that takes no registrations. */
    EVENT_TAKES_NO_PARTNER_REGISTRATIONS(
            Area.EVENTS, 134, HttpStatus.BAD_REQUEST, "This appointment does not take registrations"),

    /** A partner registering a member after the appointment stopped taking people. */
    REGISTRATION_CLOSED_TO_PARTNER(Area.EVENTS, 135, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_CLOSED),

    /** A comment on an appointment changed by a partner instance after it had gone. */
    REMOTE_EVENT_COMMENT_NOT_HERE_ON_UPDATE(Area.EVENTS, 136, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment on an appointment by another member, changed by a partner instance. */
    REMOTE_EVENT_COMMENT_NOT_YOURS_TO_EDIT(
            Area.EVENTS, 137, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_EDIT),

    /** A comment on an appointment that went while a partner instance was changing it. */
    REMOTE_EVENT_COMMENT_NOT_HERE_AFTER_UPDATE(Area.EVENTS, 138, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment on an appointment deleted by a partner instance after it had gone. */
    REMOTE_EVENT_COMMENT_NOT_HERE_ON_DELETE(Area.EVENTS, 139, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

    /** A comment on an appointment by another member, deleted by a partner instance. */
    REMOTE_EVENT_COMMENT_NOT_YOURS_TO_DELETE(
            Area.EVENTS, 140, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_DELETE),

    /** An answer to a registration question that the appointment does not ask. */
    REGISTRATION_ANSWER_TO_UNKNOWN_QUESTION(
            Area.EVENTS,
            141,
            HttpStatus.BAD_REQUEST,
            "That answers a question this appointment does not ask, so nothing was saved"),

    /** A registration question left unanswered, or answered with something it does not take. */
    REGISTRATION_ANSWER_NOT_ACCEPTED(
            Area.EVENTS,
            142,
            HttpStatus.BAD_REQUEST,
            "An answer to the registration questions is missing or does not suit its question, so nothing was saved"),

    /** A registration question of an appointment set up to start from a value it would not take. */
    REGISTRATION_QUESTION_DEFAULT_NOT_ACCEPTED(
            Area.EVENTS, 143, HttpStatus.BAD_REQUEST, Sentences.QUESTION_DEFAULT_NOT_ACCEPTED),

    /** A registration question of an appointment template set up to start from a value it would not take. */
    EVENT_TEMPLATE_REGISTRATION_DEFAULT_NOT_ACCEPTED(
            Area.EVENTS, 144, HttpStatus.BAD_REQUEST, Sentences.QUESTION_DEFAULT_NOT_ACCEPTED),

    /** A question of an appointment template set up to start from a value it would not take. */
    EVENT_TEMPLATE_FIELD_DEFAULT_NOT_ACCEPTED(
            Area.EVENTS, 145, HttpStatus.BAD_REQUEST, Sentences.QUESTION_DEFAULT_NOT_ACCEPTED),

    /** A new appointment that ends before it starts. */
    EVENT_ENDS_BEFORE_IT_STARTS_ON_CREATE(
            Area.EVENTS, 146, HttpStatus.BAD_REQUEST, Sentences.EVENT_ENDS_BEFORE_IT_STARTS),

    /** An appointment changed so that it ends before it starts. */
    EVENT_ENDS_BEFORE_IT_STARTS_ON_CHANGE(
            Area.EVENTS, 147, HttpStatus.BAD_REQUEST, Sentences.EVENT_ENDS_BEFORE_IT_STARTS),

    /** A series given both a last day and a number of times to end after. */
    EVENT_SERIES_END_GIVEN_TWICE(
            Area.EVENTS,
            148,
            HttpStatus.BAD_REQUEST,
            "A series ends on a day or after a number of times, not both, so nothing was saved"),

    /** An end to its repetition given to an appointment that does not repeat. */
    EVENT_SERIES_END_ON_ONE_OFF(
            Area.EVENTS,
            149,
            HttpStatus.BAD_REQUEST,
            "Only a repeating appointment has an end to its repetition, so nothing was saved"),

    /** A series told to take place fewer than once. */
    EVENT_SERIES_COUNT_BELOW_ONE(
            Area.EVENTS,
            150,
            HttpStatus.BAD_REQUEST,
            "A series that repeats takes place at least once, so nothing was saved"),

    /** A series told to end on a day before its first. */
    EVENT_SERIES_ENDS_BEFORE_IT_STARTS(
            Area.EVENTS, 151, HttpStatus.BAD_REQUEST, "A series cannot end before it starts, so nothing was saved"),

    /** A registration given a new status while a question of the appointment holds the place. */
    REGISTRATION_HELD_BY_A_FIELD_ON_STATUS_CHANGE(
            Area.EVENTS, 152, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_HELD_BY_A_FIELD),

    /** A registration withdrawn while a question of the appointment holds the place. */
    REGISTRATION_HELD_BY_A_FIELD_ON_WITHDRAWAL(
            Area.EVENTS, 153, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_HELD_BY_A_FIELD),

    /** A registration turned down while a question of the appointment holds the place. */
    REGISTRATION_HELD_BY_A_FIELD_ON_REFUSAL(
            Area.EVENTS, 154, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_HELD_BY_A_FIELD),

    /** A member saying they will not come while a question of the appointment holds their place. */
    REGISTRATION_HELD_BY_A_FIELD_ON_DECLINE(
            Area.EVENTS, 155, HttpStatus.BAD_REQUEST, Sentences.REGISTRATION_HELD_BY_A_FIELD),

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

    /**
     * An answer to a comment that is not on the same appointment, entry, file or ticket, or not
     * there at all. Answered as a comment that is not here, which from where the answer was written
     * it is not.
     */
    COMMENT_PARENT_ELSEWHERE(Area.COMMENTS, 13, HttpStatus.NOT_FOUND, Sentences.COMMENT_NOT_HERE),

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

    /** Publishing a form that is past being a draft. */
    FORM_NOT_A_DRAFT(Area.FORMS, 26, HttpStatus.BAD_REQUEST, "Only a form that is still a draft can be published"),

    /** How far a form reaches, left unsaid where it had to be said. */
    FORM_REACH_NOT_SAID(Area.FORMS, 27, HttpStatus.BAD_REQUEST, "Say how far the form is to reach"),

    /** The link of a form the station's own members answer, which is not sent by one. */
    INTERNAL_FORM_HAS_NO_LINK(Area.FORMS, 28, HttpStatus.BAD_REQUEST, Sentences.INTERNAL_FORM_HAS_NO_LINK),

    /** Replacing a link that has been replaced by somebody else in the meantime. */
    FORM_LINK_ALREADY_REPLACED(
            Area.FORMS, 29, HttpStatus.CONFLICT, "This form has been given a different link since you last looked"),

    /** Questions of a kind the form's own kind does not ask. */
    QUESTIONS_NOT_FOR_THIS_KIND_OF_FORM(
            Area.FORMS,
            30,
            HttpStatus.BAD_REQUEST,
            "Some of these questions cannot be asked on a form of this kind, so nothing was saved"),

    /** A form that is closed, not yet open or never published, answered by a member. */
    FORM_TAKES_NO_ANSWERS(Area.FORMS, 33, HttpStatus.BAD_REQUEST, Sentences.FORM_TAKES_NO_ANSWERS),

    /** A form the member answering is not among the people it was put to. */
    FORM_NOT_YOURS_TO_ANSWER(Area.FORMS, 34, HttpStatus.FORBIDDEN, Sentences.FORM_NOT_YOURS_TO_ANSWER),

    /** Answers that were read and then refused for what they said. */
    FORM_ANSWERS_NOT_SAVED(Area.FORMS, 35, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWERS_NOT_SAVED),

    /** A form that takes an answer once and does not take it again. */
    FORM_ANSWER_NOT_CHANGEABLE(Area.FORMS, 37, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWER_NOT_CHANGEABLE),

    /** The same form put to somebody else, at the moment an answer to it is changed. */
    FORM_NOT_YOURS_TO_CHANGE_ANSWER(Area.FORMS, 38, HttpStatus.FORBIDDEN, Sentences.FORM_NOT_YOURS_TO_ANSWER),

    /** A changed answer that was read and then refused for what it said. */
    FORM_ANSWER_CHANGE_NOT_SAVED(Area.FORMS, 39, HttpStatus.BAD_REQUEST, Sentences.FORM_ANSWERS_NOT_SAVED),

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

    /** Saving a form's questions with one that names a question of some other form, or none that exists. */
    QUESTION_NOT_ON_THIS_FORM(
            Area.FORMS,
            49,
            HttpStatus.BAD_REQUEST,
            "Some of these questions are not on this form, so nothing was saved"),

    /** Saving a question that already exists under a different type than the one it was asked as. */
    QUESTION_TYPE_NOT_CHANGEABLE(
            Area.FORMS,
            50,
            HttpStatus.BAD_REQUEST,
            "A question that already exists keeps its type, so nothing was saved"),

    /**
     * A first answer given by a member, or for somebody they look after, who has answered already.
     * The answer on file is changed, where the form allows it, and never replaced by a second first
     * answer.
     */
    FORM_ANSWER_ALREADY_ON_FILE(
            Area.FORMS, 51, HttpStatus.CONFLICT, "This form has already been answered, so nothing was saved"),

    /**
     * Saving a question whose options or statements do not each carry a key of their own. Answers
     * name an option by its key, so an option without one, or two sharing one, cannot be told apart.
     */
    QUESTION_OPTION_KEYS_NOT_DISTINCT(
            Area.FORMS,
            53,
            HttpStatus.BAD_REQUEST,
            "Every option of a question needs a key of its own, so nothing was saved"),

    /**
     * Saving a form without a page, or with pages that do not each carry a key of their own. The
     * page that follows and the path a response took name a page by its key.
     */
    FORM_PAGE_KEYS_NOT_DISTINCT(
            Area.FORMS,
            54,
            HttpStatus.BAD_REQUEST,
            "A form needs at least one page, and every page a key of its own, so nothing was saved"),

    /**
     * A page that is to be followed by a page that is not further down, or by one the form does not
     * have. Pages only lead forward, which is what keeps every path through a form finite.
     */
    FORM_PAGE_TARGET_NOT_FURTHER_DOWN(
            Area.FORMS,
            55,
            HttpStatus.BAD_REQUEST,
            "A page can only lead to a page further down, so nothing was saved"),

    /** A question put on a page the form does not have. */
    QUESTION_ON_NO_PAGE(
            Area.FORMS,
            56,
            HttpStatus.BAD_REQUEST,
            "Some of these questions stand on a page the form does not have, so nothing was saved"),

    /**
     * A question that is to decide where its page leads but is not a single-answer choice, or names
     * options it does not have. Only one picked option can say which page comes next.
     */
    QUESTION_BRANCH_NOT_ON_A_SINGLE_CHOICE(
            Area.FORMS,
            57,
            HttpStatus.BAD_REQUEST,
            "Only a question with one answer from its own options can decide the next page, so nothing was saved"),

    /** A page on which more than one question is to decide where it leads. */
    PAGE_BRANCHES_ON_TWO_QUESTIONS(
            Area.FORMS,
            58,
            HttpStatus.BAD_REQUEST,
            "Only one question per page can decide the next page, so nothing was saved"),

    /** A required question on a page the answers went through, left without an answer. */
    QUESTION_NEEDS_AN_ANSWER(Area.FORMS, 59, HttpStatus.BAD_REQUEST, "This question needs an answer"),

    /** An answer that does not fit its question: an option it does not have, too many picks, a rating off the scale. */
    ANSWER_DOES_NOT_FIT_QUESTION(Area.FORMS, 60, HttpStatus.BAD_REQUEST, "This answer does not fit the question"),

    /** An answer to a question the form does not have. */
    ANSWER_TO_QUESTION_NOT_ON_FORM(
            Area.FORMS, 61, HttpStatus.BAD_REQUEST, "This answer belongs to a question the form does not have"),

    /** A copy of a form asked for without a title. */
    FORM_COPY_NEEDS_A_TITLE(Area.FORMS, 62, HttpStatus.BAD_REQUEST, "A copy needs a title, so nothing was copied"),

    /** A form that went before it could be copied. */
    FORM_NOT_HERE_ON_COPY(Area.FORMS, 64, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /**
     * A link offered after sending a form that is neither a web address nor an address on this site.
     * The link is put in front of strangers, so nothing else is let through.
     */
    FORM_COMPLETION_LINK_NOT_A_LINK(
            Area.FORMS,
            65,
            HttpStatus.BAD_REQUEST,
            "The link after sending has to be a web address or an address on this site, so nothing was saved"),

    /** A half-filled form kept for later on a form that is not taking answers. */
    FORM_TAKES_NO_DRAFTS(Area.FORMS, 67, HttpStatus.BAD_REQUEST, Sentences.FORM_TAKES_NO_ANSWERS),

    /** A half-filled form kept by a member the form was not put to. */
    FORM_NOT_YOURS_TO_DRAFT(Area.FORMS, 68, HttpStatus.FORBIDDEN, Sentences.FORM_NOT_YOURS_TO_ANSWER),

    /**
     * Reading, keeping or ending another member's half-filled form without looking after them. An
     * unsent answer is private: managing the station's polls does not reach it.
     */
    FORM_DRAFT_NOT_YOURS(
            Area.FORMS,
            69,
            HttpStatus.FORBIDDEN,
            "Only the member and whoever looks after them can see or keep an answer that was not sent"),

    /** A new link asked for a form the station's own members answer, which is not sent by one. */
    FORM_INTERNAL_HAS_NO_LINK(Area.FORMS, 70, HttpStatus.BAD_REQUEST, Sentences.INTERNAL_FORM_HAS_NO_LINK),

    /** How far it reaches, set on a form the station's own members answer. */
    FORM_INTERNAL_HAS_NO_REACH(
            Area.FORMS,
            71,
            HttpStatus.BAD_REQUEST,
            "A form for the station's own members is not reached from outside at all, so nothing was saved"),

    /** Who may answer, narrowed on a form that is not here any more. */
    FORM_NOT_HERE_FOR_RESTRICTIONS(Area.FORMS, 72, HttpStatus.NOT_FOUND, Sentences.FORM_NOT_HERE),

    /** Who may answer, narrowed on a form answered from outside the station. */
    FORM_FROM_OUTSIDE_HAS_NO_RESTRICTIONS(
            Area.FORMS,
            73,
            HttpStatus.BAD_REQUEST,
            "A form answered from outside the station has nobody to narrow it to, so nothing was saved"),

    /** Results asked to be grouped without saying by what. */
    FORM_RESULTS_GROUPING_MISSING(Area.FORMS, 74, HttpStatus.BAD_REQUEST, "Say what to group the results by"),

    /** Results grouped by a profile question that is not here or cannot be grouped by. */
    FORM_RESULTS_GROUPING_FIELD_NOT_HERE(
            Area.FORMS, 75, HttpStatus.BAD_REQUEST, "The results cannot be grouped by that profile question"),

    /** A form whose results were asked for, gone before they could be counted. */
    FORM_NOT_HERE_FOR_ANALYTICS(Area.FORMS, 76, HttpStatus.NOT_FOUND, "That form is not here any more"),

    /** One response to a form opened, which the form does not have. */
    FORM_RESPONSE_NOT_HERE(Area.FORMS, 77, HttpStatus.NOT_FOUND, "That answer to the form is not here any more"),

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

    /** A waiting list whose testing group does not take somebody on trial. */
    WAITING_LIST_TESTING_GROUP_WRONG_USER_TYPE(
            Area.WAITING_LISTS,
            47,
            HttpStatus.BAD_REQUEST,
            "The group for the trial period does not take people on trial, so nothing was saved"),

    /** A waiting list whose join group does not take members. */
    WAITING_LIST_JOIN_GROUP_WRONG_USER_TYPE(
            Area.WAITING_LISTS,
            48,
            HttpStatus.BAD_REQUEST,
            "The group for joining does not take members, so nothing was saved"),

    /** A waiting list whose testing group belongs to another station, or is gone. */
    WAITING_LIST_TESTING_GROUP_NOT_HERE(
            Area.WAITING_LISTS, 49, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE_NOTHING_SAVED),

    /** A waiting list whose join group belongs to another station, or is gone. */
    WAITING_LIST_JOIN_GROUP_NOT_HERE(
            Area.WAITING_LISTS, 50, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE_NOTHING_SAVED),

    /** A question of a waiting list given a type a waiting list does not offer. */
    WAITING_LIST_FIELD_TYPE_NOT_OFFERED(
            Area.WAITING_LISTS,
            51,
            HttpStatus.BAD_REQUEST,
            "Waiting lists do not offer that type of question, so nothing was saved"),

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

    /**
     * A picture whose header declares more pixels than may be unpacked, wherever it was uploaded.
     * One code for every upload, because the one place that measures pictures is the one that
     * refuses them.
     */
    PICTURE_TOO_MANY_PIXELS(
            Area.GENERAL,
            12,
            HttpStatus.CONTENT_TOO_LARGE,
            "That picture has too many pixels to be worked with, so nothing was saved. "
                    + "A smaller version of it will work"),

    /**
     * An upload to a picture that is none of the formats the picture takes, read from its bytes
     * rather than from what the browser declared. One code for every sized picture, because the one
     * pipeline that stores them is the one that refuses them.
     */
    PICTURE_KIND_NOT_TAKEN(
            Area.GENERAL,
            13,
            HttpStatus.BAD_REQUEST,
            "That file is not a picture that can be kept here, so nothing was saved. "
                    + "A PNG, JPEG, GIF or WebP file will work"),

    /** An upload to a picture that weighs more than the place it is for takes. */
    PICTURE_TOO_LARGE(
            Area.GENERAL,
            14,
            HttpStatus.CONTENT_TOO_LARGE,
            "That picture is larger than this place takes, so nothing was saved. A smaller file will work"),

    /** A request to something that lives at a station, sent without naming one. */
    NO_STATION_CHOSEN(Area.GENERAL, 15, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /** A request to something that lives at a station, from somebody who is no member of it. */
    NOT_A_MEMBER_OF_THIS_STATION(Area.GENERAL, 16, HttpStatus.FORBIDDEN, "You are not a member of this station"),

    /** A request to something only somebody signed in may do, with no session behind it. */
    NOT_SIGNED_IN(Area.GENERAL, 17, HttpStatus.UNAUTHORIZED, Sentences.SIGN_IN_FIRST),

    /** Any request from one address far more often than the instance answers for anybody. */
    REQUESTS_TOO_OFTEN(Area.GENERAL, 18, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A request to a route that needs a signed-in caller, sent without any session. */
    ROUTE_NEEDS_SIGN_IN(Area.GENERAL, 19, HttpStatus.UNAUTHORIZED, Sentences.SIGN_IN_FIRST),

    /** A session that has ended, been signed out or never existed. */
    SIGN_IN_SESSION_NOT_VALID(Area.GENERAL, 20, HttpStatus.UNAUTHORIZED, "Your sign-in has ended. Sign in again"),

    /** A request naming a station in its header that is not on this instance. */
    REQUESTED_STATION_NOT_HERE(Area.GENERAL, 21, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_HERE),

    /** A request whose station header does not read as a station's identity. */
    REQUESTED_STATION_NOT_AN_IDENTITY(Area.GENERAL, 22, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_AN_IDENTITY),

    /** A request naming a cluster in its header that is not on this instance. */
    REQUESTED_CLUSTER_NOT_HERE(Area.GENERAL, 23, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NOT_HERE),

    /** A request whose cluster header does not read as a cluster's identity. */
    REQUESTED_CLUSTER_NOT_AN_IDENTITY(Area.GENERAL, 24, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NOT_AN_IDENTITY),

    /** A route asked for by somebody holding none of the permissions it needs. */
    ROUTE_PERMISSION_MISSING(Area.GENERAL, 25, HttpStatus.FORBIDDEN, "You do not have the permission this needs"),

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
    CLUSTER_THEME_FEEL_UNKNOWN(Area.CLUSTERS, 11, HttpStatus.BAD_REQUEST, Sentences.LOOK_NOT_OFFERED),

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

    /** A cluster a station is asking to join, named in a way no cluster can be named. */
    CLUSTER_NOT_AN_IDENTITY_ON_APPLICATION(
            Area.CLUSTERS, 69, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NOT_AN_IDENTITY),

    /**
     * The settings of an association's expiry date that count days backwards or repeat without a gap,
     * the same refusal a station's own field gets.
     */
    CLUSTER_EXPIRY_SETTINGS_OUT_OF_RANGE(Area.CLUSTERS, 70, HttpStatus.BAD_REQUEST, Sentences.EXPIRY_OUT_OF_RANGE),

    /** An association question saved with a default its own answers would not take. */
    CLUSTER_PROFILE_DEFAULT_NOT_ACCEPTED(Area.CLUSTERS, 71, HttpStatus.BAD_REQUEST, Sentences.DEFAULT_NOT_SUITING),

    /** A station asking to join a cluster, asked by somebody other than its owner. */
    CLUSTER_APPLICATION_NOT_BY_STATION_OWNER(
            Area.CLUSTERS,
            72,
            HttpStatus.FORBIDDEN,
            "Only the station's owner can ask to join a cluster, so nothing was sent"),

    /** A cluster's own station asking to join a cluster. */
    CLUSTER_APPLICATION_FROM_CLUSTER_HOME(
            Area.CLUSTERS,
            73,
            HttpStatus.BAD_REQUEST,
            "A cluster's own station cannot join another cluster, so nothing was sent"),

    /** A station asking to join a cluster while it already belongs to one. */
    CLUSTER_APPLICATION_STATION_ALREADY_JOINED(
            Area.CLUSTERS,
            74,
            HttpStatus.BAD_REQUEST,
            "This station already belongs to a cluster, so nothing was sent"),

    /** A station asking to join a cluster while a request of its own is still waiting. */
    CLUSTER_APPLICATION_ALREADY_WAITING(
            Area.CLUSTERS,
            75,
            HttpStatus.BAD_REQUEST,
            "This station already has a request to join waiting, so nothing was sent"),

    /** A request to join taken back by somebody other than the station's owner. */
    CLUSTER_APPLICATION_WITHDRAWN_NOT_BY_STATION_OWNER(
            Area.CLUSTERS,
            76,
            HttpStatus.FORBIDDEN,
            "Only the station's owner can take its request back, so nothing was changed"),

    /** A request to join approved for a station that joined some cluster after asking. */
    CLUSTER_APPLICATION_STATION_JOINED_MEANWHILE(
            Area.CLUSTERS,
            77,
            HttpStatus.BAD_REQUEST,
            "This station has joined a cluster in the meantime, so nothing was changed"),

    /** A request to join withdrawn, approved or denied after it was already decided. */
    CLUSTER_APPLICATION_ALREADY_DECIDED(
            Area.CLUSTERS,
            78,
            HttpStatus.BAD_REQUEST,
            "That request to join has already been decided, so nothing was changed"),

    /**
     * A request to join that is not here, or one addressed to another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say that a station asked to join a
     * cluster the reader does not act for.
     */
    CLUSTER_APPLICATION_NOT_HERE(Area.CLUSTERS, 79, HttpStatus.NOT_FOUND, "That request to join is not here any more"),

    /** The station behind a request to join, gone while the request was being handled. */
    CLUSTER_APPLICATION_STATION_NOT_HERE(Area.CLUSTERS, 80, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** The cluster a request to join is about, gone while it was being handled. */
    CLUSTER_APPLICATION_CLUSTER_NOT_HERE(Area.CLUSTERS, 81, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A folder in the cluster's knowledge base written down without a name. */
    CLUSTER_KB_FOLDER_NEEDS_A_NAME(
            Area.CLUSTERS, 82, HttpStatus.BAD_REQUEST, "A folder needs a name, so nothing was saved"),

    /** An article in the cluster's knowledge base written down without a name. */
    CLUSTER_KB_ARTICLE_NEEDS_A_NAME(Area.CLUSTERS, 83, HttpStatus.BAD_REQUEST, Sentences.KB_ARTICLE_NEEDS_A_NAME),

    /**
     * A cluster article removed that is not here, or one kept at another station.
     *
     * <p>One code for both on purpose: telling them apart would say that the article exists somewhere
     * the cluster has no business with.
     */
    CLUSTER_KB_ARTICLE_NOT_HERE(Area.CLUSTERS, 84, HttpStatus.NOT_FOUND, Sentences.KB_ARTICLE_NOT_HERE),

    /** A cluster that is not here, asked for its knowledge base. */
    CLUSTER_KB_CLUSTER_NOT_HERE(Area.CLUSTERS, 85, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster that is not here, asked for the gear in its store. */
    CLUSTER_DISPATCH_CLUSTER_NOT_HERE(Area.CLUSTERS, 86, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** Gear sent out by a cluster that has no chain of its own for sending gear out. */
    CLUSTER_DISPATCH_WITHOUT_CHAIN(
            Area.CLUSTERS,
            87,
            HttpStatus.BAD_REQUEST,
            "This cluster has no chain for sending gear out yet, so nothing was sent. Add one under its inventory "
                    + "settings"),

    /** Gear sent out with no piece picked. */
    CLUSTER_DISPATCH_WITHOUT_GEAR(
            Area.CLUSTERS, 88, HttpStatus.BAD_REQUEST, "Pick at least one piece of gear to send, so nothing was sent"),

    /** Gear sent out to a station that is not here. */
    CLUSTER_DISPATCH_STATION_NOT_HERE(Area.CLUSTERS, 89, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Gear sent out to a station that does not belong to the sending cluster. */
    CLUSTER_DISPATCH_STATION_NOT_IN_CLUSTER(
            Area.CLUSTERS,
            90,
            HttpStatus.BAD_REQUEST,
            "That station does not belong to this cluster, so nothing was sent"),

    /** Gear sent out that is not resting in the cluster's own store. */
    CLUSTER_DISPATCH_GEAR_NOT_IN_STORE(
            Area.CLUSTERS,
            91,
            HttpStatus.BAD_REQUEST,
            "Some of that gear is not in the cluster's store, so nothing was sent"),

    /** The station a cluster keeps its own things on, gone while the cluster still names it. */
    CLUSTER_GOVERNANCE_HOME_STATION_NOT_HERE(
            Area.CLUSTERS, 92, HttpStatus.NOT_FOUND, "The cluster's own station is not here any more"),

    /** Modules switched off or read through a group of stations that is gone or belongs to another cluster. */
    CLUSTER_GOVERNANCE_STATION_GROUP_NOT_OWN(
            Area.CLUSTERS, 93, HttpStatus.BAD_REQUEST, "That group of stations is not one of this cluster's"),

    /** A cluster that is not here, asked about what it allows its stations. */
    CLUSTER_GOVERNANCE_CLUSTER_NOT_HERE(Area.CLUSTERS, 94, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A second chain added for a kind of movement the cluster already has a chain for. */
    CLUSTER_INVENTORY_FLOW_PURPOSE_TAKEN(
            Area.CLUSTERS,
            95,
            HttpStatus.BAD_REQUEST,
            "Nothing was saved, as that kind of movement already has a chain, which has to be archived first"),

    /**
     * A cluster chain that is not here, or one belonging to another cluster or a station.
     *
     * <p>One code for both on purpose: telling them apart would say that the chain exists somewhere
     * the cluster has no business with.
     */
    CLUSTER_INVENTORY_FLOW_NOT_HERE(Area.CLUSTERS, 96, HttpStatus.NOT_FOUND, Sentences.FLOW_NOT_HERE),

    /** A step of a cluster chain that is not here. */
    CLUSTER_INVENTORY_FLOW_STEP_NOT_HERE(Area.CLUSTERS, 97, HttpStatus.NOT_FOUND, Sentences.FLOW_STEP_NOT_HERE),

    /** A cluster that is not here, asked about its gear. */
    CLUSTER_INVENTORY_CLUSTER_NOT_HERE(Area.CLUSTERS, 98, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A word recommended to stations that the cluster already recommends to them. */
    CLUSTER_INVENTORY_TAG_TAKEN_ON_CREATE(Area.CLUSTERS, 99, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_TAG_TAKEN),

    /** A recommended word changed into one the cluster already recommends to the same stations. */
    CLUSTER_INVENTORY_TAG_TAKEN_ON_CHANGE(Area.CLUSTERS, 100, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_TAG_TAKEN),

    /** A recommended word that went between being changed and being read back. */
    CLUSTER_INVENTORY_TAG_NOT_HERE_AFTER_CHANGE(
            Area.CLUSTERS, 101, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A word recommended to stations without any word in it. */
    CLUSTER_INVENTORY_TAG_NEEDS_A_NAME(
            Area.CLUSTERS, 102, HttpStatus.BAD_REQUEST, "A tag needs a name, so nothing was saved"),

    /**
     * A recommended word that is not here, or one another cluster recommends.
     *
     * <p>One code for both on purpose: telling them apart would say what another cluster recommends.
     */
    CLUSTER_INVENTORY_TAG_NOT_HERE(Area.CLUSTERS, 103, HttpStatus.NOT_FOUND, Sentences.CLUSTER_TAG_NOT_HERE),

    /**
     * A member taken on at a station that is not here or does not belong to the cluster.
     *
     * <p>One code for both on purpose: telling them apart would say that a station exists outside the
     * cluster.
     */
    CLUSTER_MANAGED_MEMBER_STATION_NOT_HERE(Area.CLUSTERS, 104, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Documents read or filed by a cluster at a station that keeps no documents. */
    CLUSTER_MANAGED_STATION_KEEPS_NO_DOCUMENTS(
            Area.CLUSTERS, 105, HttpStatus.BAD_REQUEST, "This station keeps no documents"),

    /**
     * A document a cluster asked for that is not here, or one about nobody at its stations.
     *
     * <p>One code for both on purpose: telling them apart would say what is filed at a station outside
     * the cluster.
     */
    CLUSTER_MANAGED_DOCUMENT_NOT_HERE(Area.CLUSTERS, 106, HttpStatus.NOT_FOUND, Sentences.DOCUMENT_NOT_HERE),

    /** A document a cluster may read whose file is gone from storage. */
    CLUSTER_MANAGED_DOCUMENT_FILE_NOT_HERE(
            Area.CLUSTERS, 107, HttpStatus.NOT_FOUND, "The file of that document is not here any more"),

    /**
     * A member a cluster reached for who is not here, whose station is gone, or who belongs to a
     * station outside the cluster.
     *
     * <p>One code for all three on purpose: telling them apart would say that somebody is a member of a
     * station outside the cluster.
     */
    CLUSTER_MANAGED_MEMBER_NOT_HERE(Area.CLUSTERS, 108, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A cluster manager changing their own membership at a station from the cluster. */
    CLUSTER_MANAGED_MEMBER_IS_YOURSELF(
            Area.CLUSTERS,
            109,
            HttpStatus.FORBIDDEN,
            "Your own membership cannot be changed from the cluster, so nothing was changed"),

    /** A cluster manager changing a station's owner from the cluster. */
    CLUSTER_MANAGED_MEMBER_OWNS_STATION(
            Area.CLUSTERS,
            110,
            HttpStatus.FORBIDDEN,
            "A station's owner can only be changed at their own station, so nothing was changed"),

    /** A person added to a cluster that is not here. */
    CLUSTER_MEMBER_ADDED_TO_NO_CLUSTER(Area.CLUSTERS, 111, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A person added to a cluster without an address. */
    CLUSTER_MEMBER_ADDRESS_MISSING(
            Area.CLUSTERS,
            112,
            HttpStatus.BAD_REQUEST,
            "Give the email address of the person to add, so nothing was saved"),

    /** A group of cluster members being made without a name. */
    CLUSTER_MEMBER_GROUP_NAME_MISSING_ON_CREATE(
            Area.CLUSTERS, 113, HttpStatus.BAD_REQUEST, Sentences.GROUP_NAME_MISSING),

    /** A group of cluster members being renamed to nothing. */
    CLUSTER_MEMBER_GROUP_NAME_MISSING_ON_CHANGE(
            Area.CLUSTERS, 114, HttpStatus.BAD_REQUEST, Sentences.GROUP_NAME_MISSING),

    /** A permission given to a group of cluster members that this instance does not keep. */
    CLUSTER_MEMBER_GROUP_PERMISSION_UNKNOWN(
            Area.CLUSTERS,
            115,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "That is not a permission a group can carry, so nothing was saved"),

    /** A cluster that is not here, asked about its members or groups. */
    CLUSTER_MEMBER_CLUSTER_NOT_HERE(Area.CLUSTERS, 116, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /**
     * A cluster member that is not here, or one of another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say who belongs to another cluster.
     */
    CLUSTER_MEMBER_NOT_HERE(Area.CLUSTERS, 117, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /**
     * A group of cluster members that is not here, or one of another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say which groups another cluster keeps.
     */
    CLUSTER_MEMBER_GROUP_NOT_HERE(Area.CLUSTERS, 118, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE),

    /** A cluster being created with no name. */
    CLUSTER_NEEDS_A_NAME_ON_CREATE(Area.CLUSTERS, 119, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NEEDS_A_NAME),

    /** A cluster being renamed to no name at all. */
    CLUSTER_NEEDS_A_NAME_ON_RENAME(Area.CLUSTERS, 120, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_NEEDS_A_NAME),

    /** A cluster that went before it could be renamed. */
    CLUSTER_GONE_BEFORE_RENAME(Area.CLUSTERS, 121, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster that went before it could be deleted. */
    CLUSTER_GONE_BEFORE_DELETE(Area.CLUSTERS, 122, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A cluster asked to be deleted while stations still belong to it. */
    CLUSTER_STILL_HAS_STATIONS(
            Area.CLUSTERS,
            123,
            HttpStatus.BAD_REQUEST,
            "Stations still belong to this cluster. Release them first, so nothing was deleted"),

    /** A station the cluster creates for itself, given no name. */
    CLUSTER_NEW_STATION_NEEDS_A_NAME(Area.CLUSTERS, 124, HttpStatus.BAD_REQUEST, Sentences.STATION_NEEDS_A_NAME),

    /** A cluster's own station put forward to join another cluster. */
    CLUSTER_HOME_STATION_CANNOT_JOIN(
            Area.CLUSTERS,
            125,
            HttpStatus.BAD_REQUEST,
            "A cluster's own station cannot join another cluster, so nothing was changed"),

    /** A station taken into a cluster while it still belongs to another one. */
    CLUSTER_STATION_ALREADY_IN_ANOTHER(
            Area.CLUSTERS,
            126,
            HttpStatus.BAD_REQUEST,
            "That station already belongs to another cluster, so nothing was changed"),

    /** A station a cluster lets go that never belonged to it. */
    CLUSTER_RELEASE_STATION_NOT_IN_IT(Area.CLUSTERS, 127, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_IN_CLUSTER),

    /** An account taken on by a cluster it already acts for. */
    CLUSTER_ACCOUNT_ALREADY_A_MEMBER(
            Area.CLUSTERS,
            128,
            HttpStatus.CONFLICT,
            "That account is already a member of this cluster, so nothing was changed"),

    /** A permission granted to a cluster member that the instance keeps no record of. */
    CLUSTER_PERMISSION_NOT_KNOWN_ON_GRANT(
            Area.CLUSTERS,
            129,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "That permission is not known here, so nothing was granted"),

    /** The cluster a station is being created in, taken in by or released from, gone before the change. */
    CLUSTER_GONE_FOR_STATION_CHANGE(Area.CLUSTERS, 130, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** The station a cluster is taking in or letting go, gone before the change. */
    CLUSTER_STATION_GONE_FOR_CHANGE(Area.CLUSTERS, 131, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A group of stations removed while questions are still asked of it. */
    CLUSTER_STATION_GROUP_STILL_ASKED_QUESTIONS(
            Area.CLUSTERS,
            132,
            HttpStatus.BAD_REQUEST,
            "Questions are still asked of this group. Point them somewhere else first, so nothing was removed"),

    /** A group of stations removed while modules are still switched off for it. */
    CLUSTER_STATION_GROUP_STILL_HAS_MODULES_OFF(
            Area.CLUSTERS,
            133,
            HttpStatus.BAD_REQUEST,
            "Modules are still switched off for this group. Switch them back on first, so nothing was removed"),

    /** A group of stations removed while words are still recommended to it. */
    CLUSTER_STATION_GROUP_STILL_RECOMMENDED_TAGS(
            Area.CLUSTERS,
            134,
            HttpStatus.BAD_REQUEST,
            "Tags are still recommended to this group. Point them somewhere else first, so nothing was removed"),

    /** A group of stations removed while stock requirements still count at it. */
    CLUSTER_STATION_GROUP_STILL_COUNTS_REQUIREMENTS(
            Area.CLUSTERS,
            135,
            HttpStatus.BAD_REQUEST,
            "Stock requirements still count at this group. Point them somewhere else first, so nothing was removed"),

    /** A station filed under a group that is not here at all. */
    CLUSTER_STATION_GROUP_STATION_GONE(Area.CLUSTERS, 136, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station filed under a group of a cluster it does not belong to. */
    CLUSTER_STATION_GROUP_STATION_NOT_IN_CLUSTER(
            Area.CLUSTERS, 137, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_IN_CLUSTER),

    /** A cluster's own station filed under one of its groups. */
    CLUSTER_STATION_GROUP_TAKES_NO_HOME_STATION(
            Area.CLUSTERS,
            138,
            HttpStatus.BAD_REQUEST,
            "The cluster's own station is not one of its stations, so nothing was saved"),

    /** The cluster behind its groups of stations, gone before the change. */
    CLUSTER_STATION_GROUP_CLUSTER_GONE(Area.CLUSTERS, 139, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /**
     * A group of stations that is not here, or one of another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say that the group exists at a
     * cluster the reader does not act for.
     */
    CLUSTER_STATION_GROUP_NOT_HERE(
            Area.CLUSTERS, 140, HttpStatus.NOT_FOUND, "That group of stations is not here any more"),

    /** A group of stations given no name. */
    CLUSTER_STATION_GROUP_NEEDS_A_NAME(
            Area.CLUSTERS, 141, HttpStatus.BAD_REQUEST, "A group of stations needs a name, so nothing was saved"),

    /** A group of stations given a name another group of the same cluster already has. */
    CLUSTER_STATION_GROUP_NAME_TAKEN(
            Area.CLUSTERS,
            142,
            HttpStatus.BAD_REQUEST,
            "This cluster already has a group of stations by that name, so nothing was saved"),

    /** A tier of room handed out without naming a station to hand it to. */
    CLUSTER_QUOTA_TIER_NAMES_NO_STATION(
            Area.CLUSTERS,
            143,
            HttpStatus.BAD_REQUEST,
            "Choose at least one station to hand the tier to, so nothing was changed"),

    /** A tier handed to stations that would together take more room than the cluster has left. */
    CLUSTER_QUOTA_TIER_MORE_THAN_POOL(Area.CLUSTERS, 144, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_POOL_TOO_SMALL),

    /** Room granted to a station that would take more than the cluster has left. */
    CLUSTER_QUOTA_GRANT_MORE_THAN_POOL(Area.CLUSTERS, 145, HttpStatus.BAD_REQUEST, Sentences.CLUSTER_POOL_TOO_SMALL),

    /** The cluster behind the room it hands out, gone before the change. */
    CLUSTER_QUOTA_CLUSTER_GONE(Area.CLUSTERS, 146, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /**
     * A tier of room that is not here, or one of another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say that the tier exists at a
     * cluster the reader does not act for.
     */
    CLUSTER_QUOTA_TIER_NOT_HERE(Area.CLUSTERS, 147, HttpStatus.NOT_FOUND, "That tier is not here any more"),

    /** A station named by its identity for room from the cluster, which no station here has. */
    CLUSTER_QUOTA_STATION_NOT_KNOWN(Area.CLUSTERS, 148, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station given room by the cluster that is not here any more. */
    CLUSTER_QUOTA_STATION_GONE(Area.CLUSTERS, 149, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Room handed to a station that does not belong to the cluster. */
    CLUSTER_QUOTA_STATION_NOT_IN_CLUSTER(Area.CLUSTERS, 150, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_IN_CLUSTER),

    /** A tier of room given no name. */
    CLUSTER_QUOTA_TIER_NEEDS_A_NAME(
            Area.CLUSTERS, 151, HttpStatus.BAD_REQUEST, "A tier needs a name, so nothing was saved"),

    /** A tier of room given a name another tier of the same cluster already has. */
    CLUSTER_QUOTA_TIER_NAME_TAKEN(
            Area.CLUSTERS,
            152,
            HttpStatus.BAD_REQUEST,
            "This cluster already has a tier by that name, so nothing was saved"),

    /** Room set to less than nothing, which is a typing mistake. */
    CLUSTER_QUOTA_ROOM_BELOW_NOTHING(
            Area.CLUSTERS, 153, HttpStatus.BAD_REQUEST, "Room cannot be less than nothing, so nothing was saved"),

    /** A reach chosen for the cluster's storage before any storage has been set up. */
    CLUSTER_STORAGE_REACH_WITHOUT_STORAGE(
            Area.CLUSTERS,
            154,
            HttpStatus.BAD_REQUEST,
            "Set up the cluster's storage before deciding what it is for, so nothing was saved"),

    /** A station's files moved that are already where the cluster's storage says they belong. */
    CLUSTER_STORAGE_STATION_ALREADY_IN_PLACE(
            Area.CLUSTERS,
            155,
            HttpStatus.BAD_REQUEST,
            "That station's files are already where they belong, so nothing was moved"),

    /** A station's files moved onto the cluster's storage when the cluster keeps none. */
    CLUSTER_STORAGE_NONE_TO_MOVE_ONTO(
            Area.CLUSTERS,
            156,
            HttpStatus.BAD_REQUEST,
            "This cluster keeps no storage to move the files onto, so nothing was moved"),

    /** A station's files moved by the cluster when they stand on storage the station brought itself. */
    CLUSTER_STORAGE_STATION_OWN_STORAGE(
            Area.CLUSTERS,
            157,
            HttpStatus.BAD_REQUEST,
            "That station keeps its files on storage of its own, which only the station can change. Nothing was moved"),

    /** The cluster whose storage is being set or used, gone before the change. */
    CLUSTER_STORAGE_CLUSTER_GONE(Area.CLUSTERS, 158, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /** A station whose files the cluster moves, gone before the move. */
    CLUSTER_STORAGE_STATION_GONE(Area.CLUSTERS, 159, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A station whose files the cluster moves that does not belong to the cluster. */
    CLUSTER_STORAGE_STATION_NOT_IN_CLUSTER(
            Area.CLUSTERS, 160, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_IN_CLUSTER),

    /** An answer written to a cluster question that is not asked of the member's station. */
    CLUSTER_PROFILE_FIELD_NOT_ASKED_AT_STATION(
            Area.CLUSTERS,
            161,
            HttpStatus.BAD_REQUEST,
            "That question is not asked of this member's station, so nothing was saved"),

    /** A cluster question pointed at a group of stations of another cluster. */
    CLUSTER_PROFILE_FIELD_GROUP_NOT_OWN(
            Area.CLUSTERS,
            162,
            HttpStatus.BAD_REQUEST,
            "That group of stations belongs to another cluster, so nothing was saved"),

    /** A cluster question named like another one that reaches some of the same stations. */
    CLUSTER_PROFILE_FIELD_NAME_REACHES_TWICE(
            Area.CLUSTERS,
            163,
            HttpStatus.BAD_REQUEST,
            "A question of that name already reaches a station this one would reach, so nothing was saved"),

    /** A cluster question given no name. */
    CLUSTER_PROFILE_FIELD_NEEDS_A_NAME(
            Area.CLUSTERS, 164, HttpStatus.BAD_REQUEST, "A question needs a name, so nothing was saved"),

    /** A cluster question of a type a cluster does not ask, which is the date of birth a station asks itself. */
    CLUSTER_PROFILE_FIELD_TYPE_NOT_OFFERED(
            Area.CLUSTERS,
            165,
            HttpStatus.BAD_REQUEST,
            "A station asks for the date of birth itself, so a cluster cannot ask for it as well. Nothing was saved"),

    /** The cluster behind its questions, gone before the request was carried out. */
    CLUSTER_PROFILE_FIELD_CLUSTER_GONE(Area.CLUSTERS, 166, HttpStatus.NOT_FOUND, Sentences.CLUSTER_NOT_HERE),

    /**
     * A cluster question that is not here, or one of another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say that the question exists at a
     * cluster the reader does not act for.
     */
    CLUSTER_PROFILE_FIELD_NOT_HERE(Area.CLUSTERS, 167, HttpStatus.NOT_FOUND, Sentences.PROFILE_FIELD_NOT_HERE),

    /**
     * A member whose cluster answers are asked for whose station is gone, or belongs to another cluster.
     *
     * <p>One code for both on purpose: telling them apart would say that the member exists at a
     * cluster the reader does not act for.
     */
    CLUSTER_PROFILE_FIELD_MEMBER_NOT_IN_CLUSTER(Area.CLUSTERS, 168, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A member whose cluster answers are asked for who is not here any more. */
    CLUSTER_PROFILE_FIELD_MEMBER_GONE(Area.CLUSTERS, 169, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

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

    /** A file in the media library deleted while news entries or appointments still hand it out, naming them. */
    MEDIA_FILE_STILL_ATTACHED(
            Area.MEDIA_LIBRARY,
            23,
            HttpStatus.BAD_REQUEST,
            "That file is still attached to news entries or appointments, so it was not deleted. "
                    + "Detach it there first. Places it is attached to"),

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

    /** An upload or a file of another kind turned into an article built from blocks. */
    KB_ONLY_WRITTEN_ARTICLES_TAKE_BLOCKS(
            Area.KNOWLEDGE_BASE,
            72,
            HttpStatus.BAD_REQUEST,
            "Only a written article can be built from blocks, so nothing was changed"),

    /** Blocks saved onto an article that is not built from them. */
    KB_ARTICLE_NOT_BUILT_FROM_BLOCKS(
            Area.KNOWLEDGE_BASE,
            73,
            HttpStatus.BAD_REQUEST,
            "This article is not built from blocks, so nothing was saved"),

    /**
     * An entry of this station marked as a favourite that is not here, belongs to another station,
     * or is closed to the reader. One code for all three, as the wiki gives one answer for them.
     */
    KB_FAVOURITE_ENTRY_NOT_HERE_OR_NOT_YOURS(
            Area.KNOWLEDGE_BASE, 74, HttpStatus.NOT_FOUND, Sentences.NOT_HERE_OR_NOT_YOURS),

    /** A favourite of this station's entry that could not be read back after being marked. */
    KB_FAVOURITE_NOT_READ_BACK_AFTER_MARKING(
            Area.KNOWLEDGE_BASE, 75, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A partner favourite asked for with a kind of entry that does not live at a partner station. */
    KB_FAVOURITE_TARGET_NOT_AT_A_PARTNER(
            Area.KNOWLEDGE_BASE, 76, HttpStatus.NOT_FOUND, "That is not an entry at a partner station"),

    /** A favourite of a partner's entry that could not be read back after being marked. */
    KB_PARTNER_FAVOURITE_NOT_READ_BACK_AFTER_MARKING(
            Area.KNOWLEDGE_BASE, 77, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A partner folder marked as a favourite that the partner's answer does not name. */
    PARTNER_KB_FOLDER_NOT_IN_ITS_TRAIL(Area.KNOWLEDGE_BASE, 78, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** The partner a favourite is kept for, not a partner of this station any more. */
    KB_FAVOURITE_PARTNER_NOT_HERE(Area.KNOWLEDGE_BASE, 79, HttpStatus.NOT_FOUND, Sentences.PARTNER_STATION_NOT_HERE),

    /** The partner whose article is commented on, not a partner of this station any more. */
    KB_COMMENT_PARTNER_NOT_HERE(Area.KNOWLEDGE_BASE, 80, HttpStatus.NOT_FOUND, Sentences.PARTNER_STATION_NOT_HERE),

    /** A comment a partner's member changes or removes on an article here, which is not here. */
    REMOTE_KB_COMMENT_NOT_HERE(Area.KNOWLEDGE_BASE, 81, HttpStatus.NOT_FOUND, Sentences.KB_COMMENT_NOT_HERE),

    /** A comment on an article here that a partner's member removes without having written it. */
    REMOTE_KB_COMMENT_NOT_YOURS_TO_DELETE(
            Area.KNOWLEDGE_BASE, 82, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_DELETE),

    /** A comment on an article here that a partner's member changes without having written it. */
    REMOTE_KB_COMMENT_NOT_YOURS_TO_EDIT(
            Area.KNOWLEDGE_BASE, 83, HttpStatus.FORBIDDEN, Sentences.NEWS_COMMENT_NOT_YOURS_TO_EDIT),

    /**
     * An article a partner asks for that is not here, belongs to another station, or is not shared
     * with it. One code for all three: telling them apart would let a partner count the articles it
     * was never shown.
     */
    REMOTE_KB_FILE_NOT_SHARED(Area.KNOWLEDGE_BASE, 84, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /**
     * A folder a partner opens that is not here, belongs to another station, or is not shared with
     * it. One code for all three, for the same reason.
     */
    REMOTE_KB_FOLDER_NOT_SHARED(Area.KNOWLEDGE_BASE, 85, HttpStatus.NOT_FOUND, Sentences.FOLDER_NOT_HERE),

    /** Who an entry is shared with, said for both an article and a folder at once, or for neither. */
    KB_AUDIENCE_NEEDS_ONE_ENTRY(
            Area.KNOWLEDGE_BASE,
            86,
            HttpStatus.BAD_REQUEST,
            "Name either an article or a folder, so nothing was saved"),

    /** An entry shared with every partner while the folder above it reaches named stations only. */
    KB_SHARE_WIDER_THAN_ITS_FOLDER(
            Area.KNOWLEDGE_BASE,
            87,
            HttpStatus.BAD_REQUEST,
            "The folder above this is shared with named stations only, so nothing was saved"),

    /** An entry shared with a station the folder above it does not reach. */
    KB_SHARE_NAMES_STATIONS_ITS_FOLDER_DOES_NOT(
            Area.KNOWLEDGE_BASE,
            88,
            HttpStatus.BAD_REQUEST,
            "The folder above this does not reach every station named, so nothing was saved"),

    /** A partner's file asked for as a PDF that has no written body to print. */
    PARTNER_KB_ONLY_WRITTEN_AS_PDF(Area.KNOWLEDGE_BASE, 89, HttpStatus.BAD_REQUEST, Sentences.KB_ONLY_WRITTEN_AS_PDF),

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

    /** A member named by the identifier a member menu hands over, who is gone or has left. */
    MEMBER_NOT_HERE_BY_UID(Area.MEMBERS, 52, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A membership being made without saying whose account it is for. */
    MEMBER_ACCOUNT_NOT_NAMED(Area.MEMBERS, 53, HttpStatus.BAD_REQUEST, "Name the account to add as a member"),

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

    /**
     * A registration code that went between the list being drawn and it being opened, or one of
     * another station. One code for both, so a number does not say that a code exists elsewhere.
     */
    REGISTRATION_CODE_NOT_HERE(Area.MEMBERS, 99, HttpStatus.NOT_FOUND, Sentences.REGISTRATION_CODE_NOT_HERE),

    /**
     * A registration code that was already gone when its deletion was asked for, or one of another
     * station. One code for both, for the same reason as opening one.
     */
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

    /**
     * The settings of an expiry date that count days backwards or repeat without a gap: a warning
     * or a reminder a negative number of days before the date, or a repeat every zero days.
     */
    EXPIRY_SETTINGS_OUT_OF_RANGE(Area.MEMBERS, 120, HttpStatus.BAD_REQUEST, Sentences.EXPIRY_OUT_OF_RANGE),

    /**
     * A change sent with the session cookie but without the token that proves this application sent
     * it. Another site can make a browser send the cookie; it cannot read the token.
     */
    REQUEST_NOT_FROM_THIS_PAGE(
            Area.MEMBERS,
            121,
            HttpStatus.FORBIDDEN,
            "This change did not come from an open page of this site, so nothing was done. Reload the page and try again"),

    /** A sign-in started on another site, which must not be able to sign a browser in here. */
    SIGN_IN_FROM_ANOTHER_SITE(
            Area.MEMBERS, 122, HttpStatus.FORBIDDEN, "Signing in only works from this site's own sign-in page"),

    /** A member put into a group from their own page whose user type the group does not take. */
    GROUP_WRONG_USER_TYPE_FOR_MEMBER(Area.MEMBERS, 123, HttpStatus.BAD_REQUEST, Sentences.GROUP_WRONG_USER_TYPE),

    /** Members added on the group page whose user type the group does not take. */
    GROUP_WRONG_USER_TYPE_ON_ADD(Area.MEMBERS, 124, HttpStatus.BAD_REQUEST, Sentences.GROUP_WRONG_USER_TYPE),

    /** Two groups of one set chosen for a member at once. */
    GROUP_SET_TWO_CHOSEN(
            Area.MEMBERS,
            125,
            HttpStatus.BAD_REQUEST,
            "A member can be in only one group of a set, so nothing was saved"),

    /** Members added to a group while they are in another group of its set, without asking to move them. */
    GROUP_SET_ALREADY_IN(
            Area.MEMBERS,
            126,
            HttpStatus.CONFLICT,
            "Some of these members are already in another group of the same set, so nothing was saved"),

    /** Groups put into one set while some members are in more than one of them. */
    GROUP_SET_MEMBERS_OVERLAP(
            Area.MEMBERS,
            127,
            HttpStatus.CONFLICT,
            "Some members are in more than one of these groups, and a set allows only one, so nothing was saved"),

    /** A group bound to user types that some of its members are not of, without asking to take them out. */
    GROUP_BINDING_EXCLUDES_MEMBERS(
            Area.MEMBERS,
            128,
            HttpStatus.CONFLICT,
            "Some members of the group are not of the chosen types, so nothing was saved"),

    /** Member ids sent for a group that belong to another station, or to nobody. */
    GROUP_MEMBER_NOT_HERE(
            Area.MEMBERS, 129, HttpStatus.BAD_REQUEST, "Some of these members are not here, so nothing was saved"),

    /** A group chosen for a member that belongs to another station, or is gone. */
    GROUP_NOT_HERE_FOR_MEMBER(Area.MEMBERS, 130, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE_NOTHING_SAVED),

    /** A member put into a group from their own page that grants more than the caller holds. */
    GROUP_GRANTS_MORE_THAN_YOURS_FOR_MEMBER(
            Area.MEMBERS, 131, HttpStatus.FORBIDDEN, Sentences.GROUP_GRANTS_MORE_THAN_YOURS),

    /** Members added on the group page to a group that grants more than the caller holds. */
    GROUP_GRANTS_MORE_THAN_YOURS_ON_ADD(
            Area.MEMBERS, 132, HttpStatus.FORBIDDEN, Sentences.GROUP_GRANTS_MORE_THAN_YOURS),

    /** New members invited into a group that grants more than the caller holds. */
    GROUP_GRANTS_MORE_THAN_YOURS_ON_INVITE(
            Area.MEMBERS, 133, HttpStatus.FORBIDDEN, Sentences.GROUP_GRANTS_MORE_THAN_YOURS),

    /** New members invited into a group of another station, or one that is gone. */
    INVITE_GROUP_NOT_HERE(Area.MEMBERS, 134, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE_NOTHING_SAVED),

    /** A group put into a set of another station, or one that is gone. */
    GROUP_SET_NOT_HERE_FOR_GROUP(Area.MEMBERS, 135, HttpStatus.NOT_FOUND, Sentences.GROUP_SET_NOT_HERE),

    /** A set of groups created without a name. */
    GROUP_SET_NAME_MISSING_ON_CREATE(Area.MEMBERS, 136, HttpStatus.BAD_REQUEST, Sentences.GROUP_SET_NAME_MISSING),

    /** A set of groups renamed to nothing. */
    GROUP_SET_NAME_MISSING_ON_CHANGE(Area.MEMBERS, 137, HttpStatus.BAD_REQUEST, Sentences.GROUP_SET_NAME_MISSING),

    /** A set of groups created with a name another set of the station carries. */
    GROUP_SET_NAME_TAKEN_ON_CREATE(Area.MEMBERS, 138, HttpStatus.CONFLICT, Sentences.GROUP_SET_NAME_TAKEN),

    /** A set of groups renamed to a name another set of the station carries. */
    GROUP_SET_NAME_TAKEN_ON_CHANGE(Area.MEMBERS, 139, HttpStatus.CONFLICT, Sentences.GROUP_SET_NAME_TAKEN),

    /** A set of groups renamed that is not here, or belongs to another station. */
    GROUP_SET_NOT_HERE_ON_CHANGE(Area.MEMBERS, 140, HttpStatus.NOT_FOUND, Sentences.GROUP_SET_NOT_HERE),

    /** A set of groups deleted that is not here, or belongs to another station. */
    GROUP_SET_NOT_HERE_ON_DELETE(Area.MEMBERS, 141, HttpStatus.NOT_FOUND, Sentences.GROUP_SET_NOT_HERE),

    /** A registration code whose groups include one that does not take members. */
    REGISTRATION_CODE_GROUP_WRONG_USER_TYPE(
            Area.MEMBERS,
            142,
            HttpStatus.BAD_REQUEST,
            "Somebody registering with a code becomes a member, and one of these groups does not take members, so nothing was saved"),

    /** A registration code whose groups include one of another station, or one that is gone. */
    REGISTRATION_CODE_GROUP_NOT_HERE(Area.MEMBERS, 143, HttpStatus.NOT_FOUND, Sentences.GROUP_NOT_HERE_NOTHING_SAVED),

    /** The consequences of a type change asked for a type that does not exist. */
    USER_TYPE_UNKNOWN_FOR_CONSEQUENCES(
            Area.MEMBERS, 144, HttpStatus.BAD_REQUEST, "That is not a type a member can have"),

    /** The groups of a registration code asked for that is gone or belongs to another station. */
    REGISTRATION_CODE_NOT_HERE_FOR_GROUPS(
            Area.MEMBERS, 145, HttpStatus.NOT_FOUND, Sentences.REGISTRATION_CODE_NOT_HERE),

    /** The groups of a registration code changed that is gone or belongs to another station. */
    REGISTRATION_CODE_NOT_HERE_TO_CHANGE_GROUPS(
            Area.MEMBERS, 146, HttpStatus.NOT_FOUND, Sentences.REGISTRATION_CODE_NOT_HERE),

    /** A value written under an age, which counts itself from a date and holds none of its own. */
    PROFILE_AGE_TAKES_NO_ANSWER(
            Area.MEMBERS,
            147,
            HttpStatus.BAD_REQUEST,
            "An age is counted from a date and takes no answer of its own, so nothing was saved"),

    /** A profile answer the question does not take, such as an unknown choice or a day that is no date. */
    PROFILE_ANSWER_NOT_ACCEPTED(
            Area.MEMBERS, 148, HttpStatus.BAD_REQUEST, "That answer does not suit this question, so nothing was saved"),

    /** A profile question given a type the member profile does not offer. */
    PROFILE_FIELD_TYPE_NOT_OFFERED(
            Area.MEMBERS,
            149,
            HttpStatus.BAD_REQUEST,
            "The member profile does not offer that type of question, so nothing was saved"),

    /** Registering far more often from one address than a person could. */
    REGISTERING_TOO_OFTEN(Area.MEMBERS, 150, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Confirming an email address far more often from one address than a person could. */
    EMAIL_VERIFYING_TOO_OFTEN(Area.MEMBERS, 151, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Asking for the confirmation mail again far more often than a person could. */
    VERIFICATION_MAIL_TOO_OFTEN(Area.MEMBERS, 152, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting a password through a link far more often from one address than a person could. */
    PASSWORD_SETTING_TOO_OFTEN(Area.MEMBERS, 153, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting a sign-in address through a link far more often from one address than a person could. */
    ADDRESS_SETTING_TOO_OFTEN(Area.MEMBERS, 154, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Checking whether a password link still holds far more often than a person could. */
    PASSWORD_LINK_CHECKED_TOO_OFTEN(Area.MEMBERS, 155, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Asking for a password reset far more often than a person could. */
    PASSWORD_RESET_TOO_OFTEN(Area.MEMBERS, 156, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Signing in with a password far more often than a person could. */
    SIGN_IN_TOO_OFTEN(Area.MEMBERS, 157, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Changing one's own password far more often than a person could. */
    PASSWORD_CHANGE_TOO_OFTEN(Area.MEMBERS, 158, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Confirming a change of email address far more often than a person could. */
    EMAIL_CHANGE_CONFIRMED_TOO_OFTEN(Area.MEMBERS, 159, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A profile answer sent in a shape no answer is written in. */
    PROFILE_ANSWER_NOT_READABLE(
            Area.MEMBERS,
            160,
            HttpStatus.BAD_REQUEST,
            "That is not an answer this field can take, so nothing was saved"),

    /** A permission named for a member or a group that no permission answers to. */
    MEMBER_PERMISSION_UNKNOWN(
            Area.MEMBERS, 161, HttpStatus.BAD_REQUEST, "That permission does not exist, so nothing was saved"),

    /** A permission handed to a member or a group by somebody who does not hold it themselves. */
    MEMBER_PERMISSION_NOT_YOURS_TO_GRANT(
            Area.MEMBERS,
            162,
            HttpStatus.FORBIDDEN,
            "You cannot hand out a permission you do not hold yourself, so nothing was saved"),

    /** A member taking away one of their own permissions. */
    MEMBER_OWN_PERMISSION_NOT_REMOVABLE(
            Area.MEMBERS, 163, HttpStatus.FORBIDDEN, "You cannot take away your own permissions, so nothing was saved"),

    /** The permission to administer the station, taken from whoever owns it. */
    MEMBER_OWNER_KEEPS_ADMINISTRATION(
            Area.MEMBERS,
            164,
            HttpStatus.FORBIDDEN,
            "Whoever owns the station keeps the permission to administer it, so nothing was saved"),

    /** Signing in switched on for a member whose account has no address to sign in with. */
    MEMBER_SIGN_IN_NEEDS_AN_ADDRESS(
            Area.MEMBERS,
            165,
            HttpStatus.BAD_REQUEST,
            "Signing in needs an email address on the account, so nothing was saved"),

    /** The member somebody is to look after, gone before the guardians were written. */
    MEMBER_NOT_HERE_FOR_GUARDIANS(Area.MEMBERS, 166, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A guardian given to a member who is neither a member nor a trial member, and so looks after themselves. */
    MEMBER_TYPE_TAKES_NO_GUARDIANS(
            Area.MEMBERS,
            167,
            HttpStatus.BAD_REQUEST,
            "Only members and trial members can be given a guardian, so nothing was saved"),

    /** A passkey code a guardian asked for, for a member with an address of their own. */
    MANAGED_MEMBER_HAS_OWN_ADDRESS(
            Area.MEMBERS, 168, HttpStatus.FORBIDDEN, "This member has an address of their own: the way in is by mail"),

    /** Signing in switched on for a member in somebody's care, at an instance that has no permission for it. */
    MANAGED_SIGN_IN_PERMISSION_MISSING(
            Area.MEMBERS,
            169,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Signing in cannot be switched on here, so nothing was changed"),

    /** Signing in switched on for a member in somebody's care who has neither an address nor a username. */
    MANAGED_SIGN_IN_NEEDS_A_NAME_OR_ADDRESS(
            Area.MEMBERS,
            170,
            HttpStatus.BAD_REQUEST,
            "Give this member an email address or a username before letting them sign in, so nothing was changed"),

    /** A member in somebody's care who has no account to give access to. */
    MANAGED_MEMBER_HAS_NO_ACCOUNT(Area.MEMBERS, 171, HttpStatus.BAD_REQUEST, "This member has no account yet"),

    /** The account of a member in somebody's care, gone between the member and the account being read. */
    MANAGED_ACCOUNT_NOT_HERE(Area.MEMBERS, 172, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** A guardian reaching for the access of a member they do not look after. */
    MANAGED_MEMBER_NOT_YOURS(Area.MEMBERS, 173, HttpStatus.FORBIDDEN, Sentences.MEMBER_NOT_YOURS),

    /** A member a guardian looks after, gone before their access was read. */
    MANAGED_MEMBER_NOT_HERE(Area.MEMBERS, 174, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A guardian reaching for the access of somebody who is neither a member nor a trial member. */
    MANAGED_MEMBER_TYPE_NOT_MANAGED(
            Area.MEMBERS, 175, HttpStatus.FORBIDDEN, "Only members and trial members are looked after this way"),

    /** The member a nickname is written for, gone before it was written. */
    NICKNAME_MEMBER_NOT_HERE(Area.MEMBERS, 176, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A nickname written by somebody who is neither the member, nor looks after them, nor keeps the members. */
    NICKNAME_NOT_YOURS_TO_SET(
            Area.MEMBERS,
            177,
            HttpStatus.FORBIDDEN,
            "Only the member, whoever looks after them, or whoever keeps the station's members may set the "
                    + "nickname, so nothing was saved"),

    /** A nickname longer than one is kept. */
    NICKNAME_TOO_LONG(
            Area.MEMBERS, 178, HttpStatus.BAD_REQUEST, "A nickname is at most 60 characters, so nothing was saved"),

    /** A nickname with a line break in it. */
    NICKNAME_NOT_ONE_LINE(Area.MEMBERS, 179, HttpStatus.BAD_REQUEST, "A nickname is one line, so nothing was saved"),

    /** A username shorter or longer than one is kept. */
    USERNAME_LENGTH_NOT_TAKEN(
            Area.MEMBERS,
            180,
            HttpStatus.BAD_REQUEST,
            "A username is between 3 and 32 characters long, so nothing was saved"),

    /** A username with a character in it that a username does not hold, an at sign among them. */
    USERNAME_CHARACTERS_NOT_TAKEN(
            Area.MEMBERS,
            181,
            HttpStatus.BAD_REQUEST,
            "A username holds only letters, digits, dots, hyphens and underscores, so nothing was saved"),

    /** A username another account already signs in with. */
    USERNAME_TAKEN(
            Area.MEMBERS,
            182,
            HttpStatus.CONFLICT,
            "That username already belongs to another account, so nothing was saved"),

    /** A username taken away from an account that has nothing else to sign in with. */
    USERNAME_IS_THE_ONLY_WAY_IN(
            Area.MEMBERS,
            183,
            HttpStatus.BAD_REQUEST,
            "This account signs in with its username alone. Give it an email address before taking the "
                    + "username away"),

    /** An account writing its own address without confirming it, which is the way an account is taken over. */
    OWN_ADDRESS_NOT_WRITTEN_UNCONFIRMED(
            Area.MEMBERS,
            184,
            HttpStatus.FORBIDDEN,
            "Your own address is changed by confirming the new one, so nothing was saved"),

    /** The account an address is written onto, gone before it was written. */
    ACCOUNT_NOT_HERE_ON_ADDRESS_WRITE(Area.MEMBERS, 185, HttpStatus.NOT_FOUND, Sentences.ACCOUNT_NOT_HERE),

    /** An address written onto an account that is not one, or one nothing can be delivered to. */
    ADDRESS_NOT_GOOD_ON_ADDRESS_WRITE(
            Area.MEMBERS,
            186,
            HttpStatus.BAD_REQUEST,
            "That is not an address anything can be sent to, so nothing was saved"),

    /** An address written onto an account that another account already carries. */
    ADDRESS_TAKEN_ON_ADDRESS_WRITE(Area.MEMBERS, 187, HttpStatus.CONFLICT, Sentences.ADDRESS_BELONGS_TO_ANOTHER),

    /** A second date of birth asked for at a station that already asks for one, named after it. */
    PROFILE_BIRTH_DATE_ALREADY_ASKED(
            Area.MEMBERS,
            188,
            HttpStatus.BAD_REQUEST,
            "This station already asks for the date of birth in another question, so nothing was saved"),

    /** A profile question saved with a default its own answers would not take, naming what is wrong. */
    PROFILE_DEFAULT_NOT_ACCEPTED(Area.MEMBERS, 189, HttpStatus.BAD_REQUEST, Sentences.DEFAULT_NOT_SUITING),

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
     * A settings change whose configuration file could not be written. The change is taken back,
     * so the instance keeps running on what the file says.
     */
    SETTINGS_NOT_SAVED(
            Area.SYSTEM,
            34,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The configuration file could not be written, so the settings were left as they were"),

    /** A look the instance is to start every station from that is not one of the looks on offer. */
    THEME_FEEL_UNKNOWN(Area.SYSTEM, 35, HttpStatus.BAD_REQUEST, Sentences.LOOK_NOT_OFFERED),

    /** An action the public demo blocks outright by its address. */
    DEMO_BLOCKS_ACTION(
            Area.SYSTEM, 36, HttpStatus.BAD_REQUEST, "This is switched off in the demo, so nothing was done"),

    /** An upload to an address the public demo answers reads on but takes no writes at. */
    DEMO_BLOCKS_UPLOAD(Area.SYSTEM, 37, HttpStatus.BAD_REQUEST, Sentences.DEMO_UPLOADS_OFF),

    /** Creating or deleting a station on the public demo. */
    DEMO_BLOCKS_STATION_MANAGEMENT(
            Area.SYSTEM,
            38,
            HttpStatus.BAD_REQUEST,
            "Adding and removing stations is switched off in the demo, so nothing was changed"),

    /** Changing the roles of a member or a group on the public demo. */
    DEMO_BLOCKS_ROLE_CHANGES(
            Area.SYSTEM,
            39,
            HttpStatus.BAD_REQUEST,
            "Changing roles is switched off in the demo, so nothing was changed"),

    /** Setting up a security key on the public demo, which would lock the demo account behind it. */
    DEMO_BLOCKS_SECURITY_KEY_SETUP(
            Area.SYSTEM,
            40,
            HttpStatus.BAD_REQUEST,
            "Setting up a security key is switched off in the demo, so nothing was saved"),

    /** Accepting an invite to a station on the public demo. */
    DEMO_BLOCKS_ACCEPTING_INVITES(
            Area.SYSTEM,
            41,
            HttpStatus.BAD_REQUEST,
            "Accepting invites is switched off in the demo, so nothing was done"),

    /** Signing up for a public waiting list on the public demo. */
    DEMO_BLOCKS_PUBLIC_WAITING_LIST_SIGN_UP(
            Area.SYSTEM,
            42,
            HttpStatus.BAD_REQUEST,
            "Signing up for a waiting list is switched off in the demo, so nothing was saved"),

    /** A file uploaded to a page on the public demo. */
    DEMO_BLOCKS_PAGE_UPLOADS(Area.SYSTEM, 43, HttpStatus.BAD_REQUEST, Sentences.DEMO_UPLOADS_OFF),

    /** A folder icon or an article picture uploaded to the knowledge base on the public demo. */
    DEMO_BLOCKS_KB_UPLOADS(Area.SYSTEM, 44, HttpStatus.BAD_REQUEST, Sentences.DEMO_UPLOADS_OFF),

    /** Probing another instance from the public demo. */
    DEMO_BLOCKS_PEER_PROBES(
            Area.SYSTEM,
            45,
            HttpStatus.BAD_REQUEST,
            "Reaching out to other instances is switched off in the demo, so nothing was done"),

    /** Asking another station to lend gear on the public demo. */
    DEMO_BLOCKS_LENDING(
            Area.SYSTEM,
            46,
            HttpStatus.BAD_REQUEST,
            "Borrowing from other stations is switched off in the demo, so nothing was saved"),

    /** Asking an AI provider for its models on the public demo. */
    DEMO_BLOCKS_AI_CALLS(
            Area.SYSTEM,
            47,
            HttpStatus.BAD_REQUEST,
            "Calling AI providers is switched off in the demo, so nothing was done"),

    /** Mail settings tested on the instance with a recipient that is plainly not an address. */
    INSTANCE_TEST_MAIL_RECIPIENT_NOT_AN_ADDRESS(
            Area.SYSTEM, 48, HttpStatus.BAD_REQUEST, "That is not an email address, so nothing was sent"),

    /** The picture of a problem report that arrived but could not be kept. */
    PROBLEM_PICTURE_NOT_KEPT(
            Area.SYSTEM,
            49,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The picture could not be kept, so the report was not sent"),

    /** The picture of a problem report that does not read as a picture at all. */
    PROBLEM_PICTURE_NOT_READABLE(
            Area.SYSTEM, 50, HttpStatus.BAD_REQUEST, "The picture could not be read, so the report was not sent"),

    /** The picture of a problem report that was announced and has nothing in it. */
    PROBLEM_PICTURE_EMPTY(Area.SYSTEM, 51, HttpStatus.BAD_REQUEST, "The picture is empty, so the report was not sent"),

    /** The picture of a problem report larger than a report takes. */
    PROBLEM_PICTURE_TOO_LARGE(
            Area.SYSTEM, 52, HttpStatus.BAD_REQUEST, "The picture is too large, so the report was not sent"),

    /** The picture of a problem report that is neither a PNG nor a WebP. */
    PROBLEM_PICTURE_KIND_NOT_TAKEN(
            Area.SYSTEM,
            53,
            HttpStatus.BAD_REQUEST,
            "A report only takes a picture as PNG or WebP, so the report was not sent"),

    /** Data tracking changed for a table it keeps no record of. */
    TRACKED_TABLE_NOT_HERE_TO_UPDATE(Area.SYSTEM, 54, HttpStatus.NOT_FOUND, Sentences.TRACKED_TABLE_NOT_HERE),

    /** Every column of a table marked checked where data tracking keeps no record of the table. */
    TRACKED_TABLE_NOT_HERE_TO_VERIFY(Area.SYSTEM, 55, HttpStatus.NOT_FOUND, Sentences.TRACKED_TABLE_NOT_HERE),

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

    /**
     * The storage the files of this station are kept on could not be reached: the connection was
     * lost and could not be had again, or the server did not answer in time. Not a fault of Ember's,
     * which is why it is a {@code 503} and says to try again rather than hand out a reference.
     */
    STORAGE_UNREACHABLE(
            Area.STORAGE,
            18,
            HttpStatus.SERVICE_UNAVAILABLE,
            "The storage of this station cannot be reached right now. Trying again in a moment may work"),

    /** A station named on the storage administration that is not here. */
    STATION_NOT_HERE_FOR_STORAGE_ADMIN(Area.STORAGE, 19, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** Something named as a station on the storage administration that does not read as one. */
    STATION_NOT_AN_IDENTITY_FOR_STORAGE_ADMIN(
            Area.STORAGE, 20, HttpStatus.BAD_REQUEST, Sentences.STATION_NOT_AN_IDENTITY),

    /** The storage history asked for from before something that does not read as a point in time. */
    STORAGE_AUDIT_BEFORE_NOT_A_TIME(
            Area.STORAGE,
            21,
            HttpStatus.BAD_REQUEST,
            "The point in time to list the changes before is not a point in time"),

    /** Files asked for on behalf of a station moving away that is not here any more. */
    STATION_NOT_HERE_FOR_TRANSFER(Area.STORAGE, 22, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A file asked for on behalf of a station moving away that is not here any more. */
    TRANSFER_FILE_NOT_HERE(Area.STORAGE, 23, HttpStatus.NOT_FOUND, Sentences.FILE_NOT_HERE),

    /** A change at the caller's own station while that station is moving to another instance. */
    STATION_READ_ONLY_FOR_TRANSFER(
            Area.STORAGE, 24, HttpStatus.SERVICE_UNAVAILABLE, Sentences.STATION_BEING_TRANSFERRED),

    /** A change to a station's storage quota or usage by an administrator while that station is moving. */
    STATION_READ_ONLY_FOR_TRANSFER_ON_ADMIN_STORAGE(
            Area.STORAGE, 25, HttpStatus.SERVICE_UNAVAILABLE, Sentences.STATION_BEING_TRANSFERRED),

    /** Work outside a request, such as a scheduled job or a partner's call, at a station that is moving. */
    STATION_READ_ONLY_FOR_BACKGROUND_WRITE(
            Area.STORAGE, 26, HttpStatus.SERVICE_UNAVAILABLE, Sentences.STATION_BEING_TRANSFERRED),

    /** A file stored for a station that is moving to another instance. */
    STATION_READ_ONLY_FOR_FILE_WRITE(
            Area.STORAGE, 27, HttpStatus.SERVICE_UNAVAILABLE, Sentences.STATION_BEING_TRANSFERRED),

    /** A file stored while the instance moves all its files to another storage. */
    INSTANCE_STORAGE_MOVING(
            Area.STORAGE,
            28,
            HttpStatus.SERVICE_UNAVAILABLE,
            "The instance is moving its files to another storage, so nothing was saved. Try again once the move is done"),

    /** The storage of a moving station asked for with a transfer code that is not good. */
    TRANSFER_TOKEN_NOT_GOOD_ON_BACKEND(Area.STORAGE, 29, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** The storage of a moving station asked for a second time, when it is handed over once only. */
    TRANSFER_BACKEND_ALREADY_HANDED_OVER(
            Area.STORAGE,
            30,
            HttpStatus.GONE,
            "The storage of this move has already been handed over, and it is handed over only once"),

    /** The files of a moving station listed with a transfer code that is not good. */
    TRANSFER_TOKEN_NOT_GOOD_ON_FILE_LIST(Area.STORAGE, 31, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** One file of a moving station asked for with a transfer code that is not good. */
    TRANSFER_TOKEN_NOT_GOOD_ON_FILE(Area.STORAGE, 32, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** One file of a moving station asked for without saying which. */
    TRANSFER_FILE_KEY_MISSING(Area.STORAGE, 33, HttpStatus.BAD_REQUEST, "Say which file is wanted"),

    /** An account's picture asked for with a transfer code that is not good. */
    TRANSFER_TOKEN_NOT_GOOD_ON_AVATAR(Area.STORAGE, 34, HttpStatus.FORBIDDEN, Sentences.TRANSFER_TOKEN_NOT_GOOD),

    /** An account's picture asked for during a move where the account has none. */
    TRANSFER_AVATAR_NOT_HERE(Area.STORAGE, 35, HttpStatus.NOT_FOUND, "That account has no picture to carry over"),

    /** Files of a moving station asked for without saying which kind. */
    TRANSFER_FILE_KIND_MISSING(Area.STORAGE, 36, HttpStatus.BAD_REQUEST, "Say which kind of file is wanted"),

    /** Files of a moving station asked for by a kind no instance keeps. */
    TRANSFER_FILE_KIND_UNKNOWN(
            Area.STORAGE, 37, HttpStatus.BAD_REQUEST, "That is not a kind of file this instance keeps"),

    /** Files of a moving station asked for by a kind that belongs to the instance, not to a station. */
    TRANSFER_FILE_KIND_NOT_A_STATIONS(
            Area.STORAGE, 38, HttpStatus.BAD_REQUEST, "That kind of file does not belong to a station"),

    /** Files of a moving station asked for by a kind that does not move with a station. */
    TRANSFER_FILE_KIND_NOT_MOVABLE(
            Area.STORAGE, 39, HttpStatus.BAD_REQUEST, "That kind of file does not move with a station"),

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

    /** A stretch of time for a station's figures that ends before it starts. */
    STATION_TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS(
            Area.TRAFFIC, 10, HttpStatus.BAD_REQUEST, Sentences.TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS),

    /** A piece of the map asked for without saying which piece. */
    MAP_TILE_NUMBER_MISSING(Area.MAPS, 1, HttpStatus.BAD_REQUEST, "Name the piece of the map you want"),

    /** A piece of the map tried out by an administrator and named by something that is not a number. */
    MAP_TILE_NUMBER_NOT_A_NUMBER(Area.MAPS, 2, HttpStatus.BAD_REQUEST, Sentences.MAP_TILE_NOT_A_NUMBER),

    /** A piece of the map asked for on a public page and named by something that is not a number. */
    PUBLIC_MAP_TILE_NUMBER_NOT_A_NUMBER(Area.MAPS, 3, HttpStatus.BAD_REQUEST, Sentences.MAP_TILE_NOT_A_NUMBER),

    /** A piece of the map asked for at a zoom this instance does not serve. */
    MAP_TILE_ZOOM_OUT_OF_RANGE(Area.MAPS, 4, HttpStatus.NOT_FOUND, "The map is not served at that zoom"),

    /** A piece of the map whose column or row lies outside the map at its zoom. */
    MAP_TILE_OFF_THE_MAP(Area.MAPS, 5, HttpStatus.NOT_FOUND, "That piece lies outside the map"),

    /** Pieces of the map asked for faster than one address may ask for them. */
    MAP_TILES_TOO_OFTEN(Area.MAPS, 6, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A map setting saved without choosing where the map pictures come from. */
    MAP_TILE_PROVIDER_MISSING(
            Area.MAPS, 7, HttpStatus.BAD_REQUEST, "Choose where the map comes from, so nothing was saved"),

    /** A map setting whose closest and farthest zoom do not make a range the map is served at. */
    MAP_ZOOM_RANGE_NOT_GOOD(
            Area.MAPS,
            8,
            HttpStatus.BAD_REQUEST,
            "The zoom has to run from 0 to at most 22, smallest first, so nothing was saved"),

    /** A map provider that needs a key, chosen without one. */
    MAP_TILE_PROVIDER_KEY_MISSING(
            Area.MAPS, 9, HttpStatus.BAD_REQUEST, "That map provider needs a key, so nothing was saved"),

    /** A map of its own chosen without the address its pictures are fetched from. */
    MAP_TILE_ADDRESS_MISSING(
            Area.MAPS,
            10,
            HttpStatus.BAD_REQUEST,
            "A map of your own needs the address its pictures come from, so nothing was saved"),

    /** An address search setting saved without choosing who answers the search. */
    MAP_GEOCODING_PROVIDER_MISSING(
            Area.MAPS, 11, HttpStatus.BAD_REQUEST, "Choose who looks up addresses, so nothing was saved"),

    /** A map cache size below nothing or above what an instance may keep. */
    MAP_TILE_CACHE_SIZE_OUT_OF_RANGE(
            Area.MAPS,
            12,
            HttpStatus.BAD_REQUEST,
            "The map cache has to be between 0 and 10000 MB, so nothing was saved"),

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

    /** Consent given without saying which version of the consent text was clicked through. */
    LEGAL_CONSENT_VERSION_MISSING(
            Area.LEGAL,
            2,
            HttpStatus.BAD_REQUEST,
            "Say which version of the consent text was agreed to, so nothing was saved"),

    /** Consent given without saying which version of the privacy policy was clicked through. */
    LEGAL_PRIVACY_VERSION_MISSING(
            Area.LEGAL,
            3,
            HttpStatus.BAD_REQUEST,
            "Say which version of the privacy policy was agreed to, so nothing was saved"),

    /** Consent given without saying which version of the terms of service was clicked through. */
    LEGAL_TERMS_VERSION_MISSING(
            Area.LEGAL,
            4,
            HttpStatus.BAD_REQUEST,
            "Say which version of the terms of service was agreed to, so nothing was saved"),

    /** Consent given to legal documents that have changed since the form was loaded. */
    LEGAL_DOCUMENTS_CHANGED(
            Area.LEGAL,
            5,
            HttpStatus.CONFLICT,
            "The legal documents have changed since the form was loaded, so nothing was saved. "
                    + "Reload the page and agree again"),

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

    /** A reply to the passkey offer that gives no answer. */
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

    /** A change of the passkey mode that names no mode. */
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

    /** A passkey set up on another device, asked for far more often than a person could. */
    PASSKEY_DEVICE_REQUEST_TOO_OFTEN(Area.PASSKEYS, 43, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A sign-in through another device asked for far more often than a person could. */
    PASSKEY_SIGN_IN_REQUEST_TOO_OFTEN(Area.PASSKEYS, 44, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A sign-in another device approved, collected far more often than a person could. */
    PASSKEY_SIGN_IN_CLAIM_TOO_OFTEN(Area.PASSKEYS, 45, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Waiting on another device's approval, asked after far more often than a screen would. */
    PASSKEY_DEVICE_POLL_TOO_OFTEN(Area.PASSKEYS, 46, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting up a passkey on an approved device, started far more often than a person could. */
    PASSKEY_DEVICE_ENROLL_BEGIN_TOO_OFTEN(Area.PASSKEYS, 47, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting up a passkey on an approved device, finished far more often than a person could. */
    PASSKEY_DEVICE_ENROLL_FINISH_TOO_OFTEN(
            Area.PASSKEYS, 48, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A passkey setup link looked up far more often than a person could. */
    PASSKEY_TOKEN_LOOKUP_TOO_OFTEN(Area.PASSKEYS, 49, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting up a passkey through a link, started far more often than a person could. */
    PASSKEY_TOKEN_ENROLL_BEGIN_TOO_OFTEN(Area.PASSKEYS, 50, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Setting up a passkey through a link, finished far more often than a person could. */
    PASSKEY_TOKEN_ENROLL_FINISH_TOO_OFTEN(Area.PASSKEYS, 51, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A code shown on another device, looked up far more often than a person could type one. */
    PASSKEY_DEVICE_CODE_LOOKUP_TOO_OFTEN(Area.PASSKEYS, 52, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Another device approved far more often than a person could. */
    PASSKEY_DEVICE_APPROVAL_TOO_OFTEN(Area.PASSKEYS, 53, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Signing in with a passkey, started far more often than a person could. */
    PASSKEY_SIGN_IN_BEGIN_TOO_OFTEN(Area.PASSKEYS, 54, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Signing in with a passkey, finished far more often than a person could. */
    PASSKEY_SIGN_IN_FINISH_TOO_OFTEN(Area.PASSKEYS, 55, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

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

    /** A second factor proved at the sign-in step far more often than a person could. */
    TWO_FACTOR_CODE_TOO_OFTEN(Area.TWO_FACTOR, 42, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A second factor proved again for a sensitive action far more often than a person could. */
    TWO_FACTOR_STEP_UP_TOO_OFTEN(Area.TWO_FACTOR, 43, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A security key used to sign in far more often than a person could. */
    SECURITY_KEY_SIGN_IN_TOO_OFTEN(Area.TWO_FACTOR, 44, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A security key used to confirm a sensitive action far more often than a person could. */
    SECURITY_KEY_STEP_UP_TOO_OFTEN(Area.TWO_FACTOR, 45, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Another device asked to confirm a sensitive action far more often than a person could. */
    STEP_UP_DEVICE_REQUEST_TOO_OFTEN(Area.TWO_FACTOR, 46, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** Waiting on another device's confirmation, asked after far more often than a screen would. */
    STEP_UP_DEVICE_POLL_TOO_OFTEN(Area.TWO_FACTOR, 47, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A sensitive action confirmed with the password far more often than a person could. */
    STEP_UP_PASSWORD_TOO_OFTEN(Area.TWO_FACTOR, 48, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A sensitive action confirmed with a passkey far more often than a person could. */
    STEP_UP_PASSKEY_TOO_OFTEN(Area.TWO_FACTOR, 49, HttpStatus.TOO_MANY_REQUESTS, Sentences.TOO_MANY_ATTEMPTS),

    /** A session that another device vouched for, trying to vouch for a further one. */
    VOUCHED_SESSION_CANNOT_VOUCH(
            Area.TWO_FACTOR,
            50,
            HttpStatus.FORBIDDEN,
            "A sign-in another device confirmed cannot confirm a further device. Use a device you signed in on yourself"),

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

    /** A page filed under one that is reached by its link alone. */
    PAGE_UNDER_A_LINK_ONLY_PAGE(
            Area.PAGES,
            27,
            HttpStatus.BAD_REQUEST,
            "A page reached by its link alone cannot hold pages under it, so nothing was saved"),

    /** A page reached by its link alone, filed under another page. */
    PAGE_LINK_ONLY_UNDER_ANOTHER(
            Area.PAGES,
            28,
            HttpStatus.BAD_REQUEST,
            "A page reached by its link alone does not sit under another, so nothing was saved"),

    /** A page with pages under it, made reachable by its link alone. */
    PAGE_WITH_CHILDREN_NOT_LINK_ONLY(
            Area.PAGES,
            29,
            HttpStatus.BAD_REQUEST,
            "A page with pages under it cannot be reached by its link alone, so nothing was changed"),

    /** A new link asked for on a page nobody outside the station can open. */
    PAGE_LINK_NOT_FOR_A_CLOSED_PAGE(
            Area.PAGES, 30, HttpStatus.BAD_REQUEST, "Nobody outside can open this page, so it has no link to replace"),

    /** The page the site opens on, named from another station's pages. */
    LANDING_PAGE_ELSEWHERE(
            Area.PAGES, 31, HttpStatus.BAD_REQUEST, "That page belongs to another station, so nothing was changed"),

    /** The page the site opens on, named from pages that are not public. */
    LANDING_PAGE_NOT_PUBLIC(
            Area.PAGES,
            32,
            HttpStatus.BAD_REQUEST,
            "The page the site opens on has to be public, so nothing was changed"),

    /** The page the site opens on, named from pages under another page. */
    LANDING_PAGE_UNDER_ANOTHER(
            Area.PAGES,
            33,
            HttpStatus.BAD_REQUEST,
            "The page the site opens on cannot sit under another page, so nothing was changed"),

    /** A page filed deeper than pages go. */
    PAGE_TREE_TOO_DEEP(
            Area.PAGES, 34, HttpStatus.BAD_REQUEST, "Pages go at most three levels deep, so nothing was saved"),

    /** A block that only a page may hold, put into a news entry or a knowledge base article. */
    CONTENT_BLOCK_ONLY_ON_PAGES(
            Area.PAGES,
            35,
            HttpStatus.BAD_REQUEST,
            "That block can only be used on a page, not in a news entry or an article, so nothing was saved"),

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

    /** A test started by somebody it is not open to, whether by time or by who may sit it. */
    QUIZ_TEST_NOT_OPEN_TO_YOU(
            Area.QUIZZES, 47, HttpStatus.FORBIDDEN, "This test is not open to you now, so no attempt was started"),

    /** An answer on a paper that is gone, reached for by somebody marking it. */
    QUIZ_ANSWER_NOT_HERE(Area.QUIZZES, 49, HttpStatus.NOT_FOUND, "That answer is not here any more"),

    /** A mark given for an answer without saying how many points it is worth. */
    QUIZ_GRADE_NEEDS_POINTS(
            Area.QUIZZES, 50, HttpStatus.BAD_REQUEST, "Give the points for this answer, so no mark was saved"),

    /** A paper that went between its marks being written and being read back, with the marks in. */
    QUIZ_ATTEMPT_NOT_HERE_AFTER_GRADING(
            Area.QUIZZES,
            52,
            HttpStatus.NOT_FOUND,
            "The marks were saved, but that attempt could not be read back. "
                    + "Reload the page to see it as it stands"),

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

    /** A personal AI key saved for a provider this instance cannot call. */
    AI_KEY_PROVIDER_UNKNOWN(
            Area.QUIZZES, 70, HttpStatus.BAD_REQUEST, "That AI provider is not one this instance can use"),

    /**
     * A personal AI setting saved without a key, where none is stored for that provider to keep: the
     * first save, a change of provider, or a stored key that no longer opens.
     */
    AI_KEY_MISSING(Area.QUIZZES, 71, HttpStatus.BAD_REQUEST, "Enter the key for this provider, so nothing was saved"),

    /** A personal AI key that was saved and then could not be read back to answer with. */
    AI_KEY_NOT_READ_BACK(Area.QUIZZES, 72, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A catalog to generate questions or answers for that is gone or belongs to another station. */
    AI_GENERATION_CATALOG_NOT_HERE(Area.QUIZZES, 73, HttpStatus.NOT_FOUND, Sentences.QUIZ_CATALOG_NOT_HERE),

    /** The example catalog sheet Ember ships, missing from the build. Ours to look into. */
    QUIZ_CATALOG_EXAMPLE_MISSING(
            Area.QUIZZES, 74, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.QUIZ_CATALOG_EXAMPLE_NOT_READ),

    /** The example catalog sheet Ember ships, found but not read. Ours to look into. */
    QUIZ_CATALOG_EXAMPLE_NOT_READ(
            Area.QUIZZES, 75, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.QUIZ_CATALOG_EXAMPLE_NOT_READ),

    /** A catalog file uploaded that is not a single object at all. */
    QUIZ_CATALOG_FILE_NOT_AN_OBJECT(Area.QUIZZES, 76, HttpStatus.BAD_REQUEST, Sentences.QUIZ_CATALOG_FILE_NOT_ONE),

    /** A catalog file uploaded that is neither the current kind nor the one earlier versions wrote. */
    QUIZ_CATALOG_FILE_NOT_RECOGNISED(Area.QUIZZES, 77, HttpStatus.BAD_REQUEST, Sentences.QUIZ_CATALOG_FILE_NOT_ONE),

    /** A catalog file of the current kind whose contents do not fit it. */
    QUIZ_CATALOG_FILE_NOT_READ(Area.QUIZZES, 78, HttpStatus.BAD_REQUEST, Sentences.QUIZ_CATALOG_FILE_NOT_ONE),

    /** A note about a question with nothing written in it. */
    QUIZ_QUESTION_NOTE_EMPTY(
            Area.QUIZZES, 79, HttpStatus.BAD_REQUEST, "Say what is wrong with the question, so nothing was sent"),

    /** A sheet of questions to import that could not be read as a table. */
    QUIZ_IMPORT_SHEET_NOT_READ(
            Area.QUIZZES,
            80,
            HttpStatus.BAD_REQUEST,
            "The sheet could not be read as a table, so nothing was imported"),

    /** A row of a sheet to import naming a kind of question Ember does not know. */
    QUIZ_IMPORT_QUESTION_TYPE_UNKNOWN(
            Area.QUIZZES,
            81,
            HttpStatus.BAD_REQUEST,
            "A row names a kind of question Ember does not know, so nothing was imported"),

    /** A sheet to import without the column chosen to carry the questions. */
    QUIZ_IMPORT_QUESTION_COLUMN_MISSING(
            Area.QUIZZES,
            82,
            HttpStatus.BAD_REQUEST,
            "The sheet has no column with the name chosen for the questions, so nothing was imported"),

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

    /** A first station asked for on an instance that already has one; further stations are made elsewhere. */
    FIRST_STATION_ALREADY_FOUNDED(
            Area.STATIONS, 46, HttpStatus.CONFLICT, "This instance already has a station, nothing was created"),

    /** A first station asked for without a name. */
    FIRST_STATION_NEEDS_A_NAME(Area.STATIONS, 47, HttpStatus.BAD_REQUEST, Sentences.STATION_NEEDS_A_NAME),

    /** A station's mail settings tested with a recipient that is plainly not an address. */
    STATION_TEST_MAIL_RECIPIENT_NOT_AN_ADDRESS(
            Area.STATIONS, 48, HttpStatus.BAD_REQUEST, "That is not an email address, so nothing was sent"),

    /** A station location saved with no location in the request at all. */
    STATION_LOCATION_MISSING(Area.STATIONS, 49, HttpStatus.BAD_REQUEST, "Give the location, so nothing was saved"),

    /** A station location given one coordinate without the other. */
    STATION_LOCATION_HALF_PINNED(
            Area.STATIONS,
            50,
            HttpStatus.BAD_REQUEST,
            "Give both the latitude and the longitude, or neither, so nothing was saved"),

    /** A station location with a latitude north of the north pole or south of the south pole. */
    STATION_LATITUDE_OUT_OF_RANGE(
            Area.STATIONS,
            51,
            HttpStatus.BAD_REQUEST,
            "The latitude has to lie between -90 and 90, so nothing was saved"),

    /** A station location with a longitude beyond the date line either way. */
    STATION_LONGITUDE_OUT_OF_RANGE(
            Area.STATIONS,
            52,
            HttpStatus.BAD_REQUEST,
            "The longitude has to lie between -180 and 180, so nothing was saved"),

    /** A station location whose country is not written as a two-letter country code. */
    STATION_COUNTRY_NOT_A_CODE(
            Area.STATIONS,
            53,
            HttpStatus.BAD_REQUEST,
            "Give the country as its two capital letters, such as DE, so nothing was saved"),

    /** A station location whose address line is longer than a location keeps. */
    STATION_ADDRESS_LINE_TOO_LONG(
            Area.STATIONS, 54, HttpStatus.BAD_REQUEST, "The street and number are too long, so nothing was saved"),

    /** A station location whose postal code is longer than a location keeps. */
    STATION_POSTAL_CODE_TOO_LONG(
            Area.STATIONS, 55, HttpStatus.BAD_REQUEST, "The postal code is too long, so nothing was saved"),

    /** A station location whose city is longer than a location keeps. */
    STATION_CITY_TOO_LONG(Area.STATIONS, 56, HttpStatus.BAD_REQUEST, "The city is too long, so nothing was saved"),

    /** The location of a station that is not here, asked for to show it. */
    STATION_NOT_HERE_FOR_LOCATION(Area.STATIONS, 57, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** An archive imported into a cluster's home station, which holds what its cluster owns. */
    STATION_IMPORT_INTO_CLUSTER_HOME(
            Area.STATIONS,
            58,
            HttpStatus.BAD_REQUEST,
            "A cluster's home station cannot be imported into, so nothing was changed"),

    /** An archive imported over a station that belongs to a cluster. */
    STATION_IMPORT_INTO_CLUSTER_MEMBER(
            Area.STATIONS,
            59,
            HttpStatus.BAD_REQUEST,
            "A station that belongs to a cluster cannot be overwritten from an archive, so nothing was changed"),

    /** A station brought over from another instance whose answer carried no station at all. */
    STATION_IMPORT_SOURCE_HAS_NO_STATION(
            Area.STATIONS,
            60,
            HttpStatus.BAD_REQUEST,
            "The other instance sent no station to bring over, so nothing was imported"),

    /**
     * The caller's own station, gone after the first page of an import from another instance was
     * already written into it and before the rest could start.
     */
    STATION_IMPORT_TARGET_NOT_HERE(
            Area.STATIONS,
            61,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The station the import was to go into is not here any more, so nothing was imported"),

    /** A failed import tried again for a station no import is known for. */
    STATION_IMPORT_NOTHING_TO_RETRY(
            Area.STATIONS,
            62,
            HttpStatus.NOT_FOUND,
            "No import is known for that station, so there is nothing to retry"),

    /** An import tried again that has not failed. */
    STATION_IMPORT_NOT_FAILED(Area.STATIONS, 63, HttpStatus.CONFLICT, "Only an import that failed can be tried again"),

    /** An import from an address that is not a public HTTPS address. */
    STATION_IMPORT_SOURCE_NOT_PUBLIC(
            Area.STATIONS, 64, HttpStatus.BAD_REQUEST, Sentences.FEDERATION_ADDRESS_NOT_PUBLIC),

    /** An import from another instance that could not tell which version it runs. */
    STATION_IMPORT_SOURCE_NOT_READ(
            Area.STATIONS,
            65,
            HttpStatus.BAD_REQUEST,
            "The other instance could not be asked which version it runs, so nothing was imported. "
                    + "The reason is in the instance log"),

    /** An import from an instance too old to say which version it runs. */
    STATION_IMPORT_SOURCE_TOO_OLD(
            Area.STATIONS,
            66,
            HttpStatus.BAD_REQUEST,
            "The other instance is too old to bring a station over from, so nothing was imported. "
                    + "Update it first"),

    /** An import from an instance that runs a different version than this one. */
    STATION_IMPORT_SCHEMA_DIFFERS(
            Area.STATIONS,
            67,
            HttpStatus.BAD_REQUEST,
            "The two instances do not run the same version, so nothing was imported. "
                    + "Update the older one and try again"),

    /** A cluster's home station put up to move to another instance. */
    STATION_TRANSFER_OF_CLUSTER_HOME(
            Area.STATIONS, 68, HttpStatus.BAD_REQUEST, "A cluster's home station cannot be moved to another instance"),

    /** A station that belongs to a cluster put up to move to another instance. */
    STATION_TRANSFER_OF_CLUSTER_MEMBER(
            Area.STATIONS,
            69,
            HttpStatus.BAD_REQUEST,
            "A station that belongs to a cluster cannot be moved to another instance. Leave the cluster first"),

    /** An onboarding task ticked off that no task of that level is called. */
    ONBOARDING_TASK_UNKNOWN(Area.STATIONS, 70, HttpStatus.BAD_REQUEST, "That is not a step of getting started"),

    /** An onboarding task ticked off by hand that finishes itself once it is really done. */
    ONBOARDING_TASK_FINISHES_ITSELF(
            Area.STATIONS,
            71,
            HttpStatus.BAD_REQUEST,
            "This step ticks itself off once it is actually done, so nothing was changed"),

    /** A station's onboarding task marked by a member who is not here any more. */
    ONBOARDING_MEMBER_NOT_HERE(Area.STATIONS, 72, HttpStatus.NOT_FOUND, Sentences.MEMBER_NOT_HERE),

    /** A station whose look was saved, gone before the save reached it. */
    STATION_NOT_HERE_FOR_LOOK(Area.STATIONS, 73, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

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
            Area.WAITING_LISTS, 21, HttpStatus.NOT_FOUND, Sentences.WAITING_LIST_NOT_HERE),

    /** A second date of birth question added to a waiting list that already asks one. */
    WAITING_LIST_SECOND_BIRTH_DATE_ON_CREATE(
            Area.WAITING_LISTS, 52, HttpStatus.BAD_REQUEST, Sentences.WAITING_LIST_HAS_A_BIRTH_DATE),

    /** A question of a waiting list changed into a second date of birth question. */
    WAITING_LIST_SECOND_BIRTH_DATE_ON_CHANGE(
            Area.WAITING_LISTS, 53, HttpStatus.BAD_REQUEST, Sentences.WAITING_LIST_HAS_A_BIRTH_DATE),

    /** Somebody taking their own entry off a list after it has moved past waiting or being invited. */
    WAITING_LIST_ENTRY_NO_LONGER_REMOVABLE(
            Area.WAITING_LISTS,
            54,
            HttpStatus.CONFLICT,
            "This entry can no longer be taken off the list, so nothing was changed"),

    /** An answer to a waiting list question that it does not take, given through an invite code. */
    WAITING_LIST_ANSWER_NOT_ACCEPTED_ON_INVITE(
            Area.WAITING_LISTS, 55, HttpStatus.BAD_REQUEST, Sentences.WAITING_LIST_ANSWER_NOT_ACCEPTED),

    /** An answer to a waiting list question that it does not take, given as an entry is written down. */
    WAITING_LIST_ANSWER_NOT_ACCEPTED_ON_CREATE(
            Area.WAITING_LISTS, 56, HttpStatus.BAD_REQUEST, Sentences.WAITING_LIST_ANSWER_NOT_ACCEPTED),

    /** An answer to a waiting list question that it does not take, given as an entry is changed. */
    WAITING_LIST_ANSWER_NOT_ACCEPTED_ON_CHANGE(
            Area.WAITING_LISTS, 57, HttpStatus.BAD_REQUEST, Sentences.WAITING_LIST_ANSWER_NOT_ACCEPTED),

    /** An answer to an invitation the entry no longer holds, because it has moved on. */
    WAITING_LIST_INVITATION_NO_LONGER_OPEN(
            Area.WAITING_LISTS,
            58,
            HttpStatus.CONFLICT,
            "This invitation can no longer be answered, so nothing was saved"),

    /** An answer that names an appointment or a date the entry is not invited to. */
    WAITING_LIST_INVITATION_ANSWER_FOR_ANOTHER(
            Area.WAITING_LISTS,
            59,
            HttpStatus.CONFLICT,
            "This answer is about a different appointment than the invitation, so nothing was saved"),

    /** A sign-up from somebody younger than the list takes registrations from. */
    WAITING_LIST_REGISTRANT_TOO_YOUNG(
            Area.WAITING_LISTS,
            60,
            HttpStatus.BAD_REQUEST,
            "This list does not take registrations at this age yet, so nothing was saved");

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
     * @param detail what the refusal was about, in the reader's terms, or {@code null} when there is
     *               nothing to name
     * @return an exception carrying this refusal's status and code, and its sentence with the
     *         detail named
     */
    public RefusalResponse raise(@Nullable String detail) {
        if (detail == null) return raise();
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
        private static final String LOOK_NOT_OFFERED =
                "That is not a look a station can be given, so nothing was saved";
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
        private static final String PROTOCOL_NOT_HERE_BEHIND_RUN =
                "The protocol this test run follows is not here any more";

        private static final String SESSION_HAS_NO_ACCOUNT = "This session does not stand for an account";
        private static final String MEMBER_NOT_HERE = "That member is not here any more";
        private static final String ACCOUNT_NOT_HERE = "That account is not here any more";
        private static final String PROFILE_FIELD_NOT_HERE = "That profile question is not here any more";
        private static final String ADDRESS_MISSING = "Give the address to write to";
        private static final String ADDRESS_BELONGS_TO_ANOTHER = "That address already belongs to another account";
        private static final String LINK_CARRIES_NOTHING = "That link carries nothing to act on";
        private static final String PASSWORD_TOO_SHORT = "That password is too short, so nothing was saved";
        private static final String EXPIRY_OUT_OF_RANGE =
                "Days before an expiry date cannot be negative and a repeat needs at least one day, so nothing was saved";
        private static final String PASSWORD_BREACHED =
                "That password turns up in known data leaks, so choose another one";
        private static final String ACCOUNT_NOT_NAMED = "Name the account this is about";
        private static final String PROFILE_FIELD_DETAILS_MISSING = "Give the question a name and a kind";
        private static final String PROFILE_FIELD_AUDIENCE_AMBIGUOUS =
                "Name either a kind of member or a group, and only one of the two";
        private static final String GROUP_NAME_MISSING = "Give the group a name";
        private static final String GROUP_NOT_HERE = "That group is not here any more";
        private static final String GROUP_NOT_HERE_NOTHING_SAVED =
                "One of those groups is not here, so nothing was saved";
        private static final String GROUP_WRONG_USER_TYPE =
                "That group takes only members of certain types, so nothing was saved";
        private static final String GROUP_GRANTS_MORE_THAN_YOURS =
                "That group grants permissions you do not hold yourself, so nothing was saved";
        private static final String GROUP_SET_NOT_HERE = "That set of groups is not here any more";
        private static final String GROUP_SET_NAME_MISSING = "Give the set of groups a name";
        private static final String GROUP_SET_NAME_TAKEN = "Another set of groups already has that name";
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
        private static final String SIGN_IN_FIRST = "Sign in before doing this";
        private static final String DEFAULT_NOT_SUITING =
                "That default does not suit this question, so nothing was saved";
        private static final String BORROWED_GEAR_LOST_AT_PARTNER =
                "This gear belongs to a partner station. Tell them on the lending request it came in on";
        private static final String BEACON_PROTOCOL_TOO_NEW =
                "This beacon does not speak that version of the protocol yet";
        private static final String DEMO_UPLOADS_OFF =
                "Uploading files is switched off in the demo, so nothing was saved";
        private static final String STATION_BEING_TRANSFERRED =
                "This station is moving to another instance, so nothing can be changed until the move is done";
        private static final String CLUSTER_TAG_TAKEN =
                "The cluster already recommends that word to these stations, so nothing was saved";
        private static final String CLUSTER_TAG_NOT_HERE = "That recommended word is not here any more";
        private static final String CLUSTER_NEEDS_A_NAME = "A cluster needs a name, so nothing was saved";
        private static final String STATION_NOT_IN_CLUSTER =
                "That station does not belong to this cluster, so nothing was changed";
        private static final String CLUSTER_POOL_TOO_SMALL =
                "That is more room than the cluster has left to hand out, so nothing was changed";
        private static final String MOVEMENT_NOT_HERE = "That movement is not here any more";
        private static final String MOVEMENT_FLOW_GONE =
                "The chain of steps this movement walked is not here any more, so nothing was changed";
        private static final String ART_NAME_TAKEN =
                "This inventory already has a kind by that name, so nothing was saved";
        private static final String INVENTORY_TAG_NOT_HERE = "That tag is not here any more";
        private static final String SELF_CHECK_NOT_HERE = "That self-check is not here any more";
        private static final String SELF_CHECK_ANSWER_ALREADY_SETTLED = "Somebody has already settled this answer";
        private static final String ITEM_NOT_ON_MEMBERS_RECORD = "That piece of gear is not on this member's record";
        private static final String INVENTORY_CHECK_MEMBER_TAKEN = "Somebody else is already checking this member";
        private static final String SELF_CHECK_SIZE_NOT_OFFERED = "That size is not one this kind of gear comes in";
        private static final String BORROWED_SHELF_ONLY_BORROWED =
                "This shelf only holds gear borrowed from a partner station, so nothing was saved";
        private static final String INVENTORY_KIND_ELSEWHERE =
                "That kind of gear belongs to another inventory, so nothing was saved";
        private static final String QUESTION_DEFAULT_NOT_ACCEPTED =
                "A question cannot start from a value it would not take as an answer, so nothing was saved";
        private static final String EVENT_ENDS_BEFORE_IT_STARTS =
                "An appointment cannot end before it starts, so nothing was saved";
        private static final String INTERNAL_FORM_HAS_NO_LINK =
                "A form for the station's own members is not sent by link";
        private static final String QUIZ_CATALOG_EXAMPLE_NOT_READ =
                "The example sheet could not be read. This is a fault in Ember; please report it";
        private static final String QUIZ_CATALOG_FILE_NOT_ONE =
                "That file is not a catalog export, so nothing was imported";
        private static final String WAITING_LIST_HAS_A_BIRTH_DATE =
                "This list already asks for a date of birth, so nothing was saved";
        private static final String WAITING_LIST_ANSWER_NOT_ACCEPTED =
                "An answer does not suit its question on this list, so nothing was saved";
        private static final String REGISTRATION_HELD_BY_A_FIELD =
                "This place comes from a question of the appointment and is taken back there, so nothing was changed";
        private static final String PARTNER_STATION_NOT_HERE = "That partner station is not here any more";
        private static final String MAILBOX_NOT_HERE = "That mailbox is not here any more";
        private static final String MAILBOX_RULE_NOT_HERE = "That mailbox rule is not here any more";

        private Sentences() {}
    }
}

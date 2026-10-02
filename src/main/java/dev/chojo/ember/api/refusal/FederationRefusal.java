/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#FEDERATION}: federation.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum FederationRefusal implements Refusal {
    /** A handshake from another instance that arrived without the fields it is made of. */
    HANDSHAKE_INCOMPLETE(1, HttpStatus.BAD_REQUEST, "The handshake arrived without everything it is made of"),

    /** A handshake from another instance whose enrollment signature was not accepted. */
    HANDSHAKE_SIGNATURE_NOT_GOOD(2, HttpStatus.FORBIDDEN, "That handshake signature was not accepted"),

    /** A partner registering a callback without saying where it is to be called. */
    WEBHOOK_ADDRESS_MISSING(3, HttpStatus.BAD_REQUEST, "Give the address the partner is to be called back on"),

    /** A callback address a partner named that this instance will not call. */
    WEBHOOK_ADDRESS_NOT_ALLOWED(4, HttpStatus.BAD_REQUEST, Sentences.FEDERATION_ADDRESS_NOT_PUBLIC),

    /** A partner polling for changes without saying which moment to start from. */
    SYNC_SINCE_MISSING(5, HttpStatus.BAD_REQUEST, "Say which moment the changes are wanted from"),

    /** A moment a partner polled from that does not read as a moment. */
    SYNC_SINCE_NOT_A_MOMENT(6, HttpStatus.BAD_REQUEST, "The moment the changes were asked from is not a moment"),

    /** A station announcing it has moved without saying where it has moved to. */
    ANNOUNCED_HOST_MISSING(7, HttpStatus.BAD_REQUEST, "Give the address the station has moved to"),

    /** A new address a moved station announced that this instance will not call. */
    ANNOUNCED_HOST_NOT_ALLOWED(8, HttpStatus.BAD_REQUEST, Sentences.FEDERATION_ADDRESS_NOT_PUBLIC),

    /** The caller's own station, gone between the session being read and the invite being made. */
    FEDERATION_STATION_NOT_HERE(9, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A pairing entered with no code in it. */
    INVITE_CODE_MISSING(10, HttpStatus.BAD_REQUEST, "Type the pairing code, so nothing was saved"),

    /**
     * A pairing request that is not here, or one addressed to another station.
     *
     * <p>One code for both on purpose. The lookup asks about every request on the instance, so
     * telling them apart would say that a request exists between two stations the reader may not
     * see.
     */
    PAIR_REQUEST_NOT_HERE_TO_ACCEPT(11, HttpStatus.NOT_FOUND, Sentences.PAIR_REQUEST_NOT_HERE),

    /**
     * A pairing request declined that is not here, or one addressed to another station.
     *
     * <p>One code for both on purpose, for the same reason as accepting one.
     */
    PAIR_REQUEST_NOT_HERE_TO_DECLINE(12, HttpStatus.NOT_FOUND, Sentences.PAIR_REQUEST_NOT_HERE),

    /** A partner station addressed by a number nothing here uses. */
    FEDERATION_PARTNER_NOT_HERE(13, HttpStatus.NOT_FOUND, "That partner station is not here any more"),

    /** A knowledge share that went before the withdrawal reached it. */
    KB_SHARE_NOT_HERE_TO_DELETE(14, HttpStatus.NOT_FOUND, Sentences.FEDERATION_SHARE_NOT_HERE),

    /** A catalog share that went before the withdrawal reached it. */
    QUIZ_SHARE_NOT_HERE_TO_DELETE(15, HttpStatus.NOT_FOUND, Sentences.FEDERATION_SHARE_NOT_HERE),

    /** A test protocol share that went before the withdrawal reached it. */
    PROTOCOL_SHARE_NOT_HERE_TO_DELETE(16, HttpStatus.NOT_FOUND, Sentences.FEDERATION_SHARE_NOT_HERE),

    /** Gear asked for from the station already holding it. */
    LENDING_FROM_OWN_STATION(17, HttpStatus.BAD_REQUEST, "A station cannot borrow from itself, so nothing was saved"),

    /** A lending request written down without the first day the gear is wanted for. */
    LENDING_FIRST_DAY_MISSING(
            18, HttpStatus.BAD_REQUEST, "Give the first day the gear is wanted for, so nothing was saved"),

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
            19, HttpStatus.NOT_FOUND, "That lending request is not here any more, or it is not yours to open"),

    /** Gear put against a lending request that has not been agreed to yet. */
    LENDING_REQUEST_NOT_APPROVED(
            20, HttpStatus.BAD_REQUEST, "Gear can only be put against a lending request that has been agreed to"),

    /** A decision on a lending request taken by a station other than the one holding the gear. */
    LENDING_NOT_THE_OWNING_STATION(21, HttpStatus.BAD_REQUEST, "Only the station the gear belongs to can do this"),

    /** A message on a lending request with nothing written in it. */
    LENDING_MESSAGE_NEEDS_TEXT(
            22, HttpStatus.BAD_REQUEST, "A message needs something written in it, so nothing was sent"),

    /** Gear held back over a span that is missing its first or its last day. */
    LENDING_BLOCK_SPAN_MISSING(
            23, HttpStatus.BAD_REQUEST, "Give the first and the last day the gear is held back, so nothing was saved"),

    /** An offer written without saying whether the gear goes out or is held back. */
    SHARE_GRANT_MISSING(
            24, HttpStatus.BAD_REQUEST, "Say whether the gear goes on offer or is held back, so nothing was saved"),

    /** An offer written without saying who it reaches. */
    SHARE_SCOPE_MISSING(
            25,
            HttpStatus.BAD_REQUEST,
            "Say whether the offer reaches every partner station or only the ones named, so nothing was saved"),

    /** A partner that could not be read back after being suspended, with the suspension already in. */
    FEDERATION_PARTNER_NOT_HERE_AFTER_SUSPENDING(
            26, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A partner that could not be read back after being resumed, with the resumption already in. */
    FEDERATION_PARTNER_NOT_HERE_AFTER_RESUMING(
            27, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A borrowing request that could not be read back after being approved, with the approval already in. */
    LENDING_REQUEST_NOT_HERE_AFTER_APPROVAL(
            28, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A borrowing request that could not be read back after being declined, with the refusal already in. */
    LENDING_REQUEST_NOT_HERE_AFTER_DECLINE(
            29, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A borrowing request that could not be read back after the gear was marked handed over. */
    LENDING_REQUEST_NOT_HERE_AFTER_LENDING(
            30, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A borrowing request that could not be read back after the gear was marked given back. */
    LENDING_REQUEST_NOT_HERE_AFTER_RETURN(
            31, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /** A borrowing request that could not be read back after being closed, with the closing already in. */
    LENDING_REQUEST_NOT_HERE_AFTER_CLOSING(
            32, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.CHANGE_SAVED_BUT_NOT_READ_BACK),

    /**
     * A partner station on this instance asked while its partner no longer holds the partnership
     * as active, which a partner on another instance is refused for at the signature check.
     */
    FEDERATION_PARTNERSHIP_NOT_ACTIVE_THERE(
            33, HttpStatus.FORBIDDEN, "The partner station does not hold this partnership as active any more"),

    /** A partner station on another instance that did not answer, or answered with nothing to read. */
    FEDERATION_PARTNER_DID_NOT_ANSWER(
            34, HttpStatus.NOT_FOUND, "The partner station did not answer, or had nothing to give"),

    /** A partner asking what is free on a day that does not read as a day. */
    LENDING_DAY_NOT_A_DAY(35, HttpStatus.BAD_REQUEST, "A day asked about is not a day"),

    /** A partner's request naming gear, a kind of thing or an inventory that is not this station's. */
    LENDING_LINE_NAMES_FOREIGN_GEAR(
            36,
            HttpStatus.BAD_REQUEST,
            "A line of the request names gear that is not this station's, so nothing was saved"),

    /** A sign-up for a partner's appointment naming a day that does not read as a day. */
    FEDERATED_REGISTRATION_DAY_NOT_A_DAY(37, HttpStatus.BAD_REQUEST, Sentences.DAY_NOT_A_DATE),

    /** A partner's change to a checklist item that is not on the shared ticket it names. */
    REMOTE_CHECKLIST_ITEM_NOT_ON_TICKET(38, HttpStatus.NOT_FOUND, "That checklist item is not on this ticket"),

    /** A partner reordering a lane that is not on the shared board it names. */
    REMOTE_LANE_NOT_ON_BOARD(39, HttpStatus.NOT_FOUND, "That lane is not on this board"),

    /** A partner putting a label on a shared ticket, or taking it off, that is not a label of its board. */
    REMOTE_LABEL_NOT_ON_BOARD(40, HttpStatus.NOT_FOUND, "That label is not on this board"),

    /** A partner route reached without a signature this instance accepted. */
    FEDERATION_REQUEST_NOT_SIGNED(
            41, HttpStatus.FORBIDDEN, "That request was not signed by a partner this instance knows"),

    /** Content asked of a partner whose partnership is not active. */
    FEDERATION_PARTNER_NOT_ACTIVE(42, HttpStatus.BAD_REQUEST, "That partner station is not active at the moment"),

    /** A cluster's own connection paused, which would stop its content arriving. */
    FEDERATION_CLUSTER_PARTNER_NOT_PAUSABLE(
            43,
            HttpStatus.BAD_REQUEST,
            "This connection carries the cluster's own content and cannot be paused, so nothing was changed"),

    /** A cluster's connection ended by the station, when it ends only with the membership. */
    FEDERATION_CLUSTER_PARTNER_NOT_DELETABLE(
            44,
            HttpStatus.BAD_REQUEST,
            "This connection belongs to the cluster and ends when the station leaves it, so nothing was changed"),

    /**
     * An inventory an offer is written on that is not here, or is not this station's. One code for
     * both: telling them apart would say that another station keeps an inventory under that number.
     */
    SHARE_INVENTORY_NOT_HERE(45, HttpStatus.NOT_FOUND, Sentences.INVENTORY_NOT_HERE),

    /** An offer written on an inventory that holds only gear of the body above the station. */
    SHARE_INVENTORY_NOT_THE_STATIONS(
            46,
            HttpStatus.BAD_REQUEST,
            "This inventory belongs to the body above the station, which the station cannot lend from, so "
                    + "nothing was saved"),

    /** A kind of gear an offer is written on, which is not here. */
    SHARE_ITEM_KIND_NOT_HERE(47, HttpStatus.NOT_FOUND, Sentences.ITEM_KIND_NOT_HERE),

    /** A piece of gear an offer is written on, which is not here. */
    SHARE_ITEM_NOT_HERE(48, HttpStatus.NOT_FOUND, Sentences.ITEM_NOT_HERE),

    /** Gear asked for from a station that does not lend to the asking one. */
    LENDING_PARTNER_DOES_NOT_LEND(49, HttpStatus.FORBIDDEN, "That station does not lend gear to yours"),

    /** Gear set aside for a lending request that the station only holds and does not own. */
    LENDING_GEAR_NOT_THE_STATIONS(50, HttpStatus.FORBIDDEN, "That gear is not this station's to lend");

    private final Definition definition;

    FederationRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.FEDERATION, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#DISCOVERY}: discovery.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum DiscoveryRefusal implements Refusal {
    /** An invite asked for without saying which station it is for. */
    INVITE_NEEDS_A_STATION(1, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /**
     * A station an invite was asked for that is not among the ones this instance offers to be found.
     * One code for the miss and the visibility check deliberately: this endpoint answers strangers,
     * and telling them apart would let one learn that a station exists but keeps itself out of the
     * listing.
     */
    STATION_NOT_OPEN_TO_INVITES(2, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_FOUND_IN_DISCOVERY),

    /** A partnership asked for without saying which station it is with. */
    FEDERATION_REQUEST_NEEDS_A_STATION(3, HttpStatus.BAD_REQUEST, Sentences.NO_STATION_CHOSEN),

    /**
     * A station a partnership was asked for that is not among the ones this instance offers to be
     * found. One code for the miss and the visibility check deliberately, for the same reason as the
     * invite above.
     */
    STATION_NOT_OPEN_TO_FEDERATION(4, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_FOUND_IN_DISCOVERY),

    /** A partnership asked for with a station that is already a partner. */
    ALREADY_FEDERATED(5, HttpStatus.BAD_REQUEST, "This station is already a partner of yours"),

    /** A partnership asked for a second time while the first is still unanswered. */
    FEDERATION_REQUEST_ALREADY_SENT(
            6, HttpStatus.BAD_REQUEST, "A request to this station is already waiting for an answer"),

    /** An instance knocked on without saying where it is. */
    PROBE_NEEDS_AN_ADDRESS(7, HttpStatus.BAD_REQUEST, Sentences.PEER_ADDRESS_MISSING),

    /** An instance written down without saying where it is. */
    PEER_NEEDS_AN_ADDRESS(8, HttpStatus.BAD_REQUEST, Sentences.PEER_ADDRESS_MISSING),

    /** An instance that named a key other than the one it was expected to name. */
    PEER_KEY_NOT_THE_EXPECTED_ONE(
            9,
            HttpStatus.BAD_REQUEST,
            "That instance named a different key from the one you expected, so nothing was saved"),

    /** An instance that answers but wants no part in being found. */
    PEER_DOES_NOT_WANT_DISCOVERY(
            10, HttpStatus.BAD_REQUEST, "That instance does not take part in discovery, so nothing was saved"),

    /** An instance being voted on or blocked that this instance has not written down. */
    PEER_NOT_HERE(11, HttpStatus.NOT_FOUND, Sentences.PEER_NOT_HERE),

    /** An instance that went between the change to it and it being read back. */
    PEER_NOT_HERE_AFTER_CHANGE(12, HttpStatus.NOT_FOUND, Sentences.PEER_NOT_HERE),

    /** An instance being pinged that this instance has not written down. */
    PEER_NOT_HERE_ON_PING(13, HttpStatus.NOT_FOUND, Sentences.PEER_NOT_HERE),

    /** A block written down without saying what is blocked or what kind of thing it is. */
    BLOCKLIST_ENTRY_INCOMPLETE(
            14, HttpStatus.BAD_REQUEST, "Say what is being blocked and what kind of thing it is, so nothing was saved");

    private final Definition definition;

    DiscoveryRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.DISCOVERY, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

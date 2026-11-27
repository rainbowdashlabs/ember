/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#FEED}: the feed.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum FeedRefusal implements Refusal {
    /**
     * A feed link that opens nothing, or that stands for a member who has since gone. One code
     * deliberately: two would show somebody trying links which of them are real.
     */
    FEED_LINK_NOT_GOOD(1, HttpStatus.NOT_FOUND, "That feed link does not lead anywhere any more"),

    /** The station behind a calendar feed, which has gone between the link and the reading of it. */
    FEED_STATION_NOT_HERE(2, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /**
     * A lost property entry that is not here, or that is at another station. One code deliberately:
     * the two answer alike so that a feed link cannot be used to count what other stations hold.
     */
    FEED_ITEM_NOT_HERE(3, HttpStatus.NOT_FOUND, "That entry in lost and found is not here any more"),

    /** A picture of a lost property entry that the store no longer holds. */
    FEED_ITEM_PICTURE_NOT_HERE(4, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** The station behind a notification feed, which has gone between the link and the reading of it. */
    FEED_STATION_NOT_HERE_FOR_NOTIFICATIONS(5, HttpStatus.NOT_FOUND, Sentences.STATION_NOT_HERE),

    /** A feed that could not be written out once its entries had been gathered. */
    FEED_NOT_BUILT(
            6,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The feed could not be put together. Trying again may work; if it keeps happening, please report it"),

    /** A station calendar feed that is not here, or that belongs to another station. */
    FEED_STATION_FEED_NOT_HERE(8, HttpStatus.NOT_FOUND, "That calendar feed is not here any more"),

    /** A station calendar feed created or renamed without a name. */
    FEED_STATION_FEED_NEEDS_A_NAME(9, HttpStatus.BAD_REQUEST, "A calendar feed needs a name"),

    /** A station calendar feed name longer than the list and a calendar app can show. */
    FEED_STATION_FEED_NAME_TOO_LONG(
            10, HttpStatus.BAD_REQUEST, "The name of a calendar feed may be at most 100 characters long"),

    /** A station calendar feed created or changed without saying how far it reaches. */
    FEED_STATION_FEED_NEEDS_A_LEVEL(
            11, HttpStatus.BAD_REQUEST, "A calendar feed needs a visibility: public, members or everything"),

    /**
     * A category to leave out of a feed that is not one of the station's own. One code for a category that
     * is gone and one of another station, so a feed cannot be used to probe other stations' categories.
     */
    FEED_CATEGORY_NOT_HERE(12, HttpStatus.NOT_FOUND, "One of the chosen categories is not here any more");

    private final Definition definition;

    FeedRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.FEED, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

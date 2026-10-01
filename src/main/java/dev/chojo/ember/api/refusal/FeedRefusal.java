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
     * deliberately: two would tell somebody trying links which of them are real.
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
            "The feed could not be put together. Trying again may work; if it keeps happening, please report it");

    private final Definition definition;

    FeedRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.FEED, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

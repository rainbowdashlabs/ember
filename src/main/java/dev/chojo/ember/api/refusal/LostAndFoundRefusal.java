/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#LOST_AND_FOUND}: lost and found.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum LostAndFoundRefusal implements Refusal {
    /** A find whose picture is not here. */
    LOST_ITEM_PICTURE_NOT_HERE(1, HttpStatus.NOT_FOUND, Sentences.PICTURE_NOT_HERE),

    /** A picture hung on a find, arriving with no file in it. */
    LOST_ITEM_UPLOAD_MISSING_FILE(2, HttpStatus.BAD_REQUEST, Sentences.UPLOAD_MISSING_FILE),

    /** A picture hung on a find in a format that is not taken. */
    LOST_ITEM_PICTURE_KIND_NOT_TAKEN(3, HttpStatus.BAD_REQUEST, Sentences.KB_PICTURE_KIND_NOT_TAKEN),

    /** A picture of a find that the store would not keep. */
    LOST_ITEM_PICTURE_NOT_TAKEN(
            4, HttpStatus.BAD_REQUEST, "That picture was not one that could be kept, so nothing was saved"),

    /** A picture of a find that could not be worked through at all. */
    LOST_ITEM_PICTURE_NOT_PROCESSED(5, HttpStatus.INTERNAL_SERVER_ERROR, Sentences.UPLOAD_NOT_PROCESSED),

    /** A find claimed for somebody the reader does not look after. */
    LOST_ITEM_CLAIM_NOT_YOURS_TO_MAKE(
            6, HttpStatus.BAD_REQUEST, "You do not look after this member, so nothing was claimed"),

    /** A find claimed that somebody else has claimed already, or that has gone. */
    LOST_ITEM_ALREADY_CLAIMED(7, HttpStatus.BAD_REQUEST, "That find is claimed already, or it is not here any more"),

    /** A claim taken back off a find nobody has claimed. */
    LOST_ITEM_NOT_CLAIMED_TO_RELEASE(8, HttpStatus.BAD_REQUEST, Sentences.LOST_ITEM_NOT_CLAIMED),

    /** A claim of somebody the reader neither is nor looks after, taken back. */
    LOST_ITEM_CLAIM_NOT_YOURS_TO_RELEASE(9, HttpStatus.BAD_REQUEST, "That claim is not yours to take back"),

    /** A claim that went while it was being taken back. */
    LOST_ITEM_CLAIM_ALREADY_GONE(10, HttpStatus.BAD_REQUEST, Sentences.LOST_ITEM_NOT_CLAIMED),

    /** A find handed back that nobody has claimed. */
    LOST_ITEM_NOT_CLAIMED_TO_HAND_OVER(11, HttpStatus.BAD_REQUEST, Sentences.LOST_ITEM_NOT_CLAIMED),

    /** A find written down with a day of finding that does not read as a date. */
    LOST_ITEM_FOUND_DATE_NOT_A_DATE(12, HttpStatus.BAD_REQUEST, "The day the thing was found is not a date");

    private final Definition definition;

    LostAndFoundRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.LOST_AND_FOUND, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

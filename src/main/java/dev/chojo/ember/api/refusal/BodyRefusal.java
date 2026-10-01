/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#BODY}: the request body.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum BodyRefusal implements Refusal {
    /** A body arrived that is not JSON at all. */
    BODY_NOT_JSON(1, HttpStatus.BAD_REQUEST, "The request body is not valid JSON, so nothing was saved"),

    /** A body arrived carrying a field the endpoint has no place for. */
    BODY_UNEXPECTED_FIELD(2, HttpStatus.BAD_REQUEST, "The request body carries a field this endpoint does not accept"),

    /** A body arrived shaped differently from what the endpoint reads. */
    BODY_DOES_NOT_MATCH(3, HttpStatus.BAD_REQUEST, "The request body does not match what this endpoint expects"),

    /** A body arrived whose shape was right but whose contents were refused. */
    BODY_VALUE_REJECTED(4, HttpStatus.BAD_REQUEST, "A value in the request body is not one this endpoint accepts"),

    /** Something in the request could not be used, and whoever refused it said nothing readable. */
    INPUT_NOT_USABLE(5, HttpStatus.BAD_REQUEST, "Something in what was sent could not be used, so nothing was saved"),

    /** A block of a page, news entry or article sent with settings its kind of block does not take. */
    BLOCK_SETTINGS_REJECTED(
            6, HttpStatus.BAD_REQUEST, "The settings of a block do not fit its kind of block, so nothing was saved");

    private final Definition definition;

    BodyRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.BODY, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

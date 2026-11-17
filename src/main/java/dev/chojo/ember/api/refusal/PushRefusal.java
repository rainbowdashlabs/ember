/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/** The named refusals of {@link Refusal.Area#PUSH}: push messages and the browsers that receive them. */
public enum PushRefusal implements Refusal {
    /** The instance has push messages switched off. */
    PUSH_TURNED_OFF(1, HttpStatus.CONFLICT, "Push messages are switched off on this instance"),
    /** A registration that left out its address or keys. */
    SUBSCRIPTION_INCOMPLETE(2, HttpStatus.BAD_REQUEST, "The browser's registration is missing its address or keys"),
    /** An address that is not a public HTTPS push service. */
    ENDPOINT_NOT_ALLOWED(3, HttpStatus.BAD_REQUEST, "That push address is not a public HTTPS address"),
    /** Keys that are not a P-256 point and a 16-byte secret. */
    KEYS_NOT_USABLE(4, HttpStatus.BAD_REQUEST, "The browser's push keys cannot be used"),
    /** A device that does not exist or belongs to somebody else. One code for both. */
    DEVICE_NOT_HERE(5, HttpStatus.NOT_FOUND, "That device is not registered for this account");

    private final Definition definition;

    PushRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.PUSH, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

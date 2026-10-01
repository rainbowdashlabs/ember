/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.refusal;

import io.javalin.http.HttpStatus;

/**
 * The named refusals of {@link Refusal.Area#ADMIN}: instance administration and peers.
 *
 * <p>A new refusal of this area goes at the end with the next free number, which is one above
 * the highest number here and in {@link RetiredRefusals}.
 */
public enum AdminRefusal implements Refusal {
    /** A peer that answered as a peer would but named no key to recognise it by. */
    PEER_NAMED_NO_KEY(1, HttpStatus.BAD_REQUEST, Sentences.PEER_DID_NOT_ANSWER),

    /**
     * A peer whose discovery card could not be fetched at all. Named because a screen has to tell
     * this one refusal apart from every other refusal the peer form can get.
     */
    PEER_DID_NOT_ANSWER(2, HttpStatus.BAD_REQUEST, Sentences.PEER_DID_NOT_ANSWER);

    private final Definition definition;

    AdminRefusal(int number, HttpStatus status, String message) {
        this.definition = new Definition(Area.ADMIN, number, status, message);
    }

    @Override
    public Definition definition() {
        return definition;
    }
}

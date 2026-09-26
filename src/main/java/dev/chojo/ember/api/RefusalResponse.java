/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.HttpResponseException;

/**
 * A named refusal on its way out of a route.
 *
 * <p>It is a {@link HttpResponseException} so that everything already built on those keeps working,
 * and it carries the {@link Refusal} so the error body can name which refusal this was. Routes do
 * not construct it: {@link Refusal#raise()} does, which is what keeps a code and its sentence from
 * being paired up differently in two places.
 */
public class RefusalResponse extends HttpResponseException {
    private final Refusal refusal;

    RefusalResponse(Refusal refusal, String message) {
        super(refusal.status().getCode(), message);
        this.refusal = refusal;
    }

    /**
     * Which refusal this was.
     *
     * @return the refusal, whose code is what the reader and a report see
     */
    public Refusal refusal() {
        return refusal;
    }
}

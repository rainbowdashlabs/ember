/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

/**
 * One endpoint's serving function: the share check and the answer for a partner that asked.
 *
 * <p>The same function answers a signed request from another instance and a call from a station
 * on this one, so the two can never disagree about what a partner may see. It raises the same
 * refusals either way and opens no transaction of its own.
 *
 * @param <B> the request body type, {@link Void} when the endpoint takes none
 * @param <R> the answer type, {@link Void} when the endpoint answers nothing
 */
@FunctionalInterface
public interface FederationHandler<B, R> {

    /**
     * Answers one request.
     *
     * @param partner the partnership as the serving station sees it
     * @param params  the path and query parameters of the request
     * @param body    the request body, {@code null} when the endpoint takes none
     * @return the answer, {@code null} when the endpoint answers nothing
     */
    R serve(ServingPartner partner, PathParams params, B body);
}

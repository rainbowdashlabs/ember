/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

/**
 * Told once a new sealed state of a request was filed, outside the transaction that filed it, so what the
 * state has to reach beyond this installation can be sent on.
 */
@FunctionalInterface
public interface SealedStateFollowUp {

    /** Sends nothing on. */
    SealedStateFollowUp NONE = requestId -> {};

    /** @param requestId the request a new sealed version was filed for */
    void sealed(int requestId);
}

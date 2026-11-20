/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.transport;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;

import java.util.function.Supplier;

/**
 * Names a partner that did not answer in the words of the feature that asked it.
 *
 * <p>A partner on another instance answers every refusal with silence, so what reaches the reader is
 * the sentence this station has for it; a partner on this instance says what it refused, and that is
 * passed on as it is.
 */
public final class PartnerCalls {
    private PartnerCalls() {}

    /**
     * Runs a call to a partner that answers something.
     *
     * @param call       the call
     * @param unanswered what to refuse with where the partner did not answer
     * @param <T>        what the partner answers
     * @return the partner's answer
     */
    public static <T> T answeredOr(Supplier<T> call, Refusal unanswered) {
        try {
            return call.get();
        } catch (RefusalResponse e) {
            if (e.refusal() == FederationRefusal.FEDERATION_PARTNER_DID_NOT_ANSWER) throw unanswered.raise();
            throw e;
        }
    }

    /**
     * Runs a call to a partner that answers nothing.
     *
     * @param call       the call
     * @param unanswered what to refuse with where the partner did not answer
     */
    public static void deliveredOr(Runnable call, Refusal unanswered) {
        answeredOr(
                () -> {
                    call.run();
                    return Boolean.TRUE;
                },
                unanswered);
    }
}

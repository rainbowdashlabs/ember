/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignatureWithdrawal;
import jakarta.inject.Singleton;

/**
 * Keeps every withdrawal at this station: the agreements signed here are all for this station's own
 * documents and events so far, so nothing travels.
 */
@Singleton
public class LocalWithdrawalRelay implements WithdrawalRelay {

    @Override
    public void withdrawn(SignatureRequest request, SignatureWithdrawal withdrawal) {
        // TODO push the sealed withdrawal of an agreement for a partner's federated event to its organiser
    }
}

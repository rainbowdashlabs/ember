/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignatureWithdrawal;

/**
 * Where a withdrawn agreement has to be told beyond this station. An agreement signed at home for a partner's
 * federated event travels to its organiser like the signed result did, and so does its withdrawal; an
 * agreement for one of this station's own events stays here. Called once the withdrawal is stored, after the
 * attempt to seal it; a failure here never undoes it.
 */
@FunctionalInterface
public interface WithdrawalRelay {

    /**
     * @param request    the request whose agreement was withdrawn, as it stands now
     * @param withdrawal the withdrawal
     */
    void withdrawn(SignatureRequest request, SignatureWithdrawal withdrawal);
}

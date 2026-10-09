/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.SignatureRequest;

/**
 * What an agreement being signed means beyond the document: once no field of a request waits any more and
 * something on it was signed or confirmed on paper, whatever hangs on the agreement learns of it. Called
 * after the change is stored; a failure here never undoes it.
 */
@FunctionalInterface
public interface AgreementOutcomes {

    /**
     * @param request the request, as it stands now that it completed
     */
    void completed(SignatureRequest request);
}

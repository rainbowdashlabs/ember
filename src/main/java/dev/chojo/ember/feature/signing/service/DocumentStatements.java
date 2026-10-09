/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.SigningStatements;

/**
 * What the signers of a generated document are asked to confirm, read when signatures are asked for and
 * copied into each field of the request, so a later change of the template never changes what a signer
 * was asked.
 */
@FunctionalInterface
public interface DocumentStatements {

    /**
     * @param templateId the template the document was generated from
     * @param memberId   the member the document is about
     * @param memberName their official name
     * @return the statements of its signature fields
     */
    SigningStatements of(int templateId, int memberId, String memberName);
}

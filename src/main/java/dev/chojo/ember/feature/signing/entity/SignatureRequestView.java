/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A request for signatures with its fields, the evidence of every act on it and its withdrawal.
 *
 * @param request    the request
 * @param fields     its fields, in the order they were asked for
 * @param evidence   the evidence of every signing act on its fields
 * @param withdrawal the withdrawal of its agreement, or null where nobody withdrew it
 */
public record SignatureRequestView(
        SignatureRequest request,
        List<RequestedSignature> fields,
        List<StoredEvidence> evidence,
        @Nullable SignatureWithdrawal withdrawal) {

    /**
     * A request nobody withdrew.
     *
     * @param request  the request
     * @param fields   its fields, in the order they were asked for
     * @param evidence the evidence of every signing act on its fields
     */
    public SignatureRequestView(
            SignatureRequest request, List<RequestedSignature> fields, List<StoredEvidence> evidence) {
        this(request, fields, evidence, null);
    }
}

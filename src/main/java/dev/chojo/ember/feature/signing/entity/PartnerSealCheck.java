/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * The check of a document a federation partner sent, as sealed by that partner.
 *
 * @param verdict whether it may be taken as sealed by the partner
 * @param checked what the check found per seal, against the partner's pinned authorities only
 */
public record PartnerSealCheck(PartnerSealVerdict verdict, SealVerification checked) {

    /** @return whether the document may be taken as sealed by the partner */
    public boolean accepted() {
        return verdict == PartnerSealVerdict.ACCEPTED;
    }
}

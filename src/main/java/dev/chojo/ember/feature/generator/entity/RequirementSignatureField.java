/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

/**
 * One signature field of a participant's copy of a document an appointment asks for.
 *
 * @param id         the field, which the signing screen opens
 * @param name       the name of the field in the document, which says who signs it: participant,
 *                   guardian1 and further guardians by place, anyGuardian or issuer
 * @param signerName the official name of whoever is asked to sign it, or null where nobody in particular is
 * @param state      where the field stands
 * @param yours      whether the reader can sign it now, for themselves or for a member in their care
 * @param nobodyCanSign whether it is open and nobody can sign it, such as a guardian place nobody holds, so
 *                   only confirming it on paper or waiving it settles it
 */
public record RequirementSignatureField(
        int id,
        String name,
        @Nullable String signerName,
        RequirementSignatureState state,
        boolean yours,
        boolean nobodyCanSign) {

    /**
     * The field as a participant or guardian sees it: the issuer's field, still open and not theirs to
     * sign, is the station's to sign and nothing they could do anything about, so it shows as
     * {@link RequirementSignatureState#BY_STATION} rather than as open. Every other field stays as it is.
     *
     * @return the field as participants and guardians see it
     */
    public RequirementSignatureField asParticipantsSee() {
        boolean stations = SignatureRole.ISSUER.fieldNames(0).contains(name);
        if (!stations || yours || state != RequirementSignatureState.OPEN) return this;
        return new RequirementSignatureField(
                id, name, signerName, RequirementSignatureState.BY_STATION, false, nobodyCanSign);
    }
}

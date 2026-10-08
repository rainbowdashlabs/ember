/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.List;
import java.util.UUID;

/**
 * The signatures asked for on a participant's copy of a document an appointment asks for on one date.
 *
 * @param templateId the document asked for
 * @param memberId   the participant
 * @param requestUid the request for the signatures
 * @param state      where the copy stands as a whole: open while a field is, waived where every field was let
 *                   go, confirmed on paper where any field was, else signed
 * @param fields     its signature fields, in the order the copy carries them
 */
public record RequirementSignature(
        int templateId,
        int memberId,
        UUID requestUid,
        RequirementSignatureState state,
        List<RequirementSignatureField> fields) {

    public RequirementSignature {
        fields = List.copyOf(fields);
    }

    /**
     * Where a copy stands, read from its fields.
     *
     * @param fields the states of its fields
     * @return open while any field is, waived where all were let go, confirmed on paper where any was, else
     *         signed
     */
    public static RequirementSignatureState overall(List<RequirementSignatureState> fields) {
        if (fields.contains(RequirementSignatureState.OPEN)) return RequirementSignatureState.OPEN;
        if (fields.stream().allMatch(state -> state == RequirementSignatureState.WAIVED)) {
            return RequirementSignatureState.WAIVED;
        }
        boolean onPaper = fields.contains(RequirementSignatureState.PAPER_CONFIRMED);
        return onPaper ? RequirementSignatureState.PAPER_CONFIRMED : RequirementSignatureState.SIGNED;
    }
}

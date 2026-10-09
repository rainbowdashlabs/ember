/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The signatures asked for on a participant's copy of a document an appointment asks for on one date.
 *
 * @param templateId   the document asked for
 * @param memberId     the participant
 * @param requestUid   the request for the signatures
 * @param state        where the copy stands as a whole: revoked where a signer withdrew it, open while a field
 *                     is, waived where every field was let go, confirmed on paper where any field was, else
 *                     signed
 * @param fields       its signature fields, in the order the copy carries them
 * @param withdrawable whether the reader may withdraw what was signed on it
 * @param withdrawnAt  when a signer withdrew it, or null where nobody did
 */
public record RequirementSignature(
        int templateId,
        int memberId,
        UUID requestUid,
        RequirementSignatureState state,
        List<RequirementSignatureField> fields,
        boolean withdrawable,
        @Nullable Instant withdrawnAt) {

    public RequirementSignature {
        fields = List.copyOf(fields);
    }

    /**
     * The copy as a participant or guardian sees it: the issuer's open field is the station's to sign
     * ({@link RequirementSignatureField#asParticipantsSee}) and does not keep the copy open for them. A
     * withdrawn copy stays withdrawn.
     *
     * @return the copy as participants and guardians see it
     */
    public RequirementSignature asParticipantsSee() {
        var seen = fields.stream()
                .map(RequirementSignatureField::asParticipantsSee)
                .toList();
        var states = seen.stream().map(RequirementSignatureField::state).toList();
        var seenState = state == RequirementSignatureState.REVOKED ? state : overall(states);
        return new RequirementSignature(templateId, memberId, requestUid, seenState, seen, withdrawable, withdrawnAt);
    }

    /**
     * Where a copy stands, read from its fields. A field the station signs counts for nothing either way;
     * a copy with no other field stands as the station's to sign.
     *
     * @param fields the states of its fields
     * @return open while any field is, waived where all were let go, confirmed on paper where any was, else
     *         signed
     */
    public static RequirementSignatureState overall(List<RequirementSignatureState> fields) {
        var counted = fields.stream()
                .filter(state -> state != RequirementSignatureState.BY_STATION)
                .toList();
        if (counted.isEmpty() && !fields.isEmpty()) return RequirementSignatureState.BY_STATION;
        return overallOf(counted);
    }

    private static RequirementSignatureState overallOf(List<RequirementSignatureState> fields) {
        if (fields.contains(RequirementSignatureState.OPEN)) return RequirementSignatureState.OPEN;
        if (fields.stream().allMatch(state -> state == RequirementSignatureState.WAIVED)) {
            return RequirementSignatureState.WAIVED;
        }
        boolean onPaper = fields.contains(RequirementSignatureState.PAPER_CONFIRMED);
        return onPaper ? RequirementSignatureState.PAPER_CONFIRMED : RequirementSignatureState.SIGNED;
    }
}

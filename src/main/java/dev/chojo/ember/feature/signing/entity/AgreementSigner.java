/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.generator.entity.RequirementSignatureState;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A participant who signed a document an appointment asks for on one date, as whoever runs the appointment
 * sees them.
 *
 * @param memberId     the participant
 * @param name         their name, as the station shows it
 * @param templateId   the document
 * @param documentName what the document is called
 * @param state        where their copy stands: signed, confirmed on paper, still open for another signer, or
 *                     revoked where a signer withdrew it
 * @param signedAt     when the latest signature or paper confirmation on it was given
 * @param withdrawnAt  when it was withdrawn, or null
 * @param refused      whether they said they will not come on the date since
 */
public record AgreementSigner(
        int memberId,
        String name,
        int templateId,
        String documentName,
        RequirementSignatureState state,
        Instant signedAt,
        @Nullable Instant withdrawnAt,
        boolean refused) {}

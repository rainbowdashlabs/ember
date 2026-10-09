/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

/**
 * Where one document an appointment asks partners to sign stands for one member of a partner station.
 *
 * @param templateId      the document
 * @param name            what it is called
 * @param state           where it stands; {@link PartnerAgreementState#MISSING} where the partner never said it
 *                        takes the document on
 * @param complete        for a signed one, whether every signature field is settled at the partner
 * @param agreementId     the record of it, which a signed copy is read through, or null where none was written
 * @param copies          how many sealed copies came back
 * @param confirmedByName who confirmed a signed paper copy here, or null where nobody did
 */
public record PartnerSignerDocument(
        int templateId,
        String name,
        PartnerAgreementState state,
        boolean complete,
        @Nullable Integer agreementId,
        int copies,
        @Nullable String confirmedByName) {}

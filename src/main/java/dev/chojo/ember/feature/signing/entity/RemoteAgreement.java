/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A document a shared appointment asks the members of its partner stations to sign on one date, as the
 * station holding the appointment hands it to a partner: the one copy every partner's signer signs alike,
 * with what the signers confirm and how long the signed copies are kept.
 *
 * @param templateId      the document, by the id of its template at the station holding the appointment
 * @param templateVersion the version of the template the copy was drawn from
 * @param title           the title the copy carries
 * @param fileName        the file name it is handed out under, ending in {@code .pdf}
 * @param language        the language it is written in, which the signers' default statements follow
 * @param statements      what the signer of a field confirms, by the name of the field, for the fields the
 *                        template words itself; every other field asks for its signer's default statement
 * @param retentionMonths how many months the template keeps signed documents after the member is gone, or
 *                        null where it sets none
 * @param copyAttached    whether a signer's copy by mail may carry the signed PDF
 * @param sha256          SHA-256 of the PDF, lower-case hexadecimal, which every signature binds to
 * @param pdf             the PDF, Base64 encoded
 */
public record RemoteAgreement(
        int templateId,
        int templateVersion,
        String title,
        String fileName,
        DocumentLanguage language,
        Map<String, String> statements,
        @Nullable Integer retentionMonths,
        boolean copyAttached,
        String sha256,
        String pdf) {

    public RemoteAgreement {
        statements = Map.copyOf(statements);
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What a member's home installation tells the station holding a shared appointment about a document that
 * appointment asks the member to sign on a date. It travels signed with the home station's federation key,
 * like every federation request, and a sealed copy in it is checked against the home installation's
 * pinned signing authorities before it is taken.
 *
 * @param memberUid       the member, by the id their registration names
 * @param eventDate       the date of the appointment
 * @param templateId      the document, by the id of its template at the station holding the appointment
 * @param templateVersion the version of the template the copy was drawn from
 * @param contentSha256   SHA-256 of the copy that was handed out and signed
 * @param kind            what the notice says
 * @param sealedPdf       the sealed PDF, Base64 encoded, for {@link AgreementNoticeKind#SIGNED} and
 *                        {@link AgreementNoticeKind#WITHDRAWN}; null for {@link AgreementNoticeKind#ASKED}
 * @param fields          where each signature field stands at home
 * @param complete        whether every field is settled at home
 */
public record RemoteAgreementNotice(
        UUID memberUid,
        LocalDate eventDate,
        int templateId,
        int templateVersion,
        String contentSha256,
        AgreementNoticeKind kind,
        @Nullable String sealedPdf,
        List<RemoteAgreementField> fields,
        boolean complete) {

    public RemoteAgreementNotice {
        fields = List.copyOf(fields);
    }
}

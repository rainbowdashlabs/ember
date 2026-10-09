/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A document a partner station no longer asks one of its members to sign, since the organiser took it off
 * the appointment: the open request at home was let go.
 *
 * @param memberUid  the member, by the id their registration names
 * @param eventDate  the date
 * @param templateId the document, by the id of its template at the organiser
 */
public record RemoteAgreementRelease(UUID memberUid, LocalDate eventDate, int templateId) {}

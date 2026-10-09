/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * A document a partner station's appointment without registrations offers the members of this station to
 * sign on its page, where signing it says they will come.
 *
 * @param templateId the document, by the id of its template at the partner
 * @param title      what it is called
 */
public record PartnerAgreementOffer(int templateId, String title) {}

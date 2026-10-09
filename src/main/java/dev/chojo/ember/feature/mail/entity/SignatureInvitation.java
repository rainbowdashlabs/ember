/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

/**
 * What a mail asking for a signature, or reminding of one, says about the document.
 *
 * @param stationName   the station that asks
 * @param documentTitle the document's title
 * @param memberName    the official name of the member the document is about
 * @param url           where the document is read and signed
 */
public record SignatureInvitation(String stationName, String documentTitle, String memberName, String url) {}

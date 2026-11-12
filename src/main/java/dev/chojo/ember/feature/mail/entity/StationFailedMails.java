/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The mails of one station that did not arrive and that the instance's administrator has not taken
 * note of yet, counted rather than listed: whom they were for and what they said is the station's
 * business, and the station sees them in full on its own mail page.
 *
 * <p>The causes are the categories only, never the text a provider sent back, since that text often
 * names the address it refused.
 *
 * @param stationUid  the station
 * @param stationName its name
 * @param count       how many of its mails did not arrive
 * @param oldest      when the first of them failed
 * @param newest      when the last of them failed, which is what taking note of them goes up to
 * @param causes      why they did not arrive, most frequent first
 */
public record StationFailedMails(
        UUID stationUid, String stationName, int count, Instant oldest, Instant newest, List<CauseCount> causes) {

    /**
     * @param cause why
     * @param count how many of the station's mails failed for it
     */
    public record CauseCount(FailedMailCause cause, int count) {}
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * What needs an instance administrator's attention, for the dashboard they land on.
 *
 * @param emailFailed                mails of the instance and every station that did not arrive and that
 *                                   nobody has taken note of: given up on by the queue, or reported by
 *                                   the provider as bounced, blocked or spam
 * @param emailPending               the instance's own mails waiting to be sent, the ones its mail log shows
 * @param emailStuckSending          the instance's own mails a sender took in hand more than ten minutes ago
 *                                   and never handed over
 * @param stationApplicationsPending applications for a new station waiting for a decision
 * @param stationsSetupPending       regular stations still in their setup; an association's home
 *                                   station has no setup to wait for
 * @param accountsUnverified         accounts with a real address that is not confirmed yet; a stand-in
 *                                   address can never be confirmed and is not counted
 * @param federationPartnersPending  pairings with other stations waiting for an answer
 * @param discoveryPeersUnreachable  known instances that did not answer and are not blocked
 * @param problemReportsOpen         problem reports nobody has acknowledged
 * @param signingKeysLocked          signing keys in use that no longer open under the at-rest secret, so
 *                                   no station can seal until an administrator gives them up
 * @param recentApplications         the five newest applications waiting for a decision
 * @param recentProblemReports       the five newest unacknowledged problem reports
 */
public record AdminOverview(
        int emailFailed,
        int emailPending,
        int emailStuckSending,
        int stationApplicationsPending,
        int stationsSetupPending,
        int accountsUnverified,
        int federationPartnersPending,
        int discoveryPeersUnreachable,
        int problemReportsOpen,
        int signingKeysLocked,
        List<RecentApplication> recentApplications,
        List<RecentProblemReport> recentProblemReports) {

    /**
     * @param count how many signing keys no longer open, which the database cannot count
     * @return this overview with that count
     */
    public AdminOverview withSigningKeysLocked(int count) {
        return new AdminOverview(
                emailFailed,
                emailPending,
                emailStuckSending,
                stationApplicationsPending,
                stationsSetupPending,
                accountsUnverified,
                federationPartnersPending,
                discoveryPeersUnreachable,
                problemReportsOpen,
                count,
                recentApplications,
                recentProblemReports);
    }

    /**
     * An application for a new station.
     *
     * @param id          the application
     * @param name        the applicant's full name
     * @param stationName the station applied for
     * @param createdAt   when it was sent
     */
    public record RecentApplication(
            int id,
            String name,
            @JsonProperty("station_name") String stationName,
            @JsonProperty("created_at") Instant createdAt) {}

    /**
     * A problem report.
     *
     * @param id           the report
     * @param reporterName who sent it
     * @param pageUrl      the page it was sent from, if known
     * @param createdAt    when it was sent
     */
    public record RecentProblemReport(
            int id,
            @JsonProperty("reporter_name") String reporterName,
            @JsonProperty("page_url") String pageUrl,
            @JsonProperty("created_at") Instant createdAt) {}
}

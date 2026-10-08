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
 * @param emailFailed                mails given up on
 * @param emailPending               mails waiting to be sent
 * @param emailStuckSending          mails taken by a sender more than ten minutes ago and not handed over
 * @param stationApplicationsPending applications for a new station waiting for a decision
 * @param stationsSetupPending       stations still in their setup
 * @param accountsUnverified         accounts whose address is not confirmed yet
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

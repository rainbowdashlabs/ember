/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.beacon.repository.BeaconReadRepository;
import dev.chojo.ember.feature.beacon.service.BeaconSettings;
import dev.chojo.ember.feature.mail.service.FailedMailService;
import dev.chojo.ember.feature.system.repository.ProblemLogRepository;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.function.IntSupplier;

/**
 * What is waiting for the operator: faults, reports and failed mails nobody has acknowledged yet,
 * from this instance and, where it receives them, from the instances that report to it.
 */
@Singleton
public class MonitoringCountService {
    private final ProblemReportRepository reports;
    private final BeaconReadRepository beacon;
    private final BeaconSettings beaconSettings;
    private final FailedMailService failedMails;
    private final IntSupplier openProblems;

    @Inject
    public MonitoringCountService(
            ProblemReportRepository reports,
            BeaconReadRepository beacon,
            BeaconSettings beaconSettings,
            FailedMailService failedMails,
            ProblemLogRepository problems) {
        this(reports, beacon, beaconSettings, failedMails, problems::countOpen);
    }

    /**
     * The same, counting the unacknowledged faults of the log the given way.
     *
     * @param openProblems how many problems in the log wait for an administrator
     */
    MonitoringCountService(
            ProblemReportRepository reports,
            BeaconReadRepository beacon,
            BeaconSettings beaconSettings,
            FailedMailService failedMails,
            IntSupplier openProblems) {
        this.reports = reports;
        this.beacon = beacon;
        this.beaconSettings = beaconSettings;
        this.failedMails = failedMails;
        this.openProblems = openProblems;
    }

    /**
     * The counts, and whether this instance is a beacon at all.
     *
     * <p>The beacon numbers are only asked for where the instance receives, because an instance that
     * does not has no tables worth querying and no sidebar entry to put a number on.
     */
    public MonitoringCounts counts() {
        boolean receiving = beaconSettings.receiving();
        var waiting = receiving ? beacon.countWaiting() : new BeaconReadRepository.Waiting(0, 0);
        return new MonitoringCounts(
                openProblems.getAsInt(),
                reports.countUnacknowledged(),
                receiving,
                waiting.faults(),
                waiting.reports(),
                failedMails.unacknowledgedCount());
    }

    /**
     * @param problemLog     open warnings and errors in the problem log
     * @param problemReports reports from this instance's own members that nobody has looked at
     * @param beaconActive   whether this instance accepts what other instances report
     * @param beaconFaults   faults gathered from other instances, unacknowledged
     * @param beaconReports  forwarded reports from other instances, unacknowledged
     * @param failedMails    mails of the instance and every station that did not arrive and nobody
     *                       has taken note of, the same figure the overview shows
     */
    public record MonitoringCounts(
            int problemLog,
            int problemReports,
            boolean beaconActive,
            int beaconFaults,
            int beaconReports,
            int failedMails) {}
}

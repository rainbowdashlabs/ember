/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.beacon.repository.BeaconReadRepository;
import dev.chojo.ember.feature.beacon.service.BeaconSettings;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.function.IntSupplier;

/**
 * What is waiting for the operator: faults and reports nobody has acknowledged yet, from this
 * instance and, where it receives them, from the instances that report to it.
 */
@Singleton
public class MonitoringCountService {
    private final ProblemReportRepository reports;
    private final BeaconReadRepository beacon;
    private final BeaconSettings beaconSettings;
    private final IntSupplier unacknowledgedFaults;

    @Inject
    public MonitoringCountService(
            ProblemReportRepository reports, BeaconReadRepository beacon, BeaconSettings beaconSettings) {
        this(reports, beacon, beaconSettings, MonitoringCountService::faultsInTheLog);
    }

    /**
     * The same, counting the unacknowledged faults of the log the given way.
     *
     * @param unacknowledgedFaults how many faults in the log nobody has acknowledged
     */
    MonitoringCountService(
            ProblemReportRepository reports,
            BeaconReadRepository beacon,
            BeaconSettings beaconSettings,
            IntSupplier unacknowledgedFaults) {
        this.reports = reports;
        this.beacon = beacon;
        this.beaconSettings = beaconSettings;
        this.unacknowledgedFaults = unacknowledgedFaults;
    }

    private static int faultsInTheLog() {
        var log = ProblemLogAppender.instance();
        return log != null ? log.countUnacknowledged() : 0;
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
                unacknowledgedFaults.getAsInt(),
                reports.countUnacknowledged(),
                receiving,
                waiting.faults(),
                waiting.reports());
    }

    /**
     * @param problemLog     entries in the fault log nobody has acknowledged
     * @param problemReports reports from this instance's own members that nobody has looked at
     * @param beaconActive   whether this instance accepts what other instances report
     * @param beaconFaults   faults gathered from other instances, unacknowledged
     * @param beaconReports  forwarded reports from other instances, unacknowledged
     */
    public record MonitoringCounts(
            int problemLog, int problemReports, boolean beaconActive, int beaconFaults, int beaconReports) {}
}

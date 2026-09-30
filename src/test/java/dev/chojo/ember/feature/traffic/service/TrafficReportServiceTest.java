/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.service;

import dev.chojo.ember.feature.traffic.entity.AuthBucket;
import dev.chojo.ember.feature.traffic.entity.TrafficBucket;
import dev.chojo.ember.feature.traffic.repository.StationTrafficRepository;
import dev.chojo.ember.feature.traffic.service.TrafficReportService.HourlyTrafficRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrafficReportServiceTest {
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-02T00:00:00Z");

    @Test
    void theHoursAreHandedOutInTheShapeTheyAreCountedIn() {
        var repository = mock(StationTrafficRepository.class);
        var auth = AuthBucket.values()[0];
        when(repository.findHourly(FROM, TO, 3, auth)).thenReturn(List.of(new TrafficBucket(FROM, 3, auth, 10, 20, 2)));

        var rows =
                new TrafficReportService(repository).hourly(FROM, TO, 3, auth).rows();

        assertEquals(List.of(new HourlyTrafficRow(FROM, 3, auth, 10, 20, 2)), rows);
    }
}

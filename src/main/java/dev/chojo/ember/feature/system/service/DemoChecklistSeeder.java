/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.checklist.service.ChecklistService;
import dev.chojo.ember.feature.checklist.service.ChecklistService.ColumnSpec;
import dev.chojo.ember.feature.checklist.service.ChecklistService.FilterSpec;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Year;
import java.util.List;
import java.util.Random;

/**
 * Seeds a single demo checklist for the upcoming "Freizeit" trip. Targets every active
 * station member of type {@link StationUserType#MEMBER}, exposes a single "Bezahlt" column,
 * and pre-ticks roughly two thirds of the entries so the matrix lands in a realistic
 * partially-checked state.
 */
@Singleton
public class DemoChecklistSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoChecklistSeeder.class);

    private final ChecklistService service;

    @Inject
    public DemoChecklistSeeder(ChecklistService service) {
        this.service = service;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        seed(station.stationId(), station.adminMember().id(), new Random(42_030));
    }

    public void seed(int stationId, int adminMemberId, Random rng) {
        var checklist = service.create(
                stationId,
                "Freizeit " + Year.now().getValue(),
                "Anmeldungen und Zahlungen für die diesjährige Freizeit.",
                RestrictionMode.AND,
                List.of(new ColumnSpec("Bezahlt", "Teilnahmebeitrag eingegangen")),
                new FilterSpec(List.of(StationUserType.MEMBER), List.of(), List.of(), List.of()),
                adminMemberId);

        var columns = service.findColumns(checklist.id());
        if (columns.isEmpty()) return;
        int bezahltColumnId = columns.getFirst().id();

        var entries = service.findEntries(checklist.id(), false);
        int checked = 0;
        for (var entry : entries) {
            if (rng.nextInt(3) == 0) continue;
            service.writeCell(entry.id(), bezahltColumnId, true, null, adminMemberId);
            checked++;
        }
        log.info("Demo: Created Freizeit checklist with {} entries ({} pre-paid)", entries.size(), checked);
    }
}

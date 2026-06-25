/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
import dev.chojo.ember.feature.lostandfound.service.LostAndFoundService;
import dev.chojo.ember.feature.members.entity.StationMember;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Seeds a handful of lost-and-found items via {@link LostAndFoundService}. Going through the
 * service (instead of the repository) means each create / claim publishes the matching domain
 * event and produces real {@code LOST_AND_FOUND_NEW} / {@code LOST_AND_FOUND_CLAIMED}
 * notifications for the members configured as recipients.
 */
@Singleton
public class DemoLostAndFoundSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoLostAndFoundSeeder.class);

    private final LostAndFoundService service;

    @Inject
    public DemoLostAndFoundSeeder(LostAndFoundService service) {
        this.service = service;
    }

    /**
     * @return one of the seeded items so callers (e.g. the notification showcase) can
     * construct a deep link with a real id.
     */
    public LostAndFoundItem seed(
            int stationId, List<StationMember> betreuer, List<StationMember> anfaenger, List<StationMember> eltern) {
        if (betreuer.isEmpty()) return null;
        int finder = betreuer.getFirst().id();

        // An unclaimed item — produces a LOST_AND_FOUND_NEW notification for every member.
        var jacket = service.create(
                stationId,
                "Blaue Jacke Größe M, im Geräteraum gefunden",
                LocalDate.now().minusDays(2),
                finder);

        // A second item that's been claimed — produces both LOST_AND_FOUND_NEW (on create) and
        // LOST_AND_FOUND_CLAIMED (on claim, sent to LOST_AND_FOUND_MANAGER members).
        var helmet =
                service.create(stationId, "Roter Helm Größe S", LocalDate.now().minusDays(5), finder);
        if (!anfaenger.isEmpty()) {
            String claimerName = "Lena Schmidt";
            service.claim(helmet.id(), anfaenger.getFirst().id(), stationId, claimerName);
        }

        // One more so the lost-and-found page has at least three rows to scroll through.
        service.create(
                stationId,
                "Trinkflasche mit Wachsabzeichen, Marke unklar",
                LocalDate.now().minusDays(1),
                finder);
        if (!eltern.isEmpty()) {
            // No-op; eltern parameter kept symmetric with the other seeders.
        }

        log.info("Demo: Created lost-and-found items (one claimed)");
        return jacket;
    }
}

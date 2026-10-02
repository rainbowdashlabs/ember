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
import org.jspecify.annotations.Nullable;
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
public class DemoLostAndFoundSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoLostAndFoundSeeder.class);

    private final LostAndFoundService service;
    private final DemoClock clock;

    @Inject
    public DemoLostAndFoundSeeder(LostAndFoundService service, DemoClock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Override
    public int order() {
        return LOST_AND_FOUND;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var members = station.members();
        station.lostAndFoundItem(seed(
                clock.of(station.station()).today(),
                station.stationId(),
                members.betreuer(),
                members.anfaenger(),
                members.eltern()));
    }

    /**
     * Seeds three found items, one of them claimed, so both the new-item and the claimed notifications
     * fire and the page has rows to scroll through.
     *
     * @param today the station's today, which the days the items were found are counted back from
     * @param eltern unused, kept so the signature matches the other seeders
     * @return one of the seeded items so callers (e.g. the notification showcase) can
     * construct a deep link with a real id.
     */
    public @Nullable LostAndFoundItem seed(
            LocalDate today,
            int stationId,
            List<StationMember> betreuer,
            List<StationMember> anfaenger,
            List<StationMember> eltern) {
        if (betreuer.isEmpty()) return null;
        int finder = betreuer.getFirst().id();

        var jacket =
                service.create(stationId, "Blaue Jacke Größe M, im Geräteraum gefunden", today.minusDays(2), finder);

        var helmet = service.create(stationId, "Roter Helm Größe S", today.minusDays(5), finder);
        if (!anfaenger.isEmpty()) {
            String claimerName = "Lena Schmidt";
            service.claim(helmet.id(), anfaenger.getFirst().id(), stationId, claimerName);
        }

        service.create(stationId, "Trinkflasche mit Wachsabzeichen, Marke unklar", today.minusDays(1), finder);

        log.info("Demo: Created lost-and-found items (one claimed)");
        return jacket;
    }
}

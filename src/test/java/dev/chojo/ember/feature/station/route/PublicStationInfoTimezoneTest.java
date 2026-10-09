/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.signing.service.PublishedCertificates;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.PublicStationInfoService;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.read;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The clock a station's public pages are written on, as its public card hands it out.
 *
 * <p>Those pages are rendered once by the server and once again by the browser, and neither machine
 * stands where the reader does, so a date written on whichever clock ran came out differently in the
 * two copies. The station's own clock is the one both can be told to use, and it only reaches them
 * if the card carries it.
 */
class PublicStationInfoTimezoneTest {
    private static final UUID STATION_UID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static Station station(String timezone) {
        return new Station(
                9,
                STATION_UID,
                "Wache",
                timezone,
                "de-DE",
                null,
                null,
                false,
                null,
                ThemeFeel.ROUNDED,
                false,
                PublicKbMode.DENY_ALL,
                DiscoveryVisibility.NONE,
                null,
                false,
                true,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                StationKind.REGULAR,
                null,
                false,
                false);
    }

    private static JsonNode cardOf(String timezone) {
        var stationRepository = mock(StationRepository.class);
        when(stationRepository.findByAddress(STATION_UID.toString())).thenReturn(Optional.of(station(timezone)));
        var routes = new PublicStationRoutes(new PublicStationInfoService(
                stationRepository,
                mock(StationLogoService.class),
                mock(PageService.class),
                mock(WaitingListService.class),
                mock(NewsService.class),
                mock(FormService.class),
                mock(PublishedCertificates.class)));

        return read(
                RouteHarness.serving(routes)
                        .request(
                                client -> client.get(RouteHarness.PREFIX + "/public/station/" + STATION_UID + "/info")),
                JsonNode.class);
    }

    @Test
    void aStationHandsOutTheClockItKeeps() {
        assertEquals("Europe/Berlin", cardOf("Europe/Berlin").path("timezone").asString());
    }

    /**
     * A station that never said where it stands falls back to the clock its exports already fall back
     * to, spelled the way a browser reads it: Java writes that offset as {@code Z}, and no browser's
     * date formatter knows that spelling.
     */
    @Test
    void aStationThatNamedNoClockHandsOutOneABrowserCanRead() {
        assertEquals("UTC", cardOf(null).path("timezone").asString());
        assertEquals("UTC", cardOf("Nirgendwo/Nirgends").path("timezone").asString());
    }
}

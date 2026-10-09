/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.refusal.StationRefusal;
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
import io.javalin.http.HttpStatus;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.read;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Whether a station answers the open web at all.
 *
 * <p>A form put where anybody can reach it is drawn inside the station's own frame, with its name
 * and its colours around it, and that frame asks the station's public card for them. A station
 * whose only public thing is such a form therefore has to answer, or the form's page comes up empty
 * for everybody it was sent to: it had nothing else on the public web, which is exactly the case
 * where somebody reaches for a form.
 */
class PublicStationReachTest {
    private static final UUID STATION_UID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** A station that publishes nothing: no wiki, no calendar, no pages, no waiting list, no blog. */
    private static Station withdrawnStation() {
        return new Station(
                9,
                STATION_UID,
                "Wache",
                "Europe/Berlin",
                "de-DE",
                null,
                null,
                false,
                null,
                ThemeFeel.ROUNDED,
                false,
                PublicKbMode.OFF,
                DiscoveryVisibility.NONE,
                null,
                false,
                false,
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

    private static PublicStationRoutes routesWhereFormsReach(boolean openlyAddressedForms) {
        var stationRepository = mock(StationRepository.class);
        when(stationRepository.findByAddress(STATION_UID.toString())).thenReturn(Optional.of(withdrawnStation()));
        var formService = mock(FormService.class);
        when(formService.hasOpenlyAddressedForms(9)).thenReturn(openlyAddressedForms);
        return new PublicStationRoutes(new PublicStationInfoService(
                stationRepository,
                mock(StationLogoService.class),
                mock(PageService.class),
                mock(WaitingListService.class),
                mock(NewsService.class),
                formService,
                mock(PublishedCertificates.class)));
    }

    private static Response askFor(PublicStationRoutes routes) {
        return RouteHarness.serving(routes)
                .request(client -> client.get(RouteHarness.PREFIX + "/public/station/" + STATION_UID + "/info"));
    }

    @Test
    void aStationWithNothingPublicAtAllAnswersNobody() {
        var refused = askFor(routesWhereFormsReach(false));

        assertEquals(StationRefusal.PUBLIC_STATION_NOTHING_TO_SHOW, refusalOf(refused));
        assertEquals(HttpStatus.NOT_FOUND.getCode(), refused.code());
    }

    @Test
    void aFormAnybodyCanReachIsEnoughToAnswerWith() {
        var card = read(askFor(routesWhereFormsReach(true)), JsonNode.class);

        assertEquals("Wache", card.path("name").asString());
    }
}

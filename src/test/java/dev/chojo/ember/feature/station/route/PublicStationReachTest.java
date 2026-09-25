/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
                null,
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
        return new PublicStationRoutes(
                stationRepository,
                mock(StationService.class),
                mock(StationLogoService.class),
                mock(PageService.class),
                mock(WaitingListService.class),
                mock(NewsService.class),
                formService);
    }

    private static void askFor(PublicStationRoutes routes, Context ctx) throws Exception {
        when(ctx.pathParam("stationUid")).thenReturn(STATION_UID.toString());
        Method handler = PublicStationRoutes.class.getDeclaredMethod("getInfo", Context.class);
        handler.setAccessible(true);
        try {
            handler.invoke(routes, ctx);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw e;
        }
    }

    @Test
    void aStationWithNothingPublicAtAllAnswersNobody() {
        var routes = routesWhereFormsReach(false);
        Context ctx = mock(Context.class);

        assertThrows(NotFoundResponse.class, () -> askFor(routes, ctx));
    }

    @Test
    void aFormAnybodyCanReachIsEnoughToAnswerWith() throws Exception {
        var routes = routesWhereFormsReach(true);
        Context ctx = mock(Context.class);

        askFor(routes, ctx);

        var card = ArgumentCaptor.forClass(PublicStationRoutes.PublicStationInfo.class);
        verify(ctx).json(card.capture());
        assertEquals("Wache", card.getValue().name());
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class PublicStationRoutes implements Routes {
    private final StationRepository stationRepository;
    private final StationService stationService;

    @Inject
    public PublicStationRoutes(StationRepository stationRepository, StationService stationService) {
        this.stationRepository = stationRepository;
        this.stationService = stationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/station/{stationUid}/info", this::getInfo);
    }

    private void getInfo(Context ctx) {
        String uidParam = ctx.pathParam("stationUid");
        UUID uid;
        try {
            uid = UUID.fromString(uidParam);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Invalid station ID");
        }
        var station = stationRepository.findByUid(uid).orElseThrow(NotFoundResponse::new);

        boolean hasPublicKb = station.publicKbMode() != PublicKbMode.OFF;
        boolean hasPublicCalendar = station.publicCalendarEnabled();

        if (!hasPublicKb && !hasPublicCalendar) {
            throw new NotFoundResponse();
        }

        boolean hasLogo = stationService.getLogo(station.id()).isPresent();

        ctx.json(new PublicStationInfo(
                station.uid().toString(),
                station.name(),
                station.discoveryDescription(),
                hasLogo,
                hasPublicKb,
                hasPublicCalendar));
    }

    public record PublicStationInfo(
            String stationUid,
            String name,
            String description,
            boolean hasLogo,
            boolean hasPublicKb,
            boolean hasPublicCalendar) {}
}

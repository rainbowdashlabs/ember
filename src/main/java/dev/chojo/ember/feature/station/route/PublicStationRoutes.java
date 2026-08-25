/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class PublicStationRoutes implements Routes {
    private final StationRepository stationRepository;
    private final StationService stationService;
    private final StationLogoService logoService;
    private final PageService pageService;
    private final WaitingListService waitingListService;
    private final NewsService newsService;

    @Inject
    public PublicStationRoutes(
            StationRepository stationRepository,
            StationService stationService,
            StationLogoService logoService,
            PageService pageService,
            WaitingListService waitingListService,
            NewsService newsService) {
        this.stationRepository = stationRepository;
        this.stationService = stationService;
        this.logoService = logoService;
        this.pageService = pageService;
        this.waitingListService = waitingListService;
        this.newsService = newsService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/station/{stationUid}/info", this::getInfo);
    }

    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/info",
            methods = HttpMethod.GET,
            summary = "Get public information about a station",
            tags = {"Public Station"},
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            description = "An association's own station answers with its wiki alone, and only when the "
                    + "association has put that wiki on the public web.",
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicStationInfo.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getInfo(Context ctx) {
        var station = resolveStation(ctx, true);

        boolean hasPublicKb = station.publicKbMode() != PublicKbMode.OFF;

        if (station.stationKind() == StationKind.CLUSTER_HOME) {
            if (!hasPublicKb) throw new NotFoundResponse();
            ctx.json(publicInfo(station, true, false, false, false, false, null));
            return;
        }

        boolean hasPublicCalendar = station.publicCalendarEnabled();
        boolean hasPublicPages = station.publicPagesEnabled() && pageService.hasPublishedPages(station.id());
        boolean hasPublicWaitlist =
                station.publicWaitlistEnabled() && waitingListService.hasPublicWaitlists(station.id());
        boolean hasPublicBlog = station.publicBlogEnabled() && newsService.hasPublicBlogEntries(station.id());

        if (!hasPublicKb && !hasPublicCalendar && !hasPublicPages && !hasPublicWaitlist && !hasPublicBlog) {
            throw new NotFoundResponse();
        }

        String landingPageSlug =
                hasPublicPages ? pageService.getLandingPageSlug(station.id()).orElse(null) : null;

        ctx.json(publicInfo(
                station,
                hasPublicKb,
                hasPublicCalendar,
                hasPublicPages,
                hasPublicWaitlist,
                hasPublicBlog,
                landingPageSlug));
    }

    private PublicStationInfo publicInfo(
            Station station,
            boolean hasPublicKb,
            boolean hasPublicCalendar,
            boolean hasPublicPages,
            boolean hasPublicWaitlist,
            boolean hasPublicBlog,
            String landingPageSlug) {
        return new PublicStationInfo(
                station.uid().toString(),
                station.name(),
                station.discoveryDescription(),
                logoService.exists(station.id()),
                hasPublicKb,
                hasPublicCalendar,
                hasPublicPages,
                hasPublicWaitlist,
                hasPublicBlog,
                landingPageSlug,
                station.publicSlug(),
                station.defaultTheme(),
                station.defaultFeel() != null ? station.defaultFeel().name() : null,
                station.customThemeColors());
    }

    /**
     * The station a public address names, by uid or by the readable name it may have been given.
     *
     * <p>A cluster's home station is refused unless the caller says otherwise: it is not a station anybody
     * may look at, and everything it could serve as one would be an accident. Its wiki is the exception,
     * and only its wiki, which is why the exception is asked for rather than assumed.
     *
     * @param allowClusterHome whether an association's own station may answer, which only the wiki does
     */
    private Station resolveStation(Context ctx, boolean allowClusterHome) {
        String param = ctx.pathParam("stationUid");
        Station station;
        try {
            UUID uid = UUID.fromString(param);
            station = stationRepository.findByUid(uid).orElseThrow(NotFoundResponse::new);
        } catch (IllegalArgumentException e) {
            // Not a UUID - try as public slug
            station = stationRepository.findBySlug(param).orElseThrow(NotFoundResponse::new);
        }
        if (!allowClusterHome && station.stationKind() == StationKind.CLUSTER_HOME) {
            throw new NotFoundResponse();
        }
        return station;
    }

    public record PublicStationInfo(
            String stationUid,
            String name,
            String description,
            boolean hasLogo,
            boolean hasPublicKb,
            boolean hasPublicCalendar,
            boolean hasPublicPages,
            boolean hasPublicWaitlist,
            boolean hasPublicBlog,
            String landingPageSlug,
            String publicSlug,
            String defaultTheme,
            String defaultFeel,
            String customThemeColors) {}
}

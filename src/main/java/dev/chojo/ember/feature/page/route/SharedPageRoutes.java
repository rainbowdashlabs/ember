/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.feature.insights.service.PageHitRecorder;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * A page reached by its link and by nothing else.
 *
 * <p>No station stands in the address, because the reader holds a link and has nothing else to put
 * there. The token is unique across the installation, which is what makes a lookup by it alone
 * sound, and it is long enough that holding one is the whole of the permission.
 *
 * <p>Nothing here asks whether the station has opened its pages to the world. That switch decides
 * whether a station has a public site; a page sent to somebody is not part of one, and asking would
 * take the feature away from exactly the station it is for.
 *
 * <p>The answer carries the station's name, picture and colours as well as the page, so the wrapper
 * around it can be drawn without a second call, and it says whether the page also has an ordinary
 * address, so a page opened to the public since the link was sent can send the reader on to it.
 */
@Singleton
public class SharedPageRoutes implements Routes {
    private final PageService pageService;
    private final StationRepository stationRepository;
    private final StationLogoService logoService;

    @Inject
    public SharedPageRoutes(
            PageService pageService, StationRepository stationRepository, StationLogoService logoService) {
        this.pageService = pageService;
        this.stationRepository = stationRepository;
        this.logoService = logoService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/shared/{token}", this::getSharedPage);
        routes.get(prefix + "/public/shared/{token}/brand", this::getBrand);
    }

    @OpenApi(
            path = "/api/v1/public/shared/{token}",
            methods = HttpMethod.GET,
            summary = "A page reached by its share link",
            tags = {"Public Pages"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SharedPage.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree("the link is the authorisation, and which station the page belongs to is the answer rather than"
            + " part of the question: the reader holds a token and nothing else")
    private void getSharedPage(Context ctx) {
        var page = pageService.getSharedPage(ctx.pathParam("token")).orElseThrow(NotFoundResponse::new);
        var station = stationRepository.findById(page.stationId()).orElseThrow(NotFoundResponse::new);

        ctx.attribute(PageHitRecorder.ATTR_PAGE_HIT_PAGE_ID, page.id());
        ctx.json(new SharedPage(brandOf(station), page, pageService.getPagePath(page), ownAddressLive(page, station)));
    }

    /**
     * The station's name, picture and colours alone.
     *
     * <p>The theme is chosen while the page is rendered on the server, before anything the page
     * itself asks for has been fetched, so it cannot come out of the answer above. This marks no
     * page hit: it is asked for once per render, and counting it would put two visits on the board
     * for every one somebody made.
     */
    @OpenApi(
            path = "/api/v1/public/shared/{token}/brand",
            methods = HttpMethod.GET,
            summary = "The station behind a share link, for the wrapper around the page",
            tags = {"Public Pages"},
            pathParams = @OpenApiParam(name = "token", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SharedBrand.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree("the same link, answering only the name and colours of the station it leads to")
    private void getBrand(Context ctx) {
        var page = pageService.getSharedPage(ctx.pathParam("token")).orElseThrow(NotFoundResponse::new);
        var station = stationRepository.findById(page.stationId()).orElseThrow(NotFoundResponse::new);
        ctx.json(brandOf(station));
    }

    /**
     * Whether the page also answers at the path its slugs spell, which needs both the page to be
     * listed and the station to have opened its pages at all. A link that sent somebody on to an
     * address behind a switch that is off would send them to an error.
     */
    private boolean ownAddressLive(StationPage page, Station station) {
        return page.visibility().listed() && station.publicPagesEnabled();
    }

    private SharedBrand brandOf(Station station) {
        return brandOf(station, logoService);
    }

    /**
     * Shared with the form routes, which draw the same wrapper around a form somebody was sent.
     */
    public static SharedBrand brandOf(Station station, StationLogoService logoService) {
        return new SharedBrand(
                station.uid().toString(),
                station.publicSlug(),
                station.name(),
                logoService.exists(station.id()),
                station.defaultTheme(),
                station.defaultFeel() != null ? station.defaultFeel().name() : null,
                station.customThemeColors());
    }

    @OpenApiName("SharedPage")
    public record SharedPage(SharedBrand station, StationPage page, String path, boolean ownAddressLive) {}

    @OpenApiName("SharedPageBrand")
    public record SharedBrand(
            String stationUid,
            String publicSlug,
            String name,
            boolean hasLogo,
            String defaultTheme,
            String defaultFeel,
            String customThemeColors) {}
}

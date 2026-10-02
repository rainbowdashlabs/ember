/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.system.service.SitemapService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class SitemapRoutes implements Routes {
    private final SitemapService sitemaps;

    @Inject
    public SitemapRoutes(SitemapService sitemaps) {
        this.sitemaps = sitemaps;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get("/sitemap.xml", this::sitemapIndex);
        routes.get("/sitemap-station-{stationUid}.xml", this::sitemapStation);
    }

    private static void answerXml(Context ctx, String xml) {
        ctx.contentType("application/xml; charset=utf-8");
        ctx.header("Cache-Control", "public, max-age=21600");
        ctx.result(xml);
    }

    private void sitemapIndex(Context ctx) {
        answerXml(ctx, sitemaps.index());
    }

    private void sitemapStation(Context ctx) {
        answerXml(ctx, sitemaps.forStation(RouteSupport.pathUuid(ctx, "stationUid")));
    }
}

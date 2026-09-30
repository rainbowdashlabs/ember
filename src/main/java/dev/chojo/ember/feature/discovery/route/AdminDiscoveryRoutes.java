/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.discovery.service.DiscoveredStationService;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.AddPeerRequest;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.BlocklistRequest;
import dev.chojo.ember.feature.discovery.service.DiscoverySettingsService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Admin-facing discovery routes mounted under {@code /api/v1/admin/discovery}. Required
 * permission is {@link InstancePermission#ADMINISTRATOR}.
 *
 * <p>Authenticated user-facing discovery (browsing the cached peers' station cards) lives in
 * {@code /api/v1/discovery/stations} and is intentionally permissionless beyond a valid
 * session: discovery surfaces are public by design, the same data is available anonymously.
 * The partner station picker of the page editor is gated by {@link StationPermission#PAGE_EDIT}
 * so it never escapes the editor, and public scrapers cannot use it to enumerate the federation.
 */
@Singleton
public class AdminDiscoveryRoutes implements Routes {

    private final DiscoveryAdminService discovery;
    private final DiscoveredStationService discoveredStations;
    private final DiscoverySettingsService settingsService;

    @Inject
    public AdminDiscoveryRoutes(
            DiscoveryAdminService discovery,
            DiscoveredStationService discoveredStations,
            DiscoverySettingsService settingsService) {
        this.discovery = discovery;
        this.discoveredStations = discoveredStations;
        this.settingsService = settingsService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/discovery/identity", this::getIdentity, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/discovery/settings", this::getSettings, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/discovery/settings",
                this::updateSettings,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/discovery/peers", this::listPeers, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/discovery/peers/probe", this::probePeer, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/discovery/peers",
                this::addPeer,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.delete(
                prefix + "/admin/discovery/peers/{publicKey}",
                this::deletePeer,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.post(
                prefix + "/admin/discovery/peers/{publicKey}/upvote",
                this::upvotePeer,
                InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/discovery/peers/{publicKey}/downvote",
                this::downvotePeer,
                InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/discovery/peers/{publicKey}/block",
                this::blockPeer,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.post(
                prefix + "/admin/discovery/peers/{publicKey}/unblock",
                this::unblockPeer,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.post(
                prefix + "/admin/discovery/peers/{publicKey}/ping",
                this::pingPeerNow,
                InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/discovery/discover-now", this::discoverNow, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/discovery/seed",
                this::seedFederation,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.get(prefix + "/admin/discovery/blocklist", this::listBlocklist, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/discovery/blocklist",
                this::addToBlocklist,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.delete(
                prefix + "/admin/discovery/blocklist/{value}",
                this::removeFromBlocklist,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.FEDERATION);
        routes.get(prefix + "/discovery/stations", this::listCachedStations);
        routes.get(prefix + "/federation/stations/search", this::searchStationPicker, StationPermission.PAGE_EDIT);
    }

    private void getIdentity(Context ctx) {
        ctx.json(discovery.identity());
    }

    private void getSettings(Context ctx) {
        ctx.json(new SettingsResponse(
                settingsService.isEnabled(),
                settingsService.maxDepth(),
                settingsService.pingIntervalMinutes(),
                DiscoverySettingsService.MAX_DEPTH));
    }

    private void updateSettings(Context ctx) {
        var body = ctx.bodyAsClass(SettingsRequest.class);
        if (body.enabled() != null) settingsService.setEnabled(body.enabled());
        if (body.maxDepth() != null) settingsService.setMaxDepth(body.maxDepth());
        if (body.pingIntervalMinutes() != null) settingsService.setPingIntervalMinutes(body.pingIntervalMinutes());
        getSettings(ctx);
    }

    private void listPeers(Context ctx) {
        ctx.json(discovery.peers());
    }

    private void probePeer(Context ctx) {
        ctx.json(discovery.probe(ctx.bodyAsClass(ProbeRequest.class).baseUrl()));
    }

    private void addPeer(Context ctx) {
        ctx.json(discovery.addPeer(ctx.bodyAsClass(AddPeerRequest.class)));
    }

    private void deletePeer(Context ctx) {
        ctx.json(new ChangedResponse(discovery.deletePeer(ctx.pathParam("publicKey"))));
    }

    private void upvotePeer(Context ctx) {
        ctx.json(discovery.upvote(ctx.pathParam("publicKey")));
    }

    private void downvotePeer(Context ctx) {
        ctx.json(discovery.downvote(ctx.pathParam("publicKey")));
    }

    private void blockPeer(Context ctx) {
        ctx.json(discovery.block(ctx.pathParam("publicKey")));
    }

    private void unblockPeer(Context ctx) {
        ctx.json(discovery.unblock(ctx.pathParam("publicKey")));
    }

    private void pingPeerNow(Context ctx) {
        discovery.pingNow(ctx.pathParam("publicKey"));
        ctx.json(new MessageResponse("Ping dispatched"));
    }

    private void discoverNow(Context ctx) {
        ctx.json(discovery.discoverNow());
    }

    private void seedFederation(Context ctx) {
        ctx.json(new ChangedCountResponse(discovery.seedFromFederation()));
    }

    private void listBlocklist(Context ctx) {
        ctx.json(discovery.blocklist());
    }

    private void addToBlocklist(Context ctx) {
        discovery.addToBlocklist(ctx.bodyAsClass(BlocklistRequest.class));
        ctx.json(new MessageResponse("Added to blocklist"));
    }

    private void removeFromBlocklist(Context ctx) {
        ctx.json(new ChangedResponse(discovery.removeFromBlocklist(ctx.pathParam("value"))));
    }

    private void listCachedStations(Context ctx) {
        ctx.json(discoveredStations.cachedStations());
    }

    private void searchStationPicker(Context ctx) {
        var session = UserSession.from(ctx);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        ctx.json(discoveredStations.picker(session.stationId(), ctx.queryParam("q"), limit));
    }

    public record SettingsResponse(boolean enabled, int maxDepth, int pingIntervalMinutes, int hardMaxDepth) {}

    public record SettingsRequest(Boolean enabled, Integer maxDepth, Integer pingIntervalMinutes) {}

    public record ProbeRequest(String baseUrl) {}

    public record ChangedResponse(boolean changed) {}

    public record ChangedCountResponse(int changed) {}

    public record MessageResponse(String message) {}
}

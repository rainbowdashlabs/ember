/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryInfoResponse;
import dev.chojo.ember.feature.discovery.service.DiscoveredStationService;
import dev.chojo.ember.feature.discovery.service.DiscoveredStationService.DiscoveredStationResponse;
import dev.chojo.ember.feature.discovery.service.DiscoveredStationService.StationPickerResult;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.AddPeerRequest;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.BlocklistRequest;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.BlocklistResponse;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.DiscoverNowResponse;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.IdentityResponse;
import dev.chojo.ember.feature.discovery.service.DiscoveryAdminService.PeerResponse;
import dev.chojo.ember.feature.discovery.service.DiscoverySettingsService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
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

    @OpenApi(
            path = "/api/v1/admin/discovery/identity",
            methods = HttpMethod.GET,
            summary = "Get how this instance introduces itself to discovery peers",
            tags = {"Discovery"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = IdentityResponse.class)))
    private void getIdentity(Context ctx) {
        ctx.json(discovery.identity());
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/settings",
            methods = HttpMethod.GET,
            summary = "Get the discovery settings",
            tags = {"Discovery"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = DiscoverySettingsResponse.class)))
    private void getSettings(Context ctx) {
        ctx.json(new DiscoverySettingsResponse(
                settingsService.isEnabled(),
                settingsService.maxDepth(),
                settingsService.pingIntervalMinutes(),
                DiscoverySettingsService.MAX_DEPTH));
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/settings",
            methods = HttpMethod.PUT,
            summary = "Change the discovery settings",
            tags = {"Discovery"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DiscoverySettingsRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = DiscoverySettingsResponse.class)))
    private void updateSettings(Context ctx) {
        var body = ctx.bodyAsClass(DiscoverySettingsRequest.class);
        if (body.enabled() != null) settingsService.setEnabled(body.enabled());
        if (body.maxDepth() != null) settingsService.setMaxDepth(body.maxDepth());
        if (body.pingIntervalMinutes() != null) settingsService.setPingIntervalMinutes(body.pingIntervalMinutes());
        getSettings(ctx);
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/peers",
            methods = HttpMethod.GET,
            summary = "List the discovery peers",
            tags = {"Discovery"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PeerResponse[].class)))
    private void listPeers(Context ctx) {
        ctx.json(discovery.peers());
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/peers/probe",
            methods = HttpMethod.POST,
            summary = "Probe an address for a discovery peer",
            tags = {"Discovery"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProbeRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DiscoveryInfoResponse.class)))
    private void probePeer(Context ctx) {
        ctx.json(discovery.probe(ctx.bodyAsClass(ProbeRequest.class).baseUrl()));
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/peers",
            methods = HttpMethod.POST,
            summary = "Add a discovery peer by hand",
            tags = {"Discovery"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AddPeerRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PeerResponse.class)))
    private void addPeer(Context ctx) {
        ctx.json(discovery.addPeer(ctx.bodyAsClass(AddPeerRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/peers/{publicKey}",
            methods = HttpMethod.DELETE,
            summary = "Remove a discovery peer",
            tags = {"Discovery"},
            pathParams = @OpenApiParam(name = "publicKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ChangedResponse.class)))
    private void deletePeer(Context ctx) {
        ctx.json(new ChangedResponse(discovery.deletePeer(ctx.pathParam("publicKey"))));
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/peers/{publicKey}/upvote",
            methods = HttpMethod.POST,
            summary = "Raise a discovery peer's reputation",
            tags = {"Discovery"},
            pathParams = @OpenApiParam(name = "publicKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PeerResponse.class)))
    private void upvotePeer(Context ctx) {
        ctx.json(discovery.upvote(ctx.pathParam("publicKey")));
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/peers/{publicKey}/downvote",
            methods = HttpMethod.POST,
            summary = "Lower a discovery peer's reputation",
            tags = {"Discovery"},
            pathParams = @OpenApiParam(name = "publicKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PeerResponse.class)))
    private void downvotePeer(Context ctx) {
        ctx.json(discovery.downvote(ctx.pathParam("publicKey")));
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/peers/{publicKey}/block",
            methods = HttpMethod.POST,
            summary = "Block a discovery peer",
            tags = {"Discovery"},
            pathParams = @OpenApiParam(name = "publicKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PeerResponse.class)))
    private void blockPeer(Context ctx) {
        ctx.json(discovery.block(ctx.pathParam("publicKey")));
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/peers/{publicKey}/unblock",
            methods = HttpMethod.POST,
            summary = "Unblock a discovery peer",
            tags = {"Discovery"},
            pathParams = @OpenApiParam(name = "publicKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PeerResponse.class)))
    private void unblockPeer(Context ctx) {
        ctx.json(discovery.unblock(ctx.pathParam("publicKey")));
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/peers/{publicKey}/ping",
            methods = HttpMethod.POST,
            summary = "Ping a discovery peer now",
            tags = {"Discovery"},
            pathParams = @OpenApiParam(name = "publicKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void pingPeerNow(Context ctx) {
        discovery.pingNow(ctx.pathParam("publicKey"));
        ctx.json(new MessageResponse("Ping dispatched"));
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/discover-now",
            methods = HttpMethod.POST,
            summary = "Ping every peer and fetch every station card now",
            tags = {"Discovery"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DiscoverNowResponse.class)))
    private void discoverNow(Context ctx) {
        ctx.json(discovery.discoverNow());
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/seed",
            methods = HttpMethod.POST,
            summary = "Add the federation partners as discovery peers",
            tags = {"Discovery"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ChangedCountResponse.class)))
    private void seedFederation(Context ctx) {
        ctx.json(new ChangedCountResponse(discovery.seedFromFederation()));
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/blocklist",
            methods = HttpMethod.GET,
            summary = "List the discovery blocklist",
            tags = {"Discovery"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BlocklistResponse[].class)))
    private void listBlocklist(Context ctx) {
        ctx.json(discovery.blocklist());
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/blocklist",
            methods = HttpMethod.POST,
            summary = "Add an entry to the discovery blocklist",
            tags = {"Discovery"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BlocklistRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void addToBlocklist(Context ctx) {
        discovery.addToBlocklist(ctx.bodyAsClass(BlocklistRequest.class));
        ctx.json(new MessageResponse("Added to blocklist"));
    }

    @OpenApi(
            path = "/api/v1/admin/discovery/blocklist/{value}",
            methods = HttpMethod.DELETE,
            summary = "Remove an entry from the discovery blocklist",
            tags = {"Discovery"},
            pathParams = @OpenApiParam(name = "value", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ChangedResponse.class)))
    private void removeFromBlocklist(Context ctx) {
        ctx.json(new ChangedResponse(discovery.removeFromBlocklist(ctx.pathParam("value"))));
    }

    @OpenApi(
            path = "/api/v1/discovery/stations",
            methods = HttpMethod.GET,
            summary = "List the stations the discovery peers publish",
            tags = {"Discovery"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = DiscoveredStationResponse[].class)))
    private void listCachedStations(Context ctx) {
        ctx.json(discoveredStations.cachedStations());
    }

    @OpenApi(
            path = "/api/v1/federation/stations/search",
            methods = HttpMethod.GET,
            summary = "Search the stations a page may name as partners",
            tags = {"Discovery"},
            queryParams = {
                @OpenApiParam(name = "q", type = String.class),
                @OpenApiParam(name = "limit", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationPickerResult[].class)))
    private void searchStationPicker(Context ctx) {
        var session = StationSession.from(ctx);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        ctx.json(discoveredStations.picker(session.stationId(), ctx.queryParam("q"), limit));
    }

    /**
     * The discovery settings as they stand, with the deepest the neighbourhood may ever be searched.
     *
     * @param enabled             whether this instance takes part in discovery
     * @param maxDepth            how many hops a ping travels
     * @param pingIntervalMinutes how often the peers are pinged
     * @param hardMaxDepth        the highest depth that may be set
     */
    public record DiscoverySettingsResponse(boolean enabled, int maxDepth, int pingIntervalMinutes, int hardMaxDepth) {}

    /**
     * A change to the discovery settings; a setting left out stays as it is.
     *
     * @param enabled             whether this instance takes part in discovery
     * @param maxDepth            how many hops a ping travels
     * @param pingIntervalMinutes how often the peers are pinged
     */
    public record DiscoverySettingsRequest(Boolean enabled, Integer maxDepth, Integer pingIntervalMinutes) {}

    public record ProbeRequest(String baseUrl) {}

    public record ChangedResponse(boolean changed) {}

    public record ChangedCountResponse(int changed) {}
}

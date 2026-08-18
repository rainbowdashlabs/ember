/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryCallbackMessage;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryInfoResponse;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryPingMessage;
import dev.chojo.ember.feature.discovery.protocol.DiscoveryStationsResponse;
import dev.chojo.ember.feature.discovery.service.DiscoveryKeyService;
import dev.chojo.ember.feature.discovery.service.DiscoveryPingService;
import dev.chojo.ember.feature.discovery.service.DiscoverySettingsService;
import dev.chojo.ember.feature.discovery.service.DiscoverySigningService;
import dev.chojo.ember.feature.discovery.service.DiscoveryStationProjectionService;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Public, anonymous-internet endpoints exposed by every Ember instance.
 *
 * <ul>
 *   <li>{@code GET /public/discovery/info} - cheap metadata probe (§6.0).</li>
 *   <li>{@code GET /public/discovery/stations} - {@code PUBLIC}-scoped station cards (§6.1).</li>
 *   <li>{@code POST /discovery/ping} - receive a signed ping; answers 204 and dispatches the
 *       callback asynchronously.</li>
 *   <li>{@code POST /discovery/peers} - receive a signed callback for one of our outbound
 *       pings.</li>
 * </ul>
 */
@Singleton
public class PublicDiscoveryRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(PublicDiscoveryRoutes.class);

    private final DiscoveryKeyService keyService;
    private final DiscoveryPingService pingService;
    private final DiscoverySettingsService settingsService;
    private final DiscoveryStationProjectionService projectionService;

    private final ScheduledExecutorService inboundExecutor = Executors.newScheduledThreadPool(2, r -> {
        var t = new Thread(r, "discovery-inbound");
        t.setDaemon(true);
        return t;
    });

    @Inject
    public PublicDiscoveryRoutes(
            DiscoveryKeyService keyService,
            DiscoveryPingService pingService,
            DiscoverySettingsService settingsService,
            DiscoverySigningService signingService,
            DiscoveryStationProjectionService projectionService) {
        this.keyService = keyService;
        this.pingService = pingService;
        this.settingsService = settingsService;
        this.projectionService = projectionService;
        // signingService is constructor-injected so it gets eagerly bound, even though the
        // ping service is the actual user.
        @SuppressWarnings("unused")
        var ignored = signingService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/discovery/info", this::getInfo);
        routes.get(prefix + "/public/discovery/stations", this::getStations);
        routes.post(prefix + "/discovery/ping", this::receivePing);
        routes.post(prefix + "/discovery/peers", this::receiveCallback);
    }

    private void getInfo(Context ctx) {
        var response = new DiscoveryInfoResponse(
                pingService.selfBaseUrl(),
                keyService.instanceId(),
                keyService.publicKeyBase64(),
                FederationContractVersions.current().core(),
                settingsService.isEnabled());
        ctx.header("Cache-Control", "public, max-age=60");
        ctx.json(response);
    }

    private void getStations(Context ctx) {
        if (!settingsService.isEnabled()) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE);
            return;
        }
        var cards = projectionService.publicCards();
        var response = new DiscoveryStationsResponse(pingService.selfIdentity(), cards);
        ctx.header("Cache-Control", "public, max-age=300");
        ctx.json(response);
    }

    private void receivePing(Context ctx) {
        String body = ctx.body();
        String signature = ctx.header(DiscoverySigningService.SIGNATURE_HEADER);
        DiscoveryPingMessage message;
        try {
            message = ctx.bodyAsClass(DiscoveryPingMessage.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }
        // The service validates the signature, drift, replay, then dispatches the callback
        // asynchronously. Either way, we answer 204 - never block the peer waiting for our
        // peer-list compilation.
        inboundExecutor.execute(() -> {
            try {
                pingService.handleInboundPing(body, message, signature);
            } catch (Exception e) {
                log.debug("Inbound ping handling failed: {}", e.getMessage());
            }
        });
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void receiveCallback(Context ctx) {
        String body = ctx.body();
        String signature = ctx.header(DiscoverySigningService.SIGNATURE_HEADER);
        DiscoveryCallbackMessage message;
        try {
            message = ctx.bodyAsClass(DiscoveryCallbackMessage.class);
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }
        // Callbacks are processed synchronously: they only update local state, no further
        // network calls fan out.
        boolean accepted = pingService.handleCallback(body, message, signature);
        ctx.status(accepted ? HttpStatus.NO_CONTENT : HttpStatus.BAD_REQUEST);
    }
}

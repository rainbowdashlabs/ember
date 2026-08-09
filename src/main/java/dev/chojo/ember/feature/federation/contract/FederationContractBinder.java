/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.contract;

import dev.chojo.ember.api.FederationHeaders;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Registers a route class's declared federation contract with the Javalin router. Every
 * handler is wrapped in the contract compatibility check: a request whose core hash — or,
 * for feature surfaces, whose surface hash — differs from this build's is rejected with a
 * machine-readable {@code 409} before the handler runs. Endpoints marked
 * {@linkplain FederationEndpoint#versionExempt() version-exempt} skip the check so
 * diverged instances can still exchange their vectors.
 */
public final class FederationContractBinder {

    public static final String CORE_MISMATCH = "federation-core-mismatch";
    public static final String FEATURE_MISMATCH = "federation-feature-mismatch";

    private final JavalinDefaultRoutingApi routes;
    private final String prefix;
    private final List<FederationEndpoint> contract;
    private final List<FederationEndpoint> bound = new ArrayList<>();

    private FederationContractBinder(
            JavalinDefaultRoutingApi routes, String prefix, List<FederationEndpoint> contract) {
        this.routes = routes;
        this.prefix = prefix;
        this.contract = contract;
    }

    /**
     * Binds handlers for the given contract. The binding consumer must handle every
     * endpoint of the contract exactly once and in contract order; anything else is a
     * startup failure. That makes the {@code CONTRACT} list the authoritative registration
     * order, so a declared endpoint can neither silently lack a handler nor bypass the
     * contract, and tooling can read the router order straight from the list.
     */
    public static void register(
            JavalinDefaultRoutingApi routes,
            String prefix,
            List<FederationEndpoint> contract,
            Consumer<FederationContractBinder> bindings) {
        var binder = new FederationContractBinder(routes, prefix, contract);
        bindings.accept(binder);
        if (!binder.bound.equals(contract)) {
            throw new IllegalStateException("Handlers must be bound exactly once each, in contract order");
        }
    }

    public FederationContractBinder handle(FederationEndpoint endpoint, Handler handler) {
        bound.add(endpoint);
        routes.addHttpHandler(endpoint.method(), prefix + endpoint.path(), wrap(endpoint, handler));
        return this;
    }

    private static Handler wrap(FederationEndpoint endpoint, Handler handler) {
        if (endpoint.versionExempt()) return handler;
        return ctx -> {
            var local = FederationContractVersions.current();
            String remoteCore = ctx.header(FederationHeaders.HEADER_CORE);
            if (!local.core().equals(remoteCore)) {
                ctx.status(HttpStatus.CONFLICT)
                        .json(new MismatchResponse(CORE_MISMATCH, null, local.core(), remoteCore));
                return;
            }
            if (endpoint.surface() != FederationSurface.CORE) {
                String localSurface = local.featureHash(endpoint.surface().capability());
                String remoteSurface = ctx.header(FederationHeaders.HEADER_SURFACE);
                if (!localSurface.equals(remoteSurface)) {
                    ctx.status(HttpStatus.CONFLICT)
                            .json(new MismatchResponse(
                                    FEATURE_MISMATCH, endpoint.surface().name(), localSurface, remoteSurface));
                    return;
                }
            }
            handler.handle(ctx);
        };
    }

    /**
     * Machine-readable rejection body for contract mismatches, mirrored by the client side
     * to trigger a vector refresh for the partner.
     *
     * @param error   {@link #CORE_MISMATCH} or {@link #FEATURE_MISMATCH}
     * @param surface the mismatching feature surface, {@code null} for core mismatches
     * @param local   the hash of the instance answering the request
     * @param remote  the hash the caller presented, {@code null} when it sent none
     */
    public record MismatchResponse(String error, String surface, String local, String remote) {}
}

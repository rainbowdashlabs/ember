/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.contract;

import dev.chojo.ember.api.FederationHeaders;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationHandler;
import dev.chojo.ember.feature.federation.transport.PathParams;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Registers a route class's declared federation contract with the Javalin router. Every
 * handler is wrapped in the contract compatibility check: a request whose core hash - or,
 * for feature surfaces, whose surface hash - differs from this build's is rejected with a
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
    private final FederationEndpoints served;
    private final List<FederationEndpoint> bound = new ArrayList<>();

    private FederationContractBinder(
            JavalinDefaultRoutingApi routes,
            String prefix,
            List<FederationEndpoint> contract,
            FederationEndpoints served) {
        this.routes = routes;
        this.prefix = prefix;
        this.contract = contract;
        this.served = served;
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
        register(routes, prefix, contract, null, bindings);
    }

    /**
     * {@link #register(JavalinDefaultRoutingApi, String, List, Consumer)} for a route class whose
     * endpoints answer from their serving functions, which {@link #serve} binds.
     */
    public static void register(
            JavalinDefaultRoutingApi routes,
            String prefix,
            List<FederationEndpoint> contract,
            FederationEndpoints served,
            Consumer<FederationContractBinder> bindings) {
        var binder = new FederationContractBinder(routes, prefix, contract, served);
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

    /**
     * Binds an endpoint to its registered serving function: reads the verified partner, the
     * parameters and the body, calls the function and writes its answer with {@code 200}, or
     * {@code 204} when it answers nothing.
     */
    public FederationContractBinder serve(FederationEndpoint endpoint) {
        return handle(endpoint, adapter(endpoint, HttpStatus.OK));
    }

    /**
     * {@link #serve} for an endpoint that creates something and answers {@code 201}.
     */
    public FederationContractBinder serveCreated(FederationEndpoint endpoint) {
        return handle(endpoint, adapter(endpoint, HttpStatus.CREATED));
    }

    private Handler adapter(FederationEndpoint endpoint, HttpStatus status) {
        if (served == null) throw new IllegalStateException("No serving functions given for " + endpoint.path());
        FederationHandler<Object, Object> handler = served.handlerFor(endpoint);
        return ctx -> {
            FederationSession.requirePartner(ctx);
            Object body = endpoint.requestType() == Void.class ? null : ctx.bodyAsClass(endpoint.requestType());
            Object answer =
                    handler.serve(ServingPartner.of(FederationSession.from(ctx)), PathParams.of(ctx, endpoint), body);
            if (answer == null) {
                ctx.status(HttpStatus.NO_CONTENT);
            } else {
                ctx.status(status).json(answer);
            }
        };
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

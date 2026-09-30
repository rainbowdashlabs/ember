/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.contract;

import dev.chojo.ember.api.FederationHeaders;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederationContractBinderTest {

    record Body(String text) {}

    record Answer(String text) {}

    private static final FederationEndpoint READ =
            FederationEndpoint.get(FederationSurface.NEWS_SHARE, "/remote/binder/{id}", Answer.class);
    private static final FederationEndpoint CREATE =
            FederationEndpoint.post(FederationSurface.NEWS_SHARE, "/remote/binder", Body.class, Answer.class);
    private static final FederationEndpoint REMOVE =
            FederationEndpoint.delete(FederationSurface.NEWS_SHARE, "/remote/binder/{id}", Void.class, Void.class);

    private final AtomicReference<ServingPartner> served = new AtomicReference<>();

    private FederationEndpoints registry() {
        return new FederationEndpoints(Set.of(endpoints -> {
            endpoints.serve(READ, (partner, params, body) -> {
                served.set(partner);
                return new Answer("read " + params.integer("id"));
            });
            endpoints.serve(CREATE, (partner, params, body) -> new Answer(((Body) body).text()));
            endpoints.serve(REMOVE, (partner, params, body) -> null);
        }));
    }

    private Map<FederationEndpoint, Handler> bind(FederationEndpoints registry) {
        var routes = mock(JavalinDefaultRoutingApi.class);
        FederationContractBinder.register(
                routes, "/api/v1", List.of(READ, CREATE, REMOVE), registry, binder -> binder.serve(READ)
                        .serveCreated(CREATE)
                        .serve(REMOVE));
        var handlers = ArgumentCaptor.forClass(Handler.class);
        verify(routes).addHttpHandler(eq(HandlerType.GET), eq("/api/v1/remote/binder/{id}"), handlers.capture());
        verify(routes).addHttpHandler(eq(HandlerType.POST), eq("/api/v1/remote/binder"), handlers.capture());
        verify(routes).addHttpHandler(eq(HandlerType.DELETE), eq("/api/v1/remote/binder/{id}"), handlers.capture());
        var captured = handlers.getAllValues();
        return Map.of(READ, captured.get(0), CREATE, captured.get(1), REMOVE, captured.get(2));
    }

    private static Context signedRequest(FederationSession session) {
        var ctx = mock(Context.class);
        var contract = FederationContractVersions.current();
        when(ctx.header(FederationHeaders.HEADER_CORE)).thenReturn(contract.core());
        when(ctx.header(FederationHeaders.HEADER_SURFACE)).thenReturn(contract.featureHash(CapabilityType.NEWS_SHARE));
        when(ctx.attribute(FederationSession.ATTR_FEDERATION_SESSION)).thenReturn(session);
        when(ctx.status(any(HttpStatus.class))).thenReturn(ctx);
        when(ctx.queryParamMap()).thenReturn(Map.of());
        return ctx;
    }

    private static FederationSession session(UUID asking) {
        var row = new FederationPartner(
                7,
                3,
                asking,
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now(),
                "https://asking.example",
                "Asking");
        return new FederationSession(row, asking);
    }

    @Test
    void aServedEndpointAnswersFromItsServingFunction() throws Exception {
        var handlers = bind(registry());
        UUID asking = UUID.randomUUID();

        var read = signedRequest(session(asking));
        when(read.pathParam("id")).thenReturn("12");
        handlers.get(READ).handle(read);
        verify(read).status(HttpStatus.OK);
        verify(read).json(new Answer("read 12"));
        assertEquals(7, served.get().partnerId());
        assertEquals(3, served.get().servingStationId());
        assertEquals(asking, served.get().askingStationUid());

        var create = signedRequest(session(asking));
        when(create.bodyAsClass(Body.class)).thenReturn(new Body("made"));
        handlers.get(CREATE).handle(create);
        verify(create).status(HttpStatus.CREATED);
        verify(create).json(new Answer("made"));

        var remove = signedRequest(session(asking));
        when(remove.pathParam("id")).thenReturn("12");
        handlers.get(REMOVE).handle(remove);
        verify(remove).status(HttpStatus.NO_CONTENT);
        verify(remove, never()).json(any());
    }

    @Test
    void anUnsignedRequestIsRefusedBeforeTheServingFunction() {
        var handlers = bind(registry());
        var unsigned = signedRequest(null);
        assertThrows(ForbiddenResponse.class, () -> handlers.get(READ).handle(unsigned));
    }

    @Test
    void servingWithoutARegistryIsAStartupFailure() {
        var routes = mock(JavalinDefaultRoutingApi.class);
        assertThrows(
                IllegalStateException.class,
                () -> FederationContractBinder.register(routes, "", List.of(READ), binder -> binder.serve(READ)));
    }
}

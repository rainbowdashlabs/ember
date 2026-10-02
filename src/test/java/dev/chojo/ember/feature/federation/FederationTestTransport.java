/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation;

import dev.chojo.ember.api.PublicIdModule;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationWebhookService;
import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationHandler;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.HttpFederationTransport;
import dev.chojo.ember.feature.federation.transport.LocalFederationTransport;
import dev.chojo.ember.feature.federation.transport.PathParams;
import dev.chojo.ember.feature.federation.transport.RoutingFederationTransport;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
import io.javalin.http.HandlerType;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The federation transport as the application wires it, for tests: a partner on this instance is
 * answered through the serving functions of the features handed to {@link #serve}, a partner on
 * another instance through the given (usually mocked) HTTP client.
 *
 * <p>{@link #assertParity} is the local-versus-HTTP guarantee without two JVMs: it answers one
 * request through the local transport and through the parameters the {@code /remote} route adapter
 * reads, writes both with the API's mapper, reads both back as the asking side does and requires
 * the same result. A write is carried out twice by it.
 */
public final class FederationTestTransport {
    private static final JsonMapper WIRE_READER = OutboundHttp.lenientMapper(PublicIdModule.forPartnerResponses());

    private final AtomicReference<FederationEndpoints> endpoints = new AtomicReference<>();
    private final LocalFederationTransport local;
    private final FederationTransport transport;
    private final JsonMapper apiMapper;

    public FederationTestTransport(
            FederationHttpClient httpClient, FederationRepository federationRepo, StationRepository stationRepo) {
        this.local = new LocalFederationTransport(endpoints::get, federationRepo, stationRepo);
        this.transport = new RoutingFederationTransport(
                new HttpFederationTransport(
                        httpClient, new FederationWebhookService(federationRepo, httpClient, Runnable::run, List.of())),
                local);
        this.apiMapper = JsonMapper.builder()
                .addModule(PublicIdModule.forApi(stationRepo, new ClusterRepository()))
                .build();
    }

    /** The transport to hand the services under test. */
    public FederationTransport transport() {
        return transport;
    }

    /**
     * Registers the serving functions of the given features, as the application does at start.
     *
     * @param servers the features answering federation requests
     * @return the registry
     */
    public FederationEndpoints serve(FederationServer... servers) {
        var registry = new FederationEndpoints(Set.copyOf(Arrays.asList(servers)));
        endpoints.set(registry);
        return registry;
    }

    /**
     * Answers one request both ways and requires the same JSON from each.
     *
     * @param asking  the asking station's partner row, for a partner on this instance
     * @param request the request
     * @param body    the request body, {@code null} for none
     * @param type    the type the asking side reads the answer as
     */
    public void assertParity(FederationPartner asking, FederationRequest request, Object body, Class<?> type) {
        var endpoint = request.endpoint();
        FederationHandler<Object, Object> handler = endpoints.get().handlerFor(endpoint);
        Object overTheWire =
                handler.serve(local.servingSide(asking), PathParams.of(routedContext(request), endpoint), body);
        Object locally = answerHere(asking, request, body, type);
        assertEquals(
                asRead(overTheWire, type, endpoint.listResponse()),
                asRead(locally, type, endpoint.listResponse()),
                () -> request.path() + " answers differently on this instance");
    }

    private Object asRead(Object answer, Class<?> type, boolean list) {
        String json = apiMapper.writeValueAsString(answer);
        if (!list) return WIRE_READER.readValue(json, type);
        return WIRE_READER.readValue(json, WIRE_READER.getTypeFactory().constructCollectionType(List.class, type));
    }

    private Object answerHere(FederationPartner asking, FederationRequest request, Object body, Class<?> type) {
        boolean read = request.endpoint().method() == HandlerType.GET;
        if (request.endpoint().listResponse()) {
            return read ? transport.getList(asking, request, type) : transport.sendList(asking, request, body, type);
        }
        return read ? transport.get(asking, request, type) : transport.send(asking, request, body, type);
    }

    private static Context routedContext(FederationRequest request) {
        var ctx = mock(Context.class);
        String full = request.path();
        int separator = full.indexOf('?');
        String[] template = request.endpoint().path().split("/");
        String[] concrete = (separator < 0 ? full : full.substring(0, separator)).split("/");
        for (int i = 0; i < template.length; i++) {
            if (template[i].startsWith("{")) {
                when(ctx.pathParam(template[i].substring(1, template[i].length() - 1)))
                        .thenReturn(concrete[i]);
            }
        }
        Map<String, List<String>> query = new HashMap<>();
        PathParams.of(request).query().forEach((key, value) -> query.put(key, List.of(value)));
        when(ctx.queryParamMap()).thenReturn(query);
        return ctx;
    }
}

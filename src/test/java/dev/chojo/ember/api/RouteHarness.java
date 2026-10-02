/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.AccessGate;
import dev.chojo.ember.api.auth.CsrfGuard;
import dev.chojo.ember.api.auth.SessionCookies;
import dev.chojo.ember.api.auth.SessionGate;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.insights.service.BotClassifier;
import dev.chojo.ember.feature.insights.service.PageHitRecorder;
import dev.chojo.ember.feature.insights.service.RefererDomainExtractor;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.service.ApiRequestLogger;
import dev.chojo.ember.feature.system.service.DemoService;
import dev.chojo.ember.feature.traffic.service.AuthBucketClassifier;
import dev.chojo.ember.feature.traffic.service.StationResolver;
import dev.chojo.ember.feature.traffic.service.StationTrafficRecorder;
import dev.chojo.ember.util.DevErrorWriter;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Request;
import io.javalin.testtools.Response;
import io.javalin.testtools.TestCase;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Serves route groups over real HTTP, through the same application production runs.
 *
 * <p>The application is built by {@link ApiServer#create()}, so a request passes the access gate,
 * the exception mapping, the JSON mapper and every after-handler exactly as it would in production.
 * Only the route groups under test are registered, and everything the server itself leans on is a
 * test double: nothing is written anywhere by answering a request.
 *
 * <p>Signing in is a token handed out by {@link #as(UserSession)}. The access gate resolves it
 * through a stand-in for the session store, so a test decides who is asking by building the
 * {@link UserSession} it wants, permissions included, and the gate then refuses or admits it on the
 * roles the route declares.
 */
public final class RouteHarness {

    /** What every route group is registered under. */
    public static final String PREFIX = "/api/v1";

    private static final JsonMapper READER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final Routes[] routes;
    private final Map<String, UserSession> signedIn = new ConcurrentHashMap<>();
    private final AccessManager accessManager = mock(AccessManager.class);
    private StationRepository stations = mock(StationRepository.class);
    private Network network = new Network();

    private RouteHarness(Routes[] routes) {
        this.routes = routes;
        when(accessManager.resolveUserSession(anyString(), any(), any()))
                .thenAnswer(call -> Optional.ofNullable(signedIn.get(call.<String>getArgument(0))));
        when(accessManager.resolveUserSession(anyString(), any()))
                .thenAnswer(call -> Optional.ofNullable(signedIn.get(call.<String>getArgument(0))));
    }

    /**
     * A harness serving the given route groups, in the order given.
     *
     * @param routes the route groups under test, built with whatever doubles the test needs
     * @return the harness
     */
    public static RouteHarness serving(Routes... routes) {
        return new RouteHarness(routes);
    }

    /**
     * Uses the given station store for everything the server itself asks about stations: the
     * station header and the station ids the JSON mapper turns into addresses. Without it those
     * lookups find nothing.
     *
     * @param stations the station store, typically the test database's
     * @return this harness
     */
    public RouteHarness withStations(StationRepository stations) {
        this.stations = stations;
        return this;
    }

    /**
     * Uses the given network settings, for a test that needs the client address to come from a
     * forwarded-for header: every request of a test arrives from the loopback address.
     *
     * @param network the network settings
     * @return this harness
     */
    public RouteHarness withNetwork(Network network) {
        this.network = network;
        return this;
    }

    /**
     * Takes every request that names a federation station as signed by the given partner, the way
     * the access gate admits a request whose signature it verified.
     *
     * @param partner the partnership the requests arrive on
     * @return what to pass to a client call to ask as that partner, with the contract this instance speaks
     */
    public Consumer<Request.Builder> asPartner(FederationSession partner) {
        when(accessManager.resolveFederationSession(any())).thenReturn(Optional.of(partner));
        return request -> request.header(
                        FederationHeaders.HEADER_STATION_ID,
                        partner.partnerStationUid().toString())
                .header(
                        FederationHeaders.HEADER_CORE,
                        FederationContractVersions.current().core());
    }

    /**
     * Signs the given session in and answers the request decoration that sends its token.
     *
     * @param session who is asking, with the permissions they hold
     * @return what to pass to a client call to ask as them
     */
    public Consumer<Request.Builder> as(UserSession session) {
        String token = UUID.randomUUID().toString();
        signedIn.put(token, session);
        return request -> request.header("Cookie", SessionCookies.SESSION_COOKIE + "=" + token);
    }

    /**
     * Starts a fresh application on a free port, runs the test case against it and stops it again.
     *
     * @param test the requests and assertions
     */
    public void run(TestCase test) {
        JavalinTest.test(server().create(), test);
    }

    /**
     * Starts a fresh application, sends the one request and answers what came back.
     *
     * @param request the request, sent with the client of the running application
     * @return the response
     */
    public Response request(Function<HttpClient, Response> request) {
        var answered = new AtomicReference<Response>();
        run((server, client) -> answered.set(request.apply(client)));
        return answered.get();
    }

    /**
     * A request body written out the way a client writes it, to hand to a client call.
     *
     * <p>A request record handed to the client is written by the server's own mapper, which also
     * writes what the record merely derives, such as an {@code isEmpty()}. The server then refuses
     * its own output as naming a field it does not know, which no real client ever sends.
     *
     * @param json the body
     * @return the body as a tree, which the client writes back unchanged
     */
    public static JsonNode body(String json) {
        return READER.readTree(json);
    }

    /**
     * The body of a response, read as JSON.
     *
     * @param response the response
     * @return its body as a tree
     */
    public static JsonNode json(Response response) {
        return READER.readTree(response.body().string());
    }

    /**
     * The body of a successful response, read as the given type.
     *
     * @param response the response, which must carry a {@code 2xx} status
     * @param type     what the body holds
     * @param <T>      the type
     * @return the body
     */
    public static <T> T read(Response response, Class<T> type) {
        if (response.code() >= 300) {
            fail("Expected a successful answer, got %d: %s"
                    .formatted(response.code(), response.body().string()));
        }
        return READER.readValue(response.body().string(), type);
    }

    /**
     * A response header.
     *
     * @param response the response
     * @param name     the header
     * @return its first value, or null where the response carries none
     */
    public static String header(Response response, String name) {
        var values = response.headers().get(name);
        return values == null ? null : values.getFirst();
    }

    /**
     * The named refusal a response answered with, checked against the status it stands for.
     *
     * @param response the response
     * @return the refusal its code names
     */
    public static Refusal refusalOf(Response response) {
        String code = json(response).path("code").asString(null);
        Refusal refusal = Refusal.all().stream()
                .filter(candidate -> candidate.code().equals(code))
                .findFirst()
                .orElseGet(() -> fail("No named refusal in %d: %s"
                        .formatted(response.code(), response.body().string())));
        assertEquals(refusal.status().getCode(), response.code(), "status of " + refusal);
        return refusal;
    }

    private ApiServer server() {
        Demo demo = new Demo();
        ClusterRepository clusters = mock(ClusterRepository.class);
        DevErrorWriter devErrors = mock(DevErrorWriter.class);
        return new ApiServer(
                new LinkedHashSet<>(Arrays.asList(routes)),
                new Api(),
                demo,
                stations,
                clusters,
                mock(ApiRequestLogger.class),
                mock(StationTrafficRecorder.class),
                mock(StationResolver.class),
                mock(AuthBucketClassifier.class),
                mock(PageHitRecorder.class),
                mock(RefererDomainExtractor.class),
                mock(BotClassifier.class),
                network,
                new GlobalRateLimiter(),
                new AccessGate(
                        accessManager,
                        sessionGate(),
                        mock(StepUpGuard.class),
                        stations,
                        clusters,
                        demo,
                        mock(DemoService.class)),
                new ResponseHeaderPolicy(demo, stations),
                new ExceptionMapping(demo, devErrors),
                devErrors);
    }

    /**
     * The session gate of the real server, with the page token taken as given: what a route test
     * judges is the route and its access rule, and the page token has tests of its own.
     */
    private SessionGate sessionGate() {
        CsrfGuard csrf = mock(CsrfGuard.class);
        when(csrf.permits(any(), anyString())).thenReturn(true);
        return new SessionGate(
                accessManager, mock(AccountRepository.class), new Auth(), mock(SessionCookies.class), csrf);
    }
}

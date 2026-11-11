/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.refusal.BodyRefusal;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.util.DevErrorWriter;
import dev.chojo.ember.util.ProblemKey;
import io.javalin.Javalin;
import io.javalin.config.RoutesConfig;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Every exception answers with a status that says whose problem it is and a sentence that says what
 * it was, judged on an application that installs nothing but the mapping.
 */
class ExceptionMappingTest {
    private static final String PATH = "/api/v1/thing";

    private static final JsonMapper STRICT = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** What a body is read into, so a body that does not fit has something to miss. */
    record Thing(String name) {}

    private static Response answer(Handler handler) {
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(ApiJsonMapper.forApi(mock(StationRepository.class), mock(ClusterRepository.class)));
            new ExceptionMapping(new Demo(), mock(DevErrorWriter.class)).install(config.routes);
            config.routes.get(PATH, handler);
        });
        var answered = new AtomicReference<Response>();
        JavalinTest.test(app, (server, client) -> answered.set(client.get(PATH)));
        return answered.get();
    }

    private static Response reading(String body) {
        return answer(ctx -> STRICT.readValue(body, Thing.class));
    }

    @Test
    void aStepUpAsksForTheProofsTheAccountCanGive() {
        var response = answer(ctx -> {
            throw new StepUpRequiredException(StepUpCategory.ACCOUNT_SECURITY, Set.of(StepUpProof.TOTP));
        });

        assertEquals(401, response.code());
        assertEquals("ACCOUNT_SECURITY", header(response, "X-StepUp-Required"));
    }

    @Test
    void aNamedRefusalAnswersWithItsCodeAndStatus() {
        var response = answer(ctx -> {
            throw BodyRefusal.INPUT_NOT_USABLE.raise();
        });

        assertEquals(BodyRefusal.INPUT_NOT_USABLE, refusalOf(response));
    }

    @Test
    void aRateLimitSaysWhenToTryAgain() {
        var response = answer(ctx -> RateLimits.enforce(GeneralRefusal.REQUESTS_TOO_OFTEN, Optional.of(30L)));

        assertEquals(429, response.code());
        assertEquals("30", header(response, "Retry-After"));
        var body = json(response);
        assertEquals(30, body.path("retryAfterSeconds").asInt());
        assertEquals(GeneralRefusal.REQUESTS_TOO_OFTEN.code(), body.path("code").asString());
    }

    @Test
    void aMissIsAnsweredWithItsOwnSentence() {
        var response = answer(ctx -> {
            throw new NotFoundResponse("No such thing");
        });

        assertEquals(404, response.code());
        assertEquals("No such thing", json(response).path("message").asString());
    }

    @Test
    void anUnusableInputPassesOnAReadableReason() {
        var response = answer(ctx -> {
            throw new IllegalArgumentException("A rating runs from one to five");
        });

        assertEquals(400, response.code());
        assertEquals(
                BodyRefusal.INPUT_NOT_USABLE.code(), json(response).path("code").asString());
    }

    @Test
    void aStorageMoveThatCannotBeMadeIsARefusalWithItsReason() {
        var response = answer(ctx -> {
            throw new MigrationException("The target storage is full");
        });

        assertEquals(400, response.code());
        assertEquals(
                "The target storage is full", json(response).path("message").asString());
    }

    @Test
    void aBodyThatIsNotJsonIsNamedAsSuch() {
        assertEquals(BodyRefusal.BODY_NOT_JSON, refusalOf(reading("{not json")));
    }

    @Test
    void aFieldTheEndpointDoesNotTakeIsNamed() {
        var response = reading("{\"name\":\"a\",\"colour\":\"red\"}");

        var body = json(response);
        assertEquals(BodyRefusal.BODY_UNEXPECTED_FIELD.code(), body.path("code").asString());
        assertTrue(body.path("message").asString().endsWith(": colour"), body.toString());
        assertEquals("TEXT", body.path("detail").path("kind").asString());
        assertEquals("colour", body.path("detail").path("text").asString());
    }

    @Test
    void aRefusalCarriesTheValueItWasAboutBesideItsSentence() {
        var response = answer(ctx -> {
            throw ClusterRefusal.CLUSTER_QUOTA_GRANT_MORE_THAN_POOL.raise(RefusalDetail.room(512, 2048));
        });

        assertEquals(400, response.code());
        var detail = json(response).path("detail");
        assertEquals("ROOM", detail.path("kind").asString());
        assertEquals(512, detail.path("freeBytes").asLong());
        assertEquals(2048, detail.path("totalBytes").asLong());
    }

    @Test
    void aRefusalWithoutAValueSendsNoDetail() {
        var response = answer(ctx -> {
            throw BodyRefusal.INPUT_NOT_USABLE.raise();
        });

        assertFalse(json(response).has("detail"));
    }

    @Test
    void aFaultKeepsItsInternalsAndHandsOutAReference() {
        var response = answer(ctx -> {
            throw new IllegalStateException("connection to db-internal-7 refused");
        });

        var body = json(response);
        assertEquals(500, response.code());
        assertFalse(body.toString().contains("db-internal-7"), body.toString());
        assertFalse(body.path("reference").asString("").isBlank(), "the reference finds the log line");
    }

    private static List<ILoggingEvent> logged(String route, String asked) {
        return logged(route, asked, ctx -> {});
    }

    private static List<ILoggingEvent> logged(String route, String asked, Handler beforeMatched) {
        return logged(
                routes -> {
                    routes.beforeMatched(beforeMatched);
                    routes.get(route, ctx -> {
                        throw new NotFoundResponse("No such thing");
                    });
                },
                asked);
    }

    private static List<ILoggingEvent> logged(String route, Handler handler, String... asked) {
        return logged(routes -> routes.get(route, handler), asked);
    }

    private static List<ILoggingEvent> logged(Consumer<RoutesConfig> setup, String... asked) {
        Logger logger = (Logger) LoggerFactory.getLogger(ExceptionMapping.class);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        try {
            Javalin app = Javalin.create(config -> {
                config.jsonMapper(ApiJsonMapper.forApi(mock(StationRepository.class), mock(ClusterRepository.class)));
                new ExceptionMapping(new Demo(), mock(DevErrorWriter.class)).install(config.routes);
                setup.accept(config.routes);
            });
            JavalinTest.test(app, (server, client) -> {
                for (String address : asked) client.get(address);
            });
            return events.list.stream()
                    .filter(event -> !event.getFormattedMessage().contains("/clear-cookies"))
                    .toList();
        } finally {
            logger.detachAppender(events);
        }
    }

    private static List<ILoggingEvent> refusals(List<ILoggingEvent> events) {
        return events.stream()
                .filter(event ->
                        event.getMarkerList() != null && event.getMarkerList().contains(ProblemKey.REFUSAL))
                .toList();
    }

    private static String problemOf(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> ProblemKey.NAME.equals(pair.key))
                .map(pair -> String.valueOf(pair.value))
                .findFirst()
                .orElse(null);
    }

    @Test
    void aMissBehindARouteIsAWarningNamedByTheRoute() {
        var warnings = logged("/api/v1/things/{id}", "/api/v1/things/7").stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .toList();

        assertEquals(1, warnings.size());
        assertEquals("404 GET /api/v1/things/{id}", problemOf(warnings.getFirst()));
        assertTrue(warnings.getFirst().getFormattedMessage().contains("/api/v1/things/7"));
    }

    @Test
    void aMissNoRouteClaimsIsOnlyADebugLine() {
        var events = logged(PATH, "/wp-login.php");

        assertTrue(
                events.stream().noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN)), events.toString());
        assertEquals(
                1,
                events.stream()
                        .filter(event -> event.getLevel() == Level.DEBUG)
                        .filter(event -> event.getFormattedMessage().contains("/wp-login.php"))
                        .count(),
                events.toString());
    }

    @Test
    void aMissBeforeTheHandlerIsStillNamedByTheRoute() {
        var warnings = logged("/api/v1/things/{id}", "/api/v1/things/7", ctx -> {
                    throw new NotFoundResponse("Gone before the handler");
                })
                .stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .filter(event -> event.getFormattedMessage().contains("/api/v1/things/7"))
                .toList();

        assertEquals(1, warnings.size(), warnings.toString());
        assertEquals("404 GET /api/v1/things/{id}", problemOf(warnings.getFirst()));
    }

    @Test
    void refusalsOnOneRouteAreOneProblemWhateverTheirIds() {
        var refused = refusals(logged(
                "/api/v1/things/{id}",
                ctx -> {
                    throw BodyRefusal.INPUT_NOT_USABLE.raise();
                },
                "/api/v1/things/7",
                "/api/v1/things/8"));

        assertEquals(2, refused.size());
        for (var refusal : refused) {
            assertEquals(
                    "refusal " + BodyRefusal.INPUT_NOT_USABLE.code() + " GET /api/v1/things/{id}", problemOf(refusal));
        }
    }

    @Test
    void aRefusalWithoutACodeIsNamedByItsStatus() {
        var refused = refusals(logged(
                "/api/v1/things/{id}",
                ctx -> {
                    throw new ForbiddenResponse("Not yours");
                },
                "/api/v1/things/7"));

        assertEquals(1, refused.size());
        assertEquals("refusal HTTP 403 GET /api/v1/things/{id}", problemOf(refused.getFirst()));
    }

    @Test
    void anApiRefusalIsNamedByItsStatus() {
        var refused = refusals(logged(
                "/api/v1/things/{id}",
                ctx -> {
                    throw new ApiException(HttpStatus.CONFLICT, "Already there");
                },
                "/api/v1/things/7"));

        assertEquals(1, refused.size());
        assertEquals("refusal HTTP 409 GET /api/v1/things/{id}", problemOf(refused.getFirst()));
    }

    @Test
    void aRefusalBeforeTheHandlerIsNamedByTheRoute() {
        var refused = refusals(logged(
                routes -> {
                    routes.before(ctx -> {
                        throw GeneralRefusal.REQUESTS_TOO_OFTEN.raise();
                    });
                    routes.beforeMatched(ctx -> {});
                    routes.get("/api/v1/things/{id}", ctx -> {});
                },
                "/api/v1/things/7",
                "/wp-login.php"));

        assertEquals(2, refused.size());
        String code = GeneralRefusal.REQUESTS_TOO_OFTEN.code();
        assertEquals("refusal " + code + " GET /api/v1/things/{id}", problemOf(refused.get(0)));
        assertEquals("refusal " + code + " GET unmatched", problemOf(refused.get(1)));
    }

    @Test
    void anExpiredSessionLeavesNoWarning() {
        var events = logged(
                "/api/v1/things/{id}",
                ctx -> {
                    throw new UnauthorizedResponse("Session expired");
                },
                "/api/v1/things/7");

        assertTrue(
                events.stream().noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN)), events.toString());
    }

    @Test
    void differentMalformedBodiesAreOneRefusal() {
        var refused = refusals(logged(
                "/api/v1/things/{id}",
                ctx -> STRICT.readValue(ctx.pathParam("id").equals("1") ? "{not json" : "{\"name\": }", Thing.class),
                "/api/v1/things/1",
                "/api/v1/things/2"));

        assertEquals(2, refused.size());
        assertEquals(problemOf(refused.get(0)), problemOf(refused.get(1)));
        assertEquals(
                "refusal " + BodyRefusal.BODY_NOT_JSON.code() + " GET /api/v1/things/{id}", problemOf(refused.get(0)));
    }

    /** One particular refusal, standing for itself without a refusal code. */
    static final class ThingLocked extends ApiException {
        ThingLocked() {
            super(HttpStatus.LOCKED, "Locked");
        }
    }

    @Test
    void anApiRefusalOfItsOwnKindIsNamedByThatKind() {
        var refused = refusals(logged(
                "/api/v1/things/{id}",
                ctx -> {
                    throw new ThingLocked();
                },
                "/api/v1/things/7"));

        assertEquals(1, refused.size());
        assertEquals("refusal ThingLocked GET /api/v1/things/{id}", problemOf(refused.getFirst()));
    }
}

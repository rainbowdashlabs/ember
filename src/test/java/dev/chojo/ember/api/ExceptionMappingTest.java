/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import io.javalin.Javalin;
import io.javalin.http.Handler;
import io.javalin.http.NotFoundResponse;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
            new ExceptionMapping(new Demo()).install(config.routes);
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
            throw Refusal.INPUT_NOT_USABLE.raise();
        });

        assertEquals(Refusal.INPUT_NOT_USABLE, refusalOf(response));
    }

    @Test
    void aRateLimitSaysWhenToTryAgain() {
        var response = answer(ctx -> RateLimits.enforce(Optional.of(30L)));

        assertEquals(429, response.code());
        assertEquals("30", header(response, "Retry-After"));
        assertEquals(30, json(response).path("retryAfterSeconds").asInt());
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
                Refusal.INPUT_NOT_USABLE.code(), json(response).path("code").asString());
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
        assertEquals(Refusal.BODY_NOT_JSON, refusalOf(reading("{not json")));
    }

    @Test
    void aFieldTheEndpointDoesNotTakeIsNamed() {
        var response = reading("{\"name\":\"a\",\"colour\":\"red\"}");

        var body = json(response);
        assertEquals(Refusal.BODY_UNEXPECTED_FIELD.code(), body.path("code").asString());
        assertTrue(body.path("message").asString().endsWith(": colour"), body.toString());
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
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.spec;

import dev.chojo.ember.api.ApiJsonMapper;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ApiSpecTest {

    private static final JsonMapper MAPPER = ApiJsonMapper.create();

    record Note(int id, String text) {}

    record NoteRequest(String text) {}

    static final class NoteRoutes {
        @OpenApi(
                path = "/api/v1/notes/{id}",
                methods = HttpMethod.PUT,
                summary = "Change a note",
                tags = {"Notes"},
                pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
                queryParams = @OpenApiParam(name = "draft", type = Boolean.class),
                requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NoteRequest.class)),
                responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = Note.class)),
                    @OpenApiResponse(status = "404")
                })
        private void change() {}

        @OpenApi(
                path = "/api/v1/notes",
                methods = HttpMethod.GET,
                responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = Note[].class)),
                    @OpenApiResponse(status = "204", content = @OpenApiContent(from = String.class))
                })
        private void list() {}

        @OpenApi(path = "/api/v1/notes/feed", methods = HttpMethod.GET, ignore = true)
        private void ignored() {}
    }

    static final class OtherNoteRoutes {
        @OpenApi(path = "/api/v1/notes", methods = HttpMethod.GET)
        private void listAgain() {}
    }

    /**
     * A change to a record or a route annotation without a regenerated description fails here, the way
     * a schema change without a refreshed tracking file fails the data tracking suite.
     */
    @Test
    void committedSpecIsWhatAGenerationWrites() throws IOException {
        String committed = Files.readString(ApiSpecCli.SPEC_PATH);
        if (!committed.equals(ApiSpecCli.generate())) {
            fail(ApiSpecCli.SPEC_PATH + " differs from what a generation writes. Run ./toolchain.sh be-api-spec"
                    + " and commit the result.");
        }
    }

    @Test
    void anOperationCarriesItsParametersBodyAndResponses() {
        JsonNode operation = document(NoteRoutes.class)
                .get("paths")
                .get("/api/v1/notes/{id}")
                .get("put");

        assertEquals("Change a note", operation.get("summary").asString());
        assertEquals("path", operation.get("parameters").get(0).get("in").asString());
        assertTrue(operation.get("parameters").get(0).get("required").asBoolean());
        assertFalse(operation.get("parameters").get(1).has("required"));
        assertEquals(
                "#/components/schemas/NoteRequest",
                operation
                        .at("/requestBody/content/application~1json/schema/$ref")
                        .asString());
        assertEquals(
                "#/components/schemas/Note",
                operation
                        .at("/responses/200/content/application~1json/schema/$ref")
                        .asString());
        assertEquals("Not Found", operation.at("/responses/404/description").asString());
    }

    @Test
    void responsesAreDescribedBeforeRequestsAndIgnoredOperationsLeftOut() {
        JsonNode document = document(NoteRoutes.class);

        assertEquals(
                "array",
                document.at("/paths/~1api~1v1~1notes/get/responses/200/content/application~1json/schema/type")
                        .asString());
        assertTrue(
                document.at("/paths/~1api~1v1~1notes/get/responses/204/content").has("text/plain"));
        assertFalse(document.get("paths").has("/api/v1/notes/feed"));
        assertEquals(2, document.at("/components/schemas/Note/required").size());
        assertFalse(document.at("/components/schemas/NoteRequest").has("required"));
    }

    @Test
    void anOperationDescribedTwiceFailsTheGeneration() {
        var failure = assertThrows(
                IllegalStateException.class,
                () -> ApiSpec.render(List.of(NoteRoutes.class, OtherNoteRoutes.class), MAPPER));

        assertTrue(failure.getMessage().contains("GET /api/v1/notes"), failure.getMessage());
    }

    private static JsonNode document(Class<?> routes) {
        return MAPPER.readTree(ApiSpec.render(List.of(routes), MAPPER));
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static dev.chojo.ember.api.RouteHarness.header;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The API documentation over HTTP: {@code /docs} answers with the committed description, unchanged,
 * and the Swagger UI points at it.
 */
class ApiDocumentationTest {

    @Test
    void theDocsServeTheCommittedDescription() throws IOException {
        String committed = committedDescription();

        RouteHarness.serving().run((server, client) -> {
            var response = client.get(ApiDocumentation.PATH);
            assertEquals(200, response.code());
            assertTrue(header(response, "Content-Type").startsWith("application/json"));
            assertEquals(committed, response.body().string());
        });
    }

    @Test
    void theVersionTheSwaggerUiAsksForIsTheSameDescription() throws IOException {
        String committed = committedDescription();

        RouteHarness.serving().run((server, client) -> {
            var response = client.get(ApiDocumentation.PATH + "?v=default");
            assertEquals(200, response.code());
            assertEquals(committed, response.body().string());
        });
    }

    @Test
    void theSwaggerUiReadsTheDocs() {
        RouteHarness.serving().run((server, client) -> {
            var response = client.get("/swagger-ui");
            assertEquals(200, response.code());
            assertTrue(response.body().string().contains(ApiDocumentation.PATH + "?v=default"));
        });
    }

    private static String committedDescription() throws IOException {
        try (InputStream in = ApiDocumentation.class.getClassLoader().getResourceAsStream(ApiDocumentation.RESOURCE)) {
            assertNotNull(in, "the API description is on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

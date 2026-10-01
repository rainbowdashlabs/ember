/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Serves the committed description of the API, which the Swagger UI reads.
 *
 * <p>The description is the one the build generates from the route annotations and the records
 * the routes send and read, as the API's own mapper writes them. The frontend types come from the
 * same file, so the page shows exactly the contract the frontend is built against. It ships in the
 * jar and is read once, when the server is built.
 */
public final class ApiDocumentation implements Handler {

    /** Where the description is served, and where the Swagger UI looks for it. */
    public static final String PATH = "/docs";

    /** The classpath resource holding the description. */
    static final String RESOURCE = "api/openapi.json";

    private final byte[] description;

    private ApiDocumentation(byte[] description) {
        this.description = description;
    }

    /**
     * Reads the description from the classpath.
     *
     * @return the handler serving it
     * @throws IllegalStateException when the jar carries no description, which only a broken build
     *     produces
     */
    public static ApiDocumentation load() {
        try (InputStream in = ApiDocumentation.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("The API description " + RESOURCE + " is missing from the classpath");
            }
            return new ApiDocumentation(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the API description " + RESOURCE, e);
        }
    }

    @Override
    public void handle(Context ctx) {
        ctx.contentType(ContentType.APPLICATION_JSON).result(description);
    }
}

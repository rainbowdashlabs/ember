/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.spec;

import dev.chojo.ember.api.ApiJsonMapper;
import dev.chojo.ember.api.RegisteredRoutes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the API description for {@code ./toolchain.sh be-api-spec}: the operations of every route class
 * the application binds, described through the API's own mapper. No server and no database are started.
 */
public final class ApiSpecCli {

    /** Where the description is committed. */
    static final Path SPEC_PATH = Path.of("src/main/resources/api/openapi.json");

    private ApiSpecCli() {}

    static void main(String[] args) throws IOException {
        Path target = args.length > 0 ? Path.of(args[0]) : SPEC_PATH;
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target, generate());
        System.out.println("Wrote " + target.toAbsolutePath());
    }

    /**
     * The description as a generation writes it.
     *
     * @return the JSON text
     */
    static String generate() {
        return ApiSpec.render(RegisteredRoutes.boundClasses(), ApiJsonMapper.create());
    }
}

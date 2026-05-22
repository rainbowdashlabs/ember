/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.util.CsvParser;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.List;

@Singleton
public class UtilRoutes implements Routes {

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/util/csv/parse", this::parseCsv, Roles.LOGIN);
    }

    private void parseCsv(Context ctx) {
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("No file uploaded");
        String separator = ctx.formParam("separator");
        char sep = separator != null && !separator.isEmpty() ? separator.charAt(0) : ';';
        try (var content = file.content()) {
            String text = new String(content.readAllBytes());
            var parsed = CsvParser.parse(text, sep);
            ctx.json(new CsvResponse(parsed.headers(), parsed.rows()));
        } catch (IOException e) {
            throw new BadRequestResponse("Failed to parse CSV: " + e.getMessage());
        }
    }

    public record CsvResponse(List<String> headers, List<List<String>> rows) {}
}

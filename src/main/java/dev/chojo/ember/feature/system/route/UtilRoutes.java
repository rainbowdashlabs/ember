/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.util.CsvParser;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

@Singleton
public class UtilRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(UtilRoutes.class);

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/util/csv/parse", this::parseCsv, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/util/csv/parse",
            methods = HttpMethod.POST,
            summary = "Parse a CSV file upload",
            tags = {"Utilities"},
            requestBody =
                    @OpenApiRequestBody(
                            content = @OpenApiContent(from = String.class),
                            description = "Multipart form with 'file' (CSV) and optional 'separator' field"),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CsvResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
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
            log.warn("Failed to parse CSV file", e);
            throw new BadRequestResponse("Failed to parse CSV");
        }
    }

    public record CsvResponse(List<String> headers, List<List<String>> rows) {}
}

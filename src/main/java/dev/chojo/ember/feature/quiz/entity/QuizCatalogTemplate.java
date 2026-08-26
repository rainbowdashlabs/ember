/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import io.javalin.http.InternalServerErrorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The example files the import offers to download.
 *
 * <p>They are shipped rather than written into the documentation, because a test reads them back
 * through the importer: an example that stopped matching the format would fail the build instead of
 * quietly teaching the wrong thing.
 */
public enum QuizCatalogTemplate {
    JSON("quiz/catalog-template.json", "fragenkatalog-vorlage.json", "application/json"),
    CSV("quiz/catalog-template.csv", "fragenkatalog-vorlage.csv", "text/csv; charset=utf-8");

    private final String resourcePath;
    private final String fileName;
    private final String contentType;

    QuizCatalogTemplate(String resourcePath, String fileName, String contentType) {
        this.resourcePath = resourcePath;
        this.fileName = fileName;
        this.contentType = contentType;
    }

    /**
     * @param format the format as the request spells it, in either case
     * @return the template, or {@code null} for a format there is no example of
     */
    public static QuizCatalogTemplate byFormat(String format) {
        if (format == null) return null;
        try {
            return valueOf(format.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String fileName() {
        return fileName;
    }

    public String contentType() {
        return contentType;
    }

    /** Reads the shipped example. A missing one is a broken build, not a bad request. */
    public String read() {
        try (var stream = QuizCatalogTemplate.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) throw new InternalServerErrorResponse("Template " + resourcePath + " is missing");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InternalServerErrorResponse("Failed to read template " + resourcePath);
        }
    }
}

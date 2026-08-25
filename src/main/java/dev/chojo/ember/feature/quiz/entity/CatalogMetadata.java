/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

/**
 * Where a catalog's questions came from. A catalog is exported to a file, handed to another
 * station and imported there, and without this it arrives anonymous: the receiving station
 * cannot say who wrote the questions or under what terms it may use them.
 *
 * <p>Every part is free text nobody is obliged to fill in. A blank is stored as absent, so a
 * form that submits empty strings does not leave the catalog claiming an empty author.
 *
 * @param language the language the questions are written in, as a BCP 47 tag
 * @param source   where the questions came from: a sheet, a handbook, another station
 * @param author   who wrote them
 * @param license  the terms they may be used under
 */
public record CatalogMetadata(String language, String source, String author, String license) {
    private static final CatalogMetadata NONE = new CatalogMetadata(null, null, null, null);

    public CatalogMetadata {
        language = trimToNull(language);
        source = trimToNull(source);
        author = trimToNull(author);
        license = trimToNull(license);
    }

    /**
     * The metadata of a catalog nobody has said anything about, which is what every catalog
     * created outside an import starts as.
     */
    public static CatalogMetadata none() {
        return NONE;
    }

    /** Reads a possibly absent metadata block, so a caller can forward a nullable request field. */
    public static CatalogMetadata orNone(CatalogMetadata metadata) {
        return metadata != null ? metadata : NONE;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

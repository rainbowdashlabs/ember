/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One uploaded version of the PDF a PDF template fills in. It is stored as it arrived and never
 * changed; a new version is a new original, and a document generated from an old one keeps naming it.
 *
 * @param id         the original
 * @param templateId the template it was uploaded for
 * @param fileName   the name it was uploaded under
 * @param sizeBytes  how large it is
 * @param sha256     the SHA-256 of its bytes as lowercase hex
 * @param inspection its pages and form fields
 * @param uploadedAt when it was uploaded
 */
public record PdfOriginal(
        int id,
        int templateId,
        String fileName,
        long sizeBytes,
        String sha256,
        PdfInspection inspection,
        Instant uploadedAt) {

    /** The columns {@link #map()} reads, in a form a query can splice in. */
    public static final String COLUMNS =
            "id, template_id, file_name, size_bytes, sha256, inspection::text AS inspection, uploaded_at";

    public static RowMapping<PdfOriginal> map() {
        return row -> new PdfOriginal(
                row.getInt("id"),
                row.getInt("template_id"),
                row.getString("file_name"),
                row.getLong("size_bytes"),
                row.getString("sha256"),
                PdfInspection.parse(row.getString("inspection")),
                row.get("uploaded_at", INSTANT_TIMESTAMP));
    }
}

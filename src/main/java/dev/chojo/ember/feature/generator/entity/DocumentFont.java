/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.owner.Owner;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One uploaded font file: one style of a family, kept by its owner.
 *
 * @param id             the font, also the storage key of its file
 * @param owner          the station, association or instance that uploaded it
 * @param family         the family name templates pick it by
 * @param style          which style of the family it is
 * @param fileName       the name it was uploaded under
 * @param outline        how its glyphs are drawn
 * @param internalFamily the family name the file carries itself, which the letter renderer asks for
 * @param sizeBytes      the size of the file
 * @param sha256         the SHA-256 of the file as lowercase hex
 * @param uploadedAt     when it was uploaded
 */
public record DocumentFont(
        int id,
        Owner owner,
        String family,
        FontStyle style,
        String fileName,
        FontOutline outline,
        String internalFamily,
        long sizeBytes,
        String sha256,
        Instant uploadedAt)
        implements FontFace {

    /** The columns {@link #map()} reads, in a form a query can splice in. */
    public static final String COLUMNS =
            "id, station_id, cluster_id, family, style, file_name, outline, internal_family, size_bytes, sha256, uploaded_at";

    /** Fields on an uploaded PDF are embedded by PDFBox, which takes TrueType outlines only. */
    @Override
    public boolean printsOnPdf() {
        return outline == FontOutline.TRUETYPE;
    }

    @Override
    public String identity() {
        return "uploaded:" + sha256;
    }

    /** @return who uploaded it */
    public FontOrigin origin() {
        return switch (owner) {
            case Owner.Station ignored -> FontOrigin.STATION;
            case Owner.Association ignored -> FontOrigin.ASSOCIATION;
            case Owner.Instance ignored -> FontOrigin.INSTANCE;
        };
    }

    public static RowMapping<DocumentFont> map() {
        return row -> {
            Integer stationId = row.getObject("station_id", Integer.class);
            Integer clusterId = row.getObject("cluster_id", Integer.class);
            Owner owner = stationId != null
                    ? new Owner.Station(stationId)
                    : clusterId != null ? new Owner.Association(clusterId) : new Owner.Instance();
            return new DocumentFont(
                    row.getInt("id"),
                    owner,
                    row.getString("family"),
                    row.getEnum("style", FontStyle.class),
                    row.getString("file_name"),
                    row.getEnum("outline", FontOutline.class),
                    row.getString("internal_family"),
                    row.getLong("size_bytes"),
                    row.getString("sha256"),
                    row.get("uploaded_at", INSTANT_TIMESTAMP));
        };
    }
}

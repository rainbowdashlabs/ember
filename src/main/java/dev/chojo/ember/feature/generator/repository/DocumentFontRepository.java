/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.FontOutline;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.FontUse;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The fonts uploaded for documents, by the station, association or instance that owns them.
 *
 * <p>An owner is a station id, an association id, or neither for the instance. Every query names it
 * with both columns compared by {@code IS NOT DISTINCT FROM}, so the instance's fonts, which have
 * neither, are found by the same query as anybody else's.
 */
@Singleton
public class DocumentFontRepository {
    private static final String OWNED_BY = """
            station_id IS NOT DISTINCT FROM :station_id::int
            AND cluster_id IS NOT DISTINCT FROM :cluster_id::int""";

    /**
     * What a new font file is.
     *
     * @param family         the family name it is picked by
     * @param style          which style of the family it is
     * @param fileName       the name it was uploaded under
     * @param outline        how its glyphs are drawn
     * @param internalFamily the family name the file carries itself
     * @param sizeBytes      its size
     * @param sha256         its SHA-256 as lowercase hex
     */
    public record NewFont(
            String family,
            FontStyle style,
            String fileName,
            FontOutline outline,
            String internalFamily,
            long sizeBytes,
            String sha256) {}

    /**
     * Writes a new font.
     *
     * @param owner      who keeps it
     * @param font       what it is
     * @param uploadedBy the account that uploaded it
     * @return the font as written
     */
    public DocumentFont insert(Owner owner, NewFont font, int uploadedBy) {
        return query("""
                        INSERT INTO document_font(station_id, cluster_id, family, style, file_name, outline,
                                                  internal_family, size_bytes, sha256, uploaded_by)
                        VALUES (:station_id, :cluster_id, :family, :style, :file_name, :outline,
                                :internal_family, :size_bytes, :file_hash, :uploaded_by)
                        RETURNING %s;""", DocumentFont.COLUMNS)
                .single(owned(owner)
                        .bind("family", font.family())
                        .bind("style", font.style())
                        .bind("file_name", font.fileName())
                        .bind("outline", font.outline())
                        .bind("internal_family", font.internalFamily())
                        .bind("size_bytes", font.sizeBytes())
                        .bind("file_hash", font.sha256())
                        .bind("uploaded_by", uploadedBy))
                .map(DocumentFont.map())
                .first()
                .orElseThrow();
    }

    /**
     * @param owner  who keeps the fonts
     * @param family a family name, in any case
     * @param style  a style
     * @return whether the owner already has a file for that family and style
     */
    public boolean exists(Owner owner, String family, FontStyle style) {
        return query("""
                        SELECT EXISTS(SELECT 1 FROM document_font
                                      WHERE %s AND lower(family) = lower(:family) AND style = :style) AS taken;""", OWNED_BY)
                .single(owned(owner).bind("family", family).bind("style", style))
                .map(row -> row.getBoolean("taken"))
                .first()
                .orElse(false);
    }

    /**
     * @param owner who keeps the fonts
     * @return the owner's fonts by family and style
     */
    public List<DocumentFont> findOwned(Owner owner) {
        return query("""
                        SELECT %s FROM document_font
                        WHERE %s
                        ORDER BY lower(family), style;""", DocumentFont.COLUMNS, OWNED_BY)
                .single(owned(owner))
                .map(DocumentFont.map())
                .all();
    }

    /**
     * The fonts of several owners at once, the instance's included.
     *
     * @param stationId the station whose fonts to include, or null for none
     * @param clusterId the association whose fonts to include, or null for none
     * @return the fonts of all of them
     */
    public List<DocumentFont> findReachable(@Nullable Integer stationId, @Nullable Integer clusterId) {
        return query("""
                        SELECT %s FROM document_font
                        WHERE (station_id IS NULL AND cluster_id IS NULL)
                           OR station_id = :station_id::int
                           OR cluster_id = :cluster_id::int
                        ORDER BY lower(family), style;""", DocumentFont.COLUMNS)
                .single(call().bind("station_id", stationId).bind("cluster_id", clusterId))
                .map(DocumentFont.map())
                .all();
    }

    /**
     * @param owner who keeps the font
     * @param id    the font
     * @return the font, or empty where the owner has none of that id
     */
    public Optional<DocumentFont> find(Owner owner, int id) {
        return query("SELECT %s FROM document_font WHERE id = :id AND %s;", DocumentFont.COLUMNS, OWNED_BY)
                .single(owned(owner).bind("id", id))
                .map(DocumentFont.map())
                .first();
    }

    /**
     * @param id the font
     * @return whether a row was deleted
     */
    public boolean delete(int id) {
        return query("DELETE FROM document_font WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    /**
     * The templates in use that print in a family of that name.
     *
     * @param family     the family name, in any case
     * @param stationIds the stations whose templates to look at, or null for every station
     * @return the templates, with the station that keeps each
     */
    public List<FontUse> templatesNaming(String family, @Nullable Collection<Integer> stationIds) {
        return query("""
                        SELECT t.id, t.station_id, t.name
                        FROM document_template t
                            LEFT JOIN document_template_letter l ON l.template_id = t.id
                        WHERE t.archived_at IS NULL
                          AND (:all_stations OR t.station_id = ANY(:station_ids))
                          AND (lower(l.page ->> 'bodyFont') = lower(:family)
                               OR lower(l.page ->> 'headerFont') = lower(:family)
                               OR lower(l.page ->> 'footerFont') = lower(:family)
                               OR EXISTS(SELECT 1 FROM document_template_field f
                                         WHERE f.template_id = t.id AND lower(f.font_family) = lower(:family)))
                        ORDER BY t.name;""")
                .single(call().bind("family", family)
                        .bind("all_stations", stationIds == null)
                        .bind(
                                "station_ids",
                                stationIds == null ? List.of() : List.copyOf(stationIds),
                                PostgreSqlTypes.INTEGER))
                .map(row -> new FontUse(row.getInt("id"), row.getInt("station_id"), row.getString("name")))
                .all();
    }

    private static Call owned(Owner owner) {
        Integer stationId = owner instanceof Owner.Station station ? station.stationId() : null;
        Integer clusterId = owner instanceof Owner.Association association ? association.clusterId() : null;
        return call().bind("station_id", stationId).bind("cluster_id", clusterId);
    }
}

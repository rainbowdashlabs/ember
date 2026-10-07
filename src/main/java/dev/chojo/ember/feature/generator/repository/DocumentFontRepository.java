/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.FontOutline;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.FontUse;
import dev.chojo.ember.feature.generator.entity.WebFontFormat;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.owner.OwnerColumns;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.owner.OwnerColumns.OWNED_BY;

/**
 * The fonts uploaded for documents, by the station, association or instance that owns them.
 *
 * <p>An owner is a station id, an association id, or neither for the instance. Every query names it
 * with both columns compared by {@code IS NOT DISTINCT FROM}, so the instance's fonts, which have
 * neither, are found by the same query as anybody else's.
 */
@Singleton
public class DocumentFontRepository {

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
        return SqlSupport.insertReturning(
                """
                        INSERT INTO document_font(station_id, cluster_id, family, style, file_name, outline,
                                                  internal_family, size_bytes, sha256, uploaded_by)
                        VALUES (:station_id, :cluster_id, :family, :style, :file_name, :outline,
                                :internal_family, :size_bytes, :file_hash, :uploaded_by)
                        RETURNING %s;""",
                OwnerColumns.bind(owner)
                        .bind("family", font.family())
                        .bind("style", font.style())
                        .bind("file_name", font.fileName())
                        .bind("outline", font.outline())
                        .bind("internal_family", font.internalFamily())
                        .bind("size_bytes", font.sizeBytes())
                        .bind("file_hash", font.sha256())
                        .bind("uploaded_by", uploadedBy),
                DocumentFont.map(),
                DocumentFont.COLUMNS);
    }

    /**
     * @param owner  who keeps the fonts
     * @param family a family name, in any case
     * @param style  a style
     * @return whether the owner already has a file for that family and style
     */
    public boolean exists(Owner owner, String family, FontStyle style) {
        return SqlSupport.exists(
                """
                        SELECT 1 FROM document_font
                        WHERE %s AND lower(family) = lower(:family) AND style = :style
                        LIMIT 1;""", OwnerColumns.bind(owner).bind("family", family).bind("style", style), OWNED_BY);
    }

    /**
     * @param sha256 the SHA-256 of a font file as lowercase hex
     * @return whether any owner keeps a font with exactly this file
     */
    public boolean anyHolds(String sha256) {
        return SqlSupport.exists("""
                        SELECT 1 FROM document_font WHERE sha256 = :file_hash LIMIT 1;""", call().bind("file_hash", sha256));
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
                .single(OwnerColumns.bind(owner))
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
                .single(OwnerColumns.bind(owner).bind("id", id))
                .map(DocumentFont.map())
                .first();
    }

    /**
     * What a new web version of a font style is.
     *
     * @param fileName  the name it was uploaded under
     * @param format    what it is
     * @param sizeBytes its size
     * @param sha256    its SHA-256 as lowercase hex
     */
    public record NewWebFont(String fileName, WebFontFormat format, long sizeBytes, String sha256) {}

    /**
     * Gives a font style its web version, in place of the one it had.
     *
     * @param id  the font
     * @param web what the web version is
     * @return the font as it now stands
     */
    public DocumentFont setWeb(int id, NewWebFont web) {
        return SqlSupport.insertReturning(
                """
                        UPDATE document_font
                        SET web_file_name = :web_file_name, web_format = :web_format, web_size_bytes = :web_size_bytes,
                            web_sha256 = :web_hash, web_uploaded_at = now()
                        WHERE id = :id
                        RETURNING %s;""",
                call().bind("id", id)
                        .bind("web_file_name", web.fileName())
                        .bind("web_format", web.format())
                        .bind("web_size_bytes", web.sizeBytes())
                        .bind("web_hash", web.sha256()),
                DocumentFont.map(),
                DocumentFont.COLUMNS);
    }

    /**
     * Takes the web version of a font style away.
     *
     * @param id the font
     * @return the font as it now stands
     */
    public DocumentFont clearWeb(int id) {
        return SqlSupport.insertReturning("""
                        UPDATE document_font
                        SET web_file_name = NULL, web_format = NULL, web_size_bytes = NULL, web_sha256 = NULL,
                            web_uploaded_at = NULL
                        WHERE id = :id
                        RETURNING %s;""", call().bind("id", id), DocumentFont.map(), DocumentFont.COLUMNS);
    }

    /**
     * @param id the font
     * @return whether a row was deleted
     */
    public boolean delete(int id) {
        return SqlSupport.deleteById("document_font", id);
    }

    /**
     * The templates in use that print in a family of that name and could reach the fonts of an owner:
     * a station's own templates for a station's font; the association's templates and those of its
     * stations for an association's font; every template for the instance's.
     *
     * @param family    the family name, in any case
     * @param fontOwner who keeps the font
     * @return the templates, with the owner that keeps each
     */
    public List<FontUse> templatesNaming(String family, Owner fontOwner) {
        return query("""
                        SELECT t.id, t.station_id, t.cluster_id, t.name
                        FROM document_template t
                            LEFT JOIN document_template_letter l ON l.template_id = t.id
                        WHERE t.archived_at IS NULL
                          AND (:instance
                               OR t.station_id = :station_id::int
                               OR t.cluster_id = :cluster_id::int
                               OR t.station_id IN (SELECT s.id FROM station s WHERE s.cluster_id = :cluster_id::int))
                          AND (lower(l.page ->> 'bodyFont') = lower(:family)
                               OR lower(l.page ->> 'headerFont') = lower(:family)
                               OR lower(l.page ->> 'footerFont') = lower(:family)
                               OR EXISTS(SELECT 1 FROM document_template_field f
                                         WHERE f.template_id = t.id AND lower(f.font_family) = lower(:family)))
                        ORDER BY t.name;""")
                .single(OwnerColumns.bind(fontOwner)
                        .bind("family", family)
                        .bind("instance", fontOwner instanceof Owner.Instance))
                .map(row -> new FontUse(
                        row.getInt("id"),
                        DocumentTemplate.ownerOf(
                                row.getObject("station_id", Integer.class), row.getObject("cluster_id", Integer.class)),
                        row.getString("name")))
                .all();
    }
}

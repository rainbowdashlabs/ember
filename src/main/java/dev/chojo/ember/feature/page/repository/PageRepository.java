/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.page.entity.CellContentType;
import dev.chojo.ember.feature.page.entity.PageCell;
import dev.chojo.ember.feature.page.entity.PageFile;
import dev.chojo.ember.feature.page.entity.PageRow;
import dev.chojo.ember.feature.page.entity.StationPage;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class PageRepository {

    // --- Page CRUD ---

    public StationPage create(int stationId, String title, String slug, Integer parentId, int createdBy) {
        return query("""
                INSERT INTO station_page(station_id, title, slug, parent_id, created_by)
                VALUES(:station_id, :title, :slug, :parent_id, :created_by)
                RETURNING *;""")
                .single(call().bind("station_id", stationId)
                        .bind("title", title)
                        .bind("slug", slug)
                        .bind("parent_id", parentId)
                        .bind("created_by", createdBy))
                .map(StationPage.mapFlat())
                .first()
                .orElseThrow();
    }

    public Optional<StationPage> findById(int id) {
        return query("SELECT * FROM station_page WHERE id = :id;")
                .single(call().bind("id", id))
                .map(StationPage.mapFlat())
                .first();
    }

    public Optional<StationPage> findBySlugAndStation(String slug, int stationId) {
        return query("SELECT * FROM station_page WHERE slug = :slug AND station_id = :station_id;")
                .single(call().bind("slug", slug).bind("station_id", stationId))
                .map(StationPage.mapFlat())
                .first();
    }

    public Optional<StationPage> findBySlugAndParent(int stationId, String slug, Integer parentId) {
        if (parentId == null) {
            return query("""
                    SELECT *
                    FROM
                        station_page
                    WHERE station_id = :station_id
                      AND slug = :slug
                      AND parent_id IS NULL;""")
                    .single(call().bind("station_id", stationId).bind("slug", slug))
                    .map(StationPage.mapFlat())
                    .first();
        }
        return query("""
                SELECT *
                FROM
                    station_page
                WHERE station_id = :station_id
                  AND slug = :slug
                  AND parent_id = :parent_id;""")
                .single(call().bind("station_id", stationId).bind("slug", slug).bind("parent_id", parentId))
                .map(StationPage.mapFlat())
                .first();
    }

    public List<StationPage> findByStation(int stationId) {
        return query("SELECT * FROM station_page WHERE station_id = :station_id ORDER BY sort_order;")
                .single(call().bind("station_id", stationId))
                .map(StationPage.mapFlat())
                .all();
    }

    public List<StationPage> findPublishedByStation(int stationId) {
        return query("""
                SELECT
                    *
                FROM
                    station_page
                WHERE station_id = :station_id
                  AND published
                ORDER BY sort_order;""")
                .single(call().bind("station_id", stationId))
                .map(StationPage.mapFlat())
                .all();
    }

    /**
     * Editor's PAGE_LINK picker. Returns a compact shape — {@code publicUid},
     * {@code title}, {@code slug}, {@code updatedAt} — for the published pages of the supplied
     * station, optionally filtered by case-insensitive title substring. Empty {@code search}
     * returns the most recently updated pages so the picker has something on first focus.
     */
    public List<PickerPage> searchForPicker(int stationId, String search, int limit) {
        boolean hasSearch = search != null && !search.isBlank();
        String predicate = hasSearch ? "AND LOWER(title) LIKE :q" : "";
        var c = call().bind("station_id", stationId).bind("limit", limit);
        if (hasSearch) {
            c = c.bind("q", "%" + search.trim().toLowerCase() + "%");
        }
        return query("""
                SELECT public_uid, title, slug, updated_at
                FROM station_page
                WHERE station_id = :station_id
                  AND PUBLISHED
                  %s
                ORDER BY updated_at DESC
                LIMIT :limit;""", predicate)
                .single(c)
                .map(row -> new PickerPage(
                        row.get("public_uid", StandardValueConverter.UUID_STRING),
                        row.getString("title"),
                        row.getString("slug"),
                        row.get("updated_at", INSTANT_TIMESTAMP)))
                .all();
    }

    public boolean updateMeta(
            int id, String title, String slug, Integer parentId, String metaDescription, Integer ogImageId) {
        return query("""
                UPDATE station_page
                SET
                    title            = :title,
                    slug             = :slug,
                    parent_id        = :parent_id,
                    meta_description = :meta_description,
                    og_image_id      = :og_image_id,
                    updated_at       = :updated_at
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("title", title)
                        .bind("slug", slug)
                        .bind("parent_id", parentId)
                        .bind("meta_description", metaDescription)
                        .bind("og_image_id", ogImageId)
                        .bind("updated_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    public boolean setPublished(int id, boolean published) {
        return query("UPDATE station_page SET published = :published, updated_at = :updated_at WHERE id = :id;")
                .single(call().bind("id", id)
                        .bind("published", published)
                        .bind("updated_at", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return query("DELETE FROM station_page WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    public boolean slugExists(int stationId, String slug, int excludePageId) {
        return query(
                        "SELECT 1 FROM station_page WHERE station_id = :station_id AND slug = :slug AND id != :exclude_id;")
                .single(call().bind("station_id", stationId).bind("slug", slug).bind("exclude_id", excludePageId))
                .map(_ -> true)
                .first()
                .isPresent();
    }

    public int countChildren(int parentId) {
        return query("SELECT count(*) AS cnt FROM station_page WHERE parent_id = :parent_id;")
                .single(call().bind("parent_id", parentId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    public int depth(int pageId) {
        int d = 0;
        Integer currentParent = findById(pageId).map(StationPage::parentId).orElse(null);
        while (currentParent != null) {
            d++;
            currentParent = findById(currentParent).map(StationPage::parentId).orElse(null);
        }
        return d;
    }

    public List<PageRow> findRowsByPage(int pageId) {
        return query("SELECT id, page_id, sort_order FROM page_row WHERE page_id = :page_id ORDER BY sort_order;")
                .single(call().bind("page_id", pageId))
                .map(PageRow.mapFlat())
                .all();
    }

    // --- Row/Cell operations (full tree save) ---

    public List<PageCell> findCellsByRow(int rowId) {
        return query("""
                SELECT
                    id,
                    row_id,
                    sort_order,
                    width_percent,
                    content_type,
                    content,
                    config
                FROM
                    page_cell
                WHERE row_id = :row_id
                ORDER BY sort_order;""")
                .single(call().bind("row_id", rowId))
                .map(PageCell.map())
                .all();
    }

    public void deleteRowsByPage(int pageId) {
        query("DELETE FROM page_row WHERE page_id = :page_id;")
                .single(call().bind("page_id", pageId))
                .delete();
    }

    public int insertRow(int pageId, int sortOrder) {
        return query("INSERT INTO page_row(page_id, sort_order) VALUES(:page_id, :sort_order) RETURNING id;")
                .single(call().bind("page_id", pageId).bind("sort_order", sortOrder))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    public void insertCell(
            int rowId,
            int sortOrder,
            double widthPercent,
            CellContentType contentType,
            String content,
            CellConfig config) {
        query("""
                INSERT
                INTO
                    page_cell(row_id, sort_order, width_percent, content_type, content, config)
                VALUES
                    (:row_id, :sort_order, :width_percent, :content_type, :content, :config::JSONB);""")
                .single(call().bind("row_id", rowId)
                        .bind("sort_order", sortOrder)
                        .bind("width_percent", widthPercent)
                        .bind("content_type", contentType)
                        .bind("content", content)
                        .bind("config", config.toJson()))
                .insert();
    }

    public StationPage loadFullTree(StationPage page) {
        var rows = findRowsByPage(page.id()).stream()
                .map(r -> r.withCells(findCellsByRow(r.id())))
                .toList();
        return page.withRows(rows);
    }

    public PageFile createFile(
            Integer pageId, int stationId, String contentHash, String fileName, String mimeType, long fileSize) {
        return query("""
                INSERT
                INTO
                    page_file(
                        page_id, station_id, content_hash, file_name, mime_type, file_size,
                        default_alt_text, default_description, folder_id)
                VALUES
                    (:page_id, :station_id, :content_hash, :file_name, :mime_type, :file_size, NULL, NULL, NULL)
                RETURNING *;""")
                .single(call().bind("page_id", pageId)
                        .bind("station_id", stationId)
                        .bind("content_hash", contentHash)
                        .bind("file_name", fileName)
                        .bind("mime_type", mimeType)
                        .bind("file_size", fileSize))
                .map(PageFile.map())
                .first()
                .orElseThrow();
    }

    // --- Image operations ---

    public Optional<PageFile> findFile(int fileId) {
        return query("SELECT * FROM page_file WHERE id = :id;")
                .single(call().bind("id", fileId))
                .map(PageFile.map())
                .first();
    }

    public Optional<PageFile> findByStationAndHash(int stationId, String contentHash) {
        return query("""
                SELECT *
                FROM page_file
                WHERE station_id = :station_id AND content_hash = :content_hash
                LIMIT 1;""")
                .single(call().bind("station_id", stationId).bind("content_hash", contentHash))
                .map(PageFile.map())
                .first();
    }

    public List<PageFile> findFilesByPage(int pageId) {
        return query("SELECT * FROM page_file WHERE page_id = :page_id;")
                .single(call().bind("page_id", pageId))
                .map(PageFile.map())
                .all();
    }

    public List<PageFile> findFilesByStation(int stationId) {
        return query("SELECT * FROM page_file WHERE station_id = :station_id ORDER BY uploaded_at DESC;")
                .single(call().bind("station_id", stationId))
                .map(PageFile.map())
                .all();
    }

    public boolean deleteFile(int fileId) {
        return query("DELETE FROM page_file WHERE id = :id;")
                .single(call().bind("id", fileId))
                .delete()
                .changed();
    }

    public boolean updateFileMeta(int fileId, String altText, String description) {
        return query("""
                UPDATE page_file
                SET default_alt_text = :alt, default_description = :description
                WHERE id = :id;""")
                .single(call().bind("id", fileId).bind("alt", altText).bind("description", description))
                .update()
                .changed();
    }

    public List<PageCell> findAllCellsByPage(int pageId) {
        return findRowsByPage(pageId).stream()
                .flatMap(r -> findCellsByRow(r.id()).stream())
                .toList();
    }

    public List<PageCell> findAllCellsByStation(int stationId) {
        return findByStation(stationId).stream()
                .flatMap(p -> findAllCellsByPage(p.id()).stream())
                .toList();
    }

    public void setLandingPage(int stationId, Integer pageId) {
        query("UPDATE station SET landing_page_id = :page_id WHERE id = :station_id;")
                .single(call().bind("page_id", pageId).bind("station_id", stationId))
                .update();
    }

    // --- Landing page ---

    public Optional<Integer> getLandingPageId(int stationId) {
        return query("SELECT landing_page_id FROM station WHERE id = :id;")
                .single(call().bind("id", stationId))
                .map(row -> row.getObject("landing_page_id") != null ? row.getInt("landing_page_id") : null)
                .first();
    }

    /**
     * Lightweight picker result row for the page picker. Exposes only the public UUID — never the
     * internal integer id.
     */
    public record PickerPage(UUID pageUid, String title, String slug, Instant updatedAt) {}
}

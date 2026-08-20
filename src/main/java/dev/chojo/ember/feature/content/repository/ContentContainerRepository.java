/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.repository;

import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentContainer;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Containers and the rows and cells inside them.
 *
 * <p>These are the six methods the page repository used to own, keyed by container rather than by
 * page. Nothing else about them changed, which is the point: the editor reads and writes exactly
 * what it always did, and only what owns the result is different.
 */
@Singleton
public class ContentContainerRepository {

    private static final String CONTAINER_COLUMNS = "id, station_id, created_at";

    public ContentContainer create(int stationId) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO content_container(station_id)
                VALUES (:station_id)
                RETURNING %s;""", call().bind("station_id", stationId), ContentContainer.map(), CONTAINER_COLUMNS);
    }

    public Optional<ContentContainer> findById(int containerId) {
        return SqlSupport.findById("content_container", CONTAINER_COLUMNS, containerId, ContentContainer.map());
    }

    /**
     * Deletes the container and, through the cascade, every row and cell in it.
     */
    public boolean delete(int containerId) {
        return SqlSupport.deleteById("content_container", containerId);
    }

    // --- Rows and cells ---

    public List<ContentRow> findRows(int containerId) {
        return query("""
                SELECT id, container_id, sort_order
                FROM page_row
                WHERE container_id = :container_id
                ORDER BY sort_order;""")
                .single(call().bind("container_id", containerId))
                .map(ContentRow.mapFlat())
                .all();
    }

    public List<ContentCell> findCellsByRow(int rowId) {
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
                .map(ContentCell.map())
                .all();
    }

    public void deleteRows(int containerId) {
        query("DELETE FROM page_row WHERE container_id = :container_id;")
                .single(call().bind("container_id", containerId))
                .delete();
    }

    public int insertRow(int containerId, int sortOrder) {
        return SqlSupport.insertReturning(
                "INSERT INTO page_row(container_id, sort_order) VALUES(:container_id, :sort_order) RETURNING id;",
                call().bind("container_id", containerId).bind("sort_order", sortOrder),
                row -> row.getInt("id"));
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

    /**
     * The container's rows with their cells filled in, in reading order.
     */
    public List<ContentRow> loadRows(int containerId) {
        return findRows(containerId).stream()
                .map(r -> r.withCells(findCellsByRow(r.id())))
                .toList();
    }

    public List<ContentCell> findAllCells(int containerId) {
        return findRows(containerId).stream()
                .flatMap(r -> findCellsByRow(r.id()).stream())
                .toList();
    }

    /**
     * Every cell of every container in the station, whatever owns it. This is what the media
     * reference registry walks: an image in a rich article counts exactly as much as one on a page.
     */
    public List<ContentCell> findAllCellsByStation(int stationId) {
        return query("""
                SELECT
                    c.id,
                    c.row_id,
                    c.sort_order,
                    c.width_percent,
                    c.content_type,
                    c.content,
                    c.config
                FROM page_cell c
                JOIN page_row r ON r.id = c.row_id
                JOIN content_container k ON k.id = r.container_id
                WHERE k.station_id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(ContentCell.map())
                .all();
    }
}

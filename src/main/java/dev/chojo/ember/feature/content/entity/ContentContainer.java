/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The rows and cells of one piece of authored content.
 *
 * <p>A container knows which station it belongs to and nothing else: a page, a news entry and a
 * knowledge-base article each own one, and the container cannot tell which of them it is. That is
 * the whole point of it. Blocks used to hang off a page, which is why the page editor could only
 * ever build a page.
 *
 * <p>The station is {@code null} for content the instance owns. A system news entry is read in
 * every station, so its blocks belong to none of them, the same way the pictures inside it come
 * out of the instance library rather than one station's.
 *
 * <p>The container is the owned side of the relation, so deleting the content that owns it has to
 * delete it explicitly. The reference points the wrong way for the database to do that.
 */
public record ContentContainer(int id, Integer stationId, Instant createdAt) {

    public static RowMapping<ContentContainer> map() {
        return row -> new ContentContainer(
                row.getInt("id"),
                row.getObject("station_id") != null ? row.getInt("station_id") : null,
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}

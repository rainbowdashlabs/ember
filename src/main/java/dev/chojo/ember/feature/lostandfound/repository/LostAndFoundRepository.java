/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.lostandfound.entity.LostAndFoundItem;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class LostAndFoundRepository {

    private static final String ITEM_COLUMNS =
            "id, station_id, description, found_at, image, claimed_by, claimed_at, created_by, created_at";

    public List<LostAndFoundItem> findByStation(int stationId) {
        return Query.query("SELECT " + ITEM_COLUMNS
                        + " FROM lost_and_found_item WHERE station_id = :station_id ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(LostAndFoundItem.map())
                .all();
    }

    public List<LostAndFoundItem> findUnclaimedByStation(int stationId) {
        return Query.query(
                        "SELECT " + ITEM_COLUMNS
                                + " FROM lost_and_found_item WHERE station_id = :station_id AND claimed_by IS NULL ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(LostAndFoundItem.map())
                .all();
    }

    public List<LostAndFoundItem> findUnclaimedOrClaimedBy(int stationId, int memberId) {
        return Query.query(
                        "SELECT " + ITEM_COLUMNS
                                + " FROM lost_and_found_item WHERE station_id = :station_id AND (claimed_by IS NULL OR claimed_by = :member_id) ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId).bind("member_id", memberId))
                .map(LostAndFoundItem.map())
                .all();
    }

    public Optional<LostAndFoundItem> findById(int id) {
        return Query.query("SELECT " + ITEM_COLUMNS + " FROM lost_and_found_item WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(LostAndFoundItem.map())
                .first();
    }

    public LostAndFoundItem create(int stationId, String description, LocalDate foundAt, int createdBy) {
        return Query.query(
                        "INSERT INTO lost_and_found_item(station_id, description, found_at, created_by) VALUES(:station_id, :description, :found_at, :created_by) RETURNING "
                                + ITEM_COLUMNS + ";")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("description", description)
                        .bind("found_at", foundAt)
                        .bind("created_by", createdBy))
                .map(LostAndFoundItem.map())
                .first()
                .orElseThrow();
    }

    public boolean updateImage(int id, byte[] image, String contentType) {
        return Query.query(
                        "UPDATE lost_and_found_item SET image = :image, image_content_type = :content_type WHERE id = :id;")
                .single(Call.of()
                        .bind("image", image)
                        .bind("content_type", contentType)
                        .bind("id", id))
                .update()
                .changed();
    }

    public Optional<ImageData> findImage(int id) {
        return Query.query(
                        "SELECT image, image_content_type FROM lost_and_found_item WHERE id = :id AND image IS NOT NULL;")
                .single(Call.of().bind("id", id))
                .map(row -> new ImageData(row.getBytes("image"), row.getString("image_content_type")))
                .first();
    }

    public boolean claim(int id, int claimedBy) {
        return Query.query(
                        "UPDATE lost_and_found_item SET claimed_by = :claimed_by, claimed_at = :claimed_at WHERE id = :id AND claimed_by IS NULL;")
                .single(Call.of()
                        .bind("claimed_by", claimedBy)
                        .bind("claimed_at", Instant.now(), INSTANT_TIMESTAMP)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM lost_and_found_item WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public record ImageData(byte[] data, String contentType) {}
}

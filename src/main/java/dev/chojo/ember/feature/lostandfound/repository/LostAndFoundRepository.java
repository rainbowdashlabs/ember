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

/**
 * Repository for persisting and querying lost and found items in the database.
 */
@Singleton
public class LostAndFoundRepository {

    private static final String ITEM_COLUMNS =
            "id, station_id, description, found_at, image, claimed_by, claimed_at, created_by, created_at";

    /**
     * Finds all lost and found items for a station, ordered by creation date descending.
     *
     * @param stationId the station to query
     * @return all items including claimed ones
     */
    public List<LostAndFoundItem> findByStation(int stationId) {
        return Query.query("SELECT " + ITEM_COLUMNS
                        + " FROM lost_and_found_item WHERE station_id = :station_id ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(LostAndFoundItem.map())
                .all();
    }

    /**
     * Finds only unclaimed items for a station.
     *
     * @param stationId the station to query
     * @return unclaimed items ordered by creation date descending
     */
    public List<LostAndFoundItem> findUnclaimedByStation(int stationId) {
        return Query.query(
                        "SELECT " + ITEM_COLUMNS
                                + " FROM lost_and_found_item WHERE station_id = :station_id AND claimed_by IS NULL ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId))
                .map(LostAndFoundItem.map())
                .all();
    }

    /**
     * Finds items that are either unclaimed or claimed by a specific member.
     *
     * @param stationId the station to query
     * @param memberId  the member whose claimed items should also be included
     * @return matching items ordered by creation date descending
     */
    public List<LostAndFoundItem> findUnclaimedOrClaimedBy(int stationId, int memberId) {
        return Query.query(
                        "SELECT " + ITEM_COLUMNS
                                + " FROM lost_and_found_item WHERE station_id = :station_id AND (claimed_by IS NULL OR claimed_by = :member_id) ORDER BY created_at DESC;")
                .single(Call.of().bind("station_id", stationId).bind("member_id", memberId))
                .map(LostAndFoundItem.map())
                .all();
    }

    /**
     * Finds a single lost and found item by its ID.
     *
     * @param id the item ID
     * @return the item, or empty if not found
     */
    public Optional<LostAndFoundItem> findById(int id) {
        return Query.query("SELECT " + ITEM_COLUMNS + " FROM lost_and_found_item WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(LostAndFoundItem.map())
                .first();
    }

    /**
     * Creates a new lost and found item.
     *
     * @param stationId   the station where the item was found
     * @param description a description of the item
     * @param foundAt     the date the item was found
     * @param createdBy   the member ID of the person reporting the item
     * @return the created item
     */
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

    /**
     * Updates the image attached to a lost and found item.
     *
     * @param id          the item ID
     * @param image       the image bytes
     * @param contentType the MIME type of the image
     * @return true if the item was updated
     */
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

    /**
     * Retrieves the image data for a lost and found item.
     *
     * @param id the item ID
     * @return the image data and content type, or empty if no image is attached
     */
    public Optional<ImageData> findImage(int id) {
        return Query.query(
                        "SELECT image, image_content_type FROM lost_and_found_item WHERE id = :id AND image IS NOT NULL;")
                .single(Call.of().bind("id", id))
                .map(row -> new ImageData(row.getBytes("image"), row.getString("image_content_type")))
                .first();
    }

    /**
     * Marks an unclaimed item as claimed by a member.
     *
     * @param id        the item ID
     * @param claimedBy the member ID claiming the item
     * @return true if the item was successfully claimed (was previously unclaimed)
     */
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

    /**
     * Deletes a lost and found item.
     *
     * @param id the item ID
     * @return true if the item was deleted
     */
    public boolean delete(int id) {
        return Query.query("DELETE FROM lost_and_found_item WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Container for image binary data and its MIME content type.
     *
     * @param data        the raw image bytes
     * @param contentType the MIME type (e.g. "image/png")
     */
    public record ImageData(byte[] data, String contentType) {}
}

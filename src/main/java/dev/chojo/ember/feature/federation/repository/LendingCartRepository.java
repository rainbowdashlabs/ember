/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.federation.entity.LendingCart;
import dev.chojo.ember.feature.federation.entity.LendingCartLine;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.util.sql.SqlSupport.count;

/**
 * A station's cart and its lines. The cart row is written the first time anything is put in it and
 * goes, with its lines, once it is sent in full.
 */
@Singleton
public class LendingCartRepository {
    private static final String CART_COLUMNS = "station_id, date_from, date_to, occasion, note";
    private static final String LINE_COLUMNS = """
            id, lending_station_uid, inventory_id, art_id, item_id, quantity, inventory_name, line_name, \
            picture_key""";

    public Optional<LendingCart> find(int stationId) {
        return query("SELECT %s FROM lending_cart WHERE station_id = :station_id;", CART_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(LendingCart.map())
                .first();
    }

    /** Sets what the cart asks of every lending station, writing the cart where it had none. */
    public void setDetails(
            int stationId, @Nullable LocalDate dateFrom, @Nullable LocalDate dateTo, String occasion, String note) {
        query("""
                INSERT INTO lending_cart(station_id, date_from, date_to, occasion, note)
                VALUES (:station_id, :date_from, :date_to, :occasion, :note)
                ON CONFLICT (station_id) DO UPDATE
                    SET date_from  = excluded.date_from,
                        date_to    = excluded.date_to,
                        occasion   = excluded.occasion,
                        note       = excluded.note,
                        updated_at = now();""")
                .single(call().bind("station_id", stationId)
                        .bind("date_from", dateFrom)
                        .bind("date_to", dateTo)
                        .bind("occasion", occasion)
                        .bind("note", note))
                .update();
    }

    public List<LendingCartLine> lines(int stationId) {
        return query(
                        "SELECT %s FROM lending_cart_line WHERE station_id = :station_id ORDER BY added_at, id;",
                        LINE_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(LendingCartLine.map())
                .all();
    }

    /**
     * Puts something in the cart. Where the cart already holds the same count, the quantities add up
     * to at most 999, the most a line asks for; a piece already in it stays one piece. The names and the photo follow what was shown last.
     */
    public void addLine(
            int stationId,
            UUID lendingStationUid,
            int inventoryId,
            @Nullable Integer artId,
            @Nullable Integer itemId,
            int quantity,
            String inventoryName,
            String lineName,
            @Nullable String pictureKey) {
        query("""
                INSERT INTO lending_cart(station_id) VALUES (:station_id) ON CONFLICT (station_id) DO NOTHING;""").single(call().bind("station_id", stationId)).insert();
        query("""
                INSERT INTO lending_cart_line(station_id, lending_station_uid, inventory_id, art_id, item_id, quantity,
                                              inventory_name, line_name, picture_key)
                VALUES (:station_id, :lending_station_uid::uuid, :inventory_id, :art_id, :item_id, :quantity,
                        :inventory_name, :line_name, :picture_key)
                ON CONFLICT ON CONSTRAINT lending_cart_line_unique DO UPDATE
                    SET quantity       = CASE
                                             WHEN lending_cart_line.item_id IS NULL
                                                 THEN least(lending_cart_line.quantity + excluded.quantity, 999)
                                             ELSE 1 END,
                        inventory_name = excluded.inventory_name,
                        line_name      = excluded.line_name,
                        picture_key    = excluded.picture_key;""")
                .single(call().bind("station_id", stationId)
                        .bind("lending_station_uid", lendingStationUid, StandardValueConverter.UUID_STRING)
                        .bind("inventory_id", inventoryId)
                        .bind("art_id", artId)
                        .bind("item_id", itemId)
                        .bind("quantity", quantity)
                        .bind("inventory_name", inventoryName)
                        .bind("line_name", lineName)
                        .bind("picture_key", pictureKey))
                .insert();
        touch(stationId);
    }

    /** Sets how many of a counted line are wanted; a piece line is never changed. */
    public boolean setQuantity(int stationId, int lineId, int quantity) {
        boolean changed = query("""
                UPDATE lending_cart_line SET quantity = :quantity
                WHERE id = :id AND station_id = :station_id AND item_id IS NULL;""")
                .single(call().bind("id", lineId).bind("station_id", stationId).bind("quantity", quantity))
                .update()
                .changed();
        if (changed) touch(stationId);
        return changed;
    }

    public boolean removeLine(int stationId, int lineId) {
        boolean changed = query("DELETE FROM lending_cart_line WHERE id = :id AND station_id = :station_id;")
                .single(call().bind("id", lineId).bind("station_id", stationId))
                .delete()
                .changed();
        if (changed) touch(stationId);
        return changed;
    }

    /** Takes lines out of the cart once they were sent, leaving any put in since. */
    public void removeLines(int stationId, List<Integer> lineIds) {
        if (lineIds.isEmpty()) return;
        query("DELETE FROM lending_cart_line WHERE id = ANY(:ids::INT[]) AND station_id = :station_id;")
                .single(call().bind("ids", lineIds, PostgreSqlTypes.INTEGER).bind("station_id", stationId))
                .delete();
        touch(stationId);
    }

    /** Throws the cart away with its days and its note, where it holds no line any more. */
    public void clearIfEmpty(int stationId) {
        query("""
                DELETE FROM lending_cart
                WHERE station_id = :station_id
                  AND NOT EXISTS (SELECT 1 FROM lending_cart_line WHERE station_id = :station_id);""").single(call().bind("station_id", stationId)).delete();
    }

    /** Throws the cart away with its lines, its days and its note. */
    public void clear(int stationId) {
        query("DELETE FROM lending_cart WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .delete();
    }

    /**
     * Claims the cart for one sending. Fails while another sending holds it, unless that one started
     * so long ago that it can only have been cut off.
     *
     * @return whether the cart is now this sending's
     */
    public boolean claimSending(int stationId) {
        return query("""
                UPDATE lending_cart SET sending_at = now()
                WHERE station_id = :station_id
                  AND (sending_at IS NULL OR sending_at < now() - INTERVAL '5 minutes');""").single(call().bind("station_id", stationId)).update().changed();
    }

    public void releaseSending(int stationId) {
        query("UPDATE lending_cart SET sending_at = NULL WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .update();
    }

    public int countLines(int stationId) {
        return count(
                "SELECT count(*) FROM lending_cart_line WHERE station_id = :station_id;",
                call().bind("station_id", stationId));
    }

    private void touch(int stationId) {
        query("UPDATE lending_cart SET updated_at = now() WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .update();
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.tracking.ColumnEntry;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.feature.station.transfer.WireValues.asInstant;

/**
 * Applies the station's own settings columns. The station row itself is created (or picked) by
 * the import service before any table is walked, so importing the {@code station} table during
 * the walk is a no-op — the fields land through {@link #applyFields(int, Map)} instead.
 */
@Singleton
public class StationTableImporter implements TableImporter {
    private static final Logger log = LoggerFactory.getLogger(StationTableImporter.class);
    private final StationRepository stationRepository;
    private final DataTracking tracking;

    @Inject
    public StationTableImporter(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
        DataTracking t;
        try {
            t = DataTrackingLoader.loadFromClasspath();
        } catch (IOException e) {
            log.warn("Could not load data_tracking.json — station fields will not be applied", e);
            t = DataTrackingLoader.empty();
        }
        this.tracking = t;
    }

    private static String columnType(List<ColumnEntry> cols, String name) {
        for (var c : cols) if (c.name().equals(name)) return c.type();
        return null;
    }

    @Override
    public String table() {
        return "station";
    }

    @Override
    public int importRows(StationImportContext context, Object payload) {
        return 0;
    }

    /**
     * Applies the columns from a {@code station} SINGLE payload to the target station row.
     * Performs a single UPDATE keyed by the tracked column list, so any column added to the
     * station SELECTED set automatically flows through here.
     *
     * @param stationId   the destination station
     * @param stationData the station payload from the bundle, or {@code null} when absent
     */
    public void applyFields(int stationId, Map<String, Object> stationData) {
        if (stationData == null || stationData.isEmpty()) return;
        var entry = tracking.tables() == null ? null : tracking.tables().get("station");
        if (entry == null) return;
        var ignored = entry.stationTransfer().ignoredColumns() == null
                ? List.<String>of()
                : entry.stationTransfer().ignoredColumns();

        Map<String, Object> updates = collectUpdates(stationData, entry.columns(), ignored);
        if (updates.isEmpty()) return;

        var columns = entry.columns();
        var c = call().bind("stationId", stationId);
        for (var e : updates.entrySet()) {
            String type = columnType(columns, e.getKey());
            Object val = e.getValue();
            c = switch (type == null ? "" : type) {
                case "bytea" ->
                    c.bind(
                            e.getKey(),
                            val instanceof byte[] b ? b : Base64.getDecoder().decode(val.toString()));
                case "bool" -> c.bind(e.getKey(), val instanceof Boolean b ? b : Boolean.parseBoolean(val.toString()));
                case "int4", "int8" ->
                    c.bind(e.getKey(), val instanceof Number n ? n.intValue() : Integer.parseInt(val.toString()));
                case "timestamptz", "timestamp" ->
                    c.bind(e.getKey(), asInstant(val), StandardValueConverter.INSTANT_TIMESTAMP);
                default -> c.bind(e.getKey(), val.toString());
            };
        }
        query(buildUpdateStatement(updates.keySet(), columns)).single(c).update();

        applySourceUid(stationId, stationData.get("uid"));
    }

    /**
     * Intersects the payload's keys with the table's writable columns. Null values are skipped so
     * the target keeps its own defaults; that also avoids the bytea/varchar binding mismatch on
     * columns like the logo when the source has no value.
     */
    private Map<String, Object> collectUpdates(
            Map<String, Object> stationData, List<ColumnEntry> columns, List<String> ignored) {
        Map<String, Object> updates = new LinkedHashMap<>();
        for (var col : columns) {
            String name = col.name();
            if (name.equals("id")) continue;
            if (name.equals("uid")) continue;
            if (ignored.contains(name)) continue;
            if (!stationData.containsKey(name)) continue;
            Object val = stationData.get(name);
            if (val == null) continue;
            updates.put(name, val);
        }
        return updates;
    }

    private String buildUpdateStatement(Iterable<String> columnNames, List<ColumnEntry> columns) {
        var sb = new StringBuilder("UPDATE station SET ");
        boolean first = true;
        for (var name : columnNames) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(name).append(" = :").append(name);
            String type = columnType(columns, name);
            if ("jsonb".equals(type) || "json".equals(type)) sb.append("::jsonb");
            else if ("uuid".equals(type)) sb.append("::uuid");
        }
        return sb.append(" WHERE id = :stationId;").toString();
    }

    /**
     * Preserves the source UUID so federation pairing codes still work. If the UID already exists
     * on the target instance (e.g. when running source and target in the same database during
     * tests, or when an earlier import already claimed it), the freshly generated target UID is
     * kept.
     */
    private void applySourceUid(int stationId, Object uid) {
        if (uid == null) return;
        try {
            stationRepository.updateUid(stationId, UUID.fromString(uid.toString()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid station UID in import payload, keeping target UID");
        } catch (RuntimeException e) {
            log.warn("Could not apply source UID {} (likely already in use); keeping the target's UID", uid);
        }
    }
}

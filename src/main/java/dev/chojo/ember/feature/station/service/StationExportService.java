/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.engine.GenericTableExporter;
import dev.chojo.ember.tracking.engine.TableOrder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Exports station data for transfer to another Ember instance.
 *
 * <p>Every wire entry is driven by {@code data_tracking.json}:
 * <ul>
 *   <li>The set of exportable tables and their order come from {@link TableOrder}, which
 *       topologically sorts TRACKED tables by FK dependency.</li>
 *   <li>Per-table column selection, FK-flattened lookups, custom scopes, and output shape
 *       come from {@link GenericTableExporter}.</li>
 * </ul>
 *
 * <p>This service deliberately contains no per-table SQL or wire-shape branching — that
 * knowledge lives entirely in the tracking metadata.
 */
@Singleton
public class StationExportService {

    private static final Logger log = LoggerFactory.getLogger(StationExportService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GenericTableExporter engine;
    private final List<String> tableOrder;
    private final String appVersion;
    private final String schemaHash;
    private final StationRepository stationRepository;

    @Inject
    public StationExportService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
        DataTracking tracking;
        try {
            tracking = DataTrackingLoader.loadFromClasspath();
        } catch (IOException e) {
            log.warn("Could not load data_tracking.json — export engine will be unusable", e);
            tracking = DataTrackingLoader.empty();
        }
        this.engine = new GenericTableExporter(tracking);
        this.tableOrder = TableOrder.topological(tracking);
        this.appVersion = loadAppVersion();
        this.schemaHash = tracking.schemaHash() != null ? tracking.schemaHash() : "unknown";
    }

    /** Returns the topologically-sorted list of TRACKED tables. */
    public List<String> getTableOrder() {
        return tableOrder;
    }

    public String getSchemaHash() {
        return schemaHash;
    }

    public String getAppVersion() {
        return appVersion;
    }

    // -- Transfer tokens --

    public String createTransferToken(int stationId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

        query("INSERT INTO transfer_token(station_id, token, expires_at) VALUES(:station_id, :token, :expires_at);")
                .single(call().bind("station_id", stationId)
                        .bind("token", token)
                        .bind("expires_at", expiresAt, StandardValueConverter.INSTANT_TIMESTAMP))
                .insert();

        // Flip the station into read-only mode for the duration of the transfer; cleared on
        // station deletion (existing flow) or by POST /station/transfer/abort.
        stationRepository.markReadOnlyForTransfer(stationId);

        return token;
    }

    /**
     * Clears the station's read-only-for-transfer flag and invalidates every outstanding
     * transfer token for the station. Used when the source operator backs out of a transfer.
     */
    public void abortTransfer(int stationId) {
        query("UPDATE transfer_token SET used = TRUE WHERE station_id = :station_id AND used = FALSE;")
                .single(call().bind("station_id", stationId))
                .update();
        stationRepository.clearReadOnlyForTransfer(stationId);
    }

    public Optional<Integer> validateToken(String token) {
        return query(
                        "SELECT station_id FROM transfer_token WHERE token = :token AND used = FALSE AND expires_at > now();")
                .single(call().bind("token", token))
                .map(row -> row.getInt("station_id"))
                .first();
    }

    /**
     * Records the destination instance URL against the token if it hasn't already been pinned.
     * Called from {@code /public/transfer/{token}/tables} on first pull when the destination
     * sends {@code X-Ember-Importing-From}. Subsequent calls with a different value are
     * ignored — the first pull wins so the banner stays stable.
     */
    public void recordTransferTarget(String token, String targetInstanceUrl) {
        if (targetInstanceUrl == null || targetInstanceUrl.isBlank()) return;
        query("""
                UPDATE transfer_token
                SET target_instance_url = :url
                WHERE token = :token AND target_instance_url IS NULL;
                """)
                .single(call().bind("token", token).bind("url", targetInstanceUrl.trim()))
                .update();
    }

    /**
     * Returns the destination instance URL recorded for any active (or recently-completed)
     * transfer token belonging to {@code stationId}. Surfaced via the station banner so users
     * see where the station is being transferred to.
     */
    public Optional<String> findTransferTarget(int stationId) {
        return query("""
                SELECT target_instance_url
                FROM transfer_token
                WHERE station_id = :station_id
                  AND target_instance_url IS NOT NULL
                ORDER BY expires_at DESC
                LIMIT 1;
                """)
                .single(call().bind("station_id", stationId))
                .map(row -> row.getString("target_instance_url"))
                .first();
    }

    /**
     * Atomically claims the one-shot backend descriptor slot for the given transfer token.
     * Returns the station id when this is the first successful claim; returns empty when the
     * descriptor has already been served for this token (the destination must reuse the value
     * it received on its first call). Callers map empty to {@code 429 Too Many Requests}.
     */
    public Optional<Integer> claimBackendDescriptor(String token) {
        return query("""
                UPDATE transfer_token
                SET backend_fetched_at = now()
                WHERE token = :token
                  AND used = FALSE
                  AND expires_at > now()
                  AND backend_fetched_at IS NULL
                RETURNING station_id;
                """)
                .single(call().bind("token", token))
                .map(row -> row.getInt("station_id"))
                .first();
    }

    public Optional<Integer> validateAndConsumeToken(String token) {
        var result = query(
                        "SELECT station_id FROM transfer_token WHERE token = :token AND used = FALSE AND expires_at > now();")
                .single(call().bind("token", token))
                .map(row -> row.getInt("station_id"))
                .first();

        if (result.isPresent()) {
            query("UPDATE transfer_token SET used = TRUE WHERE token = :token;")
                    .single(call().bind("token", token))
                    .update();
        }

        return result;
    }

    // -- Export --

    /**
     * Exports a single table's data for chunked transfer with pagination. The output map
     * carries {@code table}, {@code appVersion}, {@code offset}, {@code limit} envelope
     * fields plus the table-name keyed payload produced by the engine.
     */
    public Map<String, Object> exportTable(int stationId, String tableName, int offset, int limit) {
        var data = new LinkedHashMap<String, Object>();
        data.put("table", tableName);
        data.put("appVersion", appVersion);
        data.put("offset", offset);
        data.put("limit", limit);
        Object payload = engine.exportShaped(tableName, stationId, offset, limit);
        if (payload != null) data.put(tableName, payload);
        return data;
    }

    private String loadAppVersion() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("version")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (IOException e) {
            // ignore
        }
        return "unknown";
    }
}

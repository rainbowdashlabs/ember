/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.engine.GenericTableExporter;
import dev.chojo.ember.tracking.engine.TableOrder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
    private static final ObjectMapper TOKEN_MAPPER = JsonMapper.builder().build();

    private final GenericTableExporter engine;
    private final List<String> tableOrder;
    private final String appVersion;
    private final String schemaHash;
    private final StationRepository stationRepository;
    private final Api apiConfig;

    @Inject
    public StationExportService(StationRepository stationRepository, Api apiConfig) {
        this.stationRepository = stationRepository;
        this.apiConfig = apiConfig;
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

    /**
     * Returns the topologically-sorted list of TRACKED tables.
     */
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

    /**
     * Mints a single-use transfer token tied to the given station. The operator-visible token
     * is a base64url-encoded JSON object {@code {"host":"…","token":"…"}}; the database stores
     * only the random {@code token} field, and the destination puts only that random value in
     * the outgoing URL path. Keeping the host out of the path avoids dev-server path-matcher
     * quirks around special characters and segment-separator interpretation. The token alone
     * does NOT mark the station read-only — the source operator may revoke an unused token
     * without ever having shared it. The read-only flag is flipped the moment the destination
     * actually starts pulling (first {@code /public/transfer/{token}/tables} call), so the
     * operator can generate a token, change their mind, and abort without ever locking the
     * station.
     */
    public String createTransferToken(int stationId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

        query("INSERT INTO transfer_token(station_id, token, expires_at) VALUES(:station_id, :token, :expires_at);")
                .single(call().bind("station_id", stationId)
                        .bind("token", randomPart)
                        .bind("expires_at", expiresAt, StandardValueConverter.INSTANT_TIMESTAMP))
                .insert();

        String json;
        try {
            json = TOKEN_MAPPER.writeValueAsString(new TransferTokenPayload(apiConfig.baseUrl(), randomPart));
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize transfer token payload", e);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes the operator-visible transfer token (base64url-encoded JSON) into its
     * {@link TransferTokenPayload}. Returns {@code Optional.empty()} when the input is not a
     * valid base64url-encoded JSON object — the caller treats that as an invalid token.
     */
    public static Optional<TransferTokenPayload> parseToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            String json = new String(decoded, StandardCharsets.UTF_8);
            TransferTokenPayload payload = TOKEN_MAPPER.readValue(json, TransferTokenPayload.class);
            if (payload == null || payload.token() == null || payload.token().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(payload);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Wire-shape of the composite transfer token. {@code host} is the URL the destination
     * instance will hit; {@code token} is the random secret stored on the source and put in
     * the outgoing URL path.
     */
    public record TransferTokenPayload(String host, String token) {}

    /**
     * Flips the source station into read-only mode at the moment the destination instance
     * starts pulling. Called from the public token-authenticated tables endpoint, which is the
     * first request the destination makes against the source. Cleared on station deletion
     * (existing cascade) or by {@code POST /station/transfer/abort}.
     */
    public void markTransferStarted(int stationId) {
        stationRepository.markReadOnlyForTransfer(stationId);
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
        return query("""
                        UPDATE transfer_token
                        SET last_activity_at = now()
                        WHERE token = :token AND used = FALSE AND expires_at > now()
                        RETURNING station_id;
                        """)
                .single(call().bind("token", token))
                .map(row -> row.getInt("station_id"))
                .first();
    }

    /**
     * Treats every in-flight transfer as failed: marks every unused transfer token used and
     * clears the read-only-for-transfer flag on every affected station. Called at startup
     * because an in-progress transfer cannot survive a source-instance restart — the
     * destination's HTTP client loses its connection state and the source has no way to
     * resume mid-stream. Without this, a crash mid-transfer would leave the station locked
     * read-only until the 24-hour token expiry or until an operator hit the abort endpoint.
     *
     * @return the number of stations whose read-only flag was cleared
     */
    public int abortAllInFlightTransfers() {
        var stationIds =
                query("""
                        UPDATE transfer_token
                        SET used = TRUE
                        WHERE used = FALSE
                        RETURNING station_id;
                        """).single(call()).map(row -> row.getInt("station_id")).all();
        int cleared = 0;
        for (Integer stationId : stationIds) {
            if (stationRepository.isReadOnlyForTransfer(stationId)) {
                stationRepository.clearReadOnlyForTransfer(stationId);
                cleared++;
            }
        }
        if (cleared > 0) {
            log.warn(
                    "Startup transfer cleanup: invalidated outstanding token(s) and cleared read-only flag on {} station(s)",
                    cleared);
        }
        return cleared;
    }

    /**
     * Marks every transfer token whose destination has been silent for more than
     * {@code idleMinutes} minutes as used, and clears the read-only-for-transfer flag on the
     * affected stations. Used by the watchdog so a destination that crashes or hangs mid-pull
     * does not leave the source station locked indefinitely.
     *
     * @return the number of stations whose read-only flag was cleared
     */
    public int expireStaleTransfers(int idleMinutes) {
        var staleStationIds = query("""
                        UPDATE transfer_token
                        SET used = TRUE
                        WHERE used = FALSE
                          AND last_activity_at < now() - make_interval(mins => :idle_minutes)
                        RETURNING station_id;
                        """)
                .single(call().bind("idle_minutes", idleMinutes))
                .map(row -> row.getInt("station_id"))
                .all();
        if (staleStationIds.isEmpty()) return 0;
        int cleared = 0;
        for (Integer stationId : staleStationIds) {
            if (stationRepository.isReadOnlyForTransfer(stationId)) {
                stationRepository.clearReadOnlyForTransfer(stationId);
                cleared++;
                log.warn(
                        "Transfer for station {} timed out after {} min of inactivity — token invalidated, read-only flag cleared",
                        stationId,
                        idleMinutes);
            }
        }
        return cleared;
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

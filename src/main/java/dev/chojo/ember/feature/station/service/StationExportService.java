/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

/**
 * Exports station data for transfer to another Ember instance.
 * Excludes GDPR-specific data, account credentials, session tokens, and account roles.
 */
@Singleton
public class StationExportService {
    /**
     * Ordered list of table names for chunked export/import.
     * Tables must be in dependency order — earlier tables are imported first.
     */
    public static final List<String> TABLE_ORDER = List.of(
            "station",
            "logo",
            "disabledModules",
            "members",
            "memberRoles",
            "groups",
            "groupMembers",
            "tags",
            "tagMembers",
            "managerRelations",
            "profileFields",
            "profileFieldValues",
            "eventCategories",
            "attendanceTemplates",
            "attendanceTemplateFields",
            "events",
            "inventories",
            "inventorySizes",
            "inventoryItems",
            "forms",
            "formQuestions",
            "kbFolders",
            "kbFiles",
            "kbFileContent",
            "kbFileVersions");

    private static final SecureRandom RANDOM = new SecureRandom();
    private final StationRepository stationRepository;
    private final String appVersion;

    // -- Transfer tokens --

    @Inject
    public StationExportService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
        this.appVersion = loadAppVersion();
    }

    public String createTransferToken(int stationId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

        Query.query(
                        "INSERT INTO transfer_token(station_id, token, expires_at) VALUES(:station_id, :token, :expires_at);")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("token", token)
                        .bind(
                                "expires_at",
                                expiresAt,
                                de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP))
                .insert();

        return token;
    }

    public Optional<Integer> validateToken(String token) {
        return Query.query(
                        "SELECT station_id FROM transfer_token WHERE token = :token AND used = FALSE AND expires_at > now();")
                .single(Call.of().bind("token", token))
                .map(row -> row.getInt("station_id"))
                .first();
    }

    // -- Version --

    public Optional<Integer> validateAndConsumeToken(String token) {
        var result = Query.query(
                        "SELECT station_id FROM transfer_token WHERE token = :token AND used = FALSE AND expires_at > now();")
                .single(Call.of().bind("token", token))
                .map(row -> row.getInt("station_id"))
                .first();

        if (result.isPresent()) {
            Query.query("UPDATE transfer_token SET used = TRUE WHERE token = :token;")
                    .single(Call.of().bind("token", token))
                    .update();
        }

        return result;
    }

    public String getAppVersion() {
        return appVersion;
    }

    /**
     * Exports a single table's data for chunked transfer with pagination.
     */
    public Map<String, Object> exportTable(int stationId, String tableName, int offset, int limit) {
        var data = new LinkedHashMap<String, Object>();
        data.put("table", tableName);
        data.put("appVersion", appVersion);
        data.put("offset", offset);
        data.put("limit", limit);
        populateTable(data, stationId, tableName, offset, limit);
        return data;
    }

    private void populateTable(Map<String, Object> data, int stationId, String tableName, int offset, int limit) {
        switch (tableName) {
            case "station" ->
                stationRepository.findById(stationId).ifPresent(station -> {
                    var s = new LinkedHashMap<String, Object>();
                    s.put("uid", station.uid().toString());
                    s.put("name", station.name());
                    s.put("timezone", station.timezone());
                    s.put("locale", station.locale());
                    data.put("station", s);
                });
            case "disabledModules" ->
                data.put(
                        "disabledModules",
                        stationRepository.findDisabledModules(stationId).stream()
                                .map(Enum::name)
                                .toList());
            case "members" ->
                data.put(
                        "members",
                        queryRows(
                                "SELECT sm.id, sm.display_name, sm.former, a.email AS account_email, a.first_name AS account_first_name, a.last_name AS account_last_name FROM station_member sm LEFT JOIN account a ON a.id = sm.account_id WHERE sm.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "memberRoles" ->
                data.put(
                        "memberRoles",
                        queryRows(
                                "SELECT smr.member_id, r.name AS role_name FROM station_member_role smr JOIN role r ON r.id = smr.role_id JOIN station_member sm ON sm.id = smr.member_id WHERE sm.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "groups" ->
                data.put(
                        "groups",
                        queryRows(
                                "SELECT id, name FROM member_group WHERE station_id = :id", stationId, offset, limit));
            case "groupMembers" ->
                data.put(
                        "groupMembers",
                        queryRows(
                                "SELECT mge.group_id, mge.member_id FROM member_group_entry mge JOIN member_group mg ON mg.id = mge.group_id WHERE mg.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "tags" ->
                data.put(
                        "tags",
                        queryRows("SELECT id, name FROM user_tag WHERE station_id = :id", stationId, offset, limit));
            case "tagMembers" ->
                data.put(
                        "tagMembers",
                        queryRows(
                                "SELECT ute.tag_id, ute.member_id FROM user_tag_entry ute JOIN user_tag ut ON ut.id = ute.tag_id WHERE ut.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "managerRelations" ->
                data.put(
                        "managerRelations",
                        queryRows(
                                "SELECT mm.manager_id, mm.managed_id FROM member_manager mm JOIN station_member sm ON sm.id = mm.manager_id WHERE sm.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "profileFields" ->
                data.put(
                        "profileFields",
                        queryRows(
                                "SELECT id, name, field_type, config, position, scope, keep_on_archive FROM profile_field WHERE station_id = :id ORDER BY position",
                                stationId,
                                offset,
                                limit));
            case "profileFieldValues" ->
                data.put(
                        "profileFieldValues",
                        queryRows(
                                "SELECT pfv.member_id, pfv.field_id, pfv.value FROM profile_field_value pfv JOIN profile_field pf ON pf.id = pfv.field_id WHERE pf.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "eventCategories" ->
                data.put(
                        "eventCategories",
                        queryRows(
                                "SELECT id, name, position FROM event_category WHERE station_id = :id ORDER BY position",
                                stationId,
                                offset,
                                limit));
            case "attendanceTemplates" ->
                data.put(
                        "attendanceTemplates",
                        queryRows(
                                "SELECT id, name FROM attendance_template WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "attendanceTemplateFields" ->
                data.put(
                        "attendanceTemplateFields",
                        queryRows(
                                "SELECT atf.id, atf.template_id, atf.name, atf.field_type, atf.config, atf.position FROM attendance_template_field atf JOIN attendance_template at2 ON at2.id = atf.template_id WHERE at2.station_id = :id ORDER BY atf.position",
                                stationId,
                                offset,
                                limit));
            case "events" ->
                data.put(
                        "events",
                        queryRows(
                                "SELECT id, name, description, event_type, day_of_week, start_time, end_time, template_id, requires_registration, registration_deadline, requires_confirmation, category_id FROM station_event WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "inventories" ->
                data.put(
                        "inventories",
                        queryRows(
                                "SELECT id, name, inventory_type, has_sizes FROM inventory WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "inventorySizes" ->
                data.put(
                        "inventorySizes",
                        queryRows(
                                "SELECT s.id, s.inventory_id, s.label, s.position, s.note FROM inventory_size s JOIN inventory i ON i.id = s.inventory_id WHERE i.station_id = :id ORDER BY s.position",
                                stationId,
                                offset,
                                limit));
            case "inventoryItems" ->
                data.put(
                        "inventoryItems",
                        queryRows(
                                "SELECT ii.id, ii.inventory_id, ii.internal_id, ii.name, ii.size_id, ii.metadata, ii.assigned_to, ii.lost_at, ii.item_source FROM inventory_item ii JOIN inventory i ON i.id = ii.inventory_id WHERE i.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "forms" ->
                data.put(
                        "forms",
                        queryRows(
                                "SELECT id, title, description, status, shuffle_questions, allow_edit, start_at, end_at, closed_at, created_by, created_at, updated_at FROM form WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "formQuestions" ->
                data.put(
                        "formQuestions",
                        queryRows(
                                "SELECT fq.id, fq.form_id, fq.position, fq.question_type, fq.title, fq.description, fq.required, fq.shuffle, fq.config FROM form_question fq JOIN form f ON f.id = fq.form_id WHERE f.station_id = :id ORDER BY fq.position",
                                stationId,
                                offset,
                                limit));
            case "logo" ->
                stationRepository.findLogo(stationId).ifPresent(logo -> {
                    var l = new LinkedHashMap<String, Object>();
                    l.put("data", Base64.getEncoder().encodeToString(logo.data()));
                    l.put("contentType", logo.contentType());
                    data.put("logo", l);
                });
            case "kbFolders" ->
                data.put(
                        "kbFolders",
                        queryRows(
                                "SELECT id, parent_id, name, description, position, restricted, restriction_mode FROM kb_folder WHERE station_id = :id ORDER BY position",
                                stationId,
                                offset,
                                limit));
            case "kbFiles" ->
                data.put(
                        "kbFiles",
                        queryRows(
                                "SELECT id, folder_id, name, description, file_type, position, restricted, restriction_mode FROM kb_file WHERE station_id = :id ORDER BY position",
                                stationId,
                                offset,
                                limit));
            case "kbFileContent" ->
                data.put(
                        "kbFileContent",
                        queryRows(
                                "SELECT kfc.file_id, kfc.text_content FROM kb_file_content kfc JOIN kb_file kf ON kf.id = kfc.file_id WHERE kf.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "kbFileVersions" ->
                data.put(
                        "kbFileVersions",
                        queryRows(
                                "SELECT kfv.file_id, kfv.patch, kfv.is_full, kfv.version, kfv.created_at FROM kb_file_version kfv JOIN kb_file kf ON kf.id = kfv.file_id WHERE kf.station_id = :id ORDER BY kfv.file_id, kfv.version",
                                stationId,
                                offset,
                                limit));
            default -> {
                /* unknown table, skip */
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryRows(String sql, int id, int offset, int limit) {
        String paginatedSql = sql + " OFFSET :offset LIMIT :limit";
        var queryObj = Query.query(paginatedSql)
                .single(Call.of().bind("id", id).bind("offset", offset).bind("limit", limit));
        return (List<Map<String, Object>>) (List<?>) queryObj.map(row -> {
                    var meta = row.getMetaData();
                    var map = new LinkedHashMap<String, Object>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String typeName = meta.getColumnTypeName(i);
                        if ("jsonb".equals(typeName) || "json".equals(typeName)) {
                            map.put(meta.getColumnLabel(i), row.getString(i));
                        } else {
                            map.put(meta.getColumnLabel(i), row.getObject(i));
                        }
                    }
                    return map;
                })
                .all();
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

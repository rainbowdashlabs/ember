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
import java.util.ArrayList;
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
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StationRepository stationRepository;
    private final String appVersion;

    @Inject
    public StationExportService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
        this.appVersion = loadAppVersion();
    }

    // -- Transfer tokens --

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

    public Optional<Integer> validateAndConsumeToken(String token) {
        // Find valid, unused, non-expired token
        var result = Query.query(
                        "SELECT station_id FROM transfer_token WHERE token = :token AND used = FALSE AND expires_at > now();")
                .single(Call.of().bind("token", token))
                .map(row -> row.getInt("station_id"))
                .first();

        if (result.isPresent()) {
            // Mark as used
            Query.query("UPDATE transfer_token SET used = TRUE WHERE token = :token;")
                    .single(Call.of().bind("token", token))
                    .update();
        }

        return result;
    }

    // -- Version --

    public String getAppVersion() {
        return appVersion;
    }

    // -- Export --

    public Map<String, Object> exportStation(int stationId) {
        var data = new LinkedHashMap<String, Object>();

        // Metadata
        data.put("exportType", "Ember Station Export");
        data.put("appVersion", appVersion);
        data.put("exportedAt", Instant.now().toString());

        // Station info
        stationRepository.findById(stationId).ifPresent(station -> {
            var stationData = new LinkedHashMap<String, Object>();
            stationData.put("name", station.name());
            stationData.put("timezone", station.timezone());
            stationData.put("locale", station.locale());
            data.put("station", stationData);
        });

        // Disabled modules
        data.put("disabledModules", new ArrayList<>(stationRepository.findDisabledModules(stationId)));

        // Roles (the role definitions, not account-level roles)
        data.put("roles", queryRows("SELECT id, name FROM role", -1));

        // Members (without account_id — that's instance-specific)
        data.put(
                "members",
                queryRows("SELECT id, display_name, former FROM station_member WHERE station_id = :id", stationId));

        // Member roles
        data.put(
                "memberRoles",
                queryRows(
                        "SELECT smr.member_id, r.name AS role_name FROM station_member_role smr JOIN role r ON r.id = smr.role_id JOIN station_member sm ON sm.id = smr.member_id WHERE sm.station_id = :id",
                        stationId));

        // Groups
        data.put("groups", queryRows("SELECT id, name FROM member_group WHERE station_id = :id", stationId));

        // Group members
        data.put(
                "groupMembers",
                queryRows(
                        "SELECT mge.group_id, mge.member_id FROM member_group_entry mge JOIN member_group mg ON mg.id = mge.group_id WHERE mg.station_id = :id",
                        stationId));

        // Tags
        data.put("tags", queryRows("SELECT id, name FROM user_tag WHERE station_id = :id", stationId));

        // Tag assignments
        data.put(
                "tagMembers",
                queryRows(
                        "SELECT ute.tag_id, ute.member_id FROM user_tag_entry ute JOIN user_tag ut ON ut.id = ute.tag_id WHERE ut.station_id = :id",
                        stationId));

        // Manager relationships
        data.put(
                "managerRelations",
                queryRows(
                        "SELECT mm.manager_id, mm.managed_id FROM member_manager mm JOIN station_member sm ON sm.id = mm.manager_id WHERE sm.station_id = :id",
                        stationId));

        // Profile fields
        data.put(
                "profileFields",
                queryRows(
                        "SELECT id, name, field_type, config, position, scope, keep_on_archive FROM profile_field WHERE station_id = :id ORDER BY position",
                        stationId));

        // Profile field values
        data.put(
                "profileFieldValues",
                queryRows(
                        "SELECT pfv.member_id, pfv.field_id, pfv.value FROM profile_field_value pfv JOIN profile_field pf ON pf.id = pfv.field_id WHERE pf.station_id = :id",
                        stationId));

        // Event categories
        data.put(
                "eventCategories",
                queryRows(
                        "SELECT id, name, position FROM event_category WHERE station_id = :id ORDER BY position",
                        stationId));

        // Events
        data.put(
                "events",
                queryRows(
                        "SELECT id, name, description, event_type, day_of_week, start_time, end_time, template_id, requires_registration, registration_deadline, requires_confirmation, category_id FROM station_event WHERE station_id = :id",
                        stationId));

        // Attendance templates
        data.put(
                "attendanceTemplates",
                queryRows("SELECT id, name FROM attendance_template WHERE station_id = :id", stationId));

        // Attendance template fields
        data.put(
                "attendanceTemplateFields",
                queryRows(
                        "SELECT atf.id, atf.template_id, atf.name, atf.field_type, atf.config, atf.position FROM attendance_template_field atf JOIN attendance_template at2 ON at2.id = atf.template_id WHERE at2.station_id = :id ORDER BY atf.position",
                        stationId));

        // Inventories
        data.put(
                "inventories",
                queryRows(
                        "SELECT id, name, inventory_type, has_sizes FROM inventory WHERE station_id = :id", stationId));

        // Inventory sizes
        data.put(
                "inventorySizes",
                queryRows(
                        "SELECT s.id, s.inventory_id, s.label, s.position, s.note FROM inventory_size s JOIN inventory i ON i.id = s.inventory_id WHERE i.station_id = :id ORDER BY s.position",
                        stationId));

        // Inventory items (without binary metadata like images)
        data.put(
                "inventoryItems",
                queryRows(
                        "SELECT ii.id, ii.inventory_id, ii.internal_id, ii.name, ii.size_id, ii.metadata, ii.assigned_to, ii.lost_at, ii.item_source FROM inventory_item ii JOIN inventory i ON i.id = ii.inventory_id WHERE i.station_id = :id",
                        stationId));

        // Forms
        data.put(
                "forms",
                queryRows(
                        "SELECT id, title, description, status, shuffle_questions, allow_edit, start_at, end_at, closed_at, created_by, created_at, updated_at FROM form WHERE station_id = :id",
                        stationId));

        // Form questions
        data.put(
                "formQuestions",
                queryRows(
                        "SELECT fq.id, fq.form_id, fq.position, fq.question_type, fq.title, fq.description, fq.required, fq.shuffle, fq.config FROM form_question fq JOIN form f ON f.id = fq.form_id WHERE f.station_id = :id ORDER BY fq.position",
                        stationId));

        return data;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryRows(String sql, int id) {
        var queryObj = id == -1
                ? Query.query(sql).single()
                : Query.query(sql).single(Call.of().bind("id", id));
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

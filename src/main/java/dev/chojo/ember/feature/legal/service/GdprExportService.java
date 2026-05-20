/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for exporting all personal data associated with an account or station member
 * in compliance with GDPR/DSGVO data portability requirements (Art. 20 GDPR).
 */
@Singleton
public class GdprExportService {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public GdprExportService(AccountRepository accountRepository, StationMemberRepository stationMemberRepository) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    /**
     * Exports all personal data for an account including account info, sessions, consent records,
     * saved filters, and all station membership data.
     *
     * @param accountId the account to export data for
     * @return a structured map of all personal data suitable for JSON serialization
     */
    public Map<String, Object> exportAccountData(int accountId) {
        var data = new LinkedHashMap<String, Object>();
        data.put("exportType", "GDPR/DSGVO Data Export");
        data.put("exportedAt", java.time.Instant.now().toString());

        // Account info
        accountRepository.findById(accountId).ifPresent(account -> {
            var accountData = new LinkedHashMap<String, Object>();
            accountData.put("id", account.id());
            accountData.put("email", account.email());
            accountData.put("firstName", account.firstName());
            accountData.put("lastName", account.lastName());
            accountData.put("emailVerified", account.emailVerified());
            data.put("account", accountData);
        });

        // Sessions
        data.put(
                "sessions",
                queryRows(
                        "SELECT id, created_at, user_agent, last_used_at, expires_at, location FROM account_session WHERE account_id = :id",
                        accountId));

        // GDPR consent records
        data.put(
                "consentRecords",
                queryRows(
                        "SELECT consent_version, ip_address, country, user_agent, consented_at FROM gdpr_consent WHERE account_id = :id ORDER BY consented_at DESC",
                        accountId));

        // Saved filters
        data.put(
                "savedFilters",
                queryRows(
                        "SELECT table_type, name, filter_data, position FROM saved_filter WHERE account_id = :id",
                        accountId));

        // Station memberships
        var memberships = stationMemberRepository.findAllByAccountId(accountId);
        var stationDataList = new ArrayList<Map<String, Object>>();
        for (var member : memberships) {
            stationDataList.add(exportMemberData(member));
        }
        data.put("stationMemberships", stationDataList);

        return data;
    }

    /**
     * Exports all personal data for a specific station member by member ID.
     *
     * @param memberId the station member ID to export data for
     * @return a structured map of the member's data, or an empty map if not found
     */
    public Map<String, Object> exportMemberData(int memberId) {
        var member = stationMemberRepository.findById(memberId);
        if (member.isEmpty()) return Map.of();
        return exportMemberData(member.get());
    }

    /**
     * Exports all personal data for a station member including roles, profile fields, groups, tags,
     * manager relationships, attendance, events, absences, inventory, forms, notifications, and news.
     *
     * @param member the station member entity to export data for
     * @return a structured map of all member-related personal data
     */
    private Map<String, Object> exportMemberData(StationMember member) {
        int mid = member.id();
        var data = new LinkedHashMap<String, Object>();
        data.put("memberId", mid);
        data.put("stationId", member.stationId());
        data.put("former", member.former());

        // Station name
        var stationName = queryRows("SELECT name FROM station WHERE id = :id", member.stationId());
        if (!stationName.isEmpty()) {
            data.put("stationName", stationName.getFirst().get("name"));
        }

        // Roles
        data.put(
                "roles",
                queryRows(
                        "SELECT r.name AS role FROM station_member_role smr JOIN role r ON r.id = smr.role_id WHERE smr.member_id = :id",
                        mid));

        // Profile field values
        data.put("profileFields", queryRows("""
                SELECT pf.name, pfv.value
                FROM profile_field_value pfv
                JOIN profile_field pf ON pf.id = pfv.field_id
                WHERE pfv.member_id = :id""", mid));

        // Group memberships
        data.put("groups", queryRows("""
                SELECT mg.name
                FROM member_group_entry mge
                JOIN member_group mg ON mg.id = mge.group_id
                WHERE mge.member_id = :id""", mid));

        // Tags
        data.put("tags", queryRows("""
                SELECT ut.name
                FROM user_tag_entry ute
                JOIN user_tag ut ON ut.id = ute.tag_id
                WHERE ute.member_id = :id""", mid));

        // Manager relationships
        data.put("managedBy", queryRows("""
                SELECT sm.id AS manager_member_id
                FROM member_manager mm
                JOIN station_member sm ON sm.id = mm.manager_id
                WHERE mm.managed_id = :id""", mid));
        data.put("manages", queryRows("""
                SELECT sm.id AS managed_member_id
                FROM member_manager mm
                JOIN station_member sm ON sm.id = mm.managed_id
                WHERE mm.manager_id = :id""", mid));

        // Attendance
        data.put("attendance", queryRows("""
                SELECT ae.status, ae.check_in, ae.check_out, ae.source,
                       asess.title, asess.start_time, asess.end_time
                FROM attendance_entry ae
                JOIN attendance_session asess ON asess.id = ae.session_id
                WHERE ae.member_id = :id
                ORDER BY asess.start_time DESC""", mid));

        // Event registrations
        data.put("eventRegistrations", queryRows("""
                SELECT se.name AS event_name, er.event_date, er.status, er.created_at, er.created_by
                FROM event_registration er
                JOIN station_event se ON se.id = er.event_id
                WHERE er.member_id = :id
                ORDER BY er.event_date DESC""", mid));

        // Absences
        data.put("absences", queryRows("""
                SELECT absent_from, absent_until, reason, created_at, created_by
                FROM member_absence
                WHERE member_id = :id
                ORDER BY created_at DESC""", mid));

        // Inventory items assigned
        data.put("inventoryItems", queryRows("""
                SELECT ii.internal_id, ii.name, ii.metadata, i.name AS inventory_name, ii.lost_at
                FROM inventory_item ii
                JOIN inventory i ON i.id = ii.inventory_id
                WHERE ii.assigned_to = :id""", mid));

        // Inventory item history
        data.put("inventoryHistory", queryRows("""
                SELECT ii.name AS item_name, iih.given_out, iih.returned
                FROM inventory_item_history iih
                JOIN inventory_item ii ON ii.id = iih.item_id
                WHERE iih.member_id = :id
                ORDER BY iih.given_out DESC""", mid));

        // Exchange requests
        data.put("exchangeRequests", queryRows("""
                SELECT i.name AS inventory_name, eer.status, eer.reason, eer.created_at, eer.updated_at
                FROM equipment_exchange_request eer
                JOIN inventory i ON i.id = eer.inventory_id
                WHERE eer.member_id = :id
                ORDER BY eer.created_at DESC""", mid));

        // Procurement requests
        data.put("procurementRequests", queryRows("""
                SELECT i.name AS inventory_name, ep.notes, ep.requested_at, ep.fulfilled_at
                FROM equipment_procurement ep
                JOIN inventory i ON i.id = ep.inventory_id
                WHERE ep.member_id = :id
                ORDER BY ep.requested_at DESC""", mid));

        // Form responses with answers
        var formResponses = queryRows("""
                SELECT fr.id AS response_id, f.title AS form_title, f.description AS form_description,
                       fr.submitted_at, fr.updated_at, fr.submitted_by
                FROM form_response fr
                JOIN form f ON f.id = fr.form_id
                WHERE fr.member_id = :id
                ORDER BY fr.submitted_at DESC""", mid);
        for (var response : formResponses) {
            var responseId = (Number) response.get("response_id");
            if (responseId != null) {
                response.put("answers", queryRows("""
                        SELECT fq.title AS question, fq.question_type AS type, fa.value
                        FROM form_answer fa
                        JOIN form_question fq ON fq.id = fa.question_id
                        WHERE fa.response_id = :id
                        ORDER BY fq.position""", responseId.intValue()));
            }
        }
        data.put("formResponses", formResponses);

        // Notifications
        data.put("notifications", queryRows("""
                SELECT type, data, created_at, acknowledged_at
                FROM notification
                WHERE member_id = :id
                ORDER BY created_at DESC""", mid));

        // News authored
        data.put("newsAuthored", queryRows("""
                SELECT title, published_at, created_at
                FROM news
                WHERE author_id = :id
                ORDER BY created_at DESC""", mid));

        // News comments
        data.put("newsComments", queryRows("""
                SELECT n.title AS news_title, nc.content, nc.created_at
                FROM news_comment nc
                JOIN news n ON n.id = nc.news_id
                WHERE nc.author_id = :id
                ORDER BY nc.created_at DESC""", mid));

        // Profile field changes (as subject)
        data.put("profileFieldChanges", queryRows("""
                SELECT pf.name AS field_name, pfc.old_value, pfc.new_value, pfc.changed_at, pfc.changed_by
                FROM profile_field_change pfc
                JOIN profile_field pf ON pf.id = pfc.field_id
                WHERE pfc.member_id = :id
                ORDER BY pfc.changed_at DESC""", mid));

        // Notification settings
        data.put("notificationSettings", queryRows("""
                SELECT notification_type, app_enabled, email_enabled
                FROM user_notification_settings
                WHERE member_id = :id""", mid));

        return data;
    }

    private Object parseJsonValue(String raw) {
        if (raw == null) return null;
        // Strip surrounding quotes from JSON strings: "\"value\"" -> "value"
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1);
        }
        // Try to parse as structured JSON, otherwise return as-is
        try {
            return MAPPER.readValue(raw, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queryRows(String sql, int id) {
        return (List<Map<String, Object>>) (List<?>) Query.query(sql)
                .single(Call.of().bind("id", id))
                .map(row -> {
                    var meta = row.getMetaData();
                    var map = new LinkedHashMap<String, Object>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String typeName = meta.getColumnTypeName(i);
                        if ("jsonb".equals(typeName) || "json".equals(typeName)) {
                            String raw = row.getString(i);
                            map.put(meta.getColumnLabel(i), parseJsonValue(raw));
                        } else {
                            map.put(meta.getColumnLabel(i), row.getObject(i));
                        }
                    }
                    return map;
                })
                .all();
    }
}

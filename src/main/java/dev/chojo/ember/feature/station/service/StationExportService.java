/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import de.chojo.sadu.queries.converter.StandardValueConverter;
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

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

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
            "memberUserTypes",
            "memberPermissions",
            "groups",
            "groupMembers",
            "tags",
            "tagMembers",
            "managerRelations",
            "memberAbsences",
            "profileFields",
            "profileFieldValues",
            "eventCategories",
            "attendanceTemplates",
            "attendanceTemplateFields",
            "attendanceTemplateGroups",
            "attendanceSessions",
            "attendanceSessionFields",
            "attendanceEntries",
            "attendanceReportPresets",
            "events",
            "eventRegistrations",
            "eventComments",
            "eventFields",
            "eventTemplates",
            "eventBreaks",
            "inventories",
            "inventorySizes",
            "inventoryItems",
            "forms",
            "formQuestions",
            "kbFolders",
            "kbFiles",
            "kbFileContent",
            "kbFileVersions",
            "news",
            "newsComments",
            "boards",
            "boardLanes",
            "boardFields",
            "boardLabels",
            "boardTickets",
            "boardTicketComments",
            "boardTicketLabels",
            "boardTicketChecklist",
            "boardTicketLinks",
            "boardTicketWeblinks",
            "boardViewAccess",
            "boardEditAccess",
            "lostAndFound",
            "waitingLists",
            "waitingListFields",
            "waitingListEntries",
            "entityNotes",
            "entityNoteVersions",
            "equipmentExchangeRequests",
            "equipmentExchangeLogs",
            "equipmentProcurements",
            "formResponses",
            "formAnswers",
            "formRestrictions",
            "eventRestrictions",
            "eventLayouts",
            "eventLayoutFields",
            "eventTemplateFields",
            "eventTemplateRestrictions",
            "kbTags",
            "kbFileTags",
            "kbFolderTags",
            "kbAccessRestrictions",
            "kbComments",
            "inventoryChecks",
            "inventoryCheckItems",
            "inventoryItemHistory",
            "inventoryRequirements",
            "newsRestrictions",
            "userSettings",
            "userNotificationSettings");

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

        query("INSERT INTO transfer_token(station_id, token, expires_at) VALUES(:station_id, :token, :expires_at);")
                .single(call().bind("station_id", stationId)
                        .bind("token", token)
                        .bind("expires_at", expiresAt, StandardValueConverter.INSTANT_TIMESTAMP))
                .insert();

        return token;
    }

    public Optional<Integer> validateToken(String token) {
        return query(
                        "SELECT station_id FROM transfer_token WHERE token = :token AND used = FALSE AND expires_at > now();")
                .single(call().bind("token", token))
                .map(row -> row.getInt("station_id"))
                .first();
    }

    // -- Version --

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
            case "memberUserTypes" ->
                data.put(
                        "memberUserTypes",
                        queryRows(
                                "SELECT sm.id AS member_id, sm.user_type FROM station_member sm WHERE sm.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "memberPermissions" ->
                data.put(
                        "memberPermissions",
                        queryRows(
                                "SELECT smp.member_id, sp.name AS permission_name FROM station_member_permission smp JOIN station_permission sp ON sp.id = smp.permission_id JOIN station_member sm ON sm.id = smp.member_id WHERE sm.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "groups" ->
                data.put(
                        "groups",
                        queryRows(
                                "SELECT id, name, color, position FROM member_group WHERE station_id = :id ORDER BY position DESC, name",
                                stationId,
                                offset,
                                limit));
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
                        queryRows(
                                "SELECT id, name, color, visible, position FROM user_tag WHERE station_id = :id ORDER BY position DESC, name",
                                stationId,
                                offset,
                                limit));
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
                                "SELECT id, name, position, public, max_shown_events FROM event_category WHERE station_id = :id ORDER BY position",
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
            case "attendanceTemplateGroups" ->
                data.put(
                        "attendanceTemplateGroups",
                        queryRows(
                                "SELECT atg.template_id, atg.group_id FROM attendance_template_group atg JOIN attendance_template at2 ON at2.id = atg.template_id WHERE at2.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "attendanceSessions" ->
                data.put(
                        "attendanceSessions",
                        queryRows(
                                "SELECT s.id, s.template_id, s.start_time, s.end_time, s.created_at, s.event_id, s.title FROM attendance_session s JOIN attendance_template t ON t.id = s.template_id WHERE t.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "attendanceSessionFields" ->
                data.put(
                        "attendanceSessionFields",
                        queryRows(
                                "SELECT sf.session_id, sf.field_id, sf.value FROM attendance_session_field sf JOIN attendance_session s ON s.id = sf.session_id JOIN attendance_template t ON t.id = s.template_id WHERE t.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "attendanceEntries" ->
                data.put(
                        "attendanceEntries",
                        queryRows(
                                "SELECT ae.id, ae.session_id, ae.member_id, ae.check_in, ae.check_out, ae.status, ae.source FROM attendance_entry ae JOIN attendance_session s ON s.id = ae.session_id JOIN attendance_template t ON t.id = s.template_id WHERE t.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "attendanceReportPresets" ->
                data.put(
                        "attendanceReportPresets",
                        queryRows(
                                "SELECT id, name, role_name, group_id, period, rounding FROM attendance_report_preset WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "events" ->
                data.put(
                        "events",
                        queryRows(
                                "SELECT id, name, description, event_type, day_of_week, start_time, end_time, template_id, requires_registration, registration_deadline, requires_confirmation, category_id, restriction_mode, public, registration_limit, cancelled, cancelled_at, cancel_reason, min_registrations, threshold_date FROM station_event WHERE station_id = :id",
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
                                "SELECT id, title, description, status, shuffle_questions, allow_edit, start_at, end_at, closed_at, created_by, created_at, updated_at, restriction_mode, forced FROM form WHERE station_id = :id",
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
                                "SELECT id, parent_id, name, description, position, restriction_mode FROM kb_folder WHERE station_id = :id ORDER BY position",
                                stationId,
                                offset,
                                limit));
            case "kbFiles" ->
                data.put(
                        "kbFiles",
                        queryRows(
                                "SELECT id, folder_id, name, description, file_type, position, restriction_mode FROM kb_file WHERE station_id = :id ORDER BY position",
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
            case "memberAbsences" ->
                data.put(
                        "memberAbsences",
                        queryRows(
                                "SELECT ma.member_id, ma.absent_from, ma.absent_until, ma.reason, ma.created_at, ma.created_by FROM member_absence ma JOIN station_member sm ON sm.id = ma.member_id WHERE sm.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "eventRegistrations" ->
                data.put(
                        "eventRegistrations",
                        queryRows(
                                "SELECT er.event_id, er.member_id, er.event_date, er.status, er.created_by, er.created_at FROM event_registration er JOIN station_event se ON se.id = er.event_id WHERE se.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "eventComments" ->
                data.put(
                        "eventComments",
                        queryRows(
                                "SELECT ec.id, ec.event_id, ec.parent_id, ec.author_station_uid, ec.author_member_uid, ec.content, ec.deleted, ec.created_at, ec.updated_at FROM event_comment ec JOIN station_event se ON se.id = ec.event_id WHERE se.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "eventFields" ->
                data.put(
                        "eventFields",
                        queryRows(
                                "SELECT ef.id, ef.event_id, ef.name, ef.value, ef.position, ef.field_type, ef.config, ef.overview, ef.attendance_field_id, ef.public FROM event_field ef JOIN station_event se ON se.id = ef.event_id WHERE se.station_id = :id ORDER BY ef.position",
                                stationId,
                                offset,
                                limit));
            case "eventTemplates" ->
                data.put(
                        "eventTemplates",
                        queryRows(
                                "SELECT id, name, title, description, category_id, event_type, requires_registration, registration_deadline_offset, requires_confirmation, restriction_mode, attendance_template_id, registration_limit FROM event_template WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "eventBreaks" ->
                data.put(
                        "eventBreaks",
                        queryRows(
                                "SELECT id, name, start_date, end_date FROM station_event_break WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "news" ->
                data.put(
                        "news",
                        queryRows(
                                "SELECT id, title, content_markdown, content_html, author_station_uid, author_member_uid, published_at, created_at, restriction_mode FROM news WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "newsComments" ->
                data.put(
                        "newsComments",
                        queryRows(
                                "SELECT nc.id, nc.news_id, nc.parent_id, nc.author_station_uid, nc.author_member_uid, nc.content, nc.deleted, nc.created_at FROM news_comment nc JOIN news n ON n.id = nc.news_id WHERE n.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "boards" ->
                data.put(
                        "boards",
                        queryRows(
                                "SELECT id, uid, name, description, short_key, hide_done_after_days, ticket_counter, backlog_lane_id, created_at FROM board WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "boardLanes" ->
                data.put(
                        "boardLanes",
                        queryRows(
                                "SELECT bl.id, bl.board_id, bl.name, bl.color, bl.position FROM board_lane bl JOIN board b ON b.id = bl.board_id WHERE b.station_id = :id ORDER BY bl.position",
                                stationId,
                                offset,
                                limit));
            case "boardFields" ->
                data.put(
                        "boardFields",
                        queryRows(
                                "SELECT bf.id, bf.board_id, bf.name, bf.field_type, bf.config, bf.position FROM board_field bf JOIN board b ON b.id = bf.board_id WHERE b.station_id = :id ORDER BY bf.position",
                                stationId,
                                offset,
                                limit));
            case "boardLabels" ->
                data.put(
                        "boardLabels",
                        queryRows(
                                "SELECT bl.id, bl.board_id, bl.name, bl.color FROM board_label bl JOIN board b ON b.id = bl.board_id WHERE b.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "boardTickets" ->
                data.put(
                        "boardTickets",
                        queryRows(
                                "SELECT bt.id, bt.board_id, bt.lane_id, bt.ticket_number, bt.title, bt.description, bt.assignee_station_uid, bt.assignee_member_uid, bt.priority, bt.due_date, bt.position, bt.creator_station_uid, bt.creator_member_uid, bt.created_at, bt.updated_at, bt.lane_entered_at FROM board_ticket bt JOIN board b ON b.id = bt.board_id WHERE b.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "boardTicketComments" ->
                data.put(
                        "boardTicketComments",
                        queryRows(
                                "SELECT btc.id, btc.ticket_id, btc.parent_id, btc.author_station_uid, btc.author_member_uid, btc.content, btc.deleted, btc.created_at, btc.updated_at FROM board_ticket_comment btc JOIN board_ticket bt ON bt.id = btc.ticket_id JOIN board b ON b.id = bt.board_id WHERE b.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "boardTicketLabels" ->
                data.put(
                        "boardTicketLabels",
                        queryRows(
                                "SELECT btl.ticket_id, btl.label_id FROM board_ticket_label btl JOIN board_ticket bt ON bt.id = btl.ticket_id JOIN board b ON b.id = bt.board_id WHERE b.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "boardTicketChecklist" ->
                data.put(
                        "boardTicketChecklist",
                        queryRows(
                                "SELECT bci.id, bci.ticket_id, bci.title, bci.checked, bci.position FROM board_ticket_checklist_item bci JOIN board_ticket bt ON bt.id = bci.ticket_id JOIN board b ON b.id = bt.board_id WHERE b.station_id = :id ORDER BY bci.position",
                                stationId,
                                offset,
                                limit));
            case "boardTicketLinks" ->
                data.put(
                        "boardTicketLinks",
                        queryRows(
                                "SELECT btl.ticket_id, btl.linked_ticket_id, btl.link_type FROM board_ticket_link btl JOIN board_ticket bt ON bt.id = btl.ticket_id JOIN board b ON b.id = bt.board_id WHERE b.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "boardTicketWeblinks" ->
                data.put(
                        "boardTicketWeblinks",
                        queryRows(
                                "SELECT btw.id, btw.ticket_id, btw.url, btw.title, btw.position FROM board_ticket_weblink btw JOIN board_ticket bt ON bt.id = btw.ticket_id JOIN board b ON b.id = bt.board_id WHERE b.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "boardViewAccess" ->
                data.put(
                        "boardViewAccess",
                        queryRows(
                                "SELECT bva.board_id, bva.user_type, bva.group_id, bva.tag_id FROM board_view_access bva JOIN board b ON b.id = bva.board_id WHERE b.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "boardEditAccess" ->
                data.put(
                        "boardEditAccess",
                        queryRows(
                                "SELECT bea.board_id, bea.user_type, bea.group_id, bea.tag_id FROM board_edit_access bea JOIN board b ON b.id = bea.board_id WHERE b.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "lostAndFound" ->
                data.put(
                        "lostAndFound",
                        queryRows(
                                "SELECT id, description, found_at, claimed_by, claimed_at, created_by, created_at FROM lost_and_found_item WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "waitingLists" ->
                data.put(
                        "waitingLists",
                        queryRows(
                                "SELECT id, name, description, scoring_formula, confirm_interval_days, visible_fields, testing_group_id, join_group_id, join_user_type, attendance_threshold, created_at FROM waiting_list WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "waitingListFields" ->
                data.put(
                        "waitingListFields",
                        queryRows(
                                "SELECT wlf.id, wlf.list_id, wlf.name, wlf.field_type, wlf.config, wlf.position, wlf.required FROM waiting_list_field wlf JOIN waiting_list wl ON wl.id = wlf.list_id WHERE wl.station_id = :id ORDER BY wlf.position",
                                stationId,
                                offset,
                                limit));
            case "waitingListEntries" ->
                data.put(
                        "waitingListEntries",
                        queryRows(
                                "SELECT wle.id, wle.list_id, wle.firstname, wle.lastname, wle.parent_name, wle.email, wle.access_token, wle.status, wle.confirmed_at, wle.reminder_sent_at, wle.created_at, wle.notes, wle.member_id, wle.invited_at, wle.testing_at, wle.joined_at, wle.withdrawn_at, wle.attendance_count FROM waiting_list_entry wle JOIN waiting_list wl ON wl.id = wle.list_id WHERE wl.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "entityNotes" ->
                data.put(
                        "entityNotes",
                        queryRows(
                                "SELECT id, entity_type, entity_id, content, updated_by, updated_at FROM entity_note WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "entityNoteVersions" ->
                data.put(
                        "entityNoteVersions",
                        queryRows(
                                "SELECT env.id, env.note_id, env.diff_patch, env.author_id, env.created_at FROM entity_note_version env JOIN entity_note en ON en.id = env.note_id WHERE en.station_id = :id ORDER BY env.note_id, env.id",
                                stationId,
                                offset,
                                limit));
            case "equipmentExchangeRequests" ->
                data.put(
                        "equipmentExchangeRequests",
                        queryRows(
                                "SELECT id, member_id, item_id, inventory_id, old_size_id, new_size_id, exchanged_item_id, status, reason, created_by, created_at, updated_at FROM equipment_exchange_request WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "equipmentExchangeLogs" ->
                data.put(
                        "equipmentExchangeLogs",
                        queryRows(
                                "SELECT eel.id, eel.request_id, eel.old_status, eel.new_status, eel.changed_by, eel.changed_at, eel.note FROM equipment_exchange_log eel JOIN equipment_exchange_request eer ON eer.id = eel.request_id WHERE eer.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "equipmentProcurements" ->
                data.put(
                        "equipmentProcurements",
                        queryRows(
                                "SELECT id, inventory_id, member_id, size_id, notes, requested_at, fulfilled_at FROM equipment_procurement WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "formResponses" ->
                data.put(
                        "formResponses",
                        queryRows(
                                "SELECT fr.id, fr.form_id, fr.member_id, fr.submitted_by, fr.submitted_at, fr.updated_at FROM form_response fr JOIN form f ON f.id = fr.form_id WHERE f.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "formAnswers" ->
                data.put(
                        "formAnswers",
                        queryRows(
                                "SELECT fa.id, fa.response_id, fa.question_id, fa.value FROM form_answer fa JOIN form_response fr ON fr.id = fa.response_id JOIN form f ON f.id = fr.form_id WHERE f.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "formRestrictions" ->
                data.put(
                        "formRestrictions",
                        queryRows(
                                "SELECT frs.id, frs.form_id, frs.user_type, frs.group_id, frs.tag_id, frs.member_id FROM form_restriction frs JOIN form f ON f.id = frs.form_id WHERE f.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "eventRestrictions" ->
                data.put(
                        "eventRestrictions",
                        queryRows(
                                "SELECT er.id, er.event_id, er.user_type, er.group_id, er.tag_id, er.member_id FROM event_restriction er JOIN station_event se ON se.id = er.event_id WHERE se.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "eventLayouts" ->
                data.put(
                        "eventLayouts",
                        queryRows(
                                "SELECT id, name FROM event_layout WHERE station_id = :id", stationId, offset, limit));
            case "eventLayoutFields" ->
                data.put(
                        "eventLayoutFields",
                        queryRows(
                                "SELECT elf.id, elf.layout_id, elf.name, elf.field_type, elf.config, elf.position, elf.overview, elf.attendance_field_id FROM event_layout_field elf JOIN event_layout el ON el.id = elf.layout_id WHERE el.station_id = :id ORDER BY elf.position",
                                stationId,
                                offset,
                                limit));
            case "eventTemplateFields" ->
                data.put(
                        "eventTemplateFields",
                        queryRows(
                                "SELECT etf.id, etf.template_id, etf.name, etf.field_type, etf.config, etf.position, etf.overview, etf.public, etf.attendance_field_id FROM event_template_field etf JOIN event_template et ON et.id = etf.template_id WHERE et.station_id = :id ORDER BY etf.position",
                                stationId,
                                offset,
                                limit));
            case "eventTemplateRestrictions" ->
                data.put(
                        "eventTemplateRestrictions",
                        queryRows(
                                "SELECT etr.template_id, etr.user_type FROM event_template_restriction etr JOIN event_template et ON et.id = etr.template_id WHERE et.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "kbTags" ->
                data.put(
                        "kbTags",
                        queryRows("SELECT id, name FROM kb_tag WHERE station_id = :id", stationId, offset, limit));
            case "kbFileTags" ->
                data.put(
                        "kbFileTags",
                        queryRows(
                                "SELECT kft.file_id, kft.tag_id FROM kb_file_tag kft JOIN kb_file kf ON kf.id = kft.file_id WHERE kf.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "kbFolderTags" ->
                data.put(
                        "kbFolderTags",
                        queryRows(
                                "SELECT kfot.folder_id, kfot.tag_id FROM kb_folder_tag kfot JOIN kb_folder kfo ON kfo.id = kfot.folder_id WHERE kfo.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "kbAccessRestrictions" ->
                data.put(
                        "kbAccessRestrictions",
                        queryRows(
                                "SELECT kar.id, kar.folder_id, kar.file_id, kar.user_type, kar.group_id, kar.tag_id, kar.member_id FROM kb_access_restriction kar LEFT JOIN kb_folder kfo ON kfo.id = kar.folder_id LEFT JOIN kb_file kf ON kf.id = kar.file_id WHERE kfo.station_id = :id OR kf.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "kbComments" ->
                data.put(
                        "kbComments",
                        queryRows(
                                "SELECT kc.id, kc.file_id, kc.parent_id, kc.author_station_uid, kc.author_member_uid, kc.content, kc.deleted, kc.created_at, kc.updated_at FROM kb_comment kc JOIN kb_file kf ON kf.id = kc.file_id WHERE kf.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "inventoryChecks" ->
                data.put(
                        "inventoryChecks",
                        queryRows(
                                "SELECT id, member_id, checked_by, checked_at FROM inventory_check WHERE station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "inventoryCheckItems" ->
                data.put(
                        "inventoryCheckItems",
                        queryRows(
                                "SELECT ici.id, ici.check_id, ici.item_id, ici.inventory_id, ici.result, ici.note FROM inventory_check_item ici JOIN inventory_check ic ON ic.id = ici.check_id WHERE ic.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "inventoryItemHistory" ->
                data.put(
                        "inventoryItemHistory",
                        queryRows(
                                "SELECT iih.id, iih.item_id, iih.member_id, iih.member_name, iih.given_out, iih.returned FROM inventory_item_history iih JOIN inventory_item ii ON ii.id = iih.item_id JOIN inventory i ON i.id = ii.inventory_id WHERE i.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "inventoryRequirements" ->
                data.put(
                        "inventoryRequirements",
                        queryRows(
                                "SELECT ir.id, ir.inventory_id, ir.user_type, ir.group_id, ir.quantity, ir.position FROM inventory_requirement ir JOIN inventory i ON i.id = ir.inventory_id WHERE i.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "newsRestrictions" ->
                data.put(
                        "newsRestrictions",
                        queryRows(
                                "SELECT nr.id, nr.news_id, nr.user_type, nr.group_id, nr.tag_id, nr.member_id FROM news_restriction nr JOIN news n ON n.id = nr.news_id WHERE n.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "userSettings" ->
                data.put(
                        "userSettings",
                        queryRows(
                                "SELECT us.member_id, us.email_enabled, us.theme, us.dark_mode, us.feel FROM user_settings us JOIN station_member sm ON sm.id = us.member_id WHERE sm.station_id = :id",
                                stationId,
                                offset,
                                limit));
            case "userNotificationSettings" ->
                data.put(
                        "userNotificationSettings",
                        queryRows(
                                "SELECT uns.member_id, uns.notification_type, uns.app_enabled, uns.email_enabled, uns.feed_enabled FROM user_notification_settings uns JOIN station_member sm ON sm.id = uns.member_id WHERE sm.station_id = :id",
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
        var queryObj = query(paginatedSql)
                .single(call().bind("id", id).bind("offset", offset).bind("limit", limit));
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

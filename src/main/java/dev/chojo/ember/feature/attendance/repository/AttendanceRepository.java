/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.attendance.entity.AttendanceReportPreset;
import dev.chojo.ember.feature.attendance.entity.AttendanceSession;
import dev.chojo.ember.feature.attendance.entity.AttendanceSessionField;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplate;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplateField;
import dev.chojo.ember.feature.attendance.entity.SessionSummary;
import dev.chojo.ember.feature.members.entity.MemberAbsence;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for attendance data access, covering templates, sessions, entries, absences, and report presets.
 */
@Singleton
public class AttendanceRepository {

    // -- Templates --

    /**
     * Finds an attendance template by its ID.
     *
     * @param id the template ID
     * @return the template if found
     */
    public Optional<AttendanceTemplate> findTemplateById(int id) {
        return query("SELECT id, station_id, name FROM attendance_template WHERE id = :id;")
                .single(call().bind("id", id))
                .map(AttendanceTemplate.map())
                .first();
    }

    /**
     * Finds all attendance templates belonging to a station.
     *
     * @param stationId the station ID
     * @return list of templates for the station
     */
    public List<AttendanceTemplate> findTemplatesByStation(int stationId) {
        return query("SELECT id, station_id, name FROM attendance_template WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .map(AttendanceTemplate.map())
                .all();
    }

    /**
     * Creates a new attendance template for a station.
     *
     * @param stationId the station ID
     * @param name      the template name
     * @return the created template
     */
    public AttendanceTemplate createTemplate(int stationId, String name) {
        return query(
                        "INSERT INTO attendance_template(station_id, name) VALUES(:station_id, :name) RETURNING id, station_id, name;")
                .single(call().bind("station_id", stationId).bind("name", name))
                .map(AttendanceTemplate.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates the name of an attendance template.
     *
     * @param id   the template ID
     * @param name the new name
     * @return {@code true} if the template was updated
     */
    public boolean updateTemplate(int id, String name) {
        return query("UPDATE attendance_template SET name = :name WHERE id = :id;")
                .single(call().bind("name", name).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an attendance template by its ID.
     *
     * @param id the template ID
     * @return {@code true} if the template was deleted
     */
    public boolean deleteTemplate(int id) {
        return query("DELETE FROM attendance_template WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    // -- Template Fields --

    /**
     * Finds all fields for a template, ordered by position.
     *
     * @param templateId the template ID
     * @return list of template fields
     */
    public List<AttendanceTemplateField> findTemplateFields(int templateId) {
        return query(
                        "SELECT id, template_id, name, field_type, config, position FROM attendance_template_field WHERE template_id = :template_id ORDER BY position;")
                .single(call().bind("template_id", templateId))
                .map(AttendanceTemplateField.map())
                .all();
    }

    /**
     * Creates a new field for a template.
     *
     * @param templateId the template ID
     * @param name       field display name
     * @param fieldType  the type of field
     * @param config     JSONB configuration string
     * @param position   ordering position
     */
    public void createTemplateField(
            int templateId, String name, AttendanceFieldType fieldType, AttendanceFieldConfig config, int position) {
        query("""
                INSERT
                INTO
                    attendance_template_field(template_id, name, field_type, config, position)
                VALUES
                    (:template_id, :name, :field_type, :config::JSONB, :position);""")
                .single(call().bind("template_id", templateId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position))
                .insert();
    }

    /**
     * Updates an existing template field.
     *
     * @param id        the field ID
     * @param name      new display name
     * @param fieldType new field type
     * @param config    new JSONB configuration
     * @param position  new ordering position
     * @return {@code true} if the field was updated
     */
    public boolean updateTemplateField(
            int id, String name, AttendanceFieldType fieldType, AttendanceFieldConfig config, int position) {
        return query("""
                UPDATE attendance_template_field
                SET
                    name       = :name,
                    field_type = :field_type,
                    config     = :config::JSONB,
                    position   = :position
                WHERE id = :id;""")
                .single(call().bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes a template field by its ID.
     *
     * @param id the field ID
     * @return {@code true} if the field was deleted
     */
    public boolean deleteTemplateField(int id) {
        return query("DELETE FROM attendance_template_field WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    // -- Template Groups --

    /**
     * Finds all group associations for a template, ordered by position.
     *
     * @param templateId the template ID
     * @return list of template group associations
     */
    public List<TemplateGroup> findTemplateGroups(int templateId) {
        return query(
                        "SELECT group_id, position FROM attendance_template_group WHERE template_id = :template_id ORDER BY position;")
                .single(call().bind("template_id", templateId))
                .map(row -> new TemplateGroup(row.getInt("group_id"), row.getInt("position")))
                .all();
    }

    /**
     * Replaces all group associations for a template with the given list.
     *
     * @param templateId the template ID
     * @param groups     the new group associations to set
     */
    public void setTemplateGroups(int templateId, List<TemplateGroup> groups) {
        query("DELETE FROM attendance_template_group WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
        for (TemplateGroup group : groups) {
            query(
                            "INSERT INTO attendance_template_group(template_id, group_id, position) VALUES(:template_id, :group_id, :position);")
                    .single(call().bind("template_id", templateId)
                            .bind("group_id", group.groupId())
                            .bind("position", group.position()))
                    .insert();
        }
    }

    /**
     * Finds an attendance session by its ID.
     *
     * @param id the session ID
     * @return the session if found
     */
    public Optional<AttendanceSession> findSessionById(int id) {
        return query(
                        "SELECT id, template_id, start_time, end_time, created_at, event_id, title FROM attendance_session WHERE id = :id;")
                .single(call().bind("id", id))
                .map(AttendanceSession.map())
                .first();
    }

    // -- Sessions --

    /**
     * Finds all sessions for a template, ordered by creation date descending.
     *
     * @param templateId the template ID
     * @return list of sessions
     */
    public List<AttendanceSession> findSessionsByTemplate(int templateId) {
        return query("""
                SELECT id, template_id, start_time, end_time, created_at, event_id, title
                FROM attendance_session
                WHERE template_id = :template_id
                ORDER BY created_at DESC;""")
                .single(call().bind("template_id", templateId))
                .map(AttendanceSession.map())
                .all();
    }

    /**
     * Finds session summaries with attendance counts for all sessions in a station.
     *
     * @param stationId the station ID
     * @return list of session summaries with present/absent/declined/unconfirmed counts
     */
    public List<SessionSummary> findSessionSummariesByStation(int stationId) {
        return query("""
                SELECT s.id, s.template_id, s.start_time, s.end_time, s.created_at, s.event_id, s.title,
                       count(e.id) FILTER (WHERE e.status = 'PRESENT') AS present_count,
                       count(e.id) FILTER (WHERE e.status = 'ABSENT') AS absent_count,
                       count(e.id) FILTER (WHERE e.status = 'DECLINED') AS declined_count,
                       count(e.id) FILTER (WHERE e.status = 'UNCONFIRMED') AS unconfirmed_count
                FROM attendance_session s
                JOIN attendance_template t ON t.id = s.template_id
                LEFT JOIN attendance_entry e ON e.session_id = s.id
                WHERE t.station_id = :station_id
                GROUP BY s.id
                ORDER BY s.created_at DESC;""")
                .single(call().bind("station_id", stationId))
                .map(row -> new SessionSummary(
                        row.getInt("id"),
                        row.getInt("template_id"),
                        row.get("start_time", INSTANT_TIMESTAMP),
                        row.get("end_time", INSTANT_TIMESTAMP),
                        row.get("created_at", INSTANT_TIMESTAMP),
                        row.getObject("event_id", Integer.class),
                        row.getString("title"),
                        row.getInt("present_count"),
                        row.getInt("absent_count"),
                        row.getInt("declined_count"),
                        row.getInt("unconfirmed_count")))
                .all();
    }

    /**
     * Finds the most recent session linked to an event.
     *
     * @param eventId the event ID
     * @return the session if found
     */
    public Optional<AttendanceSession> findSessionByEventId(int eventId) {
        return query(
                        "SELECT id, template_id, start_time, end_time, created_at, event_id, title FROM attendance_session WHERE event_id = :event_id ORDER BY created_at DESC LIMIT 1;")
                .single(call().bind("event_id", eventId))
                .map(AttendanceSession.map())
                .first();
    }

    /**
     * Creates a new attendance session.
     *
     * @param templateId the template this session is based on
     * @param startTime  session start time
     * @param endTime    session end time
     * @param eventId    optional linked event ID
     * @param title      session title
     * @return the created session
     */
    public AttendanceSession createSession(
            int templateId, Instant startTime, Instant endTime, Integer eventId, String title) {
        return query(
                        "INSERT INTO attendance_session(template_id, start_time, end_time, event_id, title) VALUES(:template_id, :start_time, :end_time, :event_id, :title) RETURNING id, template_id, start_time, end_time, created_at, event_id, title;")
                .single(call().bind("template_id", templateId)
                        .bind("start_time", startTime, INSTANT_TIMESTAMP)
                        .bind("end_time", endTime, INSTANT_TIMESTAMP)
                        .bind("event_id", eventId)
                        .bind("title", title))
                .map(AttendanceSession.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates an attendance session's times and title.
     *
     * @param id        the session ID
     * @param startTime new start time
     * @param endTime   new end time
     * @param title     new title
     * @return {@code true} if the session was updated
     */
    public boolean updateSession(int id, Instant startTime, Instant endTime, String title) {
        return query(
                        "UPDATE attendance_session SET start_time = :start_time, end_time = :end_time, title = :title WHERE id = :id;")
                .single(call().bind("start_time", startTime, INSTANT_TIMESTAMP)
                        .bind("end_time", endTime, INSTANT_TIMESTAMP)
                        .bind("title", title)
                        .bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an attendance session by its ID.
     *
     * @param id the session ID
     * @return {@code true} if the session was deleted
     */
    public boolean deleteSession(int id) {
        return query("DELETE FROM attendance_session WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Finds all field values for a session.
     *
     * @param sessionId the session ID
     * @return list of session field values
     */
    public List<AttendanceSessionField> findSessionFields(int sessionId) {
        return query("SELECT session_id, field_id, value FROM attendance_session_field WHERE session_id = :session_id;")
                .single(call().bind("session_id", sessionId))
                .map(AttendanceSessionField.map())
                .all();
    }

    /**
     * Upserts a field value for a session. Inserts or updates on conflict.
     *
     * @param sessionId the session ID
     * @param fieldId   the template field ID
     * @param value     the JSONB value to store
     */
    public void setSessionField(int sessionId, int fieldId, String value) {
        query("""
                INSERT
                INTO
                    attendance_session_field(session_id, field_id, value)
                VALUES
                    (:session_id, :field_id, :value::JSONB)
                ON CONFLICT (session_id, field_id) DO UPDATE SET
                    value = excluded.value;""")
                .single(call().bind("session_id", sessionId)
                        .bind("field_id", fieldId)
                        .bind("value", value))
                .insert();
    }

    // -- Session Fields --

    /**
     * Deletes a specific field value from a session.
     *
     * @param sessionId the session ID
     * @param fieldId   the field ID
     */
    public void deleteSessionField(int sessionId, int fieldId) {
        query("DELETE FROM attendance_session_field WHERE session_id = :session_id AND field_id = :field_id;")
                .single(call().bind("session_id", sessionId).bind("field_id", fieldId))
                .delete()
                .changed();
    }

    /**
     * Finds all attendance entries for a session, ordered by member name.
     *
     * @param sessionId the session ID
     * @return list of attendance entries
     */
    public List<AttendanceEntry> findEntries(int sessionId) {
        return query("""
                SELECT e.id, e.session_id, e.member_id, e.status, e.check_in, e.check_out, e.source
                FROM attendance_entry e
                JOIN station_member sm ON sm.id = e.member_id
                LEFT JOIN account a ON a.id = sm.account_id
                WHERE e.session_id = :session_id
                ORDER BY a.full_name;""")
                .single(call().bind("session_id", sessionId))
                .map(AttendanceEntry.map())
                .all();
    }

    /**
     * Finds a specific attendance entry for a member in a session.
     *
     * @param sessionId the session ID
     * @param memberId  the member ID
     * @return the entry if found
     */
    public Optional<AttendanceEntry> findEntry(int sessionId, int memberId) {
        return query("""
                SELECT
                    id,
                    session_id,
                    member_id,
                    status,
                    check_in,
                    check_out,
                    source
                FROM
                    attendance_entry
                WHERE session_id = :session_id
                  AND member_id = :member_id;""")
                .single(call().bind("session_id", sessionId).bind("member_id", memberId))
                .map(AttendanceEntry.map())
                .first();
    }

    /**
     * Finds an attendance entry by its ID.
     *
     * @param id the entry ID
     * @return the entry if found
     */
    public Optional<AttendanceEntry> findEntryById(int id) {
        return query("""
                SELECT
                    id,
                    session_id,
                    member_id,
                    status,
                    check_in,
                    check_out,
                    source
                FROM
                    attendance_entry
                WHERE id = :id;""")
                .single(call().bind("id", id))
                .map(AttendanceEntry.map())
                .first();
    }

    // -- Entries --

    /**
     * Finds all attendance entries for a specific member across all sessions.
     *
     * @param memberId the member ID
     * @return list of attendance entries
     */
    public List<AttendanceEntry> findEntriesByMember(int memberId) {
        return query(
                        "SELECT id, session_id, member_id, status, check_in, check_out, source FROM attendance_entry WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .map(AttendanceEntry.map())
                .all();
    }

    /**
     * Creates a new attendance entry for a member in a session.
     *
     * @param sessionId the session ID
     * @param memberId  the member ID
     * @param status    initial attendance status
     * @param source    how the entry was created
     */
    public void createEntry(
            int sessionId, int memberId, AttendanceEntry.AttendanceStatus status, AttendanceEntry.EntrySource source) {
        query(
                        "INSERT INTO attendance_entry(session_id, member_id, status, source) VALUES(:session_id, :member_id, :status, :source);")
                .single(call().bind("session_id", sessionId)
                        .bind("member_id", memberId)
                        .bind("status", status)
                        .bind("source", source))
                .insert();
    }

    /**
     * Records the check-in time for an attendance entry.
     *
     * @param id      the entry ID
     * @param checkIn the check-in timestamp
     * @return {@code true} if the entry was updated
     */
    public boolean checkIn(int id, Instant checkIn) {
        return query("UPDATE attendance_entry SET check_in = :check_in WHERE id = :id;")
                .single(call().bind("check_in", checkIn, INSTANT_TIMESTAMP).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Records the check-out time for an attendance entry.
     *
     * @param id       the entry ID
     * @param checkOut the check-out timestamp
     * @return {@code true} if the entry was updated
     */
    public boolean checkOut(int id, Instant checkOut) {
        return query("UPDATE attendance_entry SET check_out = :check_out WHERE id = :id;")
                .single(call().bind("check_out", checkOut, INSTANT_TIMESTAMP).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Resets check-in and check-out times to {@code null} for an attendance entry.
     *
     * @param id the entry ID
     * @return {@code true} if the entry was updated
     */
    public boolean resetTimes(int id) {
        return query("UPDATE attendance_entry SET check_in = NULL, check_out = NULL WHERE id = :id;")
                .single(call().bind("id", id))
                .update()
                .changed();
    }

    /**
     * Updates the attendance status of an entry.
     *
     * @param id     the entry ID
     * @param status the new status
     * @return {@code true} if the entry was updated
     */
    public boolean updateEntryStatus(int id, AttendanceEntry.AttendanceStatus status) {
        return query("UPDATE attendance_entry SET status = :status WHERE id = :id;")
                .single(call().bind("status", status).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Deletes an attendance entry by its ID.
     *
     * @param id the entry ID
     * @return {@code true} if the entry was deleted
     */
    public boolean deleteEntry(int id) {
        return query("DELETE FROM attendance_entry WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Finds all sessions for a station within a time range, ordered by start time.
     *
     * @param stationId the station ID
     * @param from      start of the range (inclusive)
     * @param to        end of the range (exclusive)
     * @return list of sessions in the range
     */
    public List<AttendanceSession> findSessionsByStationInRange(int stationId, Instant from, Instant to) {
        return query("""
                SELECT s.id, s.template_id, s.start_time, s.end_time, s.created_at, s.event_id, s.title
                FROM attendance_session s
                JOIN attendance_template t ON t.id = s.template_id
                WHERE t.station_id = :station_id
                  AND s.start_time >= :from_time
                  AND s.start_time < :to_time
                ORDER BY s.start_time;""")
                .single(call().bind("station_id", stationId)
                        .bind("from_time", from, INSTANT_TIMESTAMP)
                        .bind("to_time", to, INSTANT_TIMESTAMP))
                .map(AttendanceSession.map())
                .all();
    }

    public List<AttendanceSession> findRecentSessions(int stationId, int limit) {
        return query("""
                SELECT s.id, s.template_id, s.start_time, s.end_time, s.created_at, s.event_id, s.title
                FROM attendance_session s
                JOIN attendance_template t ON t.id = s.template_id
                WHERE t.station_id = :station_id
                ORDER BY s.start_time DESC
                LIMIT :limit;""")
                .single(call().bind("station_id", stationId).bind("limit", limit))
                .map(AttendanceSession.map())
                .all();
    }

    /**
     * Finds all member IDs in a station that have a specific user type.
     *
     * @param stationId the station ID
     * @param userType  the user type to filter by
     * @return list of member IDs
     */
    public List<Integer> findMemberIdsByUserType(int stationId, StationUserType userType) {
        return query("""
                SELECT sm.id
                FROM station_member sm
                WHERE sm.station_id = :station_id AND sm.user_type = :user_type;""")
                .single(call().bind("station_id", stationId).bind("user_type", userType))
                .map(row -> row.getInt("id"))
                .all();
    }

    // -- Export Queries --

    /**
     * Finds all member IDs belonging to a specific group.
     *
     * @param groupId the group ID
     * @return list of member IDs
     */
    public List<Integer> findMemberIdsByGroup(int groupId) {
        return query("""
                SELECT member_id FROM member_group_entry WHERE group_id = :group_id;""")
                .single(call().bind("group_id", groupId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    /**
     * Finds all report presets for a station, ordered by name.
     *
     * @param stationId the station ID
     * @return list of report presets
     */
    public List<AttendanceReportPreset> findPresets(int stationId) {
        return query(
                        "SELECT id, station_id, name, role_name, group_id, period, rounding FROM attendance_report_preset WHERE station_id = :station_id ORDER BY name;")
                .single(call().bind("station_id", stationId))
                .map(AttendanceReportPreset.map())
                .all();
    }

    /**
     * Creates a new report preset for a station.
     *
     * @param stationId the station ID
     * @param name      preset display name
     * @param userType  optional user-type filter
     * @param groupId   optional group filter
     * @param period    time period granularity
     * @param rounding  hour rounding mode
     * @return the created preset
     */
    public AttendanceReportPreset createPreset(
            int stationId, String name, StationUserType userType, Integer groupId, String period, String rounding) {
        return query(
                        "INSERT INTO attendance_report_preset(station_id, name, role_name, group_id, period, rounding) VALUES(:station_id, :name, :role_name, :group_id, :period, :rounding) RETURNING id, station_id, name, role_name, group_id, period, rounding;")
                .single(call().bind("station_id", stationId)
                        .bind("name", name)
                        .bind("role_name", userType)
                        .bind("group_id", groupId)
                        .bind("period", period)
                        .bind("rounding", rounding))
                .map(AttendanceReportPreset.map())
                .first()
                .orElseThrow();
    }

    // -- Report Presets --

    /**
     * Deletes a report preset by its ID.
     *
     * @param id the preset ID
     * @return {@code true} if the preset was deleted
     */
    public boolean deletePreset(int id) {
        return query("DELETE FROM attendance_report_preset WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Creates a new absence record for a member.
     *
     * @param memberId    the member ID
     * @param absentFrom  start date of the absence
     * @param absentUntil end date of the absence
     * @param reason      optional reason for the absence
     * @param createdBy   optional ID of the member who created the absence (for managed members)
     * @return the created absence
     */
    public MemberAbsence createAbsence(
            int memberId, LocalDate absentFrom, LocalDate absentUntil, String reason, Integer createdBy) {
        return query(
                        "INSERT INTO member_absence(member_id, absent_from, absent_until, reason, created_by) VALUES(:member_id, :absent_from, :absent_until, :reason, :created_by) RETURNING id, member_id, absent_from, absent_until, reason, created_at, created_by;")
                .single(call().bind("member_id", memberId)
                        .bind("absent_from", absentFrom)
                        .bind("absent_until", absentUntil)
                        .bind("reason", reason)
                        .bind("created_by", createdBy))
                .map(MemberAbsence.map())
                .first()
                .orElseThrow();
    }

    /**
     * Finds an absence record by its ID.
     *
     * @param id the absence ID
     * @return the absence if found
     */
    public Optional<MemberAbsence> findAbsenceById(int id) {
        return query(
                        "SELECT id, member_id, absent_from, absent_until, reason, created_at, created_by FROM member_absence WHERE id = :id;")
                .single(call().bind("id", id))
                .map(MemberAbsence.map())
                .first();
    }

    // -- Absences --

    /**
     * Finds all absences for a member, ordered by end date descending.
     *
     * @param memberId the member ID
     * @return list of absences
     */
    public List<MemberAbsence> findAbsencesByMember(int memberId) {
        return query(
                        "SELECT id, member_id, absent_from, absent_until, reason, created_at, created_by FROM member_absence WHERE member_id = :member_id ORDER BY absent_until DESC;")
                .single(call().bind("member_id", memberId))
                .map(MemberAbsence.map())
                .all();
    }

    /**
     * Finds all currently active absences for a station (where today falls within the absence range).
     *
     * @param stationId the station ID
     * @return list of active absences
     */
    public List<MemberAbsence> findActiveAbsencesByStation(int stationId) {
        return query("""
                SELECT ma.id, ma.member_id, ma.absent_from, ma.absent_until, ma.reason, ma.created_at, ma.created_by
                FROM member_absence ma
                    JOIN station_member sm ON ma.member_id = sm.id
                WHERE sm.station_id = :station_id
                  AND ma.absent_from <= current_date
                  AND ma.absent_until >= current_date
                ORDER BY ma.absent_until;""")
                .single(call().bind("station_id", stationId))
                .map(MemberAbsence.map())
                .all();
    }

    /**
     * Finds all absences for a station that are active on a specific date.
     *
     * @param stationId the station ID
     * @param date      the date to check
     * @return list of absences active on the given date
     */
    public List<MemberAbsence> findAbsencesByStationOnDate(int stationId, LocalDate date) {
        return query("""
                SELECT ma.id, ma.member_id, ma.absent_from, ma.absent_until, ma.reason, ma.created_at, ma.created_by
                FROM member_absence ma
                    JOIN station_member sm ON ma.member_id = sm.id
                WHERE sm.station_id = :station_id
                  AND ma.absent_from <= :date
                  AND ma.absent_until >= :date
                ORDER BY ma.absent_until;""")
                .single(call().bind("station_id", stationId).bind("date", date))
                .map(MemberAbsence.map())
                .all();
    }

    /**
     * Checks whether a member has an active absence today.
     *
     * @param memberId the member ID
     * @return {@code true} if the member is currently absent
     */
    public boolean isAbsent(int memberId) {
        return query(
                        "SELECT 1 FROM member_absence WHERE member_id = :member_id AND absent_from <= current_date AND absent_until >= current_date LIMIT 1;")
                .single(call().bind("member_id", memberId))
                .map(_ -> true)
                .first()
                .isPresent();
    }

    /**
     * Deletes an absence record by its ID.
     *
     * @param id the absence ID
     * @return {@code true} if the absence was deleted
     */
    public boolean deleteAbsence(int id) {
        return query("DELETE FROM member_absence WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Deletes all absence records that have expired (end date before today).
     *
     * @return {@code true} if any absences were deleted
     */
    public boolean deleteExpiredAbsences() {
        return query("DELETE FROM member_absence WHERE absent_until < current_date;")
                .single()
                .delete()
                .changed();
    }

    /**
     * Deletes all absence records for a specific member.
     *
     * @param memberId the member ID
     */
    public void deleteAbsencesByMember(int memberId) {
        query("DELETE FROM member_absence WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .delete();
    }

    /**
     * Associates a member group with a template at a given position.
     *
     * @param groupId  the group ID
     * @param position ordering position
     */
    public record TemplateGroup(int groupId, int position) {}
}

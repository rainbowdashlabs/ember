/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.api.results.writing.insertion.InsertionResult;
import dev.chojo.ember.entity.AttendanceEntry;
import dev.chojo.ember.entity.AttendanceReportPreset;
import dev.chojo.ember.entity.AttendanceSession;
import dev.chojo.ember.entity.AttendanceSessionField;
import dev.chojo.ember.entity.AttendanceTemplate;
import dev.chojo.ember.entity.AttendanceTemplateField;
import dev.chojo.ember.entity.MemberAbsence;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class AttendanceRepository {

    // -- Templates --

    public Optional<AttendanceTemplate> findTemplateById(int id) {
        return Query.query("SELECT id, station_id, name FROM attendance_template WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(AttendanceTemplate.map())
                .first();
    }

    public List<AttendanceTemplate> findTemplatesByStation(int stationId) {
        return Query.query("SELECT id, station_id, name FROM attendance_template WHERE station_id = :station_id;")
                .single(Call.of().bind("station_id", stationId))
                .map(AttendanceTemplate.map())
                .all();
    }

    public AttendanceTemplate createTemplate(int stationId, String name) {
        return Query.query(
                        "INSERT INTO attendance_template(station_id, name) VALUES(:station_id, :name) RETURNING id, station_id, name;")
                .single(Call.of().bind("station_id", stationId).bind("name", name))
                .map(AttendanceTemplate.map())
                .first()
                .orElseThrow();
    }

    public boolean updateTemplate(int id, String name) {
        return Query.query("UPDATE attendance_template SET name = :name WHERE id = :id;")
                .single(Call.of().bind("name", name).bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteTemplate(int id) {
        return Query.query("DELETE FROM attendance_template WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Template Fields --

    public List<AttendanceTemplateField> findTemplateFields(int templateId) {
        return Query.query(
                        "SELECT id, template_id, name, field_type, config, position FROM attendance_template_field WHERE template_id = :template_id ORDER BY position;")
                .single(Call.of().bind("template_id", templateId))
                .map(AttendanceTemplateField.map())
                .all();
    }

    public InsertionResult createTemplateField(
            int templateId, String name, String fieldType, String config, int position) {
        return Query.query("""
                            INSERT
                            INTO
                                attendance_template_field(template_id, name, field_type, config, position)
                            VALUES
                                (:template_id, :name, :field_type, :config::JSONB, :position);""")
                .single(Call.of()
                        .bind("template_id", templateId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config)
                        .bind("position", position))
                .insert();
    }

    public boolean updateTemplateField(int id, String name, String fieldType, String config, int position) {
        return Query.query("""
                            UPDATE attendance_template_field
                            SET
                                name       = :name,
                                field_type = :field_type,
                                config     = :config::JSONB,
                                position   = :position
                            WHERE id = :id;""")
                .single(Call.of()
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config)
                        .bind("position", position)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteTemplateField(int id) {
        return Query.query("DELETE FROM attendance_template_field WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Template Groups --

    public List<TemplateGroup> findTemplateGroups(int templateId) {
        return Query.query(
                        "SELECT group_id, position FROM attendance_template_group WHERE template_id = :template_id ORDER BY position;")
                .single(Call.of().bind("template_id", templateId))
                .map(row -> new TemplateGroup(row.getInt("group_id"), row.getInt("position")))
                .all();
    }

    public void setTemplateGroups(int templateId, List<TemplateGroup> groups) {
        Query.query("DELETE FROM attendance_template_group WHERE template_id = :template_id;")
                .single(Call.of().bind("template_id", templateId))
                .delete();
        for (TemplateGroup group : groups) {
            Query.query(
                            "INSERT INTO attendance_template_group(template_id, group_id, position) VALUES(:template_id, :group_id, :position);")
                    .single(Call.of()
                            .bind("template_id", templateId)
                            .bind("group_id", group.groupId())
                            .bind("position", group.position()))
                    .insert();
        }
    }

    public Optional<AttendanceSession> findSessionById(int id) {
        return Query.query(
                        "SELECT id, template_id, start_time, end_time, created_at, event_id, title FROM attendance_session WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(AttendanceSession.map())
                .first();
    }

    // -- Sessions --

    public List<AttendanceSession> findSessionsByTemplate(int templateId) {
        return Query.query("""
                            SELECT id, template_id, start_time, end_time, created_at, event_id, title
                            FROM attendance_session
                            WHERE template_id = :template_id
                            ORDER BY created_at DESC;""")
                .single(Call.of().bind("template_id", templateId))
                .map(AttendanceSession.map())
                .all();
    }

    public List<SessionSummary> findSessionSummariesByStation(int stationId) {
        return Query.query("""
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
                .single(Call.of().bind("station_id", stationId))
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

    public Optional<AttendanceSession> findSessionByEventId(int eventId) {
        return Query.query(
                        "SELECT id, template_id, start_time, end_time, created_at, event_id, title FROM attendance_session WHERE event_id = :event_id ORDER BY created_at DESC LIMIT 1;")
                .single(Call.of().bind("event_id", eventId))
                .map(AttendanceSession.map())
                .first();
    }

    public AttendanceSession createSession(
            int templateId, Instant startTime, Instant endTime, Integer eventId, String title) {
        return Query.query(
                        "INSERT INTO attendance_session(template_id, start_time, end_time, event_id, title) VALUES(:template_id, :start_time, :end_time, :event_id, :title) RETURNING id, template_id, start_time, end_time, created_at, event_id, title;")
                .single(Call.of()
                        .bind("template_id", templateId)
                        .bind("start_time", startTime, INSTANT_TIMESTAMP)
                        .bind("end_time", endTime, INSTANT_TIMESTAMP)
                        .bind("event_id", eventId)
                        .bind("title", title))
                .map(AttendanceSession.map())
                .first()
                .orElseThrow();
    }

    public boolean updateSession(int id, Instant startTime, Instant endTime, String title) {
        return Query.query(
                        "UPDATE attendance_session SET start_time = :start_time, end_time = :end_time, title = :title WHERE id = :id;")
                .single(Call.of()
                        .bind("start_time", startTime, INSTANT_TIMESTAMP)
                        .bind("end_time", endTime, INSTANT_TIMESTAMP)
                        .bind("title", title)
                        .bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteSession(int id) {
        return Query.query("DELETE FROM attendance_session WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public List<AttendanceSessionField> findSessionFields(int sessionId) {
        return Query.query(
                        "SELECT session_id, field_id, value FROM attendance_session_field WHERE session_id = :session_id;")
                .single(Call.of().bind("session_id", sessionId))
                .map(AttendanceSessionField.map())
                .all();
    }

    public InsertionResult setSessionField(int sessionId, int fieldId, String value) {
        return Query.query("""
                            INSERT
                            INTO
                                attendance_session_field(session_id, field_id, value)
                            VALUES
                                (:session_id, :field_id, :value::JSONB)
                            ON CONFLICT (session_id, field_id) DO UPDATE SET
                                value = excluded.value;""")
                .single(Call.of()
                        .bind("session_id", sessionId)
                        .bind("field_id", fieldId)
                        .bind("value", value))
                .insert();
    }

    // -- Session Fields --

    public boolean deleteSessionField(int sessionId, int fieldId) {
        return Query.query(
                        "DELETE FROM attendance_session_field WHERE session_id = :session_id AND field_id = :field_id;")
                .single(Call.of().bind("session_id", sessionId).bind("field_id", fieldId))
                .delete()
                .changed();
    }

    public List<AttendanceEntry> findEntries(int sessionId) {
        return Query.query(
                        "SELECT id, session_id, member_id, status, check_in, check_out, source FROM attendance_entry WHERE session_id = :session_id;")
                .single(Call.of().bind("session_id", sessionId))
                .map(AttendanceEntry.map())
                .all();
    }

    public Optional<AttendanceEntry> findEntry(int sessionId, int memberId) {
        return Query.query("""
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
                .single(Call.of().bind("session_id", sessionId).bind("member_id", memberId))
                .map(AttendanceEntry.map())
                .first();
    }

    // -- Entries --

    public List<AttendanceEntry> findEntriesByMember(int memberId) {
        return Query.query(
                        "SELECT id, session_id, member_id, status, check_in, check_out, source FROM attendance_entry WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .map(AttendanceEntry.map())
                .all();
    }

    public InsertionResult createEntry(
            int sessionId, int memberId, AttendanceEntry.AttendanceStatus status, AttendanceEntry.EntrySource source) {
        return Query.query(
                        "INSERT INTO attendance_entry(session_id, member_id, status, source) VALUES(:session_id, :member_id, :status, :source);")
                .single(Call.of()
                        .bind("session_id", sessionId)
                        .bind("member_id", memberId)
                        .bind("status", status)
                        .bind("source", source))
                .insert();
    }

    public boolean checkIn(int id, Instant checkIn) {
        return Query.query("UPDATE attendance_entry SET check_in = :check_in WHERE id = :id;")
                .single(Call.of().bind("check_in", checkIn, INSTANT_TIMESTAMP).bind("id", id))
                .update()
                .changed();
    }

    public boolean checkOut(int id, Instant checkOut) {
        return Query.query("UPDATE attendance_entry SET check_out = :check_out WHERE id = :id;")
                .single(Call.of().bind("check_out", checkOut, INSTANT_TIMESTAMP).bind("id", id))
                .update()
                .changed();
    }

    public boolean resetTimes(int id) {
        return Query.query("UPDATE attendance_entry SET check_in = NULL, check_out = NULL WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    public boolean updateEntryStatus(int id, AttendanceEntry.AttendanceStatus status) {
        return Query.query("UPDATE attendance_entry SET status = :status WHERE id = :id;")
                .single(Call.of().bind("status", status).bind("id", id))
                .update()
                .changed();
    }

    public boolean deleteEntry(int id) {
        return Query.query("DELETE FROM attendance_entry WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public List<AttendanceSession> findSessionsByStationInRange(int stationId, Instant from, Instant to) {
        return Query.query("""
                            SELECT s.id, s.template_id, s.start_time, s.end_time, s.created_at, s.event_id, s.title
                            FROM attendance_session s
                            JOIN attendance_template t ON t.id = s.template_id
                            WHERE t.station_id = :station_id
                              AND s.start_time >= :from_time
                              AND s.start_time < :to_time
                            ORDER BY s.start_time;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("from_time", from, INSTANT_TIMESTAMP)
                        .bind("to_time", to, INSTANT_TIMESTAMP))
                .map(AttendanceSession.map())
                .all();
    }

    public List<Integer> findMemberIdsByRole(int stationId, String roleName) {
        return Query.query("""
                            SELECT sm.id
                            FROM station_member sm
                            JOIN station_member_role smr ON smr.member_id = sm.id
                            JOIN role r ON r.id = smr.role_id
                            WHERE sm.station_id = :station_id AND r.name = :role_name;""")
                .single(Call.of().bind("station_id", stationId).bind("role_name", roleName))
                .map(row -> row.getInt("id"))
                .all();
    }

    // -- Export Queries --

    public List<Integer> findMemberIdsByGroup(int groupId) {
        return Query.query("""
                            SELECT member_id FROM member_group_entry WHERE group_id = :group_id;""")
                .single(Call.of().bind("group_id", groupId))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    public List<AttendanceReportPreset> findPresets(int stationId) {
        return Query.query(
                        "SELECT id, station_id, name, role_name, group_id, period, rounding FROM attendance_report_preset WHERE station_id = :station_id ORDER BY name;")
                .single(Call.of().bind("station_id", stationId))
                .map(AttendanceReportPreset.map())
                .all();
    }

    public AttendanceReportPreset createPreset(
            int stationId, String name, String roleName, Integer groupId, String period, String rounding) {
        return Query.query(
                        "INSERT INTO attendance_report_preset(station_id, name, role_name, group_id, period, rounding) VALUES(:station_id, :name, :role_name, :group_id, :period, :rounding) RETURNING id, station_id, name, role_name, group_id, period, rounding;")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("role_name", roleName)
                        .bind("group_id", groupId)
                        .bind("period", period)
                        .bind("rounding", rounding))
                .map(AttendanceReportPreset.map())
                .first()
                .orElseThrow();
    }

    // -- Report Presets --

    public boolean deletePreset(int id) {
        return Query.query("DELETE FROM attendance_report_preset WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public MemberAbsence createAbsence(int memberId, LocalDate absentFrom, LocalDate absentUntil, String reason) {
        return Query.query(
                        "INSERT INTO member_absence(member_id, absent_from, absent_until, reason) VALUES(:member_id, :absent_from, :absent_until, :reason) RETURNING id, member_id, absent_from, absent_until, reason, created_at;")
                .single(Call.of()
                        .bind("member_id", memberId)
                        .bind("absent_from", absentFrom)
                        .bind("absent_until", absentUntil)
                        .bind("reason", reason))
                .map(MemberAbsence.map())
                .first()
                .orElseThrow();
    }

    public Optional<MemberAbsence> findAbsenceById(int id) {
        return Query.query(
                        "SELECT id, member_id, absent_from, absent_until, reason, created_at FROM member_absence WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(MemberAbsence.map())
                .first();
    }

    // -- Absences --

    public List<MemberAbsence> findAbsencesByMember(int memberId) {
        return Query.query(
                        "SELECT id, member_id, absent_from, absent_until, reason, created_at FROM member_absence WHERE member_id = :member_id ORDER BY absent_until DESC;")
                .single(Call.of().bind("member_id", memberId))
                .map(MemberAbsence.map())
                .all();
    }

    public List<MemberAbsence> findActiveAbsencesByStation(int stationId) {
        return Query.query("""
                            SELECT ma.id, ma.member_id, ma.absent_from, ma.absent_until, ma.reason, ma.created_at
                            FROM member_absence ma
                                JOIN station_member sm ON ma.member_id = sm.id
                            WHERE sm.station_id = :station_id
                              AND ma.absent_from <= current_date
                              AND ma.absent_until >= current_date
                            ORDER BY ma.absent_until;""")
                .single(Call.of().bind("station_id", stationId))
                .map(MemberAbsence.map())
                .all();
    }

    public boolean isAbsent(int memberId) {
        return Query.query(
                        "SELECT 1 FROM member_absence WHERE member_id = :member_id AND absent_from <= CURRENT_DATE AND absent_until >= CURRENT_DATE LIMIT 1;")
                .single(Call.of().bind("member_id", memberId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    public boolean deleteAbsence(int id) {
        return Query.query("DELETE FROM member_absence WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public boolean deleteExpiredAbsences() {
        return Query.query("DELETE FROM member_absence WHERE absent_until < CURRENT_DATE;")
                .single()
                .delete()
                .changed();
    }

    public record TemplateGroup(int groupId, int position) {}

    public record SessionSummary(
            int id,
            int templateId,
            Instant startTime,
            Instant endTime,
            Instant createdAt,
            Integer eventId,
            String title,
            int presentCount,
            int absentCount,
            int declinedCount,
            int unconfirmedCount) {}

    public void deleteAbsencesByMember(int memberId) {
        Query.query("DELETE FROM member_absence WHERE member_id = :member_id;")
                .single(Call.of().bind("member_id", memberId))
                .delete();
    }
}

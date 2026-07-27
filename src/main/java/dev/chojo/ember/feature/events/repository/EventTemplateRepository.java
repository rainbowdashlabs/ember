/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.EventTemplateField;
import dev.chojo.ember.feature.events.entity.EventTemplateFieldData;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSql;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class EventTemplateRepository {

    private static final String EVENT_TEMPLATE_COLUMNS = """
            id, station_id, name, title, description, category_id, event_type,
            requires_registration, registration_deadline_offset, requires_confirmation,
            restriction_mode, attendance_template_id, registration_limit""";
    private static final String RESTRICTION_TABLE = "event_template_restriction";
    private static final String TEMPLATE_FK = "template_id";

    public List<EventTemplate> findByStation(int stationId) {
        return query("""
                SELECT %s
                FROM event_template
                WHERE station_id = :station_id
                ORDER BY name;""", EVENT_TEMPLATE_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(EventTemplate.map())
                .all();
    }

    public Optional<EventTemplate> findById(int id) {
        return SqlSupport.findById("event_template", EVENT_TEMPLATE_COLUMNS, id, EventTemplate.map());
    }

    public EventTemplate create(int stationId, String name) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_template(station_id, name)
                VALUES (:station_id, :name)
                RETURNING %s;""",
                call().bind("station_id", stationId).bind("name", name), EventTemplate.map(), EVENT_TEMPLATE_COLUMNS);
    }

    public boolean update(
            int id,
            String name,
            String title,
            String description,
            Integer categoryId,
            StationEvent.EventType eventType,
            Boolean requiresRegistration,
            String registrationDeadlineOffset,
            Boolean requiresConfirmation,
            RestrictionMode restrictionMode,
            Integer attendanceTemplateId,
            Integer registrationLimit) {
        return query("""
                UPDATE event_template SET
                    name = :name,
                    title = :title,
                    description = :description,
                    category_id = :category_id,
                    event_type = :event_type,
                    requires_registration = :requires_registration,
                    registration_deadline_offset = :registration_deadline_offset::INTERVAL,
                    requires_confirmation = :requires_confirmation,
                    restriction_mode = :restriction_mode,
                    attendance_template_id = :attendance_template_id,
                    registration_limit = :registration_limit
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("name", name)
                        .bind("title", title)
                        .bind("description", description)
                        .bind("category_id", categoryId)
                        .bind("event_type", eventType)
                        .bind("requires_registration", requiresRegistration)
                        .bind("registration_deadline_offset", registrationDeadlineOffset)
                        .bind("requires_confirmation", requiresConfirmation)
                        .bind("restriction_mode", restrictionMode)
                        .bind("attendance_template_id", attendanceTemplateId)
                        .bind("registration_limit", registrationLimit))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return SqlSupport.deleteById("event_template", id);
    }

    public List<EventTemplateField> findFields(int templateId) {
        return query("""
                SELECT id, template_id, name, field_type, config, position, overview, public, attendance_field_id
                FROM event_template_field
                WHERE template_id = :template_id
                ORDER BY position;""")
                .single(call().bind("template_id", templateId))
                .map(EventTemplateField.map())
                .all();
    }

    public void replaceFields(int templateId, List<EventTemplateFieldData> fields) {
        query("DELETE FROM event_template_field WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
        for (EventTemplateFieldData f : fields) {
            query("""
                    INSERT INTO event_template_field(template_id, name, field_type, config, position, overview, public, attendance_field_id)
                    VALUES (:template_id, :name, :field_type, :config::JSONB, :position, :overview, :public, :attendance_field_id);""")
                    .single(call().bind("template_id", templateId)
                            .bind("name", f.name())
                            .bind("field_type", f.fieldType() != null ? f.fieldType() : EventFieldType.STRING)
                            .bind("config", f.config() != null ? f.config().toJson() : "{}")
                            .bind("position", f.position())
                            .bind("overview", f.overview())
                            .bind("public", f.isPublic())
                            .bind("attendance_field_id", f.attendanceFieldId()))
                    .insert();
        }
    }

    public List<String> findRestrictions(int templateId) {
        return query("SELECT user_type FROM %s WHERE %s = :template_id;", RESTRICTION_TABLE, TEMPLATE_FK)
                .single(call().bind("template_id", templateId))
                .map(row -> row.getString("user_type"))
                .all();
    }

    public void setRestrictions(int templateId, List<StationUserType> userTypes) {
        RestrictionSql.replace(
                RESTRICTION_TABLE,
                TEMPLATE_FK,
                templateId,
                new RestrictionSelection(userTypes, List.of(), List.of(), List.of(), null));
    }

    public List<Integer> findReminderDays(int templateId) {
        return query("""
                SELECT days_before
                FROM event_template_reminder
                WHERE template_id = :template_id
                ORDER BY days_before;""")
                .single(call().bind("template_id", templateId))
                .map(row -> row.getInt("days_before"))
                .all();
    }

    public void replaceReminders(int templateId, List<Integer> daysBefore) {
        query("DELETE FROM event_template_reminder WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
        for (int days : daysBefore) {
            query("""
                    INSERT INTO event_template_reminder(template_id, days_before)
                    VALUES (:template_id, :days_before)
                    ON CONFLICT DO NOTHING;""")
                    .single(call().bind("template_id", templateId).bind("days_before", days))
                    .insert();
        }
    }
}

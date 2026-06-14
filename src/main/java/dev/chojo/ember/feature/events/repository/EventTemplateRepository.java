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
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class EventTemplateRepository {

    public List<EventTemplate> findByStation(int stationId) {
        return query("SELECT id, station_id, name, title, description, category_id, event_type,"
                        + " requires_registration, registration_deadline_offset, requires_confirmation, restriction_mode, attendance_template_id, registration_limit"
                        + " FROM event_template WHERE station_id = :station_id ORDER BY name;")
                .single(call().bind("station_id", stationId))
                .map(EventTemplate.map())
                .all();
    }

    public Optional<EventTemplate> findById(int id) {
        return query("SELECT id, station_id, name, title, description, category_id, event_type,"
                        + " requires_registration, registration_deadline_offset, requires_confirmation, restriction_mode, attendance_template_id, registration_limit"
                        + " FROM event_template WHERE id = :id;")
                .single(call().bind("id", id))
                .map(EventTemplate.map())
                .first();
    }

    public EventTemplate create(int stationId, String name) {
        return query(
                        "INSERT INTO event_template(station_id, name)"
                                + " VALUES (:station_id, :name)"
                                + " RETURNING id, station_id, name, title, description, category_id, event_type,"
                                + " requires_registration, registration_deadline_offset, requires_confirmation, restriction_mode, attendance_template_id, registration_limit;")
                .single(call().bind("station_id", stationId).bind("name", name))
                .map(EventTemplate.map())
                .first()
                .orElseThrow();
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
        return query("UPDATE event_template SET"
                        + " name = :name,"
                        + " title = :title,"
                        + " description = :description,"
                        + " category_id = :category_id,"
                        + " event_type = :event_type,"
                        + " requires_registration = :requires_registration,"
                        + " registration_deadline_offset = :registration_deadline_offset::interval,"
                        + " requires_confirmation = :requires_confirmation,"
                        + " restriction_mode = :restriction_mode,"
                        + " attendance_template_id = :attendance_template_id,"
                        + " registration_limit = :registration_limit"
                        + " WHERE id = :id;")
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
        return query("DELETE FROM event_template WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    public List<EventTemplateField> findFields(int templateId) {
        return query("SELECT id, template_id, name, field_type, config, position, overview, public, attendance_field_id"
                        + " FROM event_template_field WHERE template_id = :template_id ORDER BY position;")
                .single(call().bind("template_id", templateId))
                .map(EventTemplateField.map())
                .all();
    }

    public void replaceFields(int templateId, List<EventTemplateFieldData> fields) {
        query("DELETE FROM event_template_field WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
        for (EventTemplateFieldData f : fields) {
            query(
                            "INSERT INTO event_template_field(template_id, name, field_type, config, position, overview, public, attendance_field_id)"
                                    + " VALUES (:template_id, :name, :field_type, :config::jsonb, :position, :overview, :public, :attendance_field_id);")
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
        return query("SELECT user_type FROM event_template_restriction WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .map(row -> row.getString("user_type"))
                .all();
    }

    public void setRestrictions(int templateId, List<StationUserType> userTypes) {
        query("DELETE FROM event_template_restriction WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
        for (StationUserType userType : userTypes) {
            query("INSERT INTO event_template_restriction(template_id, user_type) VALUES (:template_id, :user_type);")
                    .single(call().bind("template_id", templateId).bind("user_type", userType))
                    .insert();
        }
    }

    // --- Reminders ---

    public List<Integer> findReminderDays(int templateId) {
        return query(
                        "SELECT days_before FROM event_template_reminder WHERE template_id = :template_id ORDER BY days_before;")
                .single(call().bind("template_id", templateId))
                .map(row -> row.getInt("days_before"))
                .all();
    }

    public void replaceReminders(int templateId, List<Integer> daysBefore) {
        query("DELETE FROM event_template_reminder WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
        for (int days : daysBefore) {
            query(
                            "INSERT INTO event_template_reminder(template_id, days_before) VALUES(:template_id, :days_before) ON CONFLICT DO NOTHING;")
                    .single(call().bind("template_id", templateId).bind("days_before", days))
                    .insert();
        }
    }
}

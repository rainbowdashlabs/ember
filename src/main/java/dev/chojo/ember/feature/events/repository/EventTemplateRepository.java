/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.EventTemplateField;
import dev.chojo.ember.feature.events.entity.EventTemplateFieldData;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class EventTemplateRepository {

    public List<EventTemplate> findByStation(int stationId) {
        return Query.query("SELECT id, station_id, name, title, description, category_id, event_type,"
                        + " requires_registration, registration_deadline_offset, requires_confirmation, restriction_mode, attendance_template_id, registration_limit"
                        + " FROM event_template WHERE station_id = :station_id ORDER BY name;")
                .single(Call.of().bind("station_id", stationId))
                .map(EventTemplate.map())
                .all();
    }

    public Optional<EventTemplate> findById(int id) {
        return Query.query("SELECT id, station_id, name, title, description, category_id, event_type,"
                        + " requires_registration, registration_deadline_offset, requires_confirmation, restriction_mode, attendance_template_id, registration_limit"
                        + " FROM event_template WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(EventTemplate.map())
                .first();
    }

    public EventTemplate create(int stationId, String name) {
        return Query.query(
                        "INSERT INTO event_template(station_id, name)"
                                + " VALUES (:station_id, :name)"
                                + " RETURNING id, station_id, name, title, description, category_id, event_type,"
                                + " requires_registration, registration_deadline_offset, requires_confirmation, restriction_mode, attendance_template_id, registration_limit;")
                .single(Call.of().bind("station_id", stationId).bind("name", name))
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
            String eventType,
            Boolean requiresRegistration,
            String registrationDeadlineOffset,
            Boolean requiresConfirmation,
            String restrictionMode,
            Integer attendanceTemplateId,
            Integer registrationLimit) {
        return Query.query("UPDATE event_template SET"
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
                .single(Call.of()
                        .bind("id", id)
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
        return Query.query("DELETE FROM event_template WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public List<EventTemplateField> findFields(int templateId) {
        return Query.query(
                        "SELECT id, template_id, name, field_type, config, position, overview, public, attendance_field_id"
                                + " FROM event_template_field WHERE template_id = :template_id ORDER BY position;")
                .single(Call.of().bind("template_id", templateId))
                .map(EventTemplateField.map())
                .all();
    }

    public void replaceFields(int templateId, List<EventTemplateFieldData> fields) {
        Query.query("DELETE FROM event_template_field WHERE template_id = :template_id;")
                .single(Call.of().bind("template_id", templateId))
                .delete();
        for (EventTemplateFieldData f : fields) {
            Query.query(
                            "INSERT INTO event_template_field(template_id, name, field_type, config, position, overview, public, attendance_field_id)"
                                    + " VALUES (:template_id, :name, :field_type, :config::jsonb, :position, :overview, :public, :attendance_field_id);")
                    .single(Call.of()
                            .bind("template_id", templateId)
                            .bind("name", f.name())
                            .bind("field_type", f.fieldType() != null ? f.fieldType() : "string")
                            .bind("config", f.config() != null ? f.config() : "{}")
                            .bind("position", f.position())
                            .bind("overview", f.overview())
                            .bind("public", f.isPublic())
                            .bind("attendance_field_id", f.attendanceFieldId()))
                    .insert();
        }
    }

    public List<Integer> findRestrictions(int templateId) {
        return Query.query("SELECT role_id FROM event_template_restriction WHERE template_id = :template_id;")
                .single(Call.of().bind("template_id", templateId))
                .map(row -> row.getInt("role_id"))
                .all();
    }

    public void setRestrictions(int templateId, List<Integer> roleIds) {
        Query.query("DELETE FROM event_template_restriction WHERE template_id = :template_id;")
                .single(Call.of().bind("template_id", templateId))
                .delete();
        for (int roleId : roleIds) {
            Query.query("INSERT INTO event_template_restriction(template_id, role_id) VALUES (:template_id, :role_id);")
                    .single(Call.of().bind("template_id", templateId).bind("role_id", roleId))
                    .insert();
        }
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.AppointmentTemplateField;
import dev.chojo.ember.feature.events.entity.AppointmentTemplateFieldDraft;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationTemplateField;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class EventTemplateRepository {

    private static final String EVENT_TEMPLATE_COLUMNS = """
            id, station_id, name, title, description, category_id, event_type,
            requires_registration, registration_deadline_offset, requires_confirmation,
            restriction_mode, view_restriction_mode, attendance_template_id, registration_limit""";

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
            @Nullable String title,
            @Nullable String description,
            @Nullable Integer categoryId,
            StationEvent.@Nullable EventType eventType,
            @Nullable Boolean requiresRegistration,
            @Nullable String registrationDeadlineOffset,
            @Nullable Boolean requiresConfirmation,
            @Nullable RestrictionMode restrictionMode,
            @Nullable Integer attendanceTemplateId,
            @Nullable Integer registrationLimit) {
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

    /**
     * Sets how the registration audience of a template combines, without touching anything else it
     * holds.
     *
     * <p>Apart from the general update because the audience is saved on its own: the editor writes
     * the template and its audience in two steps, and the second must not undo the first.
     */
    public boolean updateRestrictionMode(int id, RestrictionMode mode) {
        return query("UPDATE event_template SET restriction_mode = :mode WHERE id = :id;")
                .single(call().bind("mode", mode).bind("id", id))
                .update()
                .changed();
    }

    /**
     * Sets how the view audience of a template combines, for the same reason as its counterpart.
     */
    public boolean updateViewRestrictionMode(int id, RestrictionMode mode) {
        return query("UPDATE event_template SET view_restriction_mode = :mode WHERE id = :id;")
                .single(call().bind("mode", mode).bind("id", id))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return SqlSupport.deleteById("event_template", id);
    }

    public List<AppointmentTemplateField> findFields(int templateId) {
        return query("""
                SELECT id, template_id, name, field_type, config, position, overview, public, attendance_field_id,
                       default_value
                FROM event_template_field
                WHERE template_id = :template_id
                ORDER BY position;""")
                .single(call().bind("template_id", templateId))
                .map(AppointmentTemplateField.map())
                .all();
    }

    public void replaceFields(int templateId, List<AppointmentTemplateFieldDraft> fields) {
        query("DELETE FROM event_template_field WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
        for (var field : fields) {
            query("""
                    INSERT INTO event_template_field(template_id, name, field_type, config, position, overview, public, attendance_field_id, default_value)
                    VALUES (:template_id, :name, :field_type, :config::JSONB, :position, :overview, :public, :attendance_field_id, :default_value);""")
                    .single(call().bind("template_id", templateId)
                            .bind("name", field.name())
                            .bind("field_type", field.fieldType())
                            .bind("config", field.config().toJson())
                            .bind("position", field.position())
                            .bind("overview", field.overview())
                            .bind("public", field.isPublic())
                            .bind("attendance_field_id", field.attendanceFieldId())
                            .bind("default_value", field.defaultValue()))
                    .insert();
        }
    }

    public List<RegistrationTemplateField> findRegistrationFields(int templateId) {
        return query("""
                SELECT id, template_id, name, field_type, config, position, overview
                FROM event_template_registration_field
                WHERE template_id = :template_id
                ORDER BY position, id;""")
                .single(call().bind("template_id", templateId))
                .map(RegistrationTemplateField.map())
                .all();
    }

    public void replaceRegistrationFields(int templateId, List<RegistrationFieldDraft> fields) {
        query("DELETE FROM event_template_registration_field WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            query("""
                    INSERT INTO event_template_registration_field(template_id, name, field_type, config, position, overview)
                    VALUES (:template_id, :name, :field_type, :config::JSONB, :position, :overview);""")
                    .single(call().bind("template_id", templateId)
                            .bind("name", field.name())
                            .bind("field_type", field.fieldType())
                            .bind("config", field.config().toJson())
                            .bind("position", i)
                            .bind("overview", field.overview()))
                    .insert();
        }
    }

    /**
     * Copies a template's registration questions onto an appointment, as they stand.
     *
     * <p>Copied in one statement and untouched: a question the template has carried since before
     * registration questions were narrowed to the kinds the editor offers is still copied, because
     * the template was accepted when it was written.
     *
     * @return how many questions were copied
     */
    public int copyRegistrationFields(int templateId, int eventId) {
        return query("""
                INSERT INTO event_registration_field(event_id, name, field_type, config, position, overview)
                SELECT :event_id, name, field_type, config, position, overview
                FROM event_template_registration_field
                WHERE template_id = :template_id
                ORDER BY position, id;""")
                .single(call().bind("template_id", templateId).bind("event_id", eventId))
                .insert()
                .rows();
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

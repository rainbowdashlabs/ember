/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.events.entity.EventTemplateRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The questions an event asks of everyone registering, the copies templates carry, and the answers
 * given per registration.
 */
@Singleton
public class EventRegistrationFieldRepository {

    private static final String FIELD_COLUMNS = "id, event_id, name, field_type, config, position, overview";
    private static final String TEMPLATE_FIELD_COLUMNS =
            "id, template_id, name, field_type, config, position, overview";
    private static final String VALUE_COLUMNS = "registration_id, field_id, value";

    // -- Questions --

    public List<EventRegistrationField> findByEvent(int eventId) {
        return query("""
                SELECT %s
                FROM event_registration_field
                WHERE event_id = :event_id
                ORDER BY position, id;""", FIELD_COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(EventRegistrationField.map())
                .all();
    }

    public EventRegistrationField create(
            int eventId,
            String name,
            EventFieldType fieldType,
            EventRegistrationFieldConfig config,
            int position,
            boolean overview) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_registration_field(event_id, name, field_type, config, position, overview)
                VALUES (:event_id, :name, :field_type, :config::JSONB, :position, :overview)
                RETURNING %s;""",
                call().bind("event_id", eventId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("overview", overview),
                EventRegistrationField.map(),
                FIELD_COLUMNS);
    }

    public void deleteByEvent(int eventId) {
        query("DELETE FROM event_registration_field WHERE event_id = :event_id;")
                .single(call().bind("event_id", eventId))
                .delete();
    }

    /**
     * Replaces an event's questions with a new list, numbering them in the order given.
     *
     * <p>Answers to questions that survive the replacement are lost, because a question is
     * identified by its row: the delete cascades into the answers. That is the honest outcome —
     * a rewritten question is not the question that was answered.
     */
    public void replaceFields(int eventId, List<FieldEntry> fields) {
        deleteByEvent(eventId);
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            create(
                    eventId,
                    field.name(),
                    field.fieldType() != null ? field.fieldType() : EventFieldType.STRING,
                    field.config() != null ? field.config() : EventRegistrationFieldConfig.empty(),
                    i,
                    field.overview());
        }
    }

    // -- Template questions --

    public List<EventTemplateRegistrationField> findByTemplate(int templateId) {
        return query("""
                SELECT %s
                FROM event_template_registration_field
                WHERE template_id = :template_id
                ORDER BY position, id;""", TEMPLATE_FIELD_COLUMNS)
                .single(call().bind("template_id", templateId))
                .map(EventTemplateRegistrationField.map())
                .all();
    }

    public void deleteByTemplate(int templateId) {
        query("DELETE FROM event_template_registration_field WHERE template_id = :template_id;")
                .single(call().bind("template_id", templateId))
                .delete();
    }

    public void replaceTemplateFields(int templateId, List<FieldEntry> fields) {
        deleteByTemplate(templateId);
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            query("""
                    INSERT INTO event_template_registration_field(template_id, name, field_type, config, position, overview)
                    VALUES (:template_id, :name, :field_type, :config::JSONB, :position, :overview);""")
                    .single(call().bind("template_id", templateId)
                            .bind("name", field.name())
                            .bind("field_type", field.fieldType() != null ? field.fieldType() : EventFieldType.STRING)
                            .bind(
                                    "config",
                                    (field.config() != null ? field.config() : EventRegistrationFieldConfig.empty())
                                            .toJson())
                            .bind("position", i)
                            .bind("overview", field.overview()))
                    .insert();
        }
    }

    // -- Answers --

    public List<RegistrationFieldValue> findValues(int registrationId) {
        return query("""
                SELECT %s
                FROM event_registration_field_value
                WHERE registration_id = :registration_id;""", VALUE_COLUMNS)
                .single(call().bind("registration_id", registrationId))
                .map(RegistrationFieldValue.map())
                .all();
    }

    /**
     * Reads the answers of a whole list of registrations at once, so rendering a registration list
     * costs one query rather than one per row.
     */
    public List<RegistrationFieldValue> findValuesForRegistrations(List<Integer> registrationIds) {
        if (registrationIds.isEmpty()) return List.of();
        return query("""
                SELECT %s
                FROM event_registration_field_value
                WHERE registration_id = ANY(:registration_ids);""", VALUE_COLUMNS)
                .single(call().bind("registration_ids", registrationIds, PostgreSqlTypes.INTEGER))
                .map(RegistrationFieldValue.map())
                .all();
    }

    public void setValue(int registrationId, int fieldId, String value) {
        query("""
                INSERT INTO event_registration_field_value(registration_id, field_id, value)
                VALUES (:registration_id, :field_id, :value)
                ON CONFLICT (registration_id, field_id) DO UPDATE SET value = EXCLUDED.value;""")
                .single(call().bind("registration_id", registrationId)
                        .bind("field_id", fieldId)
                        .bind("value", value))
                .insert();
    }

    public void deleteValues(int registrationId) {
        query("DELETE FROM event_registration_field_value WHERE registration_id = :registration_id;")
                .single(call().bind("registration_id", registrationId))
                .delete();
    }

    /**
     * A question as it arrives from the editor, before it has a row of its own.
     */
    public record FieldEntry(
            String name, EventFieldType fieldType, EventRegistrationFieldConfig config, boolean overview) {}
}

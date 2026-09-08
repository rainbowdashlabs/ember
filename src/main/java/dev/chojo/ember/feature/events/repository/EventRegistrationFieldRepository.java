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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
     * <p>A question that comes back under the name it already had keeps its row, and with it every
     * answer members have given: the order can be changed, an option added and a question marked
     * required without throwing away what the list already holds. Deleting and writing the whole set
     * again is what used to happen, and it silently emptied every answer of every registration each
     * time somebody moved a question up.
     *
     * <p>A question whose name is gone from the list is gone, and its answers go with it. A renamed
     * question therefore reads as a new one, which is the one case where the old answers really do
     * belong to a question nobody is asking any more.
     */
    public void replaceFields(int eventId, List<FieldEntry> fields) {
        var byName = new LinkedHashMap<String, List<EventRegistrationField>>();
        for (var existing : findByEvent(eventId)) {
            byName.computeIfAbsent(existing.name(), name -> new ArrayList<>()).add(existing);
        }

        var kept = new HashSet<Integer>();
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            var type = field.fieldType() != null ? field.fieldType() : EventFieldType.STRING;
            var config = field.config() != null ? field.config() : EventRegistrationFieldConfig.empty();
            var matches = byName.getOrDefault(field.name(), List.of());
            var match = matches.stream()
                    .filter(candidate -> !kept.contains(candidate.id()))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                create(eventId, field.name(), type, config, i, field.overview());
                continue;
            }
            kept.add(match.id());
            update(match.id(), field.name(), type, config, i, field.overview());
        }

        for (var existing : byName.values().stream().flatMap(List::stream).toList()) {
            if (!kept.contains(existing.id())) deleteField(existing.id());
        }
    }

    /** Writes what a question asks, leaving the answers already given against it where they are. */
    public void update(
            int fieldId,
            String name,
            EventFieldType fieldType,
            EventRegistrationFieldConfig config,
            int position,
            boolean overview) {
        query("""
                UPDATE event_registration_field
                SET name = :name, field_type = :field_type, config = :config::JSONB,
                    position = :position, overview = :overview
                WHERE id = :id;""")
                .single(call().bind("id", fieldId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("position", position)
                        .bind("overview", overview))
                .update();
    }

    private void deleteField(int fieldId) {
        query("DELETE FROM event_registration_field WHERE id = :id;")
                .single(call().bind("id", fieldId))
                .delete();
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

    public void deleteValue(int registrationId, int fieldId) {
        query("""
                DELETE FROM event_registration_field_value
                WHERE registration_id = :registration_id AND field_id = :field_id;""")
                .single(call().bind("registration_id", registrationId).bind("field_id", fieldId))
                .delete();
    }

    /**
     * A question as it arrives from the editor, before it has a row of its own.
     */
    public record FieldEntry(
            String name, EventFieldType fieldType, EventRegistrationFieldConfig config, boolean overview) {}
}

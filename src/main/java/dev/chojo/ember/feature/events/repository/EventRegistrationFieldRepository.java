/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The questions an event asks of everyone registering, and the answers given per registration.
 */
@Singleton
public class EventRegistrationFieldRepository {

    private static final String FIELD_COLUMNS = "id, event_id, name, field_type, config, position, overview";
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
            FieldType fieldType,
            EventQuestionSettings config,
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
    public void replaceFields(int eventId, List<RegistrationFieldDraft> fields) {
        var byName = new LinkedHashMap<String, List<EventRegistrationField>>();
        for (var existing : findByEvent(eventId)) {
            byName.computeIfAbsent(existing.name(), name -> new ArrayList<>()).add(existing);
        }

        var kept = new HashSet<Integer>();
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            var matches = byName.getOrDefault(field.name(), List.of());
            var match = matches.stream()
                    .filter(candidate -> !kept.contains(candidate.id()))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                create(eventId, field.name(), field.fieldType(), field.config(), i, field.overview());
                continue;
            }
            kept.add(match.id());
            update(match.id(), field.name(), field.fieldType(), field.config(), i, field.overview());
        }

        for (var existing : byName.values().stream().flatMap(List::stream).toList()) {
            if (!kept.contains(existing.id())) deleteField(existing.id());
        }
    }

    /** Writes what a question asks, leaving the answers already given against it where they are. */
    public void update(
            int fieldId,
            String name,
            FieldType fieldType,
            EventQuestionSettings config,
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

    /**
     * Clears the answers behind refusals old enough that they can no longer be taken back.
     *
     * <p>A refusal keeps its answers for as long as it can be undone, because an undo that gave back
     * a registration with blank questions would be worse than no undo at all. Once the window has
     * closed there is nothing left to restore them for.
     *
     * @param window how long a refusal may be taken back
     * @return how many registrations were cleared
     */
    public int deleteValuesOfRefusalsOlderThan(Duration window) {
        return query("""
                DELETE FROM event_registration_field_value
                WHERE registration_id IN (
                    SELECT id FROM event_registration
                    WHERE status IN ('WITHDRAWN', 'DECLINED')
                      AND status_changed_at <= now() - CAST(:window AS INTERVAL))""")
                .single(call().bind("window", window.toSeconds() + " seconds"))
                .delete()
                .rows();
    }

    public void deleteValue(int registrationId, int fieldId) {
        query("""
                DELETE FROM event_registration_field_value
                WHERE registration_id = :registration_id AND field_id = :field_id;""")
                .single(call().bind("registration_id", registrationId).bind("field_id", fieldId))
                .delete();
    }
}

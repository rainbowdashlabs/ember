/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.entity.RequirementGeneration;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The documents appointments and appointment templates ask participants to bring, and the copies
 * generated for them.
 */
@Singleton
public class EventRequirementRepository {
    private static final String EVENT = "event_id";
    private static final String EVENT_TEMPLATE = "event_template_id";

    /**
     * When the template {@code t} was last generated from. Appointments ask only for templates of their
     * own station, so every entry of the log for one is the station's.
     */
    private static final String LAST_USED = """
            (SELECT max(g.generated_at) FROM document_generation g WHERE g.template_id = t.id) AS last_used_at""";

    /**
     * @param eventId the appointment
     * @return the templates it asks for, in their order
     */
    public List<RequiredTemplate> forEvent(int eventId) {
        return required(EVENT, eventId);
    }

    /**
     * @param eventTemplateId the appointment template
     * @return the templates it hands to every appointment made from it, in their order
     */
    public List<RequiredTemplate> forEventTemplate(int eventTemplateId) {
        return required(EVENT_TEMPLATE, eventTemplateId);
    }

    /**
     * @param owner the column naming what asks for the documents, one of the two constants
     */
    private static List<RequiredTemplate> required(String owner, int ownerId) {
        return query("""
                SELECT t.id AS template_id, t.name, t.kind, t.version, t.archived_at IS NOT NULL AS archived, %s
                FROM event_document_requirement r
                         JOIN document_template t ON t.id = r.template_id
                WHERE r.%s = :id
                ORDER BY r.position, r.id;""", LAST_USED, owner)
                .single(call().bind("id", ownerId))
                .map(RequiredTemplate.map())
                .all();
    }

    /**
     * Replaces what an appointment asks for.
     *
     * @param eventId     the appointment
     * @param templateIds the templates, in their order
     */
    public void replaceForEvent(int eventId, List<Integer> templateIds) {
        replace(EVENT, eventId, templateIds);
    }

    /**
     * Replaces what an appointment template hands to the appointments made from it.
     *
     * @param eventTemplateId the appointment template
     * @param templateIds     the templates, in their order
     */
    public void replaceForEventTemplate(int eventTemplateId, List<Integer> templateIds) {
        replace(EVENT_TEMPLATE, eventTemplateId, templateIds);
    }

    /**
     * @param owner the column naming what asks for the documents, one of the two constants
     */
    private static void replace(String owner, int ownerId, List<Integer> templateIds) {
        query("DELETE FROM event_document_requirement WHERE %s = :id;", owner)
                .single(call().bind("id", ownerId))
                .delete();
        query("""
                INSERT INTO event_document_requirement(%s, template_id, position)
                SELECT :id, template_id, (position - 1)::int
                FROM unnest(:template_ids::INT[]) WITH ORDINALITY AS chosen(template_id, position);""", owner)
                .single(call().bind("id", ownerId).bind("template_ids", templateIds, PostgreSqlTypes.INTEGER))
                .insert();
    }

    /**
     * The members who have a copy still filed of a document an appointment asks for on a date. For an
     * appointment without registrations this is the only list of who takes part.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @return the members, each once
     */
    public List<Integer> copiedBy(int eventId, LocalDate eventDate) {
        return query("""
                SELECT DISTINCT member_id
                FROM document_generation
                WHERE event_id = :event_id
                  AND event_date = :event_date
                  AND member_id IS NOT NULL
                  AND document_id IS NOT NULL
                ORDER BY member_id;""")
                .single(call().bind("event_id", eventId).bind("event_date", eventDate))
                .map(row -> row.getInt("member_id"))
                .all();
    }

    /**
     * The latest copy still filed of each document an appointment asks for, per participant, for one of
     * its dates.
     *
     * @param eventId   the appointment
     * @param eventDate the date
     * @param memberIds the participants
     * @return one copy per template and participant that has one
     */
    public List<RequirementGeneration> latest(int eventId, LocalDate eventDate, Collection<Integer> memberIds) {
        return query("""
                SELECT DISTINCT ON (template_id, member_id)
                       template_id, member_id, id, template_version, generated_at, document_id
                FROM document_generation
                WHERE event_id = :event_id
                  AND event_date = :event_date
                  AND member_id = ANY(:member_ids)
                  AND document_id IS NOT NULL
                ORDER BY template_id, member_id, generated_at DESC, id DESC;""")
                .single(call().bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(RequirementGeneration.map())
                .all();
    }
}

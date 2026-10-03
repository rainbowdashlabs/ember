/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.GenerationOrigin;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.entity.RequirementGeneration;
import dev.chojo.ember.feature.generator.entity.RequirementStatus;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService.GeneratedDocumentResponse;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The documents an appointment asks its participants to bring, as a participant or their guardian
 * gets them: a copy filled with the participant's data and the appointment's, filed with the
 * participant and logged like every generated document.
 *
 * <p>The reader sees the documents of every member they act for (themselves and the members in their
 * care) who holds a place, pending or accepted, on the date. Nobody else gets a copy here: a member of
 * another appointment, or of another date of this one, is refused. A copy is never hidden from the
 * participant, whatever the template says, since it is theirs to print and sign. Data the participant's
 * profile lacks is left as a gap to fill in by hand, which is what a form to print is for.
 *
 * <p>The status per participant and document is what the signing of these documents builds on later:
 * for now a copy is generated or it is not, and a copy from an older version of the template is marked
 * as such so a new one can be generated.
 */
@Singleton
public class AppointmentDocumentService {
    private final EventRequirementRepository requirements;
    private final DocumentTemplateService templates;
    private final DocumentGeneratorService generator;
    private final DocumentGenerationService generation;
    private final GuardianPolicy guardians;
    private final EventRegistrationRepository registrations;
    private final EventFieldRepository fields;
    private final MemberNameResolver names;

    @Inject
    public AppointmentDocumentService(
            EventRequirementRepository requirements,
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            DocumentGenerationService generation,
            GuardianPolicy guardians,
            EventRegistrationRepository registrations,
            EventFieldRepository fields,
            MemberNameResolver names) {
        this.requirements = requirements;
        this.templates = templates;
        this.generator = generator;
        this.generation = generation;
        this.guardians = guardians;
        this.registrations = registrations;
        this.fields = fields;
        this.names = names;
    }

    /**
     * One document an appointment asks for, as it stands for one participant.
     *
     * @param templateId  the template
     * @param name        what the template is called
     * @param status      whether a copy was generated for the participant
     * @param documentId  the copy filed with the participant, or null
     * @param generatedAt when that copy was generated, or null
     * @param outdated    whether the template changed since that copy was generated
     */
    public record RequiredDocumentStatus(
            int templateId,
            String name,
            RequirementStatus status,
            @Nullable Integer documentId,
            @Nullable Instant generatedAt,
            boolean outdated) {}

    /**
     * The documents one participant is asked to bring.
     *
     * @param memberId  the participant
     * @param name      their name
     * @param documents every document the appointment asks for, in its order
     */
    public record ParticipantDocuments(int memberId, String name, List<RequiredDocumentStatus> documents) {}

    /**
     * The documents to bring for every member the reader acts for who takes part on the date.
     *
     * @param session the reader
     * @param event   the appointment, already checked to be one the reader may see
     * @param date    the date of the appointment
     * @return one entry per participant the reader acts for, none where the appointment asks for nothing
     */
    public List<ParticipantDocuments> documentsToBring(StationSession session, StationEvent event, LocalDate date) {
        var required = inUse(event.id());
        if (required.isEmpty()) return List.of();
        var registered = registrations.findRegisteredMemberIds(event.id(), date);
        var participants = guardians.household(session.user()).stream()
                .filter(registered::contains)
                .toList();
        if (participants.isEmpty()) return List.of();
        var copies = requirements.latest(event.id(), date, participants);
        return participants.stream()
                .map(memberId -> new ParticipantDocuments(
                        memberId, names.identified(memberId), statuses(required, copies, memberId)))
                .toList();
    }

    /**
     * Generates a copy of a document the appointment asks for and files it with the participant.
     *
     * @param session    the reader, the participant or their guardian
     * @param event      the appointment, already checked to be one the reader may see
     * @param date       the date of the appointment
     * @param templateId the document asked for
     * @param memberId   the participant
     * @return the filed copy and the data it left as gaps
     */
    public GeneratedDocumentResponse generate(
            StationSession session, StationEvent event, LocalDate date, int templateId, int memberId) {
        if (!guardians.mayActFor(session.user(), memberId)
                || !registrations.findRegisteredMemberIds(event.id(), date).contains(memberId)) {
            throw DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS.raise();
        }
        boolean required = inUse(event.id()).stream().anyMatch(template -> template.templateId() == templateId);
        if (!required) throw DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_REQUIRED.raise();
        var template = templates.requireInUse(session.owner(), templateId);
        var context = new GenerationContext(session.member().id(), facts(event, date));
        var prepared = generator.prepare(generator.sourceOf(template), memberId, context);
        return generation.file(
                template, memberId, session.member().id(), GenerationOrigin.appointment(event.id(), date), prepared);
    }

    private List<RequiredTemplate> inUse(int eventId) {
        return requirements.forEvent(eventId).stream()
                .filter(template -> !template.archived())
                .toList();
    }

    private static List<RequiredDocumentStatus> statuses(
            List<RequiredTemplate> required, List<RequirementGeneration> copies, int memberId) {
        return required.stream()
                .map(template -> status(
                        template,
                        copies.stream()
                                .filter(copy ->
                                        copy.memberId() == memberId && copy.templateId() == template.templateId())
                                .findFirst()
                                .orElse(null)))
                .toList();
    }

    private static RequiredDocumentStatus status(RequiredTemplate template, @Nullable RequirementGeneration copy) {
        if (copy == null) {
            return new RequiredDocumentStatus(
                    template.templateId(), template.name(), RequirementStatus.NOT_GENERATED, null, null, false);
        }
        return new RequiredDocumentStatus(
                template.templateId(),
                template.name(),
                RequirementStatus.GENERATED,
                copy.documentId(),
                copy.generatedAt(),
                copy.templateVersion() < template.version());
    }

    /** What a document says about the appointment on the date: its name, its times and its place. */
    private GenerationContext.EventFacts facts(StationEvent event, LocalDate date) {
        var span = event.occurrenceOn(date).orElseGet(() -> new StationEvent.Span(event.startTime(), event.endTime()));
        var location = AppointmentField.firstLocation(fields.findByEventOn(event.id(), date));
        return new GenerationContext.EventFacts(event.name(), span.start(), span.end(), location);
    }
}

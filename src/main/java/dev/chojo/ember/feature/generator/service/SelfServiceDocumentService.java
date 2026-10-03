/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.MissingValue;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService.GeneratedDocumentResponse;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Documents a member generates for themselves, and a guardian for each member in their care.
 *
 * <p>A template is offered where it is marked for self service and its audience takes in the member
 * the document is about; whoever asks has to be that member or look after them. Three things refuse a
 * document that is offered, each with words of its own: data the template needs and the profile does
 * not hold (the message names it, so it can be filled in first), a template generated for the same
 * member within its wait (the message says from when), and a station that keeps no documents.
 *
 * <p>Only documents generated here count towards the wait; one a manager filed does not keep the
 * member from generating their own.
 */
@Singleton
public class SelfServiceDocumentService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DocumentTemplateRepository templates;
    private final DocumentTemplateService templateService;
    private final DocumentGeneratorService generator;
    private final DocumentGenerationService generation;
    private final DocumentGenerationRepository generations;
    private final RestrictionService restrictions;
    private final GuardianPolicy guardians;
    private final DocumentService documents;
    private final StationRepository stations;
    private final Clock clock;

    @Inject
    public SelfServiceDocumentService(
            DocumentTemplateRepository templates,
            DocumentTemplateService templateService,
            DocumentGeneratorService generator,
            DocumentGenerationService generation,
            DocumentGenerationRepository generations,
            RestrictionService restrictions,
            GuardianPolicy guardians,
            DocumentService documents,
            StationRepository stations) {
        this(
                templates,
                templateService,
                generator,
                generation,
                generations,
                restrictions,
                guardians,
                documents,
                stations,
                Clock.systemUTC());
    }

    /**
     * @param clock what the wait is measured against, which a test moves
     */
    public SelfServiceDocumentService(
            DocumentTemplateRepository templates,
            DocumentTemplateService templateService,
            DocumentGeneratorService generator,
            DocumentGenerationService generation,
            DocumentGenerationRepository generations,
            RestrictionService restrictions,
            GuardianPolicy guardians,
            DocumentService documents,
            StationRepository stations,
            Clock clock) {
        this.templates = templates;
        this.templateService = templateService;
        this.generator = generator;
        this.generation = generation;
        this.generations = generations;
        this.restrictions = restrictions;
        this.guardians = guardians;
        this.documents = documents;
        this.stations = stations;
        this.clock = clock;
    }

    /**
     * A template a member may generate for themselves or a member in their care.
     *
     * @param templateId    the template
     * @param name          what it is called
     * @param legal         whether it makes a legal document
     * @param availableFrom when it can be generated again, or null where it can be now
     * @param missing       the data the profile still lacks, which keeps it from being generated
     */
    public record SelfServiceOffer(
            int templateId,
            String name,
            boolean legal,
            @Nullable Instant availableFrom,
            List<MissingValue> missing) {}

    /**
     * The templates the reader may generate for a member.
     *
     * @param session  the reader
     * @param memberId the reader or a member in their care
     * @return the templates by name, each with what stands in the way
     */
    public List<SelfServiceOffer> offers(StationSession session, int memberId) {
        requireMayActFor(session, memberId);
        documents.requireKept(session.stationId(), DocumentDoor.STATION);
        return templates.findByStation(session.stationId(), false).stream()
                .filter(template -> offered(template, memberId))
                .map(template -> offer(session, template, memberId))
                .toList();
    }

    /**
     * Generates a document for the reader or a member in their care and files it with that member.
     *
     * @param session    the reader
     * @param templateId the template
     * @param memberId   the reader or a member in their care
     * @return the filed document
     */
    public GeneratedDocumentResponse generate(StationSession session, int templateId, int memberId) {
        requireMayActFor(session, memberId);
        var template = templateService.requireInUse(session.owner(), templateId);
        if (!offered(template, memberId)) throw DocumentRefusal.DOCUMENT_SELF_SERVICE_NOT_OFFERED.raise();
        var openAgain = availableFrom(template, memberId);
        if (openAgain != null) {
            throw DocumentRefusal.DOCUMENT_SELF_SERVICE_COOLING_DOWN.raise(RefusalDetail.text(day(session, openAgain)));
        }
        var prepared = generator.prepare(
                generator.sourceOf(template),
                memberId,
                GenerationContext.by(session.member().id()));
        var missing = generator.missing(prepared);
        if (!missing.isEmpty()) {
            String labels = missing.stream().map(MissingValue::label).collect(Collectors.joining(", "));
            throw DocumentRefusal.DOCUMENT_SELF_SERVICE_VALUES_MISSING.raise(RefusalDetail.text(labels));
        }
        return generation.file(template, memberId, session.member().id(), true, prepared);
    }

    private SelfServiceOffer offer(StationSession session, DocumentTemplate template, int memberId) {
        var prepared = generator.prepare(
                generator.sourceOf(template),
                memberId,
                GenerationContext.by(session.member().id()));
        return new SelfServiceOffer(
                template.id(),
                template.name(),
                template.legal(),
                availableFrom(template, memberId),
                generator.missing(prepared));
    }

    private boolean offered(DocumentTemplate template, int memberId) {
        return template.selfService()
                && restrictions.includes(RestrictionType.DOCUMENT_TEMPLATE, template.id(), memberId);
    }

    /**
     * When the template can be generated for the member again, or null where it can be now.
     */
    private @Nullable Instant availableFrom(DocumentTemplate template, int memberId) {
        if (template.cooldownDays() <= 0) return null;
        var last = generations.lastSelfService(template.id(), memberId);
        if (last == null) return null;
        var open = last.plus(Duration.ofDays(template.cooldownDays()));
        return open.isAfter(clock.instant()) ? open : null;
    }

    private void requireMayActFor(StationSession session, int memberId) {
        if (!guardians.mayActFor(session.user(), memberId))
            throw DocumentRefusal.DOCUMENT_SELF_SERVICE_NOT_YOURS.raise();
    }

    private String day(StationSession session, Instant instant) {
        var zone =
                StationFormat.timezoneOf(stations.findById(session.stationId()).orElse(null));
        return DAY.format(instant.atZone(zone));
    }
}

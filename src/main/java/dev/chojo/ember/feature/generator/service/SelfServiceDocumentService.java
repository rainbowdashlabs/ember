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
import dev.chojo.ember.feature.generator.entity.GenerationOrigin;
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
import java.util.stream.Stream;

/**
 * Documents a member generates for themselves, and a guardian for each member in their care.
 *
 * <p>A template is offered where it is marked for self service and its audience takes in the member
 * the document is about; a template of the association where the association offers it and the station
 * switched it on for an audience of its own ({@link TemplateStationUseService}). Whoever asks has to be
 * that member or look after them. The wait is the template's, whoever keeps it. Three things refuse a
 * document that is offered, each with words of its own: data the template needs and the profile does
 * not hold (the message names it, so it can be filled in first), a template generated for the same
 * member within its wait (the message says from when), and a station that keeps no documents.
 *
 * <p>Only documents generated here count towards the wait; one a manager filed does not keep the
 * member from generating their own.
 *
 * <p>The document is always issued by the template's issuer at the station; an issuer that cannot be
 * named is missing data like any other.
 */
@Singleton
public class SelfServiceDocumentService {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final DocumentTemplateRepository templates;
    private final DocumentTemplateService templateService;
    private final TemplateStationUseService stationUses;
    private final DocumentGeneratorService generator;
    private final DocumentGenerationService generation;
    private final DocumentGenerationRepository generations;
    private final RestrictionService restrictions;
    private final GuardianPolicy guardians;
    private final DocumentService documents;
    private final StationRepository stations;
    private final DocumentIssuerService issuers;
    private final Clock clock;

    @Inject
    public SelfServiceDocumentService(
            DocumentTemplateRepository templates,
            DocumentTemplateService templateService,
            TemplateStationUseService stationUses,
            DocumentGeneratorService generator,
            DocumentGenerationService generation,
            DocumentGenerationRepository generations,
            RestrictionService restrictions,
            GuardianPolicy guardians,
            DocumentService documents,
            StationRepository stations,
            DocumentIssuerService issuers) {
        this(
                templates,
                templateService,
                stationUses,
                generator,
                generation,
                generations,
                restrictions,
                guardians,
                documents,
                stations,
                issuers,
                Clock.systemUTC());
    }

    /**
     * @param clock what the wait is measured against, which a test moves
     */
    public SelfServiceDocumentService(
            DocumentTemplateRepository templates,
            DocumentTemplateService templateService,
            TemplateStationUseService stationUses,
            DocumentGeneratorService generator,
            DocumentGenerationService generation,
            DocumentGenerationRepository generations,
            RestrictionService restrictions,
            GuardianPolicy guardians,
            DocumentService documents,
            StationRepository stations,
            DocumentIssuerService issuers,
            Clock clock) {
        this.templates = templates;
        this.templateService = templateService;
        this.stationUses = stationUses;
        this.generator = generator;
        this.generation = generation;
        this.generations = generations;
        this.restrictions = restrictions;
        this.guardians = guardians;
        this.documents = documents;
        this.stations = stations;
        this.issuers = issuers;
        this.clock = clock;
    }

    /**
     * A template a member may generate for themselves or a member in their care.
     *
     * @param templateId    the template
     * @param name          what it is called
     * @param legal         whether it makes a legal document
     * @param availableFrom when it can be generated again, or null where it can be now
     * @param lastUsedAt    when it was last generated for the member through self service, by them or
     *                      anybody acting for them, or null where it never was
     * @param missing       the data the profile still lacks, which keeps it from being generated
     */
    public record SelfServiceOffer(
            int templateId,
            String name,
            boolean legal,
            @Nullable Instant availableFrom,
            @Nullable Instant lastUsedAt,
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
        return Stream.concat(
                        templates.findByOwner(session.owner(), false).stream(),
                        templates.findOfAssociationOf(session.stationId()).stream())
                .filter(template -> offered(template, session.stationId(), memberId))
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
        var template = templateService.requireInUse(session.stationId(), templateId);
        if (!offered(template, session.stationId(), memberId)) {
            throw DocumentRefusal.DOCUMENT_SELF_SERVICE_NOT_OFFERED.raise();
        }
        var openAgain = availableFrom(template, generations.lastSelfService(template.id(), memberId));
        if (openAgain != null) {
            throw DocumentRefusal.DOCUMENT_SELF_SERVICE_COOLING_DOWN.raise(RefusalDetail.text(day(session, openAgain)));
        }
        var prepared = prepare(session, template, memberId);
        var missing = generator.missing(prepared);
        if (!missing.isEmpty()) {
            String labels = missing.stream().map(MissingValue::label).collect(Collectors.joining(", "));
            throw DocumentRefusal.DOCUMENT_SELF_SERVICE_VALUES_MISSING.raise(RefusalDetail.text(labels));
        }
        return generation.file(template, memberId, session.member().id(), GenerationOrigin.SELF_SERVICE, prepared);
    }

    /** The values of a document for the member, issued by the template's issuer at the station. */
    private DocumentGeneratorService.Prepared prepare(StationSession session, DocumentTemplate template, int memberId) {
        var issuer = issuers.ofTemplate(template, session.stationId());
        return generator.prepare(
                generator.sourceOf(template),
                memberId,
                GenerationContext.by(session.member().id(), issuer));
    }

    private SelfServiceOffer offer(StationSession session, DocumentTemplate template, int memberId) {
        var prepared = prepare(session, template, memberId);
        var lastUsed = generations.lastSelfService(template.id(), memberId);
        return new SelfServiceOffer(
                template.id(),
                template.name(),
                template.legal(),
                availableFrom(template, lastUsed),
                lastUsed,
                generator.missing(prepared));
    }

    /**
     * Whether a template is offered to a member: a station's own by its flag and its audience, one of the
     * association by what the station chose for it.
     */
    private boolean offered(DocumentTemplate template, int stationId, int memberId) {
        if (template.ofAssociation()) return stationUses.offers(template, stationId, memberId);
        return template.selfService()
                && restrictions.includes(RestrictionType.DOCUMENT_TEMPLATE, template.id(), memberId);
    }

    /**
     * When the template can be generated for the member again, or null where it can be now.
     *
     * @param last when it was last generated for the member through self service, or null
     */
    private @Nullable Instant availableFrom(DocumentTemplate template, @Nullable Instant last) {
        if (template.cooldownDays() <= 0 || last == null) return null;
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

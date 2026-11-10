/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.MovableClock;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.repository.TemplateStationUseRepository;
import dev.chojo.ember.feature.generator.service.font.DefaultFont;
import dev.chojo.ember.feature.generator.service.pdf.PdfStamper;
import dev.chojo.ember.feature.generator.service.pdf.StampFonts;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfPictures;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GenderFields;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.feature.signing.service.ProcedureSignatures;
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
import dev.chojo.ember.feature.signing.service.SignerResolver;
import dev.chojo.ember.feature.signing.service.SigningGuards;
import dev.chojo.ember.feature.signing.service.SigningWiring;
import dev.chojo.ember.feature.signing.service.TemplateDocumentStatements;
import dev.chojo.ember.feature.signing.service.TestNotices;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.PdfText;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The document generator wired for real against the test database, with a station of its own, for the
 * tests of what is built on top of generating one document: runs for many members and the documents
 * appointments ask for. Letters are drawn through Pandoc and Typst and read back as text.
 */
public abstract class GeneratorTestBase extends RepositoryTestBase {
    static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");

    /**
     * The generator of one station.
     *
     * @param station    the station
     * @param owner      the station as the owner of its templates
     * @param clock      what documents are dated by
     * @param templates  its templates
     * @param generator  what draws a document
     * @param generation what draws and files a document
     * @param documents  the member documents
     * @param log        the generation log
     * @param issuers    who issues the documents of a template
     */
    public record Wiring(
            Station station,
            Owner.Station owner,
            MovableClock clock,
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            DocumentGenerationService generation,
            DocumentService documents,
            DocumentGenerationRepository log,
            DocumentIssuerService issuers) {

        StationMember member(String email, String first, String last) {
            return stationMemberRepo.create(
                    station.id(), accountRepo.create(email, first, last).id());
        }

        /** The text of a filed document. */
        public String textOf(int documentId) {
            var document = memberDocumentRepo.findById(documentId).orElseThrow();
            return Objects.requireNonNull(
                    PdfText.extract(documents.read(document).orElseThrow()));
        }
    }

    static Wiring wire(String stationName) {
        return wire(stationRepo.create(stationName));
    }

    /**
     * The generator wired for an existing station, such as one a seeder built.
     *
     * @param station the station
     * @return its generator
     */
    public static Wiring wire(Station station) {
        return wire(station, stationRepo, stationMemberRepo);
    }

    /**
     * The generator wired for a station, reading stations and members where it fills in placeholders
     * through the given repositories, which a test counts the reads of.
     *
     * @param station  the station
     * @param stations where the generator reads stations
     * @param members  where the generator reads memberships and guardians
     * @return its generator
     */
    static Wiring wire(Station station, StationRepository stations, StationMemberRepository members) {
        return wire(station, stations, members, IssuerSigning.NONE);
    }

    /**
     * The generator wired for a station, signing letters for their issuer the given way.
     *
     * @param station the station
     * @param signing what signs a letter for its issuer before it is filed
     * @return its generator
     */
    static Wiring wire(Station station, IssuerSigning signing) {
        return wire(station, stationRepo, stationMemberRepo, signing);
    }

    private static Wiring wire(
            Station station, StationRepository stations, StationMemberRepository members, IssuerSigning signing) {
        var clock = new MovableClock(NOW);
        var owner = new Owner.Station(station.id());
        var backend = localStorage();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        var documents = newDocumentService(storage);
        var media = mock(MediaLibraryService.class);
        var pictures = mock(KbPdfPictures.class);
        when(pictures.place(anyInt(), anyString(), anyString()))
                .thenAnswer(call -> new KbPdfPictures.Placed(call.getArgument(1), Map.of()));
        var catalogue = newPlaceholderCatalogue();
        var templateRepository = new DocumentTemplateRepository();
        var pdfTemplates = new PdfTemplateRepository();
        var fonts = newFontLibrary(storage);
        var uses = new TemplateStationUseRepository();
        var issuers = new DocumentIssuerService(stationMemberRepo, uses);
        var checks = new TemplateChecks(
                templateRepository,
                pdfTemplates,
                new LetterChecks(contentBlocks(), media),
                stationRepo,
                catalogue,
                fonts,
                newOwnerStores(),
                issuers);
        var templates = new DocumentTemplateService(
                templateRepository, pdfTemplates, uses, checks, restrictionService, catalogue, newOwnerStores());
        var pdfRenderer = new PdfTemplateRenderer(
                new PdfTemplateService(
                        templates, templateRepository, pdfTemplates, newDocumentIntake(), storage, newOwnerStores()),
                new PdfStamper(new StampFonts(DefaultFont.absent())),
                fonts);
        var resolver = new PlaceholderResolver(
                stations,
                members,
                memberNameResolver,
                profileFieldRepo,
                profileFieldCore,
                new GenderFields(profileFieldCore, stationRepo),
                clusterService,
                clock);
        var generator = new DocumentGeneratorService(
                templates,
                resolver,
                catalogue,
                new LetterRenderer(pictures, media, newStationLogoService(), fonts, newOwnerStores()),
                pdfRenderer,
                stations,
                restrictionService,
                clock);
        var log = new DocumentGenerationRepository();
        var generation = new DocumentGenerationService(
                templates, generator, documents, newDocumentIntake(), log, checks, issuers, signing, clock);
        return new Wiring(station, owner, clock, templates, generator, generation, documents, log, issuers);
    }

    static StationSession as(StationMember member, StationPermission... permissions) {
        return stationSession(member, permissions);
    }

    static void refused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }

    /**
     * The signing services for the documents of one wired station, telling what happens to requests on the
     * given bus.
     *
     * @param wiring      the station's documents
     * @param requestRepo the signature requests
     * @param bus         where closed, replaced and withdrawn requests are announced
     * @return the services
     */
    public static SigningWiring signing(Wiring wiring, SignatureRequestRepository requestRepo, DomainEventBus bus) {
        var guardians = new GuardianPolicy(stationMemberRepo);
        var guards = new SigningGuards(
                new DocumentAccessService(memberDocumentRepo, wiring.documents(), guardians),
                memberDocumentRepo,
                guardians);
        var notices = TestNotices.notices(
                newNotifier(), emailQueueRepo, stationRepo, stationMemberRepo, accountRepo, memberDocumentRepo, bus);
        var requests = new SignatureRequestService(
                requestRepo,
                new SigningEvidenceRepository(),
                wiring.log(),
                memberDocumentRepo,
                wiring.documents(),
                stationMemberRepo,
                memberNameResolver,
                guardians,
                new SignerResolver(stationMemberRepo, memberNameResolver, memberPermissionResolver),
                guards,
                notices,
                new TemplateDocumentStatements(wiring.generator()));
        var procedures = new ProcedureSignatures(
                wiring.generation(),
                requests,
                stationMemberRepo,
                memberPermissionResolver,
                wiring.log(),
                requestRepo,
                notices);
        return new SigningWiring(guardians, guards, notices, requests, procedures);
    }
}

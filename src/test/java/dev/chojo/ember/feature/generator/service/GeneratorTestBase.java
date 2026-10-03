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
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.repository.TemplateStationUseRepository;
import dev.chojo.ember.feature.generator.service.pdf.PdfStamper;
import dev.chojo.ember.feature.generator.service.pdf.StampFonts;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfPictures;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
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
abstract class GeneratorTestBase extends RepositoryTestBase {
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
     */
    record Wiring(
            Station station,
            Owner.Station owner,
            MovableClock clock,
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            DocumentGenerationService generation,
            DocumentService documents,
            DocumentGenerationRepository log) {

        StationMember member(String email, String first, String last) {
            return stationMemberRepo.create(
                    station.id(), accountRepo.create(email, first, last).id());
        }

        /** The text of a filed document. */
        String textOf(int documentId) {
            var document = memberDocumentRepo.findById(documentId).orElseThrow();
            return Objects.requireNonNull(
                    PdfText.extract(documents.read(document).orElseThrow()));
        }
    }

    static Wiring wire(String stationName) {
        var clock = new MovableClock(NOW);
        var station = stationRepo.create(stationName);
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
        var checks = new TemplateChecks(
                templateRepository,
                pdfTemplates,
                new LetterChecks(contentBlocks(), media),
                stationRepo,
                catalogue,
                fonts,
                newOwnerStores());
        var templates = new DocumentTemplateService(
                templateRepository,
                pdfTemplates,
                new TemplateStationUseRepository(),
                checks,
                restrictionService,
                catalogue,
                newOwnerStores());
        var pdfRenderer = new PdfTemplateRenderer(
                new PdfTemplateService(
                        templates, templateRepository, pdfTemplates, newDocumentIntake(), storage, newOwnerStores()),
                new PdfStamper(new StampFonts()),
                fonts);
        var generator = new DocumentGeneratorService(
                templates,
                newPlaceholderResolver(clock),
                catalogue,
                new LetterRenderer(pictures, media, newStationLogoService(), fonts, newOwnerStores()),
                pdfRenderer,
                stationRepo,
                restrictionService,
                clock);
        var log = new DocumentGenerationRepository();
        var generation =
                new DocumentGenerationService(templates, generator, documents, newDocumentIntake(), log, checks, clock);
        return new Wiring(station, owner, clock, templates, generator, generation, documents, log);
    }

    static StationSession as(StationMember member, StationPermission... permissions) {
        return stationSession(member, permissions);
    }

    static void refused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }
}

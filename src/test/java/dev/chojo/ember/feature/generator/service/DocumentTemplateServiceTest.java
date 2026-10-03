/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.generator.entity.LetterCell;
import dev.chojo.ember.feature.generator.entity.LetterCellAlign;
import dev.chojo.ember.feature.generator.entity.LetterCellKind;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.Letterhead;
import dev.chojo.ember.feature.generator.entity.Placeholder;
import dev.chojo.ember.feature.generator.entity.PronounForm;
import dev.chojo.ember.feature.generator.entity.PronounSource;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Writing, changing and archiving templates, and every refusal of what a template may not say.
 */
class DocumentTemplateServiceTest extends RepositoryTestBase {
    private static final String PICTURE = "a".repeat(64);
    private static final String DOCUMENT = "b".repeat(64);
    private static final AtomicInteger letterheads = new AtomicInteger();

    private static DocumentTemplateService service;
    private static Station station;
    private static Owner.Station owner;
    private static int authorId;
    private static int choiceField;
    private static int textField;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Template Service Wache");
        owner = new Owner.Station(station.id());
        var account = accountRepo.create("template-service@test.com", "Tara", "Vorlage");
        authorId = stationMemberRepo.create(station.id(), account.id()).id();
        var config = new ProfileFieldConfig(
                null,
                false,
                false,
                List.of("m", "w"),
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        choiceField = profileFieldRepo
                .create(station.id(), "Geschlecht", FieldType.CHOICE, config, false, false, null)
                .id();
        textField = profileFieldRepo
                .create(station.id(), "Schule", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null)
                .id();

        var media = mock(MediaLibraryService.class);
        when(media.findByHash(any(), anyString())).thenReturn(Optional.empty());
        when(media.findByHash(eq(station.id()), eq(PICTURE))).thenReturn(Optional.of(file(PICTURE, "image/png")));
        when(media.findByHash(eq(station.id()), eq(DOCUMENT)))
                .thenReturn(Optional.of(file(DOCUMENT, "application/pdf")));
        var catalogue = new PlaceholderCatalogue(profileFieldRepo, stationRepo);
        var templates = new DocumentTemplateRepository();
        service = new DocumentTemplateService(
                templates,
                new TemplateChecks(templates, media, profileFieldRepo, catalogue),
                restrictionService,
                catalogue);
    }

    private static StationFile file(String hash, String mime) {
        return new StationFile(1, 0, station.id(), hash, "f", mime, 1, Instant.EPOCH, null, null, null);
    }

    private static DocumentTemplateRequest request(String name, String body) {
        return new DocumentTemplateRequest(
                name, null, null, null, false, null, false, false, null, null, null, null, body, null);
    }

    private static DocumentTemplateRequest letterhead(Letterhead letterhead) {
        return new DocumentTemplateRequest(
                "Briefkopf " + letterheads.incrementAndGet(),
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                letterhead,
                "",
                null);
    }

    private static void refused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }

    @Test
    void aNewTemplateStartsWithWhatWasLeftOut() {
        var created = service.create(owner, request("Bescheinigung", "Hallo {{member.firstName}}"), authorId);

        assertEquals("Bescheinigung {{today}}", created.titlePattern());
        assertEquals("Bescheinigung {{member.lastName}} {{today}}", created.fileNamePattern());
        assertEquals(30, created.cooldownDays());
        assertFalse(created.keepOnArchive());
        assertEquals(1, created.version());
        assertEquals(LetterPage.defaults(), created.page());
        assertEquals("Hallo {{member.firstName}}", created.bodyMarkdown());
    }

    @Test
    void aLegalTemplateKeepsItsDocumentsPastTheMembershipUnlessToldOtherwise() {
        var legal = new DocumentTemplateRequest(
                "Einverständnis",
                null,
                null,
                List.of("Recht", " Recht ", ""),
                false,
                null,
                true,
                false,
                null,
                null,
                null,
                null,
                "",
                null);

        var created = service.create(owner, legal, authorId);

        assertTrue(created.keepOnArchive());
        assertTrue(created.legal());
        assertEquals(List.of("Recht"), created.tags());
    }

    @Test
    void aChangeCountsTheVersionUpAndWritesTheAudience() {
        var created = service.create(owner, request("Teilnahme", ""), authorId);
        var audience = new RestrictionAudience(
                List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.OR);
        var change = new DocumentTemplateRequest(
                "Teilnahme",
                "Teilnahme {{member.fullName}}",
                "teilnahme",
                List.of(),
                true,
                false,
                false,
                true,
                7,
                audience,
                new PronounSource(choiceField, Map.of("w", PronounForm.SIE), PronounForm.NAME),
                null,
                "Neu",
                null);

        var changed = service.update(owner, created.id(), change, authorId);

        assertEquals(2, changed.version());
        assertEquals("Teilnahme {{member.fullName}}", changed.titlePattern());
        assertEquals(7, changed.cooldownDays());
        assertEquals(List.of(StationUserType.MEMBER), changed.audience().userTypes());
        assertEquals(RestrictionMode.OR, changed.audience().mode());
        assertEquals(PronounForm.SIE, changed.pronounSource().formOf("w"));
        assertEquals("Neu", service.detail(owner, created.id()).bodyMarkdown());
    }

    /** A template is archived, never deleted, and comes back under its name where that is still free. */
    @Test
    void anArchivedTemplateLeavesTheListAndComesBack() {
        var created = service.create(owner, request("Archivierbar", ""), authorId);

        var archived = service.setArchived(owner, created.id(), true, authorId);

        assertNotNull(archived.archivedAt());
        assertTrue(service.list(owner, false).stream().noneMatch(template -> template.id() == created.id()));
        assertTrue(service.list(owner, true).stream().anyMatch(template -> template.id() == created.id()));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED, () -> service.requireInUse(owner, created.id()));

        var namesake = service.create(owner, request("Archivierbar", ""), authorId);
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NAME_TAKEN,
                () -> service.setArchived(owner, created.id(), false, authorId));
        service.setArchived(owner, namesake.id(), true, authorId);

        assertNull(service.setArchived(owner, created.id(), false, authorId).archivedAt());
    }

    @Test
    void anotherStationsTemplateIsNotHere() {
        var other = stationRepo.create("Template Service Fremd");
        var created = service.create(owner, request("Fremd", ""), authorId);

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE,
                () -> service.detail(new Owner.Station(other.id()), created.id()));
        stationRepo.delete(other.id());
    }

    @Test
    void namesAreNeededAndUnique() {
        service.create(owner, request("Doppelt", ""), authorId);

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NAME_MISSING,
                () -> service.create(owner, request(" ", ""), authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NAME_TAKEN,
                () -> service.create(owner, request("doppelt", ""), authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG,
                () -> service.create(owner, request("x".repeat(121), ""), authorId));
    }

    @Test
    void placeholdersHaveToExist() {
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN,
                () -> service.create(owner, request("Unbekannt", "{{member.shoeSize}}"), authorId));
        assertEquals(
                "{{profile." + textField + "}}",
                service.create(owner, request("Profil", "{{profile." + textField + "}}"), authorId)
                        .bodyMarkdown());
    }

    /** A legal document names a member by the register name only. */
    @Test
    void aLegalTemplateRefusesTheCalledName() {
        var legal = new DocumentTemplateRequest(
                "Rechtlich",
                null,
                null,
                null,
                false,
                null,
                true,
                false,
                null,
                null,
                null,
                null,
                "{{member.calledName}}",
                null);

        refused(DocumentRefusal.DOCUMENT_TEMPLATE_CALLED_NAME_IN_LEGAL, () -> service.create(owner, legal, authorId));
        assertEquals(
                "{{member.calledName}}",
                service.create(owner, request("Informell", "{{member.calledName}}"), authorId)
                        .bodyMarkdown());
    }

    @Test
    void theLetterheadHoldsThreeCellsOfPicturesFromTheLibrary() {
        var text = new LetterCell(LetterCellKind.TEXT, PICTURE, "{{station.name}}", LetterCellAlign.RIGHT, 0);
        var picture = new LetterCell(LetterCellKind.IMAGE, PICTURE, "ignored", null, 25);

        var created = service.create(
                owner, letterhead(new Letterhead(List.of(picture, LetterCell.empty(), text), List.of())), authorId);

        var header = created.letterhead().header();
        assertEquals(new LetterCell(LetterCellKind.IMAGE, PICTURE, null, LetterCellAlign.LEFT, 25), header.get(0));
        assertEquals(
                new LetterCell(LetterCellKind.TEXT, null, "{{station.name}}", LetterCellAlign.RIGHT, 18),
                header.get(2));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_TOO_MANY_CELLS,
                () -> service.create(
                        owner, letterhead(new Letterhead(List.of(), List.of(text, text, text, text))), authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PICTURE_NOT_HERE,
                () -> service.create(
                        owner,
                        letterhead(new Letterhead(
                                List.of(new LetterCell(LetterCellKind.IMAGE, DOCUMENT, null, LetterCellAlign.LEFT, 0)),
                                List.of())),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PICTURE_NOT_HERE,
                () -> service.create(
                        owner,
                        letterhead(new Letterhead(
                                List.of(new LetterCell(LetterCellKind.IMAGE, null, null, LetterCellAlign.LEFT, 0)),
                                List.of())),
                        authorId));
    }

    @Test
    void thePageAndTheSettingsStayWithinBounds() {
        var narrow = new DocumentTemplateRequest(
                "Rand",
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                "",
                new LetterPage(2, 30, 20, 20, 10));
        var negative = new DocumentTemplateRequest(
                "Warten", null, null, null, false, null, false, true, -1, null, null, null, "", null);
        var notAChoice = new DocumentTemplateRequest(
                "Pronomen",
                null,
                null,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                new PronounSource(textField, Map.of(), PronounForm.NAME),
                null,
                "",
                null);

        refused(DocumentRefusal.DOCUMENT_TEMPLATE_PAGE_OUT_OF_BOUNDS, () -> service.create(owner, narrow, authorId));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_COOLDOWN_NEGATIVE, () -> service.create(owner, negative, authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PRONOUN_FIELD_NOT_CHOICE,
                () -> service.create(owner, notAChoice, authorId));
    }

    @Test
    void theCatalogueOffersTheStationsQuestionsAndItsChoiceFields() {
        var catalogue = service.catalogue(owner);

        var keys = catalogue.placeholders().stream().map(Placeholder::key).toList();
        assertTrue(keys.contains("member.firstName"));
        assertTrue(keys.contains("profile." + textField));
        assertTrue(keys.contains("guardian2.profile." + textField));
        assertEquals("Vorname", catalogue.placeholders().getFirst().label());
        assertTrue(catalogue.placeholders().stream()
                .filter(placeholder -> placeholder.key().equals("member.calledName"))
                .allMatch(Placeholder::informal));
        assertEquals(
                List.of(new DocumentTemplateService.ChoiceField(choiceField, "Geschlecht", List.of("m", "w"))),
                catalogue.choiceFields());
    }
}

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
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.entity.GuardianCondition;
import dev.chojo.ember.feature.content.route.BlockCellRequest;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.Placeholder;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.divider;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.image;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.lined;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.rowsOf;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.spacer;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
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

    private static DocumentTemplateService service;
    private static Station station;
    private static Owner.Station owner;
    private static int authorId;
    private static int textField;
    private static int guardianField;
    private static int trialField;
    private static int unaskedField;
    private static int allergiesField;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Template Service Wache");
        owner = new Owner.Station(station.id());
        var account = accountRepo.create("template-service@test.com", "Tara", "Vorlage");
        authorId = stationMemberRepo.create(station.id(), account.id()).id();
        textField = field("Schule", ProfileFieldScope.MEMBER, ProfileFieldScope.GUARDIAN);
        guardianField = field("Arbeitgeber", ProfileFieldScope.GUARDIAN);
        trialField = field("Schnupperwunsch", ProfileFieldScope.TRIAL);
        unaskedField = field("Ungefragt");
        memberQuestion("Medizinisches", FieldType.SECTION, 1);
        allergiesField = memberQuestion("Allergien", FieldType.TEXT, 2);

        var media = mock(MediaLibraryService.class);
        when(media.findByHash(any(), anyString())).thenReturn(Optional.empty());
        when(media.findByHash(eq(station.id()), eq(PICTURE))).thenReturn(Optional.of(file(PICTURE, "image/png")));
        when(media.findByHash(eq(station.id()), eq(DOCUMENT)))
                .thenReturn(Optional.of(file(DOCUMENT, "application/pdf")));
        var catalogue = new PlaceholderCatalogue(profileFieldRepo, stationRepo);
        var templates = new DocumentTemplateRepository();
        var pdfTemplates = new PdfTemplateRepository();
        service = new DocumentTemplateService(
                templates,
                pdfTemplates,
                new TemplateChecks(
                        templates, pdfTemplates, new LetterChecks(contentBlocks(), media), stationRepo, catalogue),
                restrictionService,
                catalogue);
    }

    private static StationFile file(String hash, String mime) {
        return new StationFile(1, 0, station.id(), hash, "f", mime, 1, Instant.EPOCH, null, null, null);
    }

    /** A text question of the station, put to the given kinds of member. */
    private static int field(String name, ProfileFieldScope... roles) {
        int id = profileFieldRepo
                .create(station.id(), name, FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null)
                .id();
        for (var role : roles) profileFieldRepo.assignToRole(id, role, 0, null, null, null);
        return id;
    }

    /** A question on the member's form, at the given place. */
    private static int memberQuestion(String name, FieldType type, int position) {
        int id = profileFieldRepo
                .create(station.id(), name, type, ProfileFieldConfig.empty(), false, false, null)
                .id();
        profileFieldRepo.assignToRole(id, ProfileFieldScope.MEMBER, position, null, null, null);
        return id;
    }

    private static Placeholder placeholder(String key) {
        return service.catalogue(owner).placeholders().stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private static void refused(Refusal refusal, Executable action) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, action).refusal());
    }

    private static List<String> texts(List<ContentRow> rows) {
        return LetterContent.textsOf(rows).toList();
    }

    /** A block of rows stacked in a column, the rows written as JSON the way the editor keeps them. */
    private static BlockCellRequest nested(String rows) {
        var config = JsonNodeFactory.instance.objectNode();
        config.set("rows", CellConfig.MAPPER.readTree(rows));
        return new BlockCellRequest(0, 100.0, CellContentType.NESTED_ROWS.name(), "", config);
    }

    @Test
    void aNewTemplateStartsWithWhatWasLeftOut() {
        var created = service.create(
                owner, letter("Bescheinigung", "Hallo {{member.firstName}}").build(), authorId);

        assertEquals("Bescheinigung {{today}}", created.titlePattern());
        assertEquals("Bescheinigung {{member.lastName}} {{today}}", created.fileNamePattern());
        assertEquals(30, created.cooldownDays());
        assertFalse(created.keepOnArchive());
        assertEquals(1, created.version());
        assertEquals(LetterPage.defaults(), created.page());
        assertEquals(DocumentLanguage.DE, created.language(), "the station's language where none was chosen");
        assertEquals(List.of("Hallo {{member.firstName}}"), texts(created.body()));
        assertTrue(created.header().isEmpty());
    }

    @Test
    void aLegalTemplateKeepsItsDocumentsPastTheMembershipUnlessToldOtherwise() {
        var created = service.create(
                owner,
                letter("Einverständnis")
                        .legal()
                        .tags(List.of("Recht", " Recht ", ""))
                        .build(),
                authorId);

        assertTrue(created.keepOnArchive());
        assertTrue(created.legal());
        assertEquals(List.of("Recht"), created.tags());
    }

    @Test
    void aChangeCountsTheVersionUpAndWritesTheAudienceAndTheLanguage() {
        var created = service.create(owner, letter("Teilnahme").build(), authorId);
        var audience = new RestrictionAudience(
                List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.OR);
        var change = letter("Teilnahme", "Neu")
                .title("Teilnahme {{member.fullName}}")
                .fileName("teilnahme")
                .tags(List.of())
                .hidden(true)
                .keepOnArchive(false)
                .selfService(true)
                .cooldown(7)
                .audience(audience)
                .language(DocumentLanguage.EN)
                .build();

        var changed = service.update(owner, created.id(), change, authorId);

        assertEquals(2, changed.version());
        assertEquals("Teilnahme {{member.fullName}}", changed.titlePattern());
        assertEquals(7, changed.cooldownDays());
        assertEquals(List.of(StationUserType.MEMBER), changed.audience().userTypes());
        assertEquals(RestrictionMode.OR, changed.audience().mode());
        assertEquals(DocumentLanguage.EN, changed.language());
        assertEquals(List.of("Neu"), texts(service.detail(owner, created.id()).body()));
    }

    /** A template is archived, never deleted, and comes back under its name where that is still free. */
    @Test
    void anArchivedTemplateLeavesTheListAndComesBack() {
        var created = service.create(owner, letter("Archivierbar").build(), authorId);

        var archived = service.setArchived(owner, created.id(), true, authorId);

        assertNotNull(archived.archivedAt());
        assertTrue(service.list(owner, false).stream().noneMatch(template -> template.id() == created.id()));
        assertTrue(service.list(owner, true).stream().anyMatch(template -> template.id() == created.id()));
        refused(DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED, () -> service.requireInUse(owner, created.id()));

        var namesake = service.create(owner, letter("Archivierbar").build(), authorId);
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NAME_TAKEN,
                () -> service.setArchived(owner, created.id(), false, authorId));
        service.setArchived(owner, namesake.id(), true, authorId);

        assertNull(service.setArchived(owner, created.id(), false, authorId).archivedAt());
    }

    @Test
    void anotherStationsTemplateIsNotHere() {
        var other = stationRepo.create("Template Service Fremd");
        var created = service.create(owner, letter("Fremd").build(), authorId);

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE,
                () -> service.detail(new Owner.Station(other.id()), created.id()));
        stationRepo.delete(other.id());
    }

    @Test
    void namesAreNeededAndUnique() {
        service.create(owner, letter("Doppelt").build(), authorId);

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NAME_MISSING,
                () -> service.create(owner, letter(" ").build(), authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_NAME_TAKEN,
                () -> service.create(owner, letter("doppelt").build(), authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG,
                () -> service.create(owner, letter("x".repeat(121)).build(), authorId));
    }

    @Test
    void placeholdersHaveToExistInEveryBlock() {
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN,
                () -> service.create(
                        owner, letter("Unbekannt", "Gut", "{{member.shoeSize}}").build(), authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN,
                () -> service.create(
                        owner,
                        letter("Unbekannt im Kopf")
                                .header(rowsOf("{{station.motto}}"))
                                .build(),
                        authorId));
        assertEquals(
                List.of("{{profile." + textField + "}}", "{{pronoun.possessive.start.en}}"),
                texts(service.create(
                                owner,
                                letter("Profil", "{{profile." + textField + "}}", "{{pronoun.possessive.start.en}}")
                                        .build(),
                                authorId)
                        .body()));
    }

    /** A legal document names a member by the register name only. */
    @Test
    void aLegalTemplateRefusesTheCalledName() {
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_CALLED_NAME_IN_LEGAL,
                () -> service.create(
                        owner,
                        letter("Rechtlich", "{{member.calledName}}").legal().build(),
                        authorId));
        assertEquals(
                List.of("{{member.calledName}}"),
                texts(service.create(
                                owner,
                                letter("Informell", "{{member.calledName}}").build(),
                                authorId)
                        .body()));
    }

    /**
     * A signature line stands in the body, names its signer and keeps a short text with placeholders
     * under it. The placeholder that stood for the issuer's field before is no placeholder any more.
     */
    @Test
    void aSignatureLineStandsInTheBodyWithItsSigner() {
        var signed = service.create(
                owner,
                letter("Signiert")
                        .body(List.of(
                                row(text("Gruß")),
                                row(signature(SignatureRole.ISSUER, "{{generatedBy.fullName}}, Jugendwart"))))
                        .build(),
                authorId);

        var line = signed.body().get(1).cells().getFirst();
        assertEquals(CellContentType.SIGNATURE, line.contentType());
        assertEquals(new CellConfig.SignatureConfig(SignatureRole.ISSUER), line.config());
        assertEquals(List.of("Gruß", "{{generatedBy.fullName}}, Jugendwart"), texts(signed.body()));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN,
                () -> service.create(
                        owner, letter("Alt", "{{signature.issuer}}").build(), authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN,
                () -> service.create(
                        owner,
                        letter("Unbekannt")
                                .body(List.of(row(signature(SignatureRole.ISSUER, "{{nobody.knows}}"))))
                                .build(),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_SIGNER_MISSING,
                () -> service.create(
                        owner,
                        letter("Ohne").body(List.of(row(signature(null, "")))).build(),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_SIGNATURE_OUTSIDE_BODY,
                () -> service.create(
                        owner,
                        letter("Fuß")
                                .footer(List.of(row(signature(SignatureRole.ISSUER, ""))))
                                .build(),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG,
                () -> service.create(
                        owner,
                        letter("Lang")
                                .body(List.of(row(signature(
                                        SignatureRole.ISSUER, "x".repeat(LetterChecks.MAX_LETTERHEAD_TEXT + 1)))))
                                .build(),
                        authorId));
        assertTrue(service.catalogue(owner).placeholders().stream()
                .noneMatch(placeholder -> placeholder.key().startsWith("signature.")));
    }

    /**
     * Two lines for one signer pass when their audiences keep them apart, and are refused when some
     * member would always get both.
     */
    @Test
    void aSignerTwiceIsRefusedOnlyWhereEveryMemberWouldGetBoth() {
        var trial = new RestrictionAudience(
                List.of(StationUserType.TRIAL), List.of(), List.of(), List.of(), RestrictionMode.AND);
        var members = new RestrictionAudience(
                List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.AND);

        service.create(
                owner,
                letter("Alternativen")
                        .body(List.of(
                                row(signature(SignatureRole.ISSUER, "Probe", trial)),
                                row(signature(SignatureRole.ISSUER, "Mitglied", members))))
                        .build(),
                authorId);
        service.create(
                owner,
                letter("Zwei Erziehungsberechtigte")
                        .body(List.of(
                                row(signature(SignatureRole.GUARDIAN_1, ""), signature(SignatureRole.GUARDIAN_2, ""))))
                        .build(),
                authorId);
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_SIGNER_TWICE,
                () -> service.create(
                        owner,
                        letter("Zweimal")
                                .body(List.of(
                                        row(signature(SignatureRole.ISSUER, "")),
                                        row(signature(SignatureRole.ISSUER, ""))))
                                .build(),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_SIGNER_TWICE,
                () -> service.create(
                        owner,
                        letter("Jede und eine")
                                .body(List.of(row(
                                        signature(SignatureRole.EACH_GUARDIAN, ""),
                                        signature(SignatureRole.GUARDIAN_2, ""))))
                                .build(),
                        authorId));
    }

    @Test
    void aLetterTakesLinesGapsAndLinesBetweenColumns() {
        var created = service.create(
                owner,
                letter("Linien")
                        .header(List.of(lined(text("links"), text("rechts"))))
                        .body(List.of(
                                row(divider("Termine")),
                                row(spacer(40)),
                                row(text("beide", GuardianCondition.SECOND_GUARDIAN))))
                        .build(),
                authorId);

        assertTrue(created.header().getFirst().columnLines());
        assertFalse(created.body().getFirst().columnLines());
        assertEquals(
                new CellConfig.DividerConfig("Termine"),
                created.body().getFirst().cells().getFirst().config());
        assertEquals(
                new CellConfig.SpacerConfig(40),
                created.body().get(1).cells().getFirst().config());
        assertEquals(
                GuardianCondition.SECOND_GUARDIAN,
                created.body().get(2).cells().getFirst().guardianCondition());
    }

    @Test
    void aLetterIsBuiltFromRowsOfUpToThreeColumns() {
        var audience = new RestrictionAudience(
                List.of(StationUserType.TRIAL), List.of(), List.of(), List.of(), RestrictionMode.AND);
        var created = service.create(
                owner,
                letter("Spalten")
                        .header(List.of(row(image("logo"), text("{{station.name}}"), image(PICTURE))))
                        .body(List.of(
                                row(text("Für alle"), text("Nur zur Probe", audience)),
                                row(nested(
                                        "[{\"cells\":[{\"contentType\":\"MARKDOWN\",\"content\":\"Gestapelt\"}]}]"))))
                        .build(),
                authorId);

        var header = created.header().getFirst().cells();
        assertEquals(
                List.of(ContentCell.STATION_LOGO, "{{station.name}}", PICTURE),
                header.stream().map(ContentCell::content).toList());
        assertEquals(audience, created.body().getFirst().cells().get(1).restriction());
        assertEquals(List.of("Für alle", "Nur zur Probe", "Gestapelt"), texts(created.body()));

        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_TOO_MANY_CELLS,
                () -> service.create(
                        owner,
                        letter("Vier")
                                .body(List.of(row(text("a"), text("b"), text("c"), text("d"))))
                                .build(),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_TOO_MANY_CELLS,
                () -> service.create(
                        owner,
                        letter("Vier gestapelt")
                                .body(List.of(row(nested("[{\"cells\":[{\"contentType\":\"MARKDOWN\"},"
                                        + "{\"contentType\":\"MARKDOWN\"},{\"contentType\":\"MARKDOWN\"},"
                                        + "{\"contentType\":\"MARKDOWN\"}]}]"))))
                                .build(),
                        authorId));
    }

    @Test
    void aLetterHoldsTextsAndPicturesOfTheLibraryOnly() {
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_BLOCK_NOT_TAKEN,
                () -> service.create(
                        owner,
                        letter("Video")
                                .body(List.of(row(TemplateRequestBuilder.block(
                                        CellContentType.VIDEO, "https://example.org", null))))
                                .build(),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PICTURE_NOT_HERE,
                () -> service.create(
                        owner,
                        letter("Kein Bild").body(List.of(row(image(DOCUMENT)))).build(),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PICTURE_NOT_HERE,
                () -> service.create(
                        owner, letter("Leer").header(List.of(row(image("")))).build(), authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG,
                () -> service.create(
                        owner,
                        letter("Langer Kopf")
                                .header(rowsOf("x".repeat(LetterChecks.MAX_LETTERHEAD_TEXT + 1)))
                                .build(),
                        authorId));
    }

    @Test
    void thePageAndTheSettingsStayWithinBounds() {
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PAGE_OUT_OF_BOUNDS,
                () -> service.create(
                        owner,
                        letter("Rand").page(new LetterPage(2, 30, 20, 20, 10)).build(),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_COOLDOWN_NEGATIVE,
                () -> service.create(
                        owner, letter("Warten").selfService(true).cooldown(-1).build(), authorId));
    }

    @Test
    void theCatalogueOffersTheStationsQuestionsAndThePronounsWithTheirEndings() {
        var catalogue = service.catalogue(owner);

        var keys = catalogue.placeholders().stream().map(Placeholder::key).toList();
        assertTrue(keys.contains("member.firstName"));
        assertTrue(keys.contains("profile." + textField));
        assertTrue(keys.contains("guardian2.profile." + textField));
        assertTrue(keys.containsAll(List.of("pronoun.subject", "pronoun.dative.start", "pronoun.possessive.es")));
        assertFalse(keys.contains("pronoun.subject.e"));
        assertEquals("Vorname", catalogue.placeholders().getFirst().label());
        assertTrue(catalogue.placeholders().stream()
                .filter(placeholder -> placeholder.key().equals("member.calledName"))
                .allMatch(Placeholder::informal));
    }

    @Test
    void theCatalogueOffersAQuestionOnlyForWhomItIsPutTo() {
        var keys = service.catalogue(owner).placeholders().stream()
                .map(Placeholder::key)
                .toList();

        assertTrue(keys.contains("guardian1.profile." + guardianField));
        assertFalse(keys.contains("profile." + guardianField));
        assertTrue(keys.contains("profile." + trialField));
        assertFalse(keys.contains("guardian1.profile." + trialField));
        assertFalse(keys.contains("guardian2.profile." + trialField));
        assertFalse(keys.contains("profile." + unaskedField));
        assertFalse(keys.contains("guardian1.profile." + unaskedField));
    }

    @Test
    void everyPlaceholderNamesThePathThePickerOffersItUnder() {
        assertEquals(
                List.of("Mitglied", "Stammdaten", "Vorname"),
                placeholder("member.firstName").path());
        assertEquals(
                List.of("Mitglied", "Profil", "Schule"),
                placeholder("profile." + textField).path());
        assertEquals(
                List.of("Mitglied", "Profil", "Medizinisches", "Allergien"),
                placeholder("profile." + allergiesField).path());
        var guardian = placeholder("guardian1.firstName");
        assertEquals(List.of("Erziehungsberechtigte 1", "Stammdaten", "Vorname"), guardian.path());
        assertEquals("Erziehungsberechtigte 1: Vorname", guardian.label());
        assertEquals(
                List.of("Erziehungsberechtigte 2", "Profil", "Schule"),
                placeholder("guardian2.profile." + textField).path());
        assertEquals(
                List.of("Pronomen", "Wer (er / sie)", "Er / Sie / Vorname (Satzanfang)"),
                placeholder("pronoun.subject.start").path());
        assertEquals(
                List.of("Pronomen", "Wessen (sein / ihr)", "Am Satzanfang", "Seinen / Ihren / Vornamens"),
                placeholder("pronoun.possessive.start.en").path());
        assertEquals(
                List.of("Wache", "Name der Wache"), placeholder("station.name").path());

        var categories = service.catalogue(owner).placeholders().stream()
                .map(Placeholder::category)
                .toList();
        assertEquals(categories.stream().sorted().toList(), categories);
    }

    @Test
    void aTemplateNamesAQuestionOnlyForWhomItIsPutTo() {
        assertNotNull(service.create(
                owner,
                letter("Arbeitgeber", "{{guardian1.profile." + guardianField + "}}", "{{profile." + trialField + "}}")
                        .build(),
                authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN,
                () -> service.create(
                        owner,
                        letter("Mitglied", "{{profile." + guardianField + "}}").build(),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN,
                () -> service.create(
                        owner,
                        letter("Elternteil", "{{guardian2.profile." + trialField + "}}")
                                .build(),
                        authorId));
        refused(
                DocumentRefusal.DOCUMENT_TEMPLATE_PLACEHOLDER_UNKNOWN,
                () -> service.create(
                        owner,
                        letter("Ungefragt", "{{profile." + unaskedField + "}}").build(),
                        authorId));
    }
}

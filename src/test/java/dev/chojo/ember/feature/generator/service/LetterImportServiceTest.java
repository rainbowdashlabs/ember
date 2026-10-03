/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A certificate written in Word or in an OpenDocument editor, read into a template body: its gaps
 * become placeholders where they are recognised, its pictures go into the media library, and anything
 * else stays as it was written.
 */
class LetterImportServiceTest extends RepositoryTestBase {
    private static final String HASH = "d".repeat(64);

    private static LetterImportService service;
    private static MediaLibraryService media;
    private static StationSession session;
    private static int schoolField;

    @BeforeAll
    static void setup() throws IOException {
        var station = stationRepo.create("Letter Import Wache");
        var member = stationMemberRepo.create(
                station.id(),
                accountRepo.create("letter-import@test.com", "Doc", "Import").id());
        session = stationSession(member);
        schoolField = profileFieldRepo
                .create(station.id(), "Schule", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null)
                .id();
        media = mock(MediaLibraryService.class);
        when(media.upload(eq(station.id()), any(), any(), anyString(), anyString(), any()))
                .thenReturn(new StationFile(
                        5, 0, station.id(), HASH, "bild.png", "image/png", 1, Instant.EPOCH, null, null, null));
        service = new LetterImportService(
                media, new PlaceholderCatalogue(profileFieldRepo, stationRepo), newDocumentIntake());
    }

    private static byte[] fixture(String name) throws IOException {
        try (var in = LetterImportServiceTest.class.getResourceAsStream("/generator/" + name)) {
            return Objects.requireNonNull(in, name).readAllBytes();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"certificate.docx", "certificate.odt"})
    void gapsBecomePlaceholdersWhereTheirWordsAreKnown(String file) throws IOException {
        var imported = service.read(session, file, null, fixture(file));

        String body = text(imported);
        assertTrue(body.contains("Berlin, den {{today}}"), body);
        assertTrue(body.contains("dass {{member.fullName}}, geboren am {{member.birthDate}}"), body);
        assertTrue(body.contains("seit {{member.joinDate.monthYear}} Mitglied"), body);
        assertTrue(body.contains("{{member.firstName}} kommt"), body);
        assertTrue(body.contains("engagiert {{pronoun.subject}} sich. {{pronoun.subject.start}} besucht"), body);
        assertTrue(body.contains("die {{profile." + schoolField + "}}."), body);
        assertTrue(body.contains("\\[Uhrzeit\\]"), body);
        assertTrue(body.contains("[Link](https://example.org)"), body);
        assertEquals(List.of("Uhrzeit"), imported.unrecognised());
        assertTrue(imported.recognised().containsAll(List.of("Vorname Nachname", "er/sie", "Er/Sie", "Schule")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"certificate.docx", "certificate.odt"})
    void picturesOfTheBodyGoIntoTheMediaLibrary(String file) throws IOException {
        var imported = service.read(session, file, null, fixture(file));

        var pictures = LetterContent.blocks(imported.rows())
                .filter(cell -> cell.contentType() == CellContentType.IMAGE)
                .map(ContentCell::content)
                .toList();
        String text = text(imported);
        assertTrue(
                pictures.contains(HASH) || text.contains("/api/v1/public/media/" + session.stationUid() + "/" + HASH),
                "the picture is a block of its own, or stays in its paragraph");
        assertFalse(text.contains("extracted/"));
        verify(media, atLeastOnce())
                .upload(eq(session.stationId()), any(), eq(session.member().id()), anyString(), eq("image/png"), any());
    }

    /** What a file is, is read from the file: a Word document renamed is still read as one. */
    @Test
    void theFormatIsReadFromTheFileNotFromItsName() throws IOException {
        var imported = service.read(session, "bescheinigung.odt", null, fixture("certificate.docx"));

        assertTrue(text(imported).contains("{{member.fullName}}"));
    }

    /** A picture on a line of its own becomes a picture block; the text around it stays text. */
    @Test
    void aPictureStandingAloneBecomesABlockOfItsOwn() {
        var rows = LetterImportService.blocks("""
                Vorher mit ![klein](/api/v1/public/media/abc/%s) im Satz.

                ![Logo](/api/v1/public/media/abc/%s){width="3cm"}

                Nachher""".formatted("e".repeat(64), HASH));

        var cells = rows.stream().map(row -> row.cells().getFirst()).toList();
        assertEquals(3, cells.size());
        assertEquals(CellContentType.MARKDOWN, cells.getFirst().contentType());
        assertTrue(cells.getFirst().content().contains("im Satz"));
        assertEquals(CellContentType.IMAGE, cells.get(1).contentType());
        assertEquals(HASH, cells.get(1).content());
        assertEquals("Nachher", cells.get(2).content());
        assertTrue(LetterImportService.blocks("  \n").isEmpty());
    }

    /** The texts of an import in order, one after the other. */
    private static String text(LetterImportService.LetterImport imported) {
        return LetterContent.textsOf(imported.rows()).collect(Collectors.joining("\n\n"));
    }

    @Test
    void onlyWordAndOpenDocumentTextsAreRead() throws IOException {
        var pdf = assertThrows(
                RefusalResponse.class,
                () -> service.read(session, "bescheinigung.docx", null, new byte[] {0x25, 0x50, 0x44, 0x46}));
        var epub = assertThrows(RefusalResponse.class, () -> service.read(session, "buch.epub", null, epub()));
        var oldWord = assertThrows(
                RefusalResponse.class,
                () -> service.read(session, "alt.doc", "application/msword", new byte[] {
                    (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0
                }));

        assertEquals(DocumentRefusal.DOCUMENT_IMPORT_KIND_NOT_TAKEN, pdf.refusal());
        assertEquals(DocumentRefusal.DOCUMENT_IMPORT_KIND_NOT_TAKEN, epub.refusal());
        assertEquals(DocumentRefusal.DOCUMENT_IMPORT_KIND_NOT_TAKEN, oldWord.refusal());
        assertEquals(
                DocumentRefusal.DOCUMENT_UPLOAD_MISSING_FILE,
                assertThrows(RefusalResponse.class, () -> service.read(session, null))
                        .refusal());
    }

    @Test
    void aWordDocumentPandocCannotReadIsRefusedAsUnreadable() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("kein xml".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertEquals(
                DocumentRefusal.DOCUMENT_IMPORT_UNREADABLE,
                assertThrows(RefusalResponse.class, () -> service.read(session, "kaputt.docx", null, out.toByteArray()))
                        .refusal());
    }

    private static byte[] epub() throws IOException {
        try (var in = LetterImportServiceTest.class.getResourceAsStream("/pandoc/sample.epub")) {
            return Objects.requireNonNull(in, "the epub").readAllBytes();
        }
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Signing;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.DocumentFilter;
import dev.chojo.ember.feature.documents.entity.SealedVersion;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.documents.service.SealedDocumentService.SealedFiling;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import dev.chojo.ember.feature.legal.service.GdprExportService;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.signing.entity.SealedDocument;
import dev.chojo.ember.feature.signing.service.PdfSealer;
import dev.chojo.ember.feature.signing.service.SignatureImageService;
import dev.chojo.ember.feature.signing.service.StationKeyRevocations;
import dev.chojo.ember.feature.signing.service.TimestampServices;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Sha256;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.util.PSQLException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Sealed documents among the members' documents: filed locked, byte for byte under their hash, given
 * later versions that keep the earlier ones, and refused every way of being deleted, re-assigned or
 * rewritten, in the services and in the database beneath them.
 */
class SealedDocumentServiceTest extends RepositoryTestBase {
    private static final Variant CONTENT = new Variant("content");

    @TempDir
    static Path storageRoot;

    private static StorageService storage;
    private static DocumentService documents;
    private static DocumentCatalogService catalog;
    private static SealedDocumentService sealedDocuments;
    private static PdfSealer sealer;
    private static KeyPair keys;
    private static X509Certificate certificate;
    private static Station station;

    @BeforeAll
    static void setup() throws Exception {
        var backend = new LocalStorageBackend(storageRoot);
        storage = new StorageService(new StorageBackendResolver(backend), backend);
        documents = newDocumentService(storage);
        catalog = new DocumentCatalogService(memberDocumentRepo, documents);
        sealedDocuments = new SealedDocumentService(memberDocumentRepo, new SealedVersionRepository(), documents);
        sealer = new PdfSealer(new TimestampServices(mock(Signing.class)), mock(StationKeyRevocations.class));
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        certificate = selfSigned(keys);
        station = stationRepo.create("Sealed Document Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void filingKeepsTheSealedBytesUnderTheirHashAndLocksTheDocument() throws IOException {
        int member = newMember();
        var sealed = seal(onePagePdf());

        var document = file(member, sealed);

        assertTrue(document.sealed());
        assertTrue(document.keepOnArchive(), "a sealed document is always kept");
        assertEquals("application/pdf", document.mimeType());
        assertEquals(sealed.pdf().length, document.sizeBytes());
        assertArrayEquals(sealed.pdf(), documents.read(document).orElseThrow());
        var versions = documents.sealedVersions(document);
        assertEquals(1, versions.size());
        var first = versions.getFirst();
        assertEquals(1, first.version());
        assertEquals(Sha256.hex(sealed.pdf()), first.sha256());
        assertEquals(SealLevel.BASELINE_B, first.sealLevel());
        assertNull(first.timestampedBy());
        assertTrue(first.current());
        assertArrayEquals(
                sealed.pdf(),
                storage.readAllBytes(scope(), StorageCategory.MEMBER_DOCUMENTS, "sealed/" + first.sha256(), CONTENT)
                        .orElseThrow(),
                "the file is kept under its hash");
        assertEquals(List.of(member), memberDocumentRepo.membersOf(document.id()));
        assertEquals(List.of("Siegel"), catalog.view(document).tags());
    }

    @Test
    void aSealedPdfKeepsItsTileAndItsWords() throws IOException {
        var document = file(newMember(), seal(onePagePdf()));

        assertTrue(document.hasThumbnail());
        assertTrue(documents.thumbnail(document, 256, DocumentDoor.STATION).isPresent());
        var filter = new DocumentFilter(List.of(), "Vereinbarung", true, false, false);
        assertTrue(
                memberDocumentRepo
                        .findByStation(station.id(), filter, documents.searchConfigOf(station.id()), 100, 0)
                        .stream()
                        .anyMatch(found -> found.id() == document.id()));
    }

    @Test
    void aLaterSealSupersedesAndKeepsTheVersionBefore() throws IOException {
        var first = seal(onePagePdf());
        var document = file(newMember(), first);
        var second = seal(first.pdf());

        var updated = sealedDocuments.supersede(document, 1, second);

        assertArrayEquals(second.pdf(), documents.read(updated).orElseThrow());
        assertEquals(second.pdf().length, updated.sizeBytes());
        var versions = documents.sealedVersions(updated);
        assertEquals(
                List.of(2, 1), versions.stream().map(SealedVersion::version).toList());
        assertTrue(versions.get(0).current());
        assertFalse(versions.get(1).current(), "the version before is marked superseded");
        assertNotNull(versions.get(1).supersededAt());
        assertArrayEquals(first.pdf(), documents.read(updated, versions.get(1)).orElseThrow());
        assertArrayEquals(second.pdf(), documents.read(updated, versions.get(0)).orElseThrow());
        assertTrue(catalog.view(updated).sealed());
        assertEquals(2, catalog.view(updated).sealedVersions().size());
    }

    @Test
    void aSealBuiltOnASupersededVersionIsRefusedAndTheSameFileChangesNothing() throws IOException {
        var first = seal(onePagePdf());
        var document = file(newMember(), first);
        var second = seal(first.pdf());
        sealedDocuments.supersede(document, 1, second);

        assertRefused(
                DocumentRefusal.DOCUMENT_SEALED_VERSION_OUTDATED,
                () -> sealedDocuments.supersede(document, 1, seal(first.pdf())));
        sealedDocuments.supersede(document, 1, second);

        assertEquals(2, documents.sealedVersions(document).size(), "the file filed already is a retry, not a version");
    }

    @Test
    void aDocumentThatWasNotSealedTakesNoSealedVersion() throws IOException {
        var plain = documents.store(
                station.id(),
                List.of(newMember()),
                "Formlos",
                "formlos.pdf",
                "application/pdf",
                onePagePdf(),
                false,
                false,
                Uploader.nobody(),
                List.of());

        assertRefused(
                DocumentRefusal.DOCUMENT_NOT_SEALED, () -> sealedDocuments.supersede(plain, 1, seal(onePagePdf())));
        assertTrue(documents.sealedVersions(plain).isEmpty());
    }

    @Test
    void aSealedDocumentIsNeitherDeletedNorPrunedNorOfferedForPruning() throws IOException {
        int member = newMember();
        var sealedDocument = file(member, seal(onePagePdf()));
        var plain = documents.store(
                station.id(),
                List.of(member),
                "Notiz",
                "notiz.txt",
                "text/plain",
                "Notiz".getBytes(),
                false,
                false,
                Uploader.nobody(),
                List.of());

        assertRefused(DocumentRefusal.DOCUMENT_SEALED_NOT_REMOVABLE, () -> documents.delete(sealedDocument));
        assertRefused(
                DocumentRefusal.DOCUMENT_SEALED_NOT_REMOVABLE,
                () -> documents.remove(station.id(), List.of(plain, sealedDocument), DocumentDoor.STATION));

        assertTrue(memberDocumentRepo.findById(plain.id()).isPresent(), "a choice holding a sealed one removes none");
        assertTrue(memberDocumentRepo.findById(sealedDocument.id()).isPresent());
        var removable = catalog.ids(station.id(), new DocumentFilter(List.of(member), null, true, false, false));
        assertEquals(List.of(plain.id()), removable);
    }

    @Test
    void theMembersOfASealedDocumentStayAsSealed() throws IOException {
        int member = newMember();
        var document = file(member, seal(onePagePdf()));

        assertRefused(
                DocumentRefusal.DOCUMENT_SEALED_MEMBERS_FIXED,
                () -> catalog.setMembers(document, List.of(newMember())));
        assertRefused(DocumentRefusal.DOCUMENT_SEALED_MEMBERS_FIXED, () -> catalog.setMembers(document, List.of()));

        assertEquals(List.of(member), memberDocumentRepo.membersOf(document.id()));
    }

    @Test
    void anArchivedMemberKeepsTheSealedDocument() throws IOException {
        int member = newMember();
        var document = file(member, seal(onePagePdf()));

        documents.memberLeaves(member, DocumentService.Leaving.ARCHIVED);

        assertEquals(List.of(member), memberDocumentRepo.membersOf(document.id()));
    }

    @Test
    void aDeletedMemberLeavesTheSealedDocumentUnderTheirName() throws IOException {
        int member = newMember();
        var document = file(member, seal(onePagePdf()));

        documents.memberLeaves(member, DocumentService.Leaving.DELETED);
        assertTrue(stationMemberRepo.delete(member));

        var kept = memberDocumentRepo.findById(document.id()).orElseThrow();
        assertTrue(kept.sealed());
        assertTrue(memberDocumentRepo.membersOf(document.id()).isEmpty());
        assertEquals(1, memberDocumentRepo.departedOf(document.id()).size());
        assertFalse(memberDocumentRepo.hasNoMembers(document.id()), "it stays a member's paperwork");
        assertTrue(documents.read(kept).isPresent());
    }

    @Test
    void theDatabaseRefusesDeletingASealedDocumentItsMembersOrItsVersions() throws IOException {
        int member = newMember();
        var document = file(member, seal(onePagePdf()));

        assertGuarded("DELETE FROM member_document WHERE id = " + document.id());
        assertGuarded("DELETE FROM member_document_member WHERE document_id = " + document.id());
        assertGuarded("DELETE FROM member_document_version WHERE document_id = " + document.id());
        assertGuarded("DELETE FROM station_member WHERE id = " + member);

        assertTrue(memberDocumentRepo.findById(document.id()).isPresent());
        assertEquals(List.of(member), memberDocumentRepo.membersOf(document.id()));
    }

    @Test
    void theDatabaseRefusesUnsealingUnkeepingOrRewritingASealedDocument() throws IOException {
        var first = seal(onePagePdf());
        var document = file(newMember(), first);
        sealedDocuments.supersede(document, 1, seal(first.pdf()));
        int id = document.id();

        assertGuarded("UPDATE member_document SET sealed = FALSE WHERE id = " + id);
        assertGuarded("UPDATE member_document SET keep_on_archive = FALSE WHERE id = " + id, "23514");
        assertGuarded("UPDATE member_document_version SET sha256 = repeat('0', 64) WHERE document_id = " + id);
        assertGuarded("UPDATE member_document_version SET superseded_at = NULL WHERE document_id = " + id);

        var kept = memberDocumentRepo.findById(id).orElseThrow();
        assertTrue(kept.sealed());
        assertTrue(kept.keepOnArchive());
        assertEquals(2, documents.sealedVersions(kept).size());
    }

    @Test
    void deletingTheStationTakesItsSealedDocuments() throws IOException {
        var leaving = stationRepo.create("Sealed Leaving Station");
        var account = accountRepo.create("sealed-leaving-" + System.nanoTime() + "@test.com", "Lea", "Ving");
        int member = stationMemberRepo.create(leaving.id(), account.id()).id();
        var first = seal(onePagePdf());
        var document = sealedDocuments.file(
                leaving.id(),
                new SealedFiling(List.of(member), "Bescheinigung", "b.pdf", false, Uploader.nobody(), List.of()),
                first);
        sealedDocuments.supersede(document, 1, seal(first.pdf()));

        assertTrue(stationRepo.delete(leaving.id()));

        assertTrue(memberDocumentRepo.findById(document.id()).isEmpty());
        assertEquals(
                0,
                count("SELECT count(*) AS count FROM member_document_version WHERE document_id = :id", document.id()));
    }

    @Test
    void theDataExportCarriesEverySealedVersion() throws IOException {
        var account = accountRepo.create("sealed-export-" + System.nanoTime() + "@test.com", "Ex", "Port");
        int member = stationMemberRepo.create(station.id(), account.id()).id();
        var first = seal(onePagePdf());
        var document = file(member, first);
        var second = seal(first.pdf());
        sealedDocuments.supersede(document, 1, second);
        var export = new GdprExportService(
                accountRepo,
                stationMemberRepo,
                memberLookupService,
                mock(KbFileStorageService.class),
                memberDocumentRepo,
                documents,
                mock(SignatureImageService.class));

        @SuppressWarnings("unchecked")
        var listed =
                ((List<Map<String, Object>>) export.exportMemberData(member).get("documents")).getFirst();
        var files = filesOf(export.exportAccountDataAsZip(account.id(), "de"));

        assertEquals(true, listed.get("sealed"));
        assertEquals(2, ((List<?>) listed.get("sealedVersions")).size());
        assertArrayEquals(second.pdf(), files.get("files/documents/" + document.id() + "-vereinbarung.pdf"));
        assertArrayEquals(first.pdf(), files.get("files/documents/" + document.id() + "-v1-vereinbarung.pdf"));
    }

    private Document file(int member, SealedDocument sealed) {
        return sealedDocuments.file(
                station.id(),
                new SealedFiling(
                        List.of(member),
                        " Vereinbarung ",
                        "vereinbarung.pdf",
                        false,
                        Uploader.nobody(),
                        List.of("Siegel")),
                sealed);
    }

    private SealedDocument seal(byte[] pdf) {
        return sealer.seal(pdf, keys.getPrivate(), List.of(certificate));
    }

    private static int newMember() {
        var account = accountRepo.create("sealed-" + System.nanoTime() + "@test.com", "Sie", "Gel");
        return stationMemberRepo.create(station.id(), account.id()).id();
    }

    private static StorageScope.Station scope() {
        return new StorageScope.Station(station.id(), stationRepo.requireUid(station.id()));
    }

    private static int count(String sql, int id) {
        return query(sql)
                .single(call().bind("id", id))
                .map(row -> row.getInt("count"))
                .first()
                .orElseThrow();
    }

    private static void assertRefused(Refusal refusal, Executable call) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, call).refusal());
    }

    /** Runs a statement straight against the database and expects one of the guards to refuse it. */
    private static void assertGuarded(String sql) {
        assertGuarded(sql, "23001");
    }

    private static void assertGuarded(String sql, String sqlState) {
        var refused = assertThrows(PSQLException.class, () -> {
            try (var connection = dataSource.getConnection();
                    var statement = connection.createStatement()) {
                statement.execute(sql);
            }
        });
        assertEquals(sqlState, refused.getSQLState(), refused.getMessage());
    }

    private static Map<String, byte[]> filesOf(byte[] zip) throws IOException {
        var files = new HashMap<String, byte[]>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                files.put(entry.getName(), in.readAllBytes());
            }
        }
        return files;
    }

    private static byte[] onePagePdf() throws IOException {
        try (var pdf = new PDDocument();
                var out = new ByteArrayOutputStream()) {
            pdf.addPage(new PDPage());
            pdf.save(out);
            return out.toByteArray();
        }
    }

    private static X509Certificate selfSigned(KeyPair keys) throws Exception {
        var subject = new X500Name("CN=Ember sealed document test");
        var now = Instant.now();
        var holder = new JcaX509v3CertificateBuilder(
                        subject,
                        BigInteger.valueOf(now.toEpochMilli()),
                        Date.from(now.minus(Duration.ofDays(1))),
                        Date.from(now.plus(Duration.ofDays(30))),
                        subject,
                        keys.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate()));
        return new JcaX509CertificateConverter().getCertificate(holder);
    }
}

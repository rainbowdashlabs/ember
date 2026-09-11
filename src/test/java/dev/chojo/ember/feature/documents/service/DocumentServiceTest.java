/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The document store as a whole: the bytes going in and coming back, the picture that is made of
 * them, what can be read out of them, and what becomes of them when a member leaves.
 */
@Tag("database")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentServiceTest extends RepositoryTestBase {

    @TempDir
    static Path storageRoot;

    private static DocumentService service;
    private static Station station;
    private static Account account;
    private static int memberId;

    @BeforeAll
    static void setup() {
        var backend = new LocalStorageBackend(storageRoot);
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        service = new DocumentService(memberDocumentRepo, storage, new ImageVariantService(storage), stationRepo);
        station = stationRepo.create("Document Service Station");
        account = accountRepo.create("doc-service@test.com", "Doc", "Service");
        memberId = stationMemberRepo.create(station.id(), account.id()).id();
    }

    @AfterAll
    static void cleanup() throws IOException {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
        if (storageRoot != null && Files.exists(storageRoot)) {
            try (var walk = Files.walk(storageRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // A leftover temporary file is not worth failing a test over.
                    }
                });
            }
        }
    }

    private static byte[] onePagePdf() throws IOException {
        try (var pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            var out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        }
    }

    @Test
    @Order(1)
    void aDocumentComesBackAsItWentIn() {
        byte[] content = "Diese Vereinbarung gilt.".getBytes(StandardCharsets.UTF_8);

        var document = service.store(
                station.id(),
                List.of(memberId),
                "Vereinbarung",
                "vereinbarung.txt",
                "text/plain",
                content,
                false,
                true,
                memberId,
                List.of("Vertrag"));

        assertEquals("Vereinbarung", document.title());
        assertTrue(document.keepOnArchive());
        assertArrayEquals(content, service.read(document).orElseThrow());
        assertEquals(
                List.of("Vertrag"),
                memberDocumentRepo.findTags(document.id()).stream()
                        .map(tag -> tag.name())
                        .toList());
    }

    /** A text file says something, and what it says is what somebody searches for later. */
    @Test
    @Order(2)
    void whatATextFileSaysIsSearchableAfterwards() {
        service.store(
                station.id(),
                List.of(memberId),
                "Protokoll",
                "protokoll.txt",
                "text/plain",
                "Der Loeschzug rueckte aus.".getBytes(StandardCharsets.UTF_8),
                false,
                false,
                memberId,
                List.of());

        var found = memberDocumentRepo.findByStation(
                station.id(), List.of(), "Loeschzug", true, false, service.searchConfigOf(station.id()), 50, 0);

        assertTrue(found.stream().anyMatch(document -> "Protokoll".equals(document.title())));
    }

    /** A page is a picture waiting to be taken, which is what makes a readable tile of a PDF. */
    @Test
    @Order(3)
    void aPdfGetsAPictureOfItsFirstPage() throws IOException {
        var document = service.store(
                station.id(),
                List.of(memberId),
                "Anweisung",
                "anweisung.pdf",
                "application/pdf",
                onePagePdf(),
                false,
                false,
                memberId,
                List.of());

        assertTrue(document.hasThumbnail(), "a picture was made of it");
        assertTrue(service.thumbnail(document, 128).isPresent(), "and it can be read back");
    }

    /** Nothing can be read out of arbitrary bytes, and the store carries them all the same. */
    @Test
    @Order(4)
    void afileNothingCanBeMadeOfIsStillKept() {
        var document = service.store(
                station.id(),
                List.of(memberId),
                "Messwerte",
                "messwerte.bin",
                "application/octet-stream",
                new byte[] {1, 2, 3, 4},
                false,
                false,
                memberId,
                List.of());

        assertFalse(document.hasThumbnail(), "no picture could be made of it");
        assertTrue(service.thumbnail(document, 128).isEmpty());
        assertEquals(4, service.read(document).orElseThrow().length);
    }

    @Test
    @Order(5)
    void aDeletedDocumentTakesItsBytesWithIt() {
        var document = service.store(
                station.id(),
                List.of(memberId),
                "Kurzlebig",
                "kurz.txt",
                "text/plain",
                "weg".getBytes(StandardCharsets.UTF_8),
                false,
                false,
                memberId,
                List.of());

        service.delete(document);

        assertTrue(memberDocumentRepo.findById(document.id()).isEmpty());
        assertTrue(service.read(document).isEmpty());
    }

    /**
     * Leaving takes the documents along, except what was marked to be kept: that is the whole
     * reason the mark exists.
     */
    @Test
    @Order(6)
    void leavingKeepsWhatWasMarkedKeptAndTakesTheRest() {
        var kept = service.store(
                station.id(),
                List.of(memberId),
                "Bindend",
                "bindend.txt",
                "text/plain",
                "bleibt".getBytes(StandardCharsets.UTF_8),
                false,
                true,
                memberId,
                List.of());
        var released = service.store(
                station.id(),
                List.of(memberId),
                "Beiläufig",
                "beilaeufig.txt",
                "text/plain",
                "geht".getBytes(StandardCharsets.UTF_8),
                false,
                false,
                memberId,
                List.of());

        service.releaseMember(memberId);

        assertTrue(memberDocumentRepo.findById(kept.id()).isPresent(), "what binds outlasts the membership");
        assertTrue(memberDocumentRepo.isBoundTo(kept.id(), memberId));
        assertTrue(memberDocumentRepo.findById(released.id()).isEmpty(), "the rest goes with them");
        assertTrue(service.read(released).isEmpty(), "and so do its bytes");
    }

    /**
     * The whole of the permission split, and the reason documents could not simply be given one
     * permission and left at that: the store permission on its own must never reach a document that
     * names a member, or it hands its holder every certificate in the station without ever granting
     * them the permission that reaches member paperwork.
     */
    @Test
    @Order(7)
    void aDocumentNamingAMemberIsReadOnTheMemberDocumentPermissionAndNotTheStoreOne() {
        var onAMember = service.store(
                station.id(),
                List.of(memberId),
                "Attest",
                "attest.txt",
                "text/plain",
                "Bescheinigung".getBytes(StandardCharsets.UTF_8),
                false,
                false,
                memberId,
                List.of());

        assertTrue(service.mayRead(onAMember.id(), true, false), "whoever may read member documents may read it");
        assertTrue(service.mayRead(onAMember.id(), true, true), "and still may with both");
        assertFalse(service.mayRead(onAMember.id(), false, true), "the store permission alone must not reach it");
        assertFalse(service.mayRead(onAMember.id(), false, false), "and neither does holding nothing");
    }

    /** The station's own paperwork, which is what the store permission is for. */
    @Test
    @Order(8)
    void aDocumentNamingNobodyIsReadOnTheStorePermission() {
        var unbound = service.store(
                station.id(),
                List.of(),
                "Pruefbescheinigung",
                "pruefung.txt",
                "text/plain",
                "Leiterpruefung".getBytes(StandardCharsets.UTF_8),
                false,
                false,
                memberId,
                List.of());

        assertTrue(service.mayRead(unbound.id(), false, true), "the store permission is enough on its own");
        assertTrue(service.mayRead(unbound.id(), true, true), "and so is holding both");
        assertFalse(
                service.mayRead(unbound.id(), true, false), "reading member documents says nothing about the store");
        assertFalse(service.mayRead(unbound.id(), false, false), "and neither does holding nothing");
    }
}

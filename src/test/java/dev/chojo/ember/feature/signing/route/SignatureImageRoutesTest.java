/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import dev.chojo.ember.feature.legal.service.GdprDeletionService;
import dev.chojo.ember.feature.legal.service.GdprExportService;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import dev.chojo.ember.feature.signing.repository.AccountSignatureRepository;
import dev.chojo.ember.feature.signing.service.SignatureImageService;
import dev.chojo.ember.feature.signing.service.SignatureImages;
import dev.chojo.ember.feature.signing.service.TestSignatures;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Sha256;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Request;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A person's own signature picture and consent over HTTP, through the real service, the database and a
 * local file store: a picture is cleaned when saved and served back to its owner, a missing or unreadable
 * upload is refused, the consent is given and taken back on its own, and the picture is part of the data
 * export and goes with the account.
 */
class SignatureImageRoutesTest extends RepositoryTestBase {
    private static final String SETTINGS = PREFIX + "/session/signature";
    private static final String IMAGE = SETTINGS + "/image";
    private static final String CONSENT = SETTINGS + "/consent";
    private static final String BOUNDARY = "ember-signature-boundary";

    @TempDir
    static Path storageRoot;

    private static StorageService storage;
    private static SignatureImageService images;

    private RouteHarness harness;
    private StationMember member;

    @BeforeAll
    static void setup() {
        var backend = new LocalStorageBackend(storageRoot);
        storage = new StorageService(new StorageBackendResolver(backend), backend);
        images = new SignatureImageService(new AccountSignatureRepository(), accountRepo, storage);
    }

    @BeforeEach
    void serve() {
        harness = RouteHarness.serving(new SignatureImageRoutes(images)).withStations(stationRepo);
        var station = stationRepo.create("Unterschriften " + UUID.randomUUID());
        var account = accountRepo.create(UUID.randomUUID() + "@signature.test", "Ute", "Unterschrift", true);
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @Test
    void aSavedPictureIsCleanedAndServedToItsOwner() {
        harness.run((server, client) -> {
            JsonNode before = json(client.get(SETTINGS, asMember()));
            assertFalse(before.path("hasImage").asBoolean());
            assertEquals(204, client.get(IMAGE, asMember()).code());

            JsonNode saved = json(upload(client, TestSignatures.photographed(), "UPLOADED"));
            assertTrue(saved.path("hasImage").asBoolean());
            assertEquals("UPLOADED", saved.path("imageSource").asString());
            assertFalse(saved.path("imageSavedAt").isNull());

            var served = fetchBytes(server.port(), IMAGE, asMember());
            assertEquals(200, served.statusCode());
            assertEquals(
                    "image/png", served.headers().firstValue("Content-Type").orElseThrow());
            assertArrayEquals(
                    SignatureImages.clean(TestSignatures.photographed()).png(), served.body());
        });
        var kept = images.settings(member.accountId());
        assertEquals(
                kept.imageSha256(), Sha256.hex(images.image(member.accountId()).orElseThrow()));
    }

    @Test
    void anUploadThatIsNoSignaturePictureIsRefused() {
        harness.run((server, client) -> {
            assertEquals(
                    DocumentRefusal.SIGNATURE_IMAGE_SOURCE_UNKNOWN,
                    refusalOf(upload(client, TestSignatures.drawn(), null)));
            assertEquals(
                    DocumentRefusal.SIGNATURE_IMAGE_SOURCE_UNKNOWN,
                    refusalOf(upload(client, TestSignatures.drawn(), "PAINTED")));
            assertEquals(
                    DocumentRefusal.SIGNATURE_IMAGE_NOT_A_PICTURE,
                    refusalOf(upload(client, "kein Bild".getBytes(StandardCharsets.UTF_8), "UPLOADED")));
            assertEquals(
                    DocumentRefusal.SIGNATURE_IMAGE_EMPTY, refusalOf(upload(client, TestSignatures.empty(), "DRAWN")));
            assertEquals(DocumentRefusal.SIGNATURE_IMAGE_MISSING, refusalOf(upload(client, null, "DRAWN")));
        });
        assertFalse(images.settings(member.accountId()).hasImage());
    }

    @Test
    void theConsentIsGivenAndTakenBackApartFromThePicture() {
        harness.run((server, client) -> {
            JsonNode given = json(client.put(CONSENT, body("{\"consented\": true}"), asMember()));
            assertFalse(given.path("autoSignConsentedAt").isNull());
            assertFalse(given.path("hasImage").asBoolean());

            assertEquals(200, upload(client, TestSignatures.drawn(), "TYPED").code());
            JsonNode deleted = json(client.delete(IMAGE, null, asMember()));
            assertFalse(deleted.path("hasImage").asBoolean());
            assertFalse(deleted.path("autoSignConsentedAt").isNull(), "deleting the picture keeps the consent");

            JsonNode withdrawn = json(client.put(CONSENT, body("{\"consented\": false}"), asMember()));
            assertTrue(withdrawn.path("autoSignConsentedAt").isNull());
        });
        assertTrue(storage.readAllBytes(scopeOf(member), StorageCategory.IMAGE_SIGNATURE, "signature.png")
                .isEmpty());
    }

    @Test
    void thePictureIsExportedAndGoesWithTheAccount() throws IOException {
        images.save(member.accountId(), TestSignatures.drawn(), SignatureImageSource.DRAWN);
        var export = new GdprExportService(
                accountRepo,
                stationMemberRepo,
                memberLookupService,
                mock(KbFileStorageService.class),
                memberDocumentRepo,
                mock(DocumentService.class),
                images);

        assertTrue(entriesOf(export.exportAccountDataAsZip(member.accountId(), "de"))
                .contains("files/signature.png"));

        var deletion = new GdprDeletionService(
                accountRepo,
                stationMemberRepo,
                memberLookupService,
                new AvatarService(new ImageVariants(storage)),
                newDocumentService(storage),
                images);
        var scope = scopeOf(member);
        deletion.deleteAccount(member.accountId());

        assertTrue(storage.readAllBytes(scope, StorageCategory.IMAGE_SIGNATURE, "signature.png")
                .isEmpty());
        assertTrue(new AccountSignatureRepository().find(member.accountId()).isEmpty());
    }

    /** Sends a picture as the account settings do, a form with the file and how it was made. */
    private Response upload(HttpClient client, byte[] picture, String source) {
        byte[] form = form(picture, source);
        return client.request(IMAGE, request -> {
            asMember().accept(request);
            request.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            request.put(HttpRequest.BodyPublishers.ofByteArray(form));
        });
    }

    private static byte[] form(byte[] picture, String source) {
        var out = new ByteArrayOutputStream();
        if (source != null) {
            write(
                    out,
                    "--%s\r\nContent-Disposition: form-data; name=\"source\"\r\n\r\n%s\r\n"
                            .formatted(BOUNDARY, source));
        }
        if (picture != null) {
            write(
                    out,
                    ("--%s\r\nContent-Disposition: form-data; name=\"image\"; filename=\"signature\"\r\n"
                                    + "Content-Type: image/png\r\n\r\n")
                            .formatted(BOUNDARY));
            out.writeBytes(picture);
            write(out, "\r\n");
        }
        write(out, "--%s--\r\n".formatted(BOUNDARY));
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private Consumer<Request.Builder> asMember() {
        return harness.as(signedIn(member, StationPermission.LOGIN));
    }

    private static StorageScope.Account scopeOf(StationMember member) {
        return new StorageScope.Account(accountRepo.resolveUid(member.accountId()));
    }

    private static HttpResponse<byte[]> fetchBytes(int port, String path, Consumer<Request.Builder> session) {
        var decorated = new Request.Builder();
        session.accept(decorated);
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        decorated.url("http://localhost").build().getHeaders().forEach(request::header);
        try (var http = java.net.http.HttpClient.newHttpClient()) {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> entriesOf(byte[] zip) throws IOException {
        var names = new HashSet<String>();
        try (var in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.transfer;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.members.route.TransferRoutes;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.station.service.StationImportService;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.StoredCredentials;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end acceptance pass for the cross-instance transfer flow described in storage-backends
 * §18. Boots a Javalin server that serves the source-side endpoints over real HTTP, then runs the
 * destination's {@link StationImportService} against it and asserts on the resulting state:
 * file bytes carried over for LOCAL stations, credentials re-encrypted for station-owned remote
 * backends, avatars carried for newly-created accounts, and the read-only flag handled correctly.
 */
class StationTransferAcceptanceTest extends RepositoryTestBase {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNgAAIAAAUAAeImBZsAAAAASUVORK5CYII=");

    private static Path sharedDataRoot;
    private static LocalStorageBackend sharedBackend;
    private static StorageService storageService;
    private static AvatarService avatarService;
    private static StorageBackendResolver resolver;
    private static StationStorageConfigRepository configRepo;
    private static CredentialCipher credentialCipher;

    private static StationExportService exportService;
    private static StationImportService importService;

    private static Javalin server;
    private static String baseUrl;

    @BeforeAll
    static void setupTransferHarness() throws Exception {
        sharedDataRoot = Files.createTempDirectory("ember-transfer-acceptance");
        sharedBackend = new LocalStorageBackend(sharedDataRoot);
        resolver = new StorageBackendResolver(sharedBackend);
        storageService = new StorageService(resolver, sharedBackend);
        avatarService = new AvatarService(new ImageVariantService(storageService));

        configRepo = new StationStorageConfigRepository();
        credentialCipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        var backendImporter = new TransferBackendImporter(configRepo, credentialCipher, resolver);
        var descriptorService = new TransferBackendDescriptorService(configRepo, credentialCipher);

        exportService = new StationExportService(stationRepo, new dev.chojo.ember.conf.file.elements.Api());
        importService = new StationImportService(
                stationRepo, accountRepo, exportService, new Api(), backendImporter, storageService, avatarService);

        var transferRoutes = new TransferRoutes(exportService, importService, stationRepo);
        var assetRoutes = new StationTransferAssetRoutes(
                exportService, descriptorService, stationRepo, storageService, avatarService);

        server = Javalin.create(config -> {
            for (Routes r : new Routes[] {assetRoutes, transferRoutes}) {
                r.register(config.routes, "/api/v1");
            }
        });
        server.start(0);
        baseUrl = "http://localhost:" + server.port();
    }

    @AfterAll
    static void shutdownHarness() throws IOException {
        if (server != null) server.stop();
        if (sharedDataRoot != null) deleteRecursive(sharedDataRoot);
    }

    /**
     * End-to-end LOCAL transfer: a source station with a page file is exported via real HTTP,
     * pulled by the destination import, and reappears under the destination station's scope with
     * identical bytes. Confirms the descriptor, listing, streaming, and storage-facade write
     * paths all line up and that no override row is left behind on the destination.
     */
    @Test
    void localTransferCopiesFiles() throws Exception {
        Station source = stationRepo.create("Source LOCAL");
        var sourceScope = new StorageScope.Station(source.id(), source.uid());
        byte[] fileBytes = "hello world".getBytes(StandardCharsets.UTF_8);
        storageService.store(sourceScope, StorageCategory.PAGE_FILES, "doc.txt", fileBytes, "text/plain");

        String token = exportService.createTransferToken(source.id());
        assertTrue(
                stationRepo.isReadOnlyForTransfer(source.id()),
                "createTransferToken must flip the source read-only flag");

        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());

        int destinationId = importResult.stationId();
        Station destination =
                stationRepo.findById(destinationId).orElseThrow(() -> new AssertionError("destination missing"));
        var destinationScope = new StorageScope.Station(destinationId, destination.uid());
        var carried = storageService.readAllBytes(destinationScope, StorageCategory.PAGE_FILES, "doc.txt");
        assertTrue(carried.isPresent(), "destination should carry the file");
        assertArrayEquals(fileBytes, carried.get(), "bytes round-trip unchanged");

        assertFalse(
                configRepo.findOne(destinationId).isPresent(),
                "LOCAL source must not install an override row on the destination");
    }

    /**
     * The source-side {@code /avatars/{accountUid}} endpoint streams the stored original bytes
     * for accounts that have an avatar and reports {@code 404} when none is on file. Exercised
     * here at the HTTP layer because the destination's account-creation branch only fires when
     * the destination's account database is independent of the source's — a property that does
     * not hold inside the shared test database. The HTTP test is enough to prove the surface
     * the destination consumes is correct.
     */
    @Test
    void avatarEndpointStreamsAvatarBytes() throws Exception {
        Account hasAvatar = accountRepo.create("avatar-hit@xfer.test", "A", "User", true);
        avatarService.store(hasAvatar.uid(), ONE_PIXEL_PNG, "image/png");
        Account hasNone = accountRepo.create("avatar-miss@xfer.test", "B", "User", true);
        Station source = stationRepo.create("Avatar Source");
        String token = exportService.createTransferToken(source.id());

        var client = HttpClient.newHttpClient();

        var hit = client.send(
                HttpRequest.newBuilder(URI.create(
                                baseUrl + "/api/v1/public/transfer/" + token + "/avatars/" + hasAvatar.uid()))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, hit.statusCode());
        assertTrue(
                hit.headers().firstValue("Content-Type").orElse("").startsWith("image/"),
                "avatar response must carry an image MIME type");
        assertTrue(hit.body().length > 0, "avatar response must carry bytes");

        var miss = client.send(
                HttpRequest.newBuilder(
                                URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/avatars/" + hasNone.uid()))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(404, miss.statusCode());
    }

    /**
     * Station-owned S3 backend on the source: after import the destination receives the same
     * descriptor, re-encrypts the carried credentials under its own key, and writes the override
     * row. Decrypting the destination row yields the same access key / secret key the source had.
     */
    @Test
    void remoteBackendTransferReencryptsCredentials() throws Exception {
        Station source = stationRepo.create("Source REMOTE");
        var creds = new StoredCredentials.S3("AKIA-source-access", "ssshh-source-secret");
        var sourceOverride = new StationStorageBackendConfig.S3Variant(
                "https://s3.example.invalid",
                "us-east-1",
                "source-bucket",
                true,
                Optional.of("AES256"),
                "tenants/foo",
                credentialCipher.encrypt(creds.toJson()));
        configRepo.upsert(source.id(), sourceOverride);

        String token = exportService.createTransferToken(source.id());
        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());

        var destinationRow = configRepo
                .findOne(importResult.stationId())
                .orElseThrow(() -> new AssertionError("destination override row missing"));
        assertTrue(destinationRow.config() instanceof StationStorageBackendConfig.S3Variant);
        var dst = (StationStorageBackendConfig.S3Variant) destinationRow.config();
        assertEquals("https://s3.example.invalid", dst.endpoint());
        assertEquals("us-east-1", dst.region());
        assertEquals("source-bucket", dst.bucket());
        assertEquals(true, dst.pathStyle());
        assertEquals(Optional.of("AES256"), dst.sseAlgorithm());
        assertEquals("tenants/foo", dst.basePath());

        var redecrypted = StoredCredentials.S3.parse(credentialCipher.decryptToString(dst.credentials()));
        assertEquals("AKIA-source-access", redecrypted.accessKey());
        assertEquals("ssshh-source-secret", redecrypted.secretKey());
    }

    /**
     * The backend descriptor endpoint is one-shot per token: a second call answers 429 so the
     * destination cannot reuse the token to harvest plaintext credentials repeatedly.
     */
    @Test
    void backendDescriptorIsOneShotPerToken() throws Exception {
        Station source = stationRepo.create("Source ONE-SHOT");
        String token = exportService.createTransferToken(source.id());
        var client = HttpClient.newHttpClient();
        var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/backend");

        var first = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.statusCode());

        var second = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(429, second.statusCode());
    }

    /**
     * The source operator can back out of a transfer with {@code POST /station/transfer/abort}.
     * That clears the read-only flag and invalidates outstanding tokens so a destination cannot
     * still pull tables after the abort.
     */
    @Test
    void abortTransferClearsReadOnlyAndBurnsToken() {
        Station source = stationRepo.create("Source ABORT");
        String token = exportService.createTransferToken(source.id());
        assertTrue(stationRepo.isReadOnlyForTransfer(source.id()));

        exportService.abortTransfer(source.id());

        assertFalse(stationRepo.isReadOnlyForTransfer(source.id()), "abortTransfer must clear the read-only flag");
        assertFalse(
                exportService.validateToken(token).isPresent(), "tokens minted before abort must no longer validate");
    }

    private static void waitForImport(int stationId) throws InterruptedException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            StationImportService.ImportProgress progress = importService.getProgress(stationId);
            assertNotNull(progress, "import progress disappeared");
            if (progress.status() == StationImportService.ImportProgress.Status.COMPLETED) return;
            if (progress.status() == StationImportService.ImportProgress.Status.FAILED) {
                fail("import failed: " + progress.error());
            }
            Thread.sleep(50);
        }
        fail("import for station " + stationId + " did not finish within 30s");
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}

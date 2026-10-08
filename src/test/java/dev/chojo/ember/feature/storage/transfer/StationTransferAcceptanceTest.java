/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.transfer;

import dev.chojo.ember.api.ApiJsonMapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.service.BoardAttachmentService;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.DocumentTag;
import dev.chojo.ember.feature.documents.entity.SealedVersion;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.equipment.repository.EquipmentAvailabilityRepository;
import dev.chojo.ember.feature.equipment.repository.EquipmentNeedRepository;
import dev.chojo.ember.feature.equipment.service.EquipmentAvailabilityService;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationPartnerTransferFixupService;
import dev.chojo.ember.feature.federation.service.LendingUidClashes;
import dev.chojo.ember.feature.federation.service.MovedStationSwitchover;
import dev.chojo.ember.feature.federation.service.PartnersLeftBehind;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.repository.DocumentFontRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.repository.TemplateStationUseRepository;
import dev.chojo.ember.feature.generator.service.DocumentIssuerService;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.generator.service.LetterChecks;
import dev.chojo.ember.feature.generator.service.PdfTemplateService;
import dev.chojo.ember.feature.generator.service.TemplateChecks;
import dev.chojo.ember.feature.generator.service.TemplateRequestBuilder;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.font.TestFonts;
import dev.chojo.ember.feature.generator.service.pdf.TestPdfs;
import dev.chojo.ember.feature.inventory.entity.BorrowedItem;
import dev.chojo.ember.feature.inventory.entity.BorrowedPiece;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.service.KbFilePictureService;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import dev.chojo.ember.feature.knowledgebase.service.KbIconService;
import dev.chojo.ember.feature.knowledgebase.service.TextCompressionPolicy;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.media.service.MediaStorageService;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FilterTableType;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.ProfileAuthor;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.SavedFilter;
import dev.chojo.ember.feature.members.route.TransferRoutes;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.quiz.entity.AiVendor;
import dev.chojo.ember.feature.quiz.entity.CatalogMetadata;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.repository.AccountAiCredentialRepository;
import dev.chojo.ember.feature.quiz.repository.AiProviderRepository;
import dev.chojo.ember.feature.quiz.service.AiCredentialService;
import dev.chojo.ember.feature.quiz.service.QuizQuestionImageService;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.station.service.StationImportService;
import dev.chojo.ember.feature.station.service.StationTransferService;
import dev.chojo.ember.feature.station.transfer.AccountCredentialTableImporter;
import dev.chojo.ember.feature.station.transfer.AccountTableImporter;
import dev.chojo.ember.feature.station.transfer.DisabledModuleTableImporter;
import dev.chojo.ember.feature.station.transfer.DocumentSearchTableImporter;
import dev.chojo.ember.feature.station.transfer.ImportProgress;
import dev.chojo.ember.feature.station.transfer.StationTableImporter;
import dev.chojo.ember.feature.station.transfer.TransferFileImporter;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.StoredCredentials;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StationTransferFileService;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.feature.storage.service.TransferBackendDescriptorService;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.TestFederationServices;
import dev.chojo.ember.util.TestRemoteUrlValidator;
import dev.chojo.ember.util.TestStationKeys;
import dev.chojo.ember.util.WebpEncoder;
import io.javalin.Javalin;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private static StorageService storageService;
    private static AvatarService avatarService;
    private static MediaStorageService mediaStorageService;
    private static ImageVariants images;
    private static DocumentService documents;
    private static StationStorageConfigRepository configRepo;
    private static CredentialCipher credentialCipher;
    private static KbFileStorageService wikiFiles;
    private static KbFilePictureService wikiPictures;
    private static KbIconService folderIcons;
    private static DocumentFontRepository fontRepo;
    private static FontLibrary fontLibrary;
    private static DocumentFontService fonts;
    private static BoardAttachmentService boardAttachments;
    private static QuizQuestionImageService quizPictures;
    private static DocumentTemplateService templates;
    private static PdfTemplateService pdfTemplates;

    private static StationExportService exportService;
    private static StationImportService importService;

    private static Javalin server;
    private static String baseUrl;

    @BeforeAll
    static void setupTransferHarness() throws Exception {
        sharedDataRoot = Files.createTempDirectory("ember-transfer-acceptance");
        LocalStorageBackend sharedBackend = new LocalStorageBackend(sharedDataRoot);
        StorageBackendResolver resolver = new StorageBackendResolver(sharedBackend);
        storageService = new StorageService(resolver, sharedBackend);
        images = new ImageVariants(storageService);
        avatarService = new AvatarService(images);
        mediaStorageService = new MediaStorageService(storageService, stationRepo, sharedBackend);
        setupFileOwners(sharedBackend);

        configRepo = new StationStorageConfigRepository();
        credentialCipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        var backendImporter = new TransferBackendImporter(configRepo, credentialCipher, resolver);
        var descriptorService = new TransferBackendDescriptorService(configRepo, credentialCipher);

        exportService = new SeparateDatabaseExport();
        var fileImporter = new TransferFileImporter(storageService, avatarService, images, mediaStorageService);
        documents = newDocumentService(storageService);
        var stationImporter = new StationTableImporter(stationRepo);
        importService = new StationImportService(
                stationRepo,
                exportService,
                new Api(),
                backendImporter,
                fileImporter,
                new FederationPartnerTransferFixupService(
                        new FederationRepository(), org.mockito.Mockito.mock(FederationHttpClient.class)),
                TestStationKeys.transfer(),
                TestStationKeys.partnersLeftBehind(),
                new LendingUidClashes(new LendingRepository()),
                TestStationKeys.aiKeyTransfer(),
                TestRemoteUrlValidator.permissive(),
                TestRemoteUrlValidator.permissiveOutbound(),
                stationImporter,
                Set.of(
                        stationImporter,
                        new AccountTableImporter(accountRepo),
                        new AccountCredentialTableImporter(accountRepo, passkeyModeService),
                        new DisabledModuleTableImporter(stationRepo),
                        new DocumentSearchTableImporter(documents)),
                accountRepo,
                org.mockito.Mockito.mock(dev.chojo.ember.feature.account.service.AuthService.class),
                new TaskScheduler());

        var transferRoutes = new TransferRoutes(
                exportService,
                importService,
                new StationTransferService(
                        stationRepo,
                        exportService,
                        new MovedStationSwitchover(
                                new FederationRepository(),
                                new FederationPartnerTransferFixupService(new FederationRepository(), null),
                                TestStationKeys.store(),
                                new LendingRepository(),
                                inventoryRepo)));
        var assetRoutes = new StationTransferAssetRoutes(
                exportService,
                descriptorService,
                new StationTransferFileService(stationRepo, storageService),
                avatarService);

        server = Javalin.create(config -> {
            config.jsonMapper(ApiJsonMapper.forApi(stationRepo, clusterRepo));
            for (Routes r : new Routes[] {assetRoutes, transferRoutes}) {
                r.register(config.routes, "/api/v1");
            }
        });
        server.start(0);
        baseUrl = "http://localhost:" + server.port();
    }

    /**
     * The services that keep files named by the id of a row, over the harness's storage, so a test can
     * file through them at the source and read back through them at the destination.
     */
    private static void setupFileOwners(LocalStorageBackend backend) {
        wikiFiles = new KbFileStorageService(
                storageService, stationRepo, backend, new TextCompressionPolicy(new Storage()));
        wikiPictures = new KbFilePictureService(images, wikiFiles, stationRepo, knowledgeBaseRepo);
        folderIcons = new KbIconService(images, stationRepo);
        boardAttachments = new BoardAttachmentService(storageService, stationRepo, backend);
        quizPictures = new QuizQuestionImageService(images, stationRepo);

        fontRepo = new DocumentFontRepository();
        fontLibrary = newFontLibrary(storageService);
        fonts = new DocumentFontService(
                fontRepo,
                fontLibrary,
                storageService,
                new StorageQuotaService(storageUsageRepo, new Storage(), new DomainEventBus(Set.of())));
        setupTemplates();
    }

    /** The document templates of a station, with the PDFs PDF templates fill, over the harness's storage. */
    private static void setupTemplates() {
        var templateRepository = new DocumentTemplateRepository();
        var pdfRepository = new PdfTemplateRepository();
        var uses = new TemplateStationUseRepository();
        var catalogue = newPlaceholderCatalogue();
        var issuers = new DocumentIssuerService(stationMemberRepo, uses);
        var checks = new TemplateChecks(
                templateRepository,
                pdfRepository,
                new LetterChecks(contentBlocks(), org.mockito.Mockito.mock(MediaLibraryService.class)),
                stationRepo,
                catalogue,
                fontLibrary,
                newOwnerStores(),
                issuers);
        templates = new DocumentTemplateService(
                templateRepository, pdfRepository, uses, checks, restrictionService, catalogue, newOwnerStores());
        pdfTemplates = new PdfTemplateService(
                templates, templateRepository, pdfRepository, newDocumentIntake(), storageService, newOwnerStores());
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
        byte[] fileBytes = "hello world".getBytes(StandardCharsets.UTF_8);
        String contentHash = MediaStorageService.hash(fileBytes);
        mediaStorageService.store(source.id(), contentHash, fileBytes, "text/plain");

        String token = rawToken(exportService.createTransferToken(source.id()));

        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());

        int destinationId = importResult.stationId();
        var carried = libraryOriginal(destinationId, contentHash);
        assertTrue(carried.isPresent(), "destination should carry the file");
        assertArrayEquals(fileBytes, carried.get().data(), "bytes round-trip unchanged");

        assertFalse(
                configRepo.findOne(destinationId).isPresent(),
                "LOCAL source must not install an override row on the destination");
    }

    /**
     * The source-side {@code /avatars/{accountUid}} endpoint streams the stored original bytes
     * for accounts that have an avatar and reports {@code 404} when none is on file. Exercised
     * here at the HTTP layer because the destination's account-creation branch only fires when
     * the destination's account database is independent of the source's - a property that does
     * not hold inside the shared test database. The HTTP test is enough to prove the surface
     * the destination consumes is correct.
     */
    @Test
    void avatarEndpointStreamsAvatarBytes() throws Exception {
        Account hasAvatar = accountRepo.create("avatar-hit@xfer.test", "A", "User", true);
        avatarService.store(hasAvatar.uid(), ONE_PIXEL_PNG, "image/png");
        Account hasNone = accountRepo.create("avatar-miss@xfer.test", "B", "User", true);
        Station source = stationRepo.create("Avatar Source");
        String token = rawToken(exportService.createTransferToken(source.id()));

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

        String token = rawToken(exportService.createTransferToken(source.id()));
        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());

        var destinationRow = configRepo
                .findOne(importResult.stationId())
                .orElseThrow(() -> new AssertionError("destination override row missing"));
        assertInstanceOf(StationStorageBackendConfig.S3Variant.class, destinationRow.config());
        var dst = (StationStorageBackendConfig.S3Variant) destinationRow.config();
        assertEquals("https://s3.example.invalid", dst.endpoint());
        assertEquals("us-east-1", dst.region());
        assertEquals("source-bucket", dst.bucket());
        assertTrue(dst.pathStyle());
        assertEquals(Optional.of("AES256"), dst.sseAlgorithm());
        assertEquals("tenants/foo", dst.basePath());

        var redecrypted = StoredCredentials.S3.parse(credentialCipher.decryptToString(dst.credentials()));
        assertEquals("AKIA-source-access", redecrypted.accessKey());
        assertEquals("ssshh-source-secret", redecrypted.secretKey());
    }

    /**
     * The station's AI keys come along: the destination holds one working key per provider, with
     * the model that went with it, and the copied table row does not add a second one.
     */
    @Test
    void aiKeysTravelWithTheStation() throws Exception {
        var credentials = new AiCredentialService(
                new AccountAiCredentialRepository(), new AiProviderRepository(), TestStationKeys.cipher());
        Station source = stationRepo.create("Source AI");
        credentials.saveStationKey(source.id(), AiVendor.OPENAI, "sk-travelling", "gpt-4o");

        String token = rawToken(exportService.createTransferToken(source.id()));
        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());

        assertEquals(
                List.of(new AiCredentialService.StationKey(AiVendor.OPENAI, "gpt-4o", "sk-travelling")),
                credentials.stationKeys(importResult.stationId()));
    }

    /**
     * The backend descriptor endpoint is one-shot per token: a second call answers 410 so the
     * destination cannot reuse the token to harvest plaintext credentials repeatedly.
     */
    @Test
    void backendDescriptorIsOneShotPerToken() throws Exception {
        Station source = stationRepo.create("Source ONE-SHOT");
        String token = rawToken(exportService.createTransferToken(source.id()));
        var client = HttpClient.newHttpClient();
        var uri = URI.create(baseUrl + "/api/v1/public/transfer/" + token + "/backend");

        var first = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.statusCode());

        var second = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(410, second.statusCode());
    }

    /**
     * The source operator can back out of a transfer with {@code POST /station/transfer/abort}.
     * That clears the read-only flag and invalidates outstanding tokens so a destination cannot
     * still pull tables after the abort.
     */
    @Test
    void abortTransferClearsReadOnlyAndBurnsToken() {
        Station source = stationRepo.create("Source ABORT");
        String token = rawToken(exportService.createTransferToken(source.id()));
        exportService.markTransferStarted(source.id());
        assertTrue(stationRepo.isReadOnlyForTransfer(source.id()));

        exportService.abortTransfer(source.id());

        assertFalse(stationRepo.isReadOnlyForTransfer(source.id()), "abortTransfer must clear the read-only flag");
        assertFalse(
                exportService.validateToken(token).isPresent(), "tokens minted before abort must no longer validate");
    }

    /**
     * Full image round-trip: the source stores a page-files image plus every WebP variant; the
     * sender's listFiles filter must hide the variants so the wire payload is the original only,
     * and the receiver must rebuild the variant set locally from the streamed bytes.
     */
    @Test
    void localTransferShipsOriginalsOnlyAndRegeneratesVariants() throws Exception {
        Assumptions.assumeTrue(WebpEncoder.isAvailable(), "cwebp not available - skipping image-transfer regen check");
        Station source = stationRepo.create("Source IMAGE");
        byte[] png = pngBytes(800, 600);
        String contentHash = MediaStorageService.hash(png);
        mediaStorageService.store(source.id(), contentHash, png, "image/png");
        var at = mediaStorageService.locate(source.id(), contentHash);
        images.addSizes(at.scope(), at.category(), at.key(), png, "image/png");

        var sourceScope = new StorageScope.Station(source.id(), source.uid());
        List<String> sourceKeys = storageService.listKeys(sourceScope, StorageCategory.MEDIA_FILES, "");
        assertTrue(
                sourceKeys.stream().anyMatch(k -> k.endsWith("/w128.webp")),
                "source must have actually generated WebP variants (test precondition)");

        List<String> filtered = StationTransferFileService.originalsOnly(StorageCategory.MEDIA_FILES, sourceKeys);
        assertEquals(List.of(contentHash + "/orig.png"), filtered, "wire payload must be the original only");

        String token = rawToken(exportService.createTransferToken(source.id()));
        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());

        int destinationId = importResult.stationId();
        var carried = libraryOriginal(destinationId, contentHash);
        assertTrue(carried.isPresent(), "destination should carry the original");
        assertEquals("image/png", carried.get().contentType());

        Station destination =
                stationRepo.findById(destinationId).orElseThrow(() -> new AssertionError("destination missing"));
        var destinationScope = new StorageScope.Station(destinationId, destination.uid());
        List<String> destinationKeys = storageService.listKeys(destinationScope, StorageCategory.MEDIA_FILES, "");
        assertTrue(
                destinationKeys.stream().anyMatch(k -> k.endsWith("/w128.webp")),
                "destination must have regenerated WebP variants from the transferred original");
        assertTrue(
                destinationKeys.stream().noneMatch(k -> k.endsWith("/w128.png")),
                "destination must not re-emit dropped original-format resizes");
    }

    /**
     * Member documents get new ids on the destination, and their files follow them there: each
     * document serves its own bytes and picture, with its members, tags and sealed versions. Sealed
     * files are named by their SHA-256, so two versions of the same bytes share one file.
     */
    @Test
    void memberDocumentsKeepTheirFilesUnderTheirNewIds() throws Exception {
        Station source = stationRepo.create("Source DOCUMENTS");
        Account account = accountRepo.create("documents@xfer.test", "Doc", "Owner", true);
        int memberId = stationMemberRepo.create(source.id(), account.id()).id();
        var versions = new SealedVersionRepository();
        byte[] text = "Erste Vereinbarung".getBytes(StandardCharsets.UTF_8);
        var agreement = storeDocument(documents, source, memberId, "Vereinbarung", "text/plain", text, "Vertrag");
        var instruction =
                storeDocument(documents, source, memberId, "Anweisung", "application/pdf", onePagePdf(), "Dienst");
        assertTrue(instruction.hasThumbnail(), "a picture was made of the PDF (test precondition)");
        byte[] firstSeal = "first sealed file".getBytes(StandardCharsets.UTF_8);
        byte[] secondSeal = "second sealed file".getBytes(StandardCharsets.UTF_8);
        int contract = sealedDocument(source, memberId, "Vertrag", versions, firstSeal, secondSeal);
        int copy = sealedDocument(source, memberId, "Abschrift", versions, secondSeal);

        String token = rawToken(exportService.createTransferToken(source.id()));
        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());

        int destinationMember = stationMemberRepo
                .findByStationAndAccount(importResult.stationId(), account.id())
                .orElseThrow()
                .id();
        var arrived = memberDocumentRepo.findByMember(importResult.stationId(), destinationMember, true).stream()
                .collect(Collectors.toMap(Document::title, document -> document));
        assertEquals(Set.of("Vereinbarung", "Anweisung", "Vertrag", "Abschrift"), arrived.keySet());
        for (var document : arrived.values()) {
            assertFalse(
                    Set.of(agreement.id(), instruction.id(), contract, copy).contains(document.id()),
                    "the documents got new ids on the destination");
            assertEquals(List.of(destinationMember), memberDocumentRepo.membersOf(document.id()));
        }

        assertArrayEquals(text, documents.read(arrived.get("Vereinbarung")).orElseThrow());
        assertEquals(List.of("Vertrag"), tagNames(arrived.get("Vereinbarung")));
        assertTrue(
                memberDocumentRepo.findWithoutSourceText().stream()
                        .noneMatch(document ->
                                document.id() == arrived.get("Vereinbarung").id()),
                "the document is indexed from the text the source read out of it");
        assertArrayEquals(
                documents.read(instruction).orElseThrow(),
                documents.read(arrived.get("Anweisung")).orElseThrow());
        assertEquals(List.of("Dienst"), tagNames(arrived.get("Anweisung")));
        assertArrayEquals(
                documents
                        .thumbnail(instruction, 0, DocumentDoor.STATION)
                        .orElseThrow()
                        .data(),
                documents
                        .thumbnail(arrived.get("Anweisung"), 0, DocumentDoor.STATION)
                        .orElseThrow()
                        .data());

        var sealedContract = arrived.get("Vertrag");
        assertTrue(sealedContract.sealed());
        assertArrayEquals(secondSeal, documents.read(sealedContract).orElseThrow());
        var contractVersions = documents.sealedVersions(sealedContract);
        assertEquals(
                List.of(Sha256.hex(secondSeal), Sha256.hex(firstSeal)),
                contractVersions.stream().map(SealedVersion::sha256).toList());
        assertArrayEquals(
                firstSeal,
                documents.read(sealedContract, contractVersions.getLast()).orElseThrow());
        assertArrayEquals(secondSeal, documents.read(arrived.get("Abschrift")).orElseThrow());
    }

    /**
     * Wiki folders and files, fonts, board tickets and quiz questions get new ids on the destination, and the files named by those ids follow them there: each is read back through the
     * service that keeps it. A wiki file's picture arrives with the file rather than being drawn again,
     * and a folder's icon keeps its marker, now naming the folder's new id.
     */
    @Test
    void filesNamedByRowsKeepTheirFilesUnderTheirNewIds() throws Exception {
        Station source = stationRepo.create("Source ROW FILES");
        Account account = accountRepo.create("row-files@xfer.test", "Rhea", "Rows", true);
        var member = stationMemberRepo.create(source.id(), account.id());
        var owner = new Owner.Station(source.id());
        byte[] png = pngBytes(64, 48);
        byte[] report = "Einsatzbericht".getBytes(StandardCharsets.UTF_8);

        var folder = knowledgeBaseRepo.createFolder(source.id(), null, "Handbuch", "", member.id());
        folderIcons.store(source.id(), folder.id(), png, "image/png", 5 * 1024 * 1024);
        knowledgeBaseRepo.updateFolder(folder.id(), folder.name(), "", folderIcons.key(folder.id()), 0);
        var wikiFile = knowledgeBaseRepo.createFile(
                source.id(), folder.id(), "Lageplan", "", KbFileType.IMAGE, "image/png", png.length, null, member.id());
        wikiFiles.store(source.id(), wikiFile.id(), png, "image/png");
        wikiPictures.make(source.id(), wikiFile.id(), "image/png", png);

        fonts.upload(
                owner,
                TestUploads.of("schrift.ttf", "font/ttf", TestFonts.lisu()),
                "Wachschrift",
                FontStyle.REGULAR,
                true,
                account.id());

        var board = boardRepo.create(source.id(), "Einsatzboard", "", "EIN");
        var lane = boardRepo.createLane(board.id(), "Offen", null, 0);
        var creator = new MemberIdentity(source.uid(), member.uid());
        var ticket = boardTicketRepo.createTicket(
                board.id(), lane.id(), 1, "Bericht", null, null, TicketPriority.MEDIUM, null, 0, creator);
        String attachment = boardAttachments.newFilename("bericht.txt");
        boardTicketRepo.createAttachment(ticket.id(), attachment, "bericht.txt", "text/plain", report.length, creator);
        boardAttachments.store(source.id(), ticket.id(), attachment, report, "text/plain");

        var catalog = quizCatalogRepo.create(source.id(), "Grundlagen", "", false, CatalogMetadata.none());
        var question = quizCatalogRepo.createQuestion(
                catalog.id(),
                null,
                QuizQuestionType.TRUE_FALSE,
                "Ist das ein Hydrant?",
                "",
                "uploaded",
                1.0,
                false,
                "{\"correctAnswer\":true}",
                0);
        quizPictures.store(source.id(), question.id(), png, "image/png", 5 * 1024 * 1024);

        byte[] icon =
                folderIcons.read(source.id(), folder.id(), 0).orElseThrow().data();
        byte[] picture = wikiPictures
                .read(source.id(), wikiFile.id(), "image/png", 0)
                .orElseThrow()
                .data();
        byte[] quizPicture =
                quizPictures.read(source.id(), question.id(), 0).orElseThrow().data();

        String token = rawToken(exportService.createTransferToken(source.id()));
        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());

        int destinationId = importResult.stationId();
        var destination = stationRepo.findById(destinationId).orElseThrow();
        var destinationOwner = new Owner.Station(destinationId);

        var arrivedFolder = knowledgeBaseRepo.findAllFolders(destinationId).getFirst();
        assertNotEquals(folder.id(), arrivedFolder.id(), "the folder got a new id on the destination");
        assertEquals(folderIcons.key(arrivedFolder.id()), arrivedFolder.iconUrl());
        assertArrayEquals(
                icon,
                folderIcons
                        .read(destinationId, arrivedFolder.id(), 0)
                        .orElseThrow()
                        .data());

        var arrivedFile = knowledgeBaseRepo.findAllFiles(destinationId).getFirst();
        assertNotEquals(wikiFile.id(), arrivedFile.id(), "the wiki file got a new id on the destination");
        assertArrayEquals(
                png,
                wikiFiles.read(destinationId, arrivedFile.id()).orElseThrow().data());
        assertTrue(
                images.exists(
                        ImageProfile.CONTENT,
                        new StorageScope.Station(destinationId, destination.uid()),
                        StorageCategory.IMAGE_KB_FILE_PICTURE,
                        KbFilePictureService.key(arrivedFile.id())),
                "the picture came along with the file");
        assertArrayEquals(
                picture,
                wikiPictures
                        .read(destinationId, arrivedFile.id(), "image/png", 0)
                        .orElseThrow()
                        .data());

        var arrivedFont = fontRepo.findOwned(destinationOwner).getFirst();
        assertArrayEquals(TestFonts.lisu(), fontLibrary.read(arrivedFont).orElseThrow());

        var arrivedBoard = boardRepo.findByStation(destinationId).getFirst();
        var arrivedTicket = boardTicketRepo.findByBoard(arrivedBoard.id()).getFirst();
        assertNotEquals(ticket.id(), arrivedTicket.id(), "the ticket got a new id on the destination");
        var arrivedAttachment =
                boardTicketRepo.findAttachments(arrivedTicket.id()).getFirst();
        try (var stream = boardAttachments
                .read(destinationId, arrivedTicket.id(), arrivedAttachment.filename())
                .orElseThrow()) {
            assertArrayEquals(report, stream.body().readAllBytes());
        }

        var arrivedCatalog = quizCatalogRepo.findByStation(destinationId).getFirst();
        var arrivedQuestion = quizCatalogRepo.findQuestions(arrivedCatalog.id()).getFirst();
        assertNotEquals(question.id(), arrivedQuestion.id(), "the question got a new id on the destination");
        assertArrayEquals(
                quizPicture,
                quizPictures
                        .read(destinationId, arrivedQuestion.id(), 0)
                        .orElseThrow()
                        .data());
    }

    /**
     * A letter template arrives with its tags and its text, and a PDF template with the PDF it fills,
     * which reads back through the template under its new id. Who wrote them stays behind with the
     * account ids that do not travel, and costs neither template.
     */
    @Test
    void documentTemplatesArriveWithTheirTagsAndTheirPdf() throws Exception {
        Station source = stationRepo.create("Source TEMPLATES");
        Account author = accountRepo.create("templates@xfer.test", "Tara", "Template", true);
        stationMemberRepo.create(source.id(), author.id());
        var owner = new Owner.Station(source.id());
        var letter = templates.create(
                owner,
                TemplateRequestBuilder.letter("Bescheinigung", "Hiermit wird bescheinigt", "{{member.fullName}}")
                        .tags(List.of("Ausbildung", "Nachweis"))
                        .build(),
                author.id());
        var pdf = templates.create(owner, TemplateRequestBuilder.pdf("Formular").build(), author.id());
        byte[] form = TestPdfs.withForm();
        pdfTemplates.upload(owner, pdf.id(), TestUploads.of("formular.pdf", "application/pdf", form), author.id());

        String token = rawToken(exportService.createTransferToken(source.id()));
        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());

        var destination = new Owner.Station(importResult.stationId());
        var arrived = templates.list(destination, false).stream()
                .collect(Collectors.toMap(
                        DocumentTemplateService.DocumentTemplateSummary::name,
                        DocumentTemplateService.DocumentTemplateSummary::id));
        assertEquals(Set.of("Bescheinigung", "Formular"), arrived.keySet());

        var arrivedLetter = templates.detail(destination, arrived.get("Bescheinigung"));
        assertNotEquals(letter.id(), arrivedLetter.id(), "the letter got a new id on the destination");
        assertEquals(List.of("Ausbildung", "Nachweis"), arrivedLetter.tags());
        assertEquals(letter.body(), arrivedLetter.body());

        var arrivedPdf = pdfTemplates
                .current(destination, arrived.get("Formular"))
                .orElseThrow(() -> new AssertionError("the PDF template arrived without its PDF"));
        assertEquals("formular.pdf", arrivedPdf.fileName());
        assertArrayEquals(form, arrivedPdf.data());
    }

    /**
     * What a station has set up around its members arrives pointing at the rows they got on the
     * destination: a saved report with its user types and groups, a group with its permissions, a
     * member's saved filter, and a change to a profile answer made by somebody known by their account
     * only.
     */
    @Test
    void settingsAndHistoryArriveWithTheirStation() throws Exception {
        Station source = stationRepo.create("Source SETTINGS");
        Account account = accountRepo.create("settings@xfer.test", "Sven", "Settings", true);
        Account manager = accountRepo.create("settings-manager@xfer.test", "Maren", "Manager", true);
        var member = stationMemberRepo.create(source.id(), account.id());
        int group = memberGroupRepo.create(source.id(), "Atemschutz").id();
        var permission = stationMemberRepo
                .findPermissionByName(StationPermission.ATTENDANCE_MANAGER)
                .orElseThrow();
        memberGroupRepo.addGroupPermission(group, permission.id());
        attendanceRepo.createPreset(
                source.id(),
                "Atemschutz im Monat",
                List.of(StationUserType.MEMBER, StationUserType.TEAM),
                List.of(group),
                "month",
                "exact");
        savedFilterRepo.create(account.id(), FilterTableType.MEMBERS, "Nur Atemschutz", "{\"groups\":[]}", 0);
        int field = profileFieldRepo
                .create(source.id(), "Führerschein", FieldType.TEXT, ProfileFieldConfig.empty(), false, false, null)
                .id();
        profileFieldChangeRepo.create(
                FieldOrigin.STATION, field, member.id(), "\"B\"", "\"C\"", ProfileAuthor.account(manager.id()), false);

        String token = rawToken(exportService.createTransferToken(source.id()));
        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());

        int destinationId = importResult.stationId();
        var arrivedGroup = memberGroupRepo.findByStation(destinationId).getFirst();
        assertNotEquals(group, arrivedGroup.id(), "the group got a new id on the destination");
        assertEquals(
                List.of(StationPermission.ATTENDANCE_MANAGER),
                memberGroupRepo.findGroupPermissions(arrivedGroup.id()).stream()
                        .map(Permission::permission)
                        .toList());

        var preset = attendanceRepo.findPresets(destinationId).getFirst();
        assertEquals(List.of(StationUserType.MEMBER, StationUserType.TEAM), preset.userTypes());
        assertEquals(List.of(arrivedGroup.id()), preset.groupIds(), "the preset names the group that arrived");

        assertEquals(
                List.of("Nur Atemschutz"),
                savedFilterRepo.findByAccountAndTable(account.id(), FilterTableType.MEMBERS).stream()
                        .map(SavedFilter::name)
                        .toList(),
                "the filter was cleared at the source when it was sent, so it is the one that arrived");

        var changes = profileFieldChangeRepo.findByStation(destinationId, 10, 0);
        assertEquals(1, changes.size());
        assertEquals("\"C\"", changes.getFirst().newValue());
    }

    /**
     * Gear lent to a partner and gear borrowed from one move with the station and with their lending
     * requests. The partner is found again by its uid, here on the same database as the destination,
     * and the request lines name the gear as it arrived; what the partner wrote and what names the
     * partner's own gear stays with the partner.
     */
    @Test
    void lentAndBorrowedGearArriveWithTheirLendingRequests() throws Exception {
        Station source = stationRepo.create("Source LENDING");
        Station partner = stationRepo.create("Partner LENDING");
        var sourceMember = stationMemberRepo.create(
                source.id(),
                accountRepo.create("lender@xfer.test", "Lea", "Lender", true).id());
        var partnerMember = stationMemberRepo.create(
                partner.id(),
                accountRepo.create("borrower@xfer.test", "Bo", "Borrower", true).id());
        var lending = new LendingRepository();

        int radios = inventoryRepo
                .create(source.id(), "Funk", InventoryType.INTERNAL, false)
                .id();
        int lentItem = inventoryRepo
                .createItem(radios, "HRT-1", "Handfunkgerät 1", null, null)
                .id();
        var lentOut = lending.createRequest(
                partner.uid(),
                source.uid(),
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                partnerMember.id(),
                null,
                null,
                "Übung");
        int lentLine = lending.addRequestItem(lentOut.id(), radios, lentItem, null, 1, null)
                .id();
        lending.assignItem(lentLine, lentItem);
        lending.updateRequestStatus(lentOut.id(), LendingStatus.LENT);
        lending.createMessage(lentOut.id(), source.uid(), sourceMember.id(), "Liegt bereit", false);
        lending.createMessage(lentOut.id(), partner.uid(), partnerMember.id(), "Danke", false);
        itemCustodyService.lendToPartner(lentItem, partner.id());
        borrowedGearService.handOver(
                inventoryRepo.findItemById(lentItem).orElseThrow(), source.id(), source.uid(), partner.id(), lentLine);

        int breathing = inventoryRepo
                .create(partner.id(), "Atemschutz", InventoryType.INTERNAL, false)
                .id();
        int partnerItem = inventoryRepo
                .createItem(breathing, "PA-7", "Pressluftatmer 7", null, null)
                .id();
        var borrowed = lending.createRequest(
                source.uid(),
                partner.uid(),
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                sourceMember.id(),
                null,
                null,
                "Leistungsprüfung");
        int borrowedLine = lending.addRequestItem(borrowed.id(), breathing, partnerItem, null, 1, null)
                .id();
        lending.assignItem(borrowedLine, partnerItem);
        lending.updateRequestStatus(borrowed.id(), LendingStatus.LENT);
        itemCustodyService.lendToPartner(partnerItem, source.id());
        borrowedGearService.handOver(
                inventoryRepo.findItemById(partnerItem).orElseThrow(),
                partner.id(),
                partner.uid(),
                source.id(),
                borrowedLine);

        String token = rawToken(exportService.createTransferToken(source.id()));
        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());
        int destinationId = importResult.stationId();

        var arrivedLent = inventoryRepo.findItemsByStation(destinationId).stream()
                .filter(item -> "HRT-1".equals(item.internalId()))
                .findFirst()
                .orElseThrow();
        assertEquals(ItemCustody.WITH_PARTNER, arrivedLent.custody());
        assertEquals(partner.id(), arrivedLent.custodyPartnerStationId(), "the partner holding it, found by its uid");
        assertEquals(destinationId, arrivedLent.custodyStationId(), "the lender is the station that moved");

        var arrivedLentLine = lineNaming(arrivedLent.id());
        assertNotEquals(lentLine, arrivedLentLine.id());
        assertEquals(
                List.of(arrivedLent.id()),
                lending.findAssignedItems(arrivedLentLine.id()),
                "giving it back finds the piece that arrived");
        var arrivedLentOut =
                lending.findRequestById(arrivedLentLine.requestId()).orElseThrow();
        assertEquals(LendingStatus.LENT, arrivedLentOut.status());
        assertEquals(partner.uid(), arrivedLentOut.requestingStationUid());
        assertNull(arrivedLentOut.createdBy(), "the partner's member who asked stays with the partner");
        assertEquals(
                List.of("Liegt bereit"),
                lending.findMessagesByRequest(arrivedLentOut.id()).stream()
                        .map(LendingMessage::message)
                        .toList(),
                "only what the station wrote moves; the partner keeps its own messages");

        var arrivedBorrowed = inventoryRepo.findBorrowedItems(destinationId).getFirst();
        assertEquals("PA-7", arrivedBorrowed.item().internalId());
        assertEquals(ItemOwner.PARTNER_STATION, arrivedBorrowed.item().ownerKind());
        assertEquals(partner.id(), arrivedBorrowed.ownerStationId(), "the owner, found by its uid");
        assertEquals(ItemCustody.AT_STATION, arrivedBorrowed.item().custody());
        assertEquals(destinationId, arrivedBorrowed.item().custodyStationId());

        var arrivedBorrowedLine =
                lending.findItemsByRequest(arrivedBorrowed.loanRequestId()).getFirst();
        assertEquals(arrivedBorrowed.item().loanRequestItemId(), arrivedBorrowedLine.id());
        assertNull(arrivedBorrowedLine.itemId(), "the partner's piece is not here to be named");
        assertNull(arrivedBorrowedLine.inventoryId());
        assertEquals(List.of(), lending.findAssignedItems(arrivedBorrowedLine.id()));
        var arrivedRequest =
                lending.findRequestById(arrivedBorrowed.loanRequestId()).orElseThrow();
        assertEquals(LendingStatus.LENT, arrivedRequest.status());
        assertEquals(partner.uid(), arrivedRequest.owningStationUid());
        assertNotNull(arrivedRequest.createdBy(), "the member who asked moved with the station");
    }

    /**
     * A station that lent to and borrowed from a partner of its installation moves away. The partner
     * stays, reaches the moved station at its new address with the key the station took along, and
     * keeps both loans as loans with another installation; deleting the copy the move left behind takes
     * none of it away.
     */
    @Test
    void aStationLeftBehindSwitchesOverToTheMovedStation() throws Exception {
        Station source = stationRepo.create("Source SWITCH");
        Station partner = stationRepo.create("Partner SWITCH");
        var federationRepo = new FederationRepository();
        var federation = TestFederationServices.of(federationRepo, stationRepo);
        federation.acceptInvite(
                partner.id(), source.id(), federation.encodePublicKey(federation.generateKeyPair()), null, null);
        var lending = new LendingRepository();
        int radios = inventoryRepo
                .create(source.id(), "Funk", InventoryType.INTERNAL, false)
                .id();
        var radio = inventoryRepo.createItem(radios, "HRT-S", "Handfunkgerät S", null, null);
        var lentOut = lend(lending, source, radio, partner);
        int pumps = inventoryRepo
                .create(partner.id(), "Pumpen", InventoryType.INTERNAL, false)
                .id();
        var pump = inventoryRepo.createItem(pumps, "TS-S", "Tragkraftspritze S", null, null);
        var borrowed = lend(lending, partner, pump, source);
        var keys = TestStationKeys.store();

        var page = exportService.exportTableForTransfer("page-check", source.id(), "station", 0, 1);
        assertEquals(
                List.of(new PartnersLeftBehind.LeftBehindPartner(
                        partner.uid(), "Partner SWITCH", keys.ensurePublicKey(partner.id()))),
                page.get(PartnersLeftBehind.FIELD),
                "the station takes along whom it leaves behind");

        String token = rawToken(exportService.createTransferToken(source.id()));
        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());

        var partnership = federationRepo
                .findPartnerByStationAndRemoteUid(partner.id(), source.uid())
                .orElseThrow();
        assertEquals(new Api().baseUrl(), partnership.remoteHost(), "the partner reaches it at its new address");
        assertEquals(keys.ensurePublicKey(source.id()), partnership.partnerPublicKey());
        assertTrue(stationRepo.findHereByUid(source.uid()).isEmpty(), "the copy left behind is not where it runs");

        var lentLine = lending.findItemsByRequest(lentOut.id()).getFirst();
        assertNull(lentLine.itemId(), "the moved station's gear is not here to be named");
        assertEquals("Handfunkgerät S", lentLine.label());
        assertEquals(List.of(), lending.findAssignedItems(lentLine.id()));
        var copy = borrowedFrom(partner, lentOut.id());
        assertNull(copy.ownerStationId());
        assertEquals(source.uid(), copy.ownerStationUid());
        assertNull(inventoryRepo.findItemById(pump.id()).orElseThrow().custodyPartnerStationId());

        stationRepo.delete(source.id());

        assertEquals(1, lending.findItemsByRequest(lentOut.id()).size(), "the partner's loan outlives the copy");
        assertEquals(
                copy.item().id(), borrowedFrom(partner, lentOut.id()).item().id());
        assertEquals(
                List.of(pump.id()),
                lending.findAssignedItems(
                        lending.findItemsByRequest(borrowed.id()).getFirst().id()));
    }

    /**
     * Gear borrowed from a partner on another installation arrives with the station, still owned by
     * that partner's uid, and is handed back from the destination like any other.
     */
    @Test
    void gearBorrowedFromAnotherInstallationArrivesAndGoesBackHome() throws Exception {
        Station source = stationRepo.create("Source REMOTE OWNER");
        UUID elsewhere = UUID.randomUUID();
        var federation = TestFederationServices.of(new FederationRepository(), stationRepo);
        String someKey = federation.encodePublicKey(federation.generateKeyPair());
        new FederationRepository()
                .createRemotePartner(
                        source.id(),
                        elsewhere,
                        someKey,
                        someKey,
                        "https://owner-elsewhere.example",
                        "Owner Elsewhere",
                        FederationContractVersions.current());
        var lending = new LendingRepository();
        var request = lending.createRequest(
                UUID.randomUUID(),
                source.uid(),
                elsewhere,
                LocalDate.now(),
                LocalDate.now().plusDays(3),
                null,
                null,
                null,
                "Übung");
        int line =
                lending.addRequestItem(request.id(), null, null, null, 1, null).id();
        lending.labelItems(request.id(), List.of("Schläuche"));
        lending.updateRequestStatus(request.id(), LendingStatus.LENT);
        borrowedGearService.handOver(
                List.of(BorrowedPiece.named("B-1", "Schlauch B 1")), elsewhere, null, source.id(), line);

        String token = rawToken(exportService.createTransferToken(source.id()));
        var importResult = importService.startRemoteImport(baseUrl, token);
        waitForImport(importResult.stationId());
        int destinationId = importResult.stationId();

        var arrived = inventoryRepo.findBorrowedItems(destinationId).getFirst();
        assertEquals("B-1", arrived.item().internalId());
        assertNull(arrived.ownerStationId(), "the owner runs on another installation");
        assertEquals(elsewhere, arrived.ownerStationUid());
        assertEquals("Owner Elsewhere", arrived.ownerStationName());
        assertEquals(
                "Schläuche",
                lending.findItemsByRequest(arrived.loanRequestId()).getFirst().label());

        var lendingService = newLendingService(
                new DomainEventBus(Set.of()),
                new EquipmentAvailabilityService(
                        new EquipmentAvailabilityRepository(),
                        new EquipmentNeedRepository(),
                        eventRepo,
                        occurrenceCalendar));
        assertTrue(lendingService.markReturned(arrived.loanRequestId(), destinationId));

        assertEquals(List.of(), inventoryRepo.findBorrowedItems(destinationId), "the borrowed copy went home");
    }

    /** Hands one piece over from one station of this installation to another, as a loan. */
    private static LendingRequest lend(
            LendingRepository lending, Station lender, InventoryItem piece, Station borrower) {
        var request = lending.createRequest(
                UUID.randomUUID(),
                borrower.uid(),
                lender.uid(),
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                null,
                null,
                null,
                "Übung");
        int line = lending.addRequestItem(request.id(), piece.inventoryId(), piece.id(), null, 1, null)
                .id();
        lending.assignItem(line, piece.id());
        lending.updateRequestStatus(request.id(), LendingStatus.LENT);
        itemCustodyService.lendToPartner(piece.id(), borrower.id());
        borrowedGearService.handOver(piece, lender.id(), lender.uid(), borrower.id(), line);
        return request;
    }

    private static BorrowedItem borrowedFrom(Station borrower, int requestId) {
        return inventoryRepo.findBorrowedItems(borrower.id()).stream()
                .filter(item -> item.loanRequestId() == requestId)
                .findFirst()
                .orElseThrow();
    }

    private static LendingRequestItem lineNaming(int itemId) {
        int requestId = query("SELECT request_id FROM federation_lending_request_item WHERE item_id = :item_id;")
                .single(call().bind("item_id", itemId))
                .map(row -> row.getInt("request_id"))
                .first()
                .orElseThrow();
        return new LendingRepository()
                .findItemsByRequest(requestId).stream()
                        .filter(line -> Integer.valueOf(itemId).equals(line.itemId()))
                        .findFirst()
                        .orElseThrow();
    }

    private static Document storeDocument(
            DocumentService documents,
            Station station,
            int memberId,
            String title,
            String mimeType,
            byte[] data,
            String tag) {
        return documents.store(
                station.id(),
                List.of(memberId),
                title,
                title.toLowerCase() + ".bin",
                mimeType,
                data,
                false,
                false,
                Uploader.member(memberId),
                List.of(tag));
    }

    /**
     * Files a sealed document with one version per file, the last one current, the way sealing
     * keeps them: the rows name each file by its SHA-256, under which the file is stored.
     */
    private static int sealedDocument(
            Station station, int memberId, String title, SealedVersionRepository versions, byte[]... files) {
        var scope = new StorageScope.Station(station.id(), station.uid());
        var document = memberDocumentRepo.create(
                station.id(),
                title,
                title.toLowerCase() + ".pdf",
                "application/pdf",
                files[files.length - 1].length,
                false,
                true,
                Uploader.member(memberId),
                List.of(memberId));
        memberDocumentRepo.seal(document.id());
        for (byte[] file : files) {
            String sha256 = Sha256.hex(file);
            versions.supersedeCurrent(document.id());
            versions.add(document.id(), sha256, file.length, SealLevel.BASELINE_B, null);
            storageService.store(
                    scope,
                    StorageCategory.MEMBER_DOCUMENTS,
                    "sealed/" + sha256,
                    new Variant("content"),
                    file,
                    "application/pdf");
        }
        return document.id();
    }

    private static List<String> tagNames(Document document) {
        return memberDocumentRepo.findTags(document.id()).stream()
                .map(DocumentTag::name)
                .toList();
    }

    private static byte[] onePagePdf() throws IOException {
        try (var pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            var out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        }
    }

    private static String rawToken(String encoded) {
        return StationExportService.parseToken(encoded).orElseThrow().token();
    }

    private static Optional<MediaContent> libraryOriginal(int stationId, String contentHash) {
        var at = mediaStorageService.locate(stationId, contentHash);
        return images.read(ImageProfile.LIBRARY, at.scope(), at.category(), at.key(), 0);
    }

    private static byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(220, 70, 30));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(50, 80, 200));
            g.fillRect(width / 4, height / 4, width / 2, height / 2);
        } finally {
            g.dispose();
        }
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * The source's export as another instance would send it. Source and destination share one test
     * database here, so a row's public id, unique across the database, would collide with the source's
     * own row and the row would not arrive; each exported row gets a fresh one instead, as it would not
     * collide on a database of its own. A lending request's uid is unique the same way. A saved filter belongs to an account, which both sides share here
     * as well, so the source's copy is cleared once it is sent: the destination's own database would not
     * hold it, and what the account has afterwards is what arrived.
     *
     * <p>The station's own uid is unique the same way, and the copy the move leaves on the source keeps
     * it. Wherever it travels it is replaced by one uid per station that nothing here carries yet, which
     * the arriving station takes, as it takes the source's uid on a database of its own; so the source
     * switching over to the moved station never mistakes what arrived for what it left behind.
     */
    private static final class SeparateDatabaseExport extends StationExportService {
        private static final Map<Integer, UUID> ARRIVING_AS = new ConcurrentHashMap<>();

        SeparateDatabaseExport() {
            super(
                    stationRepo,
                    TestStationKeys.transfer(),
                    TestStationKeys.partnersLeftBehind(),
                    TestStationKeys.aiKeyTransfer(),
                    new Api());
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> exportTable(int stationId, String tableName, int offset, int limit) {
            var page = super.exportTable(stationId, tableName, offset, limit);
            String leaving = stationRepo.requireUid(stationId).toString();
            UUID arriving = ARRIVING_AS.computeIfAbsent(stationId, id -> UUID.randomUUID());
            if (page.get(tableName) instanceof Map<?, ?> single) {
                renameStation((Map<String, Object>) single, leaving, arriving);
            }
            if (page.get(tableName) instanceof List<?> rows) {
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> columns)
                        renameStation((Map<String, Object>) columns, leaving, arriving);
                    if (row instanceof Map<?, ?> columns && columns.containsKey("public_uid")) {
                        ((Map<String, Object>) columns).put("public_uid", UUID.randomUUID());
                    }
                    if ("federation_lending_request".equals(tableName) && row instanceof Map<?, ?> columns) {
                        ((Map<String, Object>) columns).put("uid", UUID.randomUUID());
                    }
                    if ("saved_filter".equals(tableName) && row instanceof Map<?, ?> columns) {
                        clearSavedFilter(columns.get("id"));
                    }
                }
            }
            return page;
        }

        private static void renameStation(Map<String, Object> row, String leaving, UUID arriving) {
            row.replaceAll((column, value) -> value != null && leaving.equals(value.toString()) ? arriving : value);
        }

        private static void clearSavedFilter(Object id) {
            query("DELETE FROM saved_filter WHERE id = :id;")
                    .single(call().bind("id", ((Number) id).intValue()))
                    .update();
        }
    }

    @SuppressWarnings("BusyWait")
    private static void waitForImport(int stationId) throws InterruptedException {
        Duration timeout = Duration.ofSeconds(120);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ImportProgress progress = importService.getProgress(stationId);
            assertNotNull(progress, "import progress disappeared");
            if (progress.status() == ImportProgress.Status.COMPLETED) return;
            if (progress.status() == ImportProgress.Status.FAILED) {
                fail("import failed: " + progress.error());
            }
            Thread.sleep(50);
        }
        fail("import for station " + stationId + " did not finish within " + timeout);
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

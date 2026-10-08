/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.auth.BreachCheckWorker;
import dev.chojo.ember.auth.HibpClient;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.conf.file.elements.WebAuthnSettings;
import dev.chojo.ember.feature.account.service.AccountEmailService;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.service.pdf.PdfFiles;
import dev.chojo.ember.feature.generator.service.pdf.SignatureFields;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailConfirmationPolicy;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.passkey.service.PasskeyService;
import dev.chojo.ember.feature.passkey.service.TestAuthenticator;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.signing.entity.ActPictureSource;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.SignatureImageSource;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.SigningStatements;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.signing.repository.AccountSignatureRepository;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.feature.signing.service.InEmberSignatureProvider;
import dev.chojo.ember.feature.signing.service.SignatureFieldService;
import dev.chojo.ember.feature.signing.service.SignatureImageService;
import dev.chojo.ember.feature.signing.service.SignatureImages;
import dev.chojo.ember.feature.signing.service.SignatureNotices;
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
import dev.chojo.ember.feature.signing.service.SignerNames;
import dev.chojo.ember.feature.signing.service.SignerResolver;
import dev.chojo.ember.feature.signing.service.SigningActService;
import dev.chojo.ember.feature.signing.service.SigningAssertions;
import dev.chojo.ember.feature.signing.service.SigningGuards;
import dev.chojo.ember.feature.signing.service.SigningStarts;
import dev.chojo.ember.feature.signing.service.SigningStateSealer;
import dev.chojo.ember.feature.signing.service.TestKeyStamps;
import dev.chojo.ember.feature.signing.service.TestSealing;
import dev.chojo.ember.feature.signing.service.TestSignatures;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.WebAuthnChallengeRepository;
import dev.chojo.ember.feature.twofactor.service.BackupCodeService;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.SecondFactorCredentialStore;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import dev.chojo.ember.feature.twofactor.service.TrustedDeviceService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.feature.twofactor.service.WebAuthnCredentialStore;
import dev.chojo.ember.feature.twofactor.service.WebAuthnRelyingPartyFactory;
import dev.chojo.ember.feature.twofactor.service.WebAuthnService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Sha256;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Request;
import io.javalin.testtools.Response;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A member's signing acts over HTTP, through the real services, the database and real credentials: a
 * passkey or security key answer binds the act and is kept as it came, a code or password confirms it
 * unbound, the proofs signing never takes are refused, a start is spent once and only by the account that
 * made it, a field is signed only by those it asks for, a guardian signs for a child and lends the child
 * their account, and wrong codes count toward the step-up limit. A field and its document are read only
 * by those it asks while it waits, and the document comes as the request froze it, also to a second signer
 * once the first act was sealed into a version of it. Each act is sealed with real keys and without
 * timestamps, and an act whose seal failed stays signed and is sealed with the next one. Every act leaves a
 * signature picture, one made for it or the saved one, and is refused with neither; a picture a child makes
 * through a guardian's account is never kept as the guardian's.
 */
class SigningRoutesTest extends RepositoryTestBase {
    private static final String RIGHT_CODE = "424242";
    private static final String WRONG_CODE = "111111";
    private static final String DEVELOPMENT_CODE = "000000";
    private static final String APP_SECRET = "JBSWY3DPEHPK3PXP";
    private static final String PASSWORD = "lang-und-geheim-genug";
    private static final SigningStatements STATEMENTS =
            new SigningStatements("Ich stimme zu.", "Ich bin erziehungsberechtigt und stimme zu.");
    private static final AtomicInteger NAMES = new AtomicInteger();

    @TempDir
    static Path storageRoot;

    private static final DocumentGenerationRepository generations = new DocumentGenerationRepository();
    private static final DocumentTemplateRepository templates = new DocumentTemplateRepository();
    private static final SignatureRequestRepository requestRepo = new SignatureRequestRepository();
    private static final SigningEvidenceRepository evidenceRepo = new SigningEvidenceRepository();
    private static final PasswordHasher hasher = new PasswordHasher();

    private static StorageService storage;
    private static DocumentService documents;
    private static SignatureRequestService requests;
    private static SigningActService acts;
    private static SignatureImageService signatureImages;
    private static Function<SigningStateSealer, SigningActService> actsSealingWith;
    private static WebAuthnService securityKeys;
    private static PasskeyService passkeys;
    private static Station station;
    private static StationMember manager;
    private static int template;
    private static int loginPermission;

    private RouteHarness harness;

    @BeforeAll
    static void setup() throws Exception {
        var backend = new LocalStorageBackend(storageRoot);
        storage = new StorageService(new StorageBackendResolver(backend), backend);
        documents = newDocumentService(storage);
        var guardianPolicy = new GuardianPolicy(stationMemberRepo);
        var guards = new SigningGuards(
                new DocumentAccessService(memberDocumentRepo, documents, guardianPolicy),
                memberDocumentRepo,
                guardianPolicy);
        requests = new SignatureRequestService(
                requestRepo,
                evidenceRepo,
                generations,
                memberDocumentRepo,
                documents,
                stationMemberRepo,
                memberNameResolver,
                guardianPolicy,
                new SignerResolver(stationMemberRepo, memberNameResolver, memberPermissionResolver),
                guards,
                mock(SignatureNotices.class),
                (template, member, name) -> STATEMENTS);
        var fields = new SignatureFieldService(
                requestRepo,
                evidenceRepo,
                requests,
                guards,
                guardianPolicy,
                memberNameResolver,
                mock(SignatureNotices.class));

        var settings = new WebAuthnSettings();
        RelyingParties parties = relyingParties(settings);
        var challenges = new WebAuthnChallengeRepository(TokenHasher.forTesting("signing-route-pepper"));
        var audit = new TwoFactorAuditService(twoFactorRepo);
        var keyStamps = TestKeyStamps.off(twoFactorRepo);
        securityKeys = new WebAuthnService(parties, twoFactorRepo, audit, challenges, settings, keyStamps);
        passkeys = new PasskeyService(parties, twoFactorRepo, audit, challenges, settings, keyStamps);
        var totp = mock(TotpService.class);
        when(totp.isDevCode(DEVELOPMENT_CODE)).thenReturn(true);
        when(totp.decryptSecret(any())).thenReturn(APP_SECRET);
        var steps = new AtomicLong();
        when(totp.matchStep(APP_SECRET, RIGHT_CODE)).thenAnswer(call -> OptionalLong.of(steps.incrementAndGet()));
        var twoFactor = new TwoFactorService(
                twoFactorRepo,
                totp,
                mock(BackupCodeService.class),
                audit,
                accountRepo,
                mock(MailLocaleService.class),
                mock(EmailService.class));
        var assertions = new SigningAssertions(parties, twoFactorRepo, settings);
        var names = new SignerNames(accountRepo, memberNameResolver);
        var starts = new SigningStarts(challenges);
        var provider = new InEmberSignatureProvider(twoFactor, assertions, names, keyStamps);
        var auth = authService();
        signatureImages = new SignatureImageService(
                new AccountSignatureRepository(),
                accountRepo,
                new StorageService(new StorageBackendResolver(backend), backend));
        actsSealingWith = stateSealer -> new SigningActService(
                requestRepo,
                requests,
                fields,
                memberDocumentRepo,
                documents,
                provider,
                stateSealer,
                starts,
                assertions,
                names,
                twoFactor,
                auth,
                evidenceRepo,
                signatureImages);
        acts = actsSealingWith.apply(TestSealing.stateSealer(memberDocumentRepo, documents, stationRepo));

        loginPermission = stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow()
                .id();
        station = stationRepo.create("Signing Route Station");
        manager = member("Maria", "Leitung", true);
        int author = accountRepo.create(email(), "Vor", "Lage").id();
        template = templates
                .create(new Owner.Station(station.id()), draft(), author)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @BeforeEach
    void freshLimits() {
        harness = serving(acts);
    }

    private static RouteHarness serving(SigningActService signingActs) {
        return RouteHarness.serving(new SigningRoutes(signingActs, requests, new AuthRateLimiter(Clock.systemUTC())))
                .withStations(stationRepo);
    }

    @Test
    void aSecurityKeyBindsTheActAndItsAnswerIsKeptAsItCame() throws IOException {
        var signer = member("Karin", "Schluessel", true);
        TestAuthenticator key = enrolSecurityKey(signer.accountId());
        var request = ask(signer, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            JsonNode started =
                    start(client, signer, fieldOf(request), "[{\"field\":\"Telefon\",\"value\":\"0171 2345678\"}]");
            assertEquals(List.of("SECURITY_KEY"), texts(started.path("acceptedProofs")));
            JsonNode options =
                    body(started.path("webAuthnOptionsJson").asString()).path("publicKey");
            assertEquals("required", options.path("userVerification").asString());
            assertEquals("ember.test", options.path("rpId").asString());
            assertEquals(1, options.path("allowCredentials").size());
            assertEquals(
                    memberNameResolver.official(signer.id()),
                    started.path("signerName").asString());
            assertEquals(request.contentSha256(), started.path("contentSha256").asString());

            String credential = key.sign(started.path("webAuthnOptionsJson").asString());
            JsonNode completed =
                    json(complete(client, signer, fieldOf(request), started, "SECURITY_KEY", credential, null));

            assertEquals("SIGNED", completed.path("state").asString());
            assertEquals("COMPLETE", completed.path("requestState").asString());
            assertEquals("SECURITY_KEY", completed.path("proof").asString());
            assertTrue(completed.path("bound").asBoolean());

            var bound = assertInstanceOf(
                    SigningEvidence.WebAuthnBound.class, onlyEvidence(request).evidence());
            JsonNode answer = body(credential).path("response");
            assertArrayEquals(decode(answer.path("clientDataJSON").asString()), bound.clientDataJson());
            assertArrayEquals(decode(answer.path("authenticatorData").asString()), bound.authenticatorData());
            assertArrayEquals(decode(options.path("challenge").asString()), bound.challenge());
            assertTrue(bound.userVerified());
            assertEquals(
                    List.of(new SignerEntry("Telefon", "0171 2345678")),
                    bound.act().entries());
        });
    }

    @Test
    void aPasskeyBindsTheActToo() throws IOException {
        var signer = member("Petra", "Passkey", true);
        TestAuthenticator passkey = enrolPasskey(signer.accountId());
        var request = ask(signer, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            JsonNode started = start(client, signer, fieldOf(request), null);
            assertEquals(List.of("PASSKEY"), texts(started.path("acceptedProofs")));
            String credential = passkey.sign(started.path("webAuthnOptionsJson").asString());

            JsonNode completed = json(complete(client, signer, fieldOf(request), started, "PASSKEY", credential, null));

            assertEquals("PASSKEY", completed.path("proof").asString());
            var bound = assertInstanceOf(
                    SigningEvidence.WebAuthnBound.class, onlyEvidence(request).evidence());
            assertArrayEquals(
                    decode(body(credential)
                            .path("response")
                            .path("clientDataJSON")
                            .asString()),
                    bound.clientDataJson());
        });
    }

    @Test
    void anAuthenticatorCodeConfirmsTheActUnbound() throws IOException {
        var signer = member("Tina", "Code", true);
        enrolAuthenticatorApp(signer.accountId());
        var request = ask(signer, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            JsonNode started = start(client, signer, fieldOf(request), null);
            assertEquals(List.of("TOTP"), texts(started.path("acceptedProofs")));
            JsonNode options = started.path("webAuthnOptionsJson");
            assertTrue(options.isNull() || options.isMissingNode());

            JsonNode completed = json(complete(client, signer, fieldOf(request), started, "TOTP", null, RIGHT_CODE));

            assertFalse(completed.path("bound").asBoolean());
            assertInstanceOf(
                    SigningEvidence.TotpUnbound.class, onlyEvidence(request).evidence());
        });
    }

    @Test
    void thePasswordConfirmsTheActUnboundOnlyWithoutASecondFactor() throws IOException {
        var plain = member("Paul", "Passwort", true);
        accountRepo.createCredential(plain.accountId(), hasher.hash(PASSWORD));
        var guarded = member("Gerd", "Gesichert", true);
        accountRepo.createCredential(guarded.accountId(), hasher.hash(PASSWORD));
        enrolAuthenticatorApp(guarded.accountId());
        var plainRequest = ask(plain, SignatureRole.PARTICIPANT);
        var guardedRequest = ask(guarded, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            JsonNode started = start(client, plain, fieldOf(plainRequest), null);
            assertEquals(List.of("PASSWORD"), texts(started.path("acceptedProofs")));
            complete(client, plain, fieldOf(plainRequest), started, "PASSWORD", null, PASSWORD);
            assertInstanceOf(
                    SigningEvidence.PasswordUnbound.class,
                    onlyEvidence(plainRequest).evidence());

            JsonNode refused = start(client, guarded, fieldOf(guardedRequest), null);
            assertEquals(List.of("TOTP"), texts(refused.path("acceptedProofs")));
            assertRefused(
                    DocumentRefusal.SIGNING_PROOF_NOT_ACCEPTED,
                    complete(client, guarded, fieldOf(guardedRequest), refused, "PASSWORD", null, PASSWORD));
        });
    }

    @Test
    void aWrongPasswordSignsNothing() throws IOException {
        var signer = member("Willi", "Falsch", true);
        accountRepo.createCredential(signer.accountId(), hasher.hash(PASSWORD));
        var request = ask(signer, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> assertRefused(
                DocumentRefusal.SIGNING_PASSWORD_WRONG,
                complete(
                        client,
                        signer,
                        fieldOf(request),
                        start(client, signer, fieldOf(request), null),
                        "PASSWORD",
                        null,
                        "nicht")));
        assertEquals(FieldState.OPEN, fieldState(request));
    }

    @Test
    void aBackupCodeAndAnotherDeviceNeverConfirmASignature() throws IOException {
        var signer = member("Bea", "Backup", true);
        enrolAuthenticatorApp(signer.accountId());
        var codes = twoFactorRepo.createFactor(signer.accountId(), TwoFactorKind.BACKUP_CODES, "Codes");
        twoFactorRepo.createBackupCode(codes.id(), "code-hash");
        var request = ask(signer, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            for (String proof : List.of("BACKUP_CODE", "ANOTHER_DEVICE")) {
                JsonNode started = start(client, signer, fieldOf(request), null);
                assertEquals(List.of("TOTP"), texts(started.path("acceptedProofs")));
                assertRefused(
                        DocumentRefusal.SIGNING_PROOF_NOT_ACCEPTED,
                        complete(client, signer, fieldOf(request), started, proof, null, "12345678"));
            }
        });
        assertEquals(FieldState.OPEN, fieldState(request));
    }

    @Test
    void anExpiredStartCannotBeCompleted() throws IOException {
        var signer = member("Eva", "Spaet", true);
        enrolAuthenticatorApp(signer.accountId());
        var request = ask(signer, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            JsonNode started = start(client, signer, fieldOf(request), null);
            query("""
                    UPDATE webauthn_challenge SET expires_at = now() - INTERVAL '1 minute'
                    WHERE purpose = 'SIGNING' AND account_id = :account;""").single(call().bind("account", signer.accountId())).update();

            assertRefused(
                    DocumentRefusal.SIGNING_START_EXPIRED,
                    complete(client, signer, fieldOf(request), started, "TOTP", null, RIGHT_CODE));
        });
        assertEquals(FieldState.OPEN, fieldState(request));
    }

    @Test
    void aStartIsSpentOnceWhetherItWasRefusedOrSigned() throws IOException {
        var signer = member("Rudi", "Einmal", true);
        enrolAuthenticatorApp(signer.accountId());
        var request = ask(signer, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            JsonNode refused = start(client, signer, fieldOf(request), null);
            assertRefused(
                    DocumentRefusal.SIGNING_CODE_WRONG,
                    complete(client, signer, fieldOf(request), refused, "TOTP", null, WRONG_CODE));
            assertRefused(
                    DocumentRefusal.SIGNING_START_UNKNOWN,
                    complete(client, signer, fieldOf(request), refused, "TOTP", null, RIGHT_CODE));

            JsonNode signed = start(client, signer, fieldOf(request), null);
            complete(client, signer, fieldOf(request), signed, "TOTP", null, RIGHT_CODE);
            assertRefused(
                    DocumentRefusal.SIGNING_START_UNKNOWN,
                    complete(client, signer, fieldOf(request), signed, "TOTP", null, RIGHT_CODE));
        });
    }

    @Test
    void aStartCanOnlyBeCompletedByItsAccountAndForItsField() throws IOException {
        var owner = member("Olga", "Eigen", true);
        enrolAuthenticatorApp(owner.accountId());
        var other = member("Otto", "Fremd", true);
        enrolAuthenticatorApp(other.accountId());
        var ownRequest = ask(owner, SignatureRole.PARTICIPANT);
        var otherRequest = ask(other, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            JsonNode stolen = start(client, owner, fieldOf(ownRequest), null);
            assertRefused(
                    DocumentRefusal.SIGNING_START_UNKNOWN,
                    complete(client, other, fieldOf(ownRequest), stolen, "TOTP", null, RIGHT_CODE));

            JsonNode elsewhere = start(client, other, fieldOf(otherRequest), null);
            assertRefused(
                    DocumentRefusal.SIGNING_START_UNKNOWN,
                    complete(client, other, fieldOf(ownRequest), elsewhere, "TOTP", null, RIGHT_CODE));
        });
        assertEquals(FieldState.OPEN, fieldState(ownRequest));
    }

    @Test
    void aFieldIsSignedOnlyByThoseItAsksFor() throws IOException {
        var owner = member("Ina", "Inhaberin", true);
        var stranger = member("Sven", "Fremder", true);
        accountRepo.createCredential(stranger.accountId(), hasher.hash(PASSWORD));
        var request = ask(owner, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            assertRefused(
                    DocumentRefusal.SIGNING_FIELD_NOT_YOURS,
                    client.post(
                            startPath(fieldOf(request)),
                            body("{}"),
                            harness.as(signedIn(stranger, StationPermission.LOGIN))));
            assertRefused(
                    DocumentRefusal.SIGNING_FIELD_NOT_YOURS,
                    client.post(
                            startPath(Integer.MAX_VALUE),
                            body("{}"),
                            harness.as(signedIn(stranger, StationPermission.LOGIN))));
            assertTrue(
                    json(client.get(PREFIX + "/signing/open", harness.as(signedIn(stranger, StationPermission.LOGIN))))
                            .isEmpty());
        });
    }

    @Test
    void theSignerReadsTheFieldAndExactlyTheFrozenDocument() throws IOException {
        var signer = member("Lea", "Leserin", true);
        var request = ask(signer, SignatureRole.PARTICIPANT);
        int fieldId = fieldOf(request);

        harness.run((server, client) -> {
            var asSigner = harness.as(signedIn(signer, StationPermission.LOGIN));
            JsonNode field = json(client.get(fieldPath(fieldId), asSigner));
            assertEquals(fieldId, field.path("fieldId").asInt());
            assertEquals("Einverstaendnis", field.path("documentTitle").asString());
            assertEquals(
                    memberNameResolver.official(signer.id()),
                    field.path("memberName").asString());
            assertEquals("PARTICIPANT", field.path("role").asString());
            assertEquals("ACCOUNT_HOLDER", field.path("capacity").asString());
            assertEquals(
                    requestRepo.fieldsOf(request.id()).getFirst().statement(),
                    field.path("statement").asString());

            HttpResponse<byte[]> document = fetchBytes(server.port(), fieldPath(fieldId) + "/document", asSigner);
            assertEquals(200, document.statusCode());
            assertTrue(
                    document.headers().firstValue("Content-Type").orElseThrow().startsWith("application/pdf"));
            assertTrue(document.headers()
                    .firstValue("Content-Disposition")
                    .orElseThrow()
                    .startsWith("inline"));
            assertEquals(request.contentSha256(), Sha256.hex(document.body()));

            assertEquals(
                    "Einverstaendnis",
                    json(client.get(PREFIX + "/signing/open", asSigner))
                            .get(0)
                            .path("documentTitle")
                            .asString());
        });
    }

    @Test
    void theFieldAndItsDocumentAreReadOnlyByThoseItAsksForWhileItWaits() throws IOException {
        var owner = member("Olga", "Offen", true);
        var stranger = member("Siggi", "Seitlich", true);
        enrolAuthenticatorApp(owner.accountId());
        var request = ask(owner, SignatureRole.PARTICIPANT);
        int fieldId = fieldOf(request);

        harness.run((server, client) -> {
            var asStranger = harness.as(signedIn(stranger, StationPermission.LOGIN));
            assertRefused(DocumentRefusal.SIGNING_FIELD_NOT_YOURS, client.get(fieldPath(fieldId), asStranger));
            assertRefused(
                    DocumentRefusal.SIGNING_FIELD_NOT_YOURS, client.get(fieldPath(fieldId) + "/document", asStranger));
            assertRefused(
                    DocumentRefusal.SIGNING_FIELD_NOT_YOURS, client.get(fieldPath(Integer.MAX_VALUE), asStranger));

            complete(client, owner, fieldId, start(client, owner, fieldId, null), "TOTP", null, RIGHT_CODE);

            var asOwner = harness.as(signedIn(owner, StationPermission.LOGIN));
            assertRefused(DocumentRefusal.SIGNING_FIELD_NOT_OPEN, client.get(fieldPath(fieldId), asOwner));
            assertRefused(
                    DocumentRefusal.SIGNING_FIELD_NOT_OPEN, client.get(fieldPath(fieldId) + "/document", asOwner));
            assertRefused(
                    DocumentRefusal.SIGNING_FIELD_NOT_YOURS,
                    client.get(fieldPath(fieldId), asStranger),
                    "a stranger learns nothing about where the field stands");
        });
        assertEquals(FieldState.SIGNED, fieldState(request));
    }

    @Test
    void aSecondSignerReadsAndSignsTheFrozenDocumentAfterTheFirstWasSealed() throws IOException {
        var child = member("Lina", "Lesend", true);
        var guardian = member("Gabriel", "Danach", true);
        accountRepo.createCredential(child.accountId(), hasher.hash(PASSWORD));
        accountRepo.createCredential(guardian.accountId(), hasher.hash(PASSWORD));
        stationMemberRepo.addManager(guardian.id(), child.id(), manager.id());
        var request = ask(child, SignatureRole.PARTICIPANT, SignatureRole.GUARDIAN_1);
        int childField = fieldNamed(request, "participant");
        int guardianField = fieldNamed(request, "guardian1");

        harness.run((server, client) -> {
            complete(client, child, childField, start(client, child, childField, null), "PASSWORD", null, PASSWORD);
            var document = filedDocument(request);
            assertTrue(document.sealed());
            byte[] firstVersion = documents.read(document).orElseThrow();
            assertNotEquals(request.contentSha256(), Sha256.hex(firstVersion));

            var asGuardian = harness.as(signedIn(guardian, StationPermission.LOGIN));
            HttpResponse<byte[]> shown = fetchBytes(server.port(), fieldPath(guardianField) + "/document", asGuardian);
            assertEquals(200, shown.statusCode());
            assertEquals(request.contentSha256(), Sha256.hex(shown.body()));

            JsonNode completed = json(complete(
                    client,
                    guardian,
                    guardianField,
                    start(client, guardian, guardianField, null),
                    "PASSWORD",
                    null,
                    PASSWORD));
            assertEquals("SIGNED", completed.path("state").asString());
            assertEquals("COMPLETE", completed.path("requestState").asString());
        });

        var document = filedDocument(request);
        var versions = documents.sealedVersions(document);
        assertEquals(2, versions.size());
        var sealedHashes = evidenceRepo.evidenceOf(request.id()).stream()
                .map(StoredEvidence::sealedSha256)
                .toList();
        assertEquals(List.of(versions.getLast().sha256(), versions.getFirst().sha256()), sealedHashes);
        assertEquals(
                versions.getFirst().sha256(),
                Sha256.hex(documents.read(document).orElseThrow()));
    }

    @Test
    void aSealThatFailsLeavesTheActSignedAndTheNextActSealsBoth() throws IOException {
        var child = member("Mia", "Misslingen", true);
        var guardian = member("Moritz", "Nachher", true);
        accountRepo.createCredential(child.accountId(), hasher.hash(PASSWORD));
        accountRepo.createCredential(guardian.accountId(), hasher.hash(PASSWORD));
        stationMemberRepo.addManager(guardian.id(), child.id(), manager.id());
        var request = ask(child, SignatureRole.PARTICIPANT, SignatureRole.GUARDIAN_1);
        int childField = fieldNamed(request, "participant");
        int guardianField = fieldNamed(request, "guardian1");
        var failing = mock(SigningStateSealer.class);
        when(failing.sealLatest(anyInt())).thenThrow(new IllegalStateException("The store is down"));

        harness = serving(actsSealingWith.apply(failing));
        harness.run((server, client) -> {
            JsonNode completed = json(complete(
                    client, child, childField, start(client, child, childField, null), "PASSWORD", null, PASSWORD));
            assertEquals("SIGNED", completed.path("state").asString());
        });
        assertEquals(
                FieldState.SIGNED, requestRepo.fieldsOf(request.id()).getFirst().state());
        assertNull(evidenceRepo.evidenceOf(request.id()).getFirst().sealedSha256());
        assertFalse(filedDocument(request).sealed());

        harness = serving(acts);
        harness.run((server, client) -> complete(
                client,
                guardian,
                guardianField,
                start(client, guardian, guardianField, null),
                "PASSWORD",
                null,
                PASSWORD));

        var versions = documents.sealedVersions(filedDocument(request));
        assertEquals(1, versions.size());
        assertTrue(evidenceRepo.evidenceOf(request.id()).stream()
                .allMatch(evidence -> versions.getFirst().sha256().equals(evidence.sealedSha256())));
    }

    @Test
    void whatASignerTypesIsCheckedBeforeTheActStarts() throws IOException {
        var signer = member("Fritz", "Feld", true);
        accountRepo.createCredential(signer.accountId(), hasher.hash(PASSWORD));
        var request = ask(signer, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            assertRefused(
                    DocumentRefusal.SIGNING_ENTRY_EMPTY,
                    startRaw(client, signer, fieldOf(request), "[{\"field\":\"Telefon\",\"value\":\" \"}]"));
            assertRefused(
                    DocumentRefusal.SIGNING_ENTRY_TWICE,
                    startRaw(
                            client,
                            signer,
                            fieldOf(request),
                            "[{\"field\":\"Telefon\",\"value\":\"1\"},{\"field\":\"Telefon\",\"value\":\"2\"}]"));
            assertRefused(
                    DocumentRefusal.SIGNING_ENTRY_UNNAMED,
                    startRaw(client, signer, fieldOf(request), "[{\"value\":\"1\"}]"));
            assertRefused(
                    DocumentRefusal.SIGNING_ENTRY_TOO_LONG,
                    startRaw(
                            client,
                            signer,
                            fieldOf(request),
                            "[{\"field\":\"Notiz\",\"value\":\"" + "x".repeat(501) + "\"}]"));
        });
    }

    @Test
    void aGuardianSignsForTheChildAndTheChildSignsThroughTheGuardiansAccount() throws IOException {
        var child = member("Kim", "Kind", false);
        var guardian = member("Gerda", "Vormund", true);
        accountRepo.createCredential(guardian.accountId(), hasher.hash(PASSWORD));
        stationMemberRepo.addManager(guardian.id(), child.id(), manager.id());
        var request = ask(child, SignatureRole.PARTICIPANT, SignatureRole.GUARDIAN_1);

        harness.run((server, client) -> {
            JsonNode open =
                    json(client.get(PREFIX + "/signing/open", harness.as(signedIn(guardian, StationPermission.LOGIN))));
            assertEquals(2, open.size());
            var signed = new ArrayList<String>();
            for (JsonNode field : open) {
                int fieldId = field.path("fieldId").asInt();
                assertEquals(child.id(), field.path("memberId").asInt());
                JsonNode started = start(client, guardian, fieldId, null);
                assertEquals(
                        memberNameResolver.official(child.id()),
                        started.path("memberName").asString());
                complete(client, guardian, fieldId, started, "PASSWORD", null, PASSWORD);
                signed.add(field.path("capacity").asString());
            }
            assertEquals(Set.of("MEMBER_THROUGH_ACCOUNT", "GUARDIAN"), Set.copyOf(signed));
        });

        var evidence = evidenceRepo.evidenceOf(request.id());
        assertEquals(
                Set.of(SignerCapacity.MEMBER_THROUGH_ACCOUNT, SignerCapacity.GUARDIAN),
                Set.copyOf(evidence.stream()
                        .map(stored -> stored.evidence().act().signer().capacity())
                        .toList()));
        assertTrue(evidence.stream().allMatch(stored -> stored.guardianLink() != null));
        assertEquals(
                RequestState.COMPLETE,
                requestRepo.findById(request.id()).orElseThrow().state());
    }

    @Test
    void anActWithoutAPictureSignsWithTheSavedOneOrIsRefusedWhereNoneIsSaved() throws IOException {
        var signer = member("Paula", "Pinsel", true);
        accountRepo.createCredential(signer.accountId(), hasher.hash(PASSWORD));
        var request = ask(signer, SignatureRole.PARTICIPANT);
        int fieldId = fieldOf(request);

        harness.run((server, client) -> {
            assertRefused(
                    DocumentRefusal.SIGNING_MARK_MISSING,
                    complete(
                            client,
                            signer,
                            fieldId,
                            start(client, signer, fieldId, null),
                            "PASSWORD",
                            null,
                            PASSWORD,
                            null,
                            false));
            assertEquals(FieldState.OPEN, fieldState(request));

            var saved = signatureImages.save(signer.accountId(), TestSignatures.drawn(), SignatureImageSource.DRAWN);
            complete(
                    client,
                    signer,
                    fieldId,
                    start(client, signer, fieldId, null),
                    "PASSWORD",
                    null,
                    PASSWORD,
                    null,
                    false);
            var mark = evidenceRepo.marksOf(request.id()).get(fieldId);
            assertEquals(saved.imageSha256(), mark.sha256());
            assertEquals(ActPictureSource.SAVED, mark.source());
        });
        assertEquals(FieldState.SIGNED, fieldState(request));
    }

    @Test
    void aPictureMadeForTheActIsKeptOnlyWhereAskedAndNeverForAChildThroughTheAccount() throws IOException {
        var child = member("Karla", "Kritzel", false);
        var guardian = member("Georg", "Griffel", true);
        accountRepo.createCredential(guardian.accountId(), hasher.hash(PASSWORD));
        stationMemberRepo.addManager(guardian.id(), child.id(), manager.id());
        var request = ask(child, SignatureRole.PARTICIPANT, SignatureRole.GUARDIAN_1);
        int childField = fieldNamed(request, "participant");
        int guardianField = fieldNamed(request, "guardian1");
        byte[] childPicture = TestSignatures.photographed();

        harness.run((server, client) -> {
            assertRefused(
                    DocumentRefusal.SIGNATURE_IMAGE_EMPTY,
                    complete(
                            client,
                            guardian,
                            childField,
                            start(client, guardian, childField, null),
                            "PASSWORD",
                            null,
                            PASSWORD,
                            TestSignatures.empty(),
                            false));
            complete(
                    client,
                    guardian,
                    childField,
                    start(client, guardian, childField, null),
                    "PASSWORD",
                    null,
                    PASSWORD,
                    childPicture,
                    true);
            assertFalse(signatureImages.settings(guardian.accountId()).hasImage());

            complete(
                    client,
                    guardian,
                    guardianField,
                    start(client, guardian, guardianField, null),
                    "PASSWORD",
                    null,
                    PASSWORD,
                    TestSignatures.drawn(),
                    true);
        });

        var kept = signatureImages.settings(guardian.accountId());
        assertTrue(kept.hasImage());
        assertEquals(SignatureImageSource.DRAWN, kept.imageSource());
        var marks = evidenceRepo.marksOf(request.id());
        assertEquals(kept.imageSha256(), marks.get(guardianField).sha256());
        assertEquals(ActPictureSource.DRAWN, marks.get(guardianField).source());
        assertEquals(
                Sha256.hex(SignatureImages.clean(childPicture).png()),
                marks.get(childField).sha256());
        assertEquals(ActPictureSource.DRAWN, marks.get(childField).source());

        var sealed = documents.read(filedDocument(request)).orElseThrow();
        assertEquals(List.of(), SignatureFields.unsigned(sealed));
    }

    @Test
    void wrongCodesCountTowardTheStepUpLimit() throws IOException {
        var signer = member("Gabi", "Grind", true);
        enrolAuthenticatorApp(signer.accountId());
        var request = ask(signer, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            for (int attempt = 0; attempt < 10; attempt++) {
                assertRefused(
                        DocumentRefusal.SIGNING_CODE_WRONG,
                        complete(
                                client,
                                signer,
                                fieldOf(request),
                                start(client, signer, fieldOf(request), null),
                                "TOTP",
                                null,
                                WRONG_CODE));
            }
            assertRefused(
                    DocumentRefusal.SIGNING_CONFIRMATION_TOO_OFTEN,
                    complete(
                            client,
                            signer,
                            fieldOf(request),
                            start(client, signer, fieldOf(request), null),
                            "TOTP",
                            null,
                            RIGHT_CODE));
        });
        assertEquals(FieldState.OPEN, fieldState(request));
    }

    @Test
    void theDevelopmentCodeNeverConfirmsASignature() throws IOException {
        var signer = member("Dev", "Kode", true);
        enrolAuthenticatorApp(signer.accountId());
        var request = ask(signer, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> assertRefused(
                DocumentRefusal.SIGNING_CODE_WRONG,
                complete(
                        client,
                        signer,
                        fieldOf(request),
                        start(client, signer, fieldOf(request), null),
                        "TOTP",
                        null,
                        DEVELOPMENT_CODE)));
        assertEquals(FieldState.OPEN, fieldState(request));
        assertTrue(requestRepo.fieldsOf(request.id()).stream().noneMatch(field -> field.state() == FieldState.SIGNED));
    }

    @Test
    void anAccountHoldsOnlySoManyUnfinishedStarts() throws IOException {
        var signer = member("Sammy", "Sammler", true);
        enrolAuthenticatorApp(signer.accountId());
        var request = ask(signer, SignatureRole.PARTICIPANT);

        harness.run((server, client) -> {
            JsonNode last = null;
            for (int started = 0; started < SigningStarts.MAX_OPEN_PER_ACCOUNT; started++) {
                last = start(client, signer, fieldOf(request), null);
            }
            assertRefused(DocumentRefusal.SIGNING_STARTS_TOO_MANY, startRaw(client, signer, fieldOf(request), null));

            complete(client, signer, fieldOf(request), last, "TOTP", null, WRONG_CODE);
            assertEquals(200, startRaw(client, signer, fieldOf(request), null).code(), "a spent start frees its place");
        });
    }

    /**
     * A passkey or security key answer completes only the start it answered, only where it was given on this
     * installation's site, and only where the key checked who holds it. Each refusal spends its start and
     * signs nothing.
     */
    @Test
    void aKeyAnswersOnlyItsOwnStartOnThisSiteWithTheHolderChecked() throws IOException {
        var signer = member("Anke", "Antwort", true);
        TestAuthenticator key = enrolSecurityKey(signer.accountId());
        var request = ask(signer, SignatureRole.PARTICIPANT);
        int fieldId = fieldOf(request);

        harness.run((server, client) -> {
            JsonNode first = start(client, signer, fieldId, null);
            JsonNode second = start(client, signer, fieldId, null);
            assertRefused(
                    DocumentRefusal.SIGNING_CHALLENGE_MISMATCH,
                    complete(client, signer, fieldId, first, "SECURITY_KEY", key.sign(optionsOf(second)), null));

            JsonNode elsewhere = start(client, signer, fieldId, null);
            assertRefused(
                    DocumentRefusal.SIGNING_FOREIGN_ORIGIN,
                    complete(
                            client,
                            signer,
                            fieldId,
                            elsewhere,
                            "SECURITY_KEY",
                            givenOn(key.sign(optionsOf(elsewhere)), "https://evil.test"),
                            null));

            JsonNode unchecked = start(client, signer, fieldId, null);
            assertRefused(
                    DocumentRefusal.SIGNING_NOT_USER_VERIFIED,
                    complete(
                            client,
                            signer,
                            fieldId,
                            unchecked,
                            "SECURITY_KEY",
                            key.sign(optionsOf(unchecked), false),
                            null));
        });
        assertEquals(FieldState.OPEN, fieldState(request));
        assertTrue(evidenceRepo.evidenceOf(request.id()).isEmpty());
    }

    /**
     * The document is read again when the act completes: one whose file was replaced after the start is
     * not signed, and neither is one that was deleted in between.
     */
    @Test
    void aDocumentReplacedOrDeletedAfterTheStartIsNotSigned() throws IOException {
        var signer = member("Rita", "Revision", true);
        enrolAuthenticatorApp(signer.accountId());
        var replaced = ask(signer, SignatureRole.PARTICIPANT);
        var deleted = ask(signer, SignatureRole.PARTICIPANT);
        byte[] otherFile = pdfWith("participant");

        harness.run((server, client) -> {
            JsonNode beforeReplacing = start(client, signer, fieldOf(replaced), null);
            storage.store(
                    new StorageScope.Station(station.id(), stationRepo.requireUid(station.id())),
                    StorageCategory.MEMBER_DOCUMENTS,
                    replaced.documentId() + "/file",
                    new Variant("content"),
                    otherFile,
                    "application/pdf");
            assertRefused(
                    DocumentRefusal.SIGNING_DOCUMENT_CHANGED,
                    complete(client, signer, fieldOf(replaced), beforeReplacing, "TOTP", null, RIGHT_CODE));

            JsonNode beforeDeleting = start(client, signer, fieldOf(deleted), null);
            documents.delete(filedDocument(deleted));
            assertRefused(
                    DocumentRefusal.SIGNING_DOCUMENT_GONE,
                    complete(client, signer, fieldOf(deleted), beforeDeleting, "TOTP", null, RIGHT_CODE));
        });
        assertEquals(FieldState.OPEN, fieldState(replaced));
        assertEquals(FieldState.OPEN, fieldState(deleted));
        assertTrue(evidenceRepo.evidenceOf(replaced.id()).isEmpty());
        assertTrue(evidenceRepo.evidenceOf(deleted.id()).isEmpty());
    }

    /**
     * A picture past the server's usual megabyte for a body is taken, up to the largest picture; a larger
     * picture is refused as too large, and so is a confirmation too large to read.
     */
    @Test
    void aPictureIsTakenUpToTheLargestPictureAndRefusedBeyond() throws IOException {
        var signer = member("Fritz", "Foto", true);
        enrolAuthenticatorApp(signer.accountId());
        var request = ask(signer, SignatureRole.PARTICIPANT);
        int fieldId = fieldOf(request);
        byte[] large = TestSignatures.uncompressed();
        assertTrue(large.length > 1_000_000, "the picture alone passes the usual limit");

        harness.run((server, client) -> {
            assertRefused(
                    DocumentRefusal.SIGNATURE_IMAGE_TOO_LARGE,
                    complete(
                            client,
                            signer,
                            fieldId,
                            start(client, signer, fieldId, null),
                            "TOTP",
                            null,
                            RIGHT_CODE,
                            new byte[SignatureImages.MAX_BYTES + 1],
                            false));
            assertRefused(
                    DocumentRefusal.SIGNATURE_IMAGE_TOO_LARGE,
                    complete(
                            client,
                            signer,
                            fieldId,
                            start(client, signer, fieldId, null),
                            "TOTP",
                            null,
                            RIGHT_CODE,
                            new byte[SigningRoutes.MAX_COMPLETE_BYTES / 4 * 3],
                            false));

            Response signed = complete(
                    client,
                    signer,
                    fieldId,
                    start(client, signer, fieldId, null),
                    "TOTP",
                    null,
                    RIGHT_CODE,
                    large,
                    false);
            assertEquals(200, signed.code(), () -> signed.body().string());
        });
        assertEquals(FieldState.SIGNED, fieldState(request));
        assertEquals(
                Sha256.hex(SignatureImages.clean(large).png()),
                evidenceRepo.marksOf(request.id()).get(fieldId).sha256());
    }

    /** The request options a start handed out for a passkey or security key. */
    private static String optionsOf(JsonNode started) {
        return started.path("webAuthnOptionsJson").asString();
    }

    /** A key's answer as if the browser had given it on another site. */
    private static String givenOn(String credential, String origin) {
        var answer = (ObjectNode) body(credential);
        var response = (ObjectNode) answer.path("response");
        var clientData = (ObjectNode)
                body(new String(decode(response.path("clientDataJSON").asString()), StandardCharsets.UTF_8));
        clientData.put("origin", origin);
        response.put(
                "clientDataJSON",
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(clientData.toString().getBytes(StandardCharsets.UTF_8)));
        return answer.toString();
    }

    /** An authenticator app on the account, whose current code the test's code service knows as the right one. */
    private static void enrolAuthenticatorApp(int accountId) {
        var factor = twoFactorRepo.createFactor(accountId, TwoFactorKind.TOTP, "App");
        twoFactorRepo.createTotp(factor.id(), new byte[] {1}, (short) 1, (short) 6, (short) 30, "SHA1");
    }

    private JsonNode start(HttpClient client, StationMember signer, int fieldId, String entries) {
        Response response = startRaw(client, signer, fieldId, entries);
        assertEquals(200, response.code(), () -> response.body().string());
        return json(response);
    }

    private Response startRaw(HttpClient client, StationMember signer, int fieldId, String entries) {
        String content = entries == null ? "{}" : "{\"entries\":" + entries + "}";
        return client.post(startPath(fieldId), body(content), harness.as(signedIn(signer, StationPermission.LOGIN)));
    }

    private Response complete(
            HttpClient client,
            StationMember signer,
            int fieldId,
            JsonNode started,
            String proof,
            String credentialJson,
            String secret) {
        return complete(client, signer, fieldId, started, proof, credentialJson, secret, TestSignatures.drawn(), false);
    }

    /**
     * Completes an act with a signature picture made for it, or with the saved one where {@code picture} is
     * null.
     */
    private Response complete(
            HttpClient client,
            StationMember signer,
            int fieldId,
            JsonNode started,
            String proof,
            String credentialJson,
            String secret,
            byte[] picture,
            boolean keep) {
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        object.put("startToken", started.path("startToken").asString());
        object.put("proof", proof);
        object.put("credentialJson", credentialJson);
        object.put("secret", secret);
        if (picture != null) {
            object.put("signatureImage", Base64.getEncoder().encodeToString(picture));
            object.put("signatureSource", "DRAWN");
            object.put("keepSignature", keep);
        }
        return client.post(
                PREFIX + "/signing/fields/" + fieldId + "/complete",
                object,
                harness.as(signedIn(signer, StationPermission.LOGIN)));
    }

    private static String startPath(int fieldId) {
        return fieldPath(fieldId) + "/start";
    }

    private static String fieldPath(int fieldId) {
        return PREFIX + "/signing/fields/" + fieldId;
    }

    /**
     * Sends a GET past the harness's client, which reads every body as text, so a PDF arrives byte for
     * byte, with the headers the harness would send for the session.
     */
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

    private static SignatureRequest ask(StationMember member, SignatureRole... roles) throws IOException {
        var names =
                Stream.of(roles).flatMap(role -> role.fieldNames(1).stream()).toArray(String[]::new);
        var generation = generated(member, names);
        return requests.request(managing(), generation.id());
    }

    private static int fieldOf(SignatureRequest request) {
        return requestRepo.fieldsOf(request.id()).getFirst().id();
    }

    private static int fieldNamed(SignatureRequest request, String fieldName) {
        return requestRepo.fieldsOf(request.id()).stream()
                .filter(field -> field.fieldName().equals(fieldName))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static Document filedDocument(SignatureRequest request) {
        return memberDocumentRepo
                .findById(Objects.requireNonNull(request.documentId()))
                .orElseThrow();
    }

    private static FieldState fieldState(SignatureRequest request) {
        return requestRepo.fieldsOf(request.id()).getFirst().state();
    }

    private static StoredEvidence onlyEvidence(SignatureRequest request) {
        var evidence = evidenceRepo.evidenceOf(request.id());
        assertEquals(1, evidence.size());
        return evidence.getFirst();
    }

    private static DocumentGeneration generated(StationMember member, String... fieldNames) throws IOException {
        byte[] pdf = pdfWith(fieldNames);
        var document = documents.store(
                member.stationId(),
                List.of(member.id()),
                "Einverstaendnis",
                "e.pdf",
                "application/pdf",
                pdf,
                false,
                true,
                Uploader.nobody(),
                List.of());
        return generations.log(
                new DocumentGeneration(
                        0,
                        member.stationId(),
                        template,
                        1,
                        member.id(),
                        null,
                        Instant.now(),
                        false,
                        document.id(),
                        Sha256.hex(pdf),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false),
                List.of());
    }

    private static byte[] pdfWith(String... fieldNames) throws IOException {
        try (var pdf = new PDDocument()) {
            pdf.getDocumentInformation().setTitle(UUID.randomUUID().toString());
            var page = new PDPage();
            pdf.addPage(page);
            float x = 40;
            for (String name : fieldNames) {
                SignatureFields.add(pdf, page, new PDRectangle(x, 60, 120, 40), name);
                x += 130;
            }
            return PdfFiles.save(pdf);
        }
    }

    private static StationMember member(String first, String last, boolean login) {
        var account = accountRepo.create(email(), first, last);
        var member = stationMemberRepo.create(station.id(), account.id());
        if (login) stationMemberRepo.grantPermission(member.id(), loginPermission);
        return member;
    }

    private static StationSession managing() {
        return stationSession(manager, StationPermission.DOCUMENT_EDIT_MEMBER, StationPermission.DOCUMENT_READ_MEMBER);
    }

    private static TestAuthenticator enrolSecurityKey(int account) {
        var key = new TestAuthenticator();
        var start = securityKeys.startRegistration(account, "key@test.com", "Key");
        securityKeys
                .finishRegistration(
                        account, start.challengeToken(), key.register(start.optionsJson()), "Key", null, null)
                .orElseThrow();
        return key;
    }

    private static TestAuthenticator enrolPasskey(int account) {
        var passkey = new TestAuthenticator();
        var start = passkeys.startCreation(account, "passkey@test.com", "Passkey");
        passkeys.finishCreation(
                        account, start.challengeToken(), passkey.register(start.optionsJson()), "Passkey", null, null)
                .orElseThrow();
        return passkey;
    }

    private static RelyingParties relyingParties(WebAuthnSettings settings) throws Exception {
        var api = new Api();
        Field baseUrl = Api.class.getDeclaredField("baseUrl");
        baseUrl.setAccessible(true);
        baseUrl.set(api, "https://ember.test");
        var store = new WebAuthnCredentialStore(twoFactorRepo);
        return WebAuthnRelyingPartyFactory.build(
                settings, api, store, new SecondFactorCredentialStore(twoFactorRepo, store));
    }

    private static AuthService authService() {
        var hibp = mock(HibpClient.class);
        when(hibp.isPwned(anyString())).thenReturn(false);
        var locales = new MailLocaleService(accountRepo, new ApplicationSettingRepository());
        var mail = mock(EmailService.class);
        return new AuthService(
                accountRepo,
                new AccountEmailService(accountRepo, locales, mail),
                mock(MailConfirmationPolicy.class),
                locales,
                new MailRecipientService(accountRepo, stationMemberRepo),
                registrationCodeRepo,
                stationMemberRepo,
                newGroupMemberships(),
                hasher,
                mail,
                new Auth(),
                new Demo(),
                hibp,
                mock(BreachCheckWorker.class),
                twoFactorRepo,
                new TrustedDeviceService(
                        twoFactorRepo, TokenHasher.forTesting("signing-pepper"), new TwoFactorSettings()),
                passkeyModeService);
    }

    private static DocumentTemplateDraft draft() {
        return new DocumentTemplateDraft(
                "Einverstaendnis",
                "Einverstaendnis",
                "Einverstaendnis",
                List.of(),
                false,
                true,
                true,
                false,
                false,
                30,
                RestrictionMode.AND,
                DocumentLanguage.DE,
                null,
                null,
                LetterContent.blank());
    }

    private static List<String> texts(JsonNode array) {
        var texts = new ArrayList<String>();
        array.forEach(node -> texts.add(node.asString()));
        return texts;
    }

    private static byte[] decode(String base64Url) {
        return Base64.getUrlDecoder().decode(base64Url);
    }

    private static String email() {
        return "signing-route-" + NAMES.incrementAndGet() + "-" + System.nanoTime() + "@test.com";
    }

    private static void assertRefused(Refusal refusal, Response response) {
        assertEquals(refusal, refusalOf(response));
    }

    private static void assertRefused(Refusal refusal, Response response, String message) {
        assertEquals(refusal, refusalOf(response), message);
    }
}

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
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.SealedDocument;
import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.SigningStatements;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.feature.signing.service.InEmberSignatureProvider;
import dev.chojo.ember.feature.signing.service.PdfSealer;
import dev.chojo.ember.feature.signing.service.SignatureFieldService;
import dev.chojo.ember.feature.signing.service.SignatureRequestService;
import dev.chojo.ember.feature.signing.service.SignerNames;
import dev.chojo.ember.feature.signing.service.SignerResolver;
import dev.chojo.ember.feature.signing.service.SigningActService;
import dev.chojo.ember.feature.signing.service.SigningAssertions;
import dev.chojo.ember.feature.signing.service.SigningGuards;
import dev.chojo.ember.feature.signing.service.SigningStarts;
import dev.chojo.ember.feature.signing.service.StationSigningKeys;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
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
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
 * their account, and wrong codes count toward the step-up limit. Sealing is a stand-in; it has tests of
 * its own.
 */
class SigningRoutesTest extends RepositoryTestBase {
    private static final String RIGHT_CODE = "424242";
    private static final String WRONG_CODE = "111111";
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

    private static DocumentService documents;
    private static SignatureRequestService requests;
    private static SigningActService acts;
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
        documents = newDocumentService(new StorageService(new StorageBackendResolver(backend), backend));
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
                guards);
        var fields = new SignatureFieldService(
                requestRepo, evidenceRepo, requests, guards, guardianPolicy, memberNameResolver);

        var settings = new WebAuthnSettings();
        RelyingParties parties = relyingParties(settings);
        var challenges = new WebAuthnChallengeRepository(TokenHasher.forTesting("signing-route-pepper"));
        var audit = new TwoFactorAuditService(twoFactorRepo);
        securityKeys = new WebAuthnService(parties, twoFactorRepo, audit, challenges, settings);
        passkeys = new PasskeyService(parties, twoFactorRepo, audit, challenges, settings);
        var totp = mock(TotpService.class);
        when(totp.isDevCode(RIGHT_CODE)).thenReturn(true);
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
        acts = new SigningActService(
                requestRepo,
                requests,
                fields,
                memberDocumentRepo,
                documents,
                new InEmberSignatureProvider(twoFactor, assertions, names, sealingKeys(), sealer()),
                new SigningStarts(challenges),
                assertions,
                names,
                twoFactor,
                authService());

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
        harness = RouteHarness.serving(new SigningRoutes(acts, requests, new AuthRateLimiter(Clock.systemUTC())))
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
        twoFactorRepo.createFactor(signer.accountId(), TwoFactorKind.TOTP, "App");
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
        twoFactorRepo.createFactor(guarded.accountId(), TwoFactorKind.TOTP, "App");
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
        twoFactorRepo.createFactor(signer.accountId(), TwoFactorKind.TOTP, "App");
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
        twoFactorRepo.createFactor(signer.accountId(), TwoFactorKind.TOTP, "App");
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
        twoFactorRepo.createFactor(signer.accountId(), TwoFactorKind.TOTP, "App");
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
        twoFactorRepo.createFactor(owner.accountId(), TwoFactorKind.TOTP, "App");
        var other = member("Otto", "Fremd", true);
        twoFactorRepo.createFactor(other.accountId(), TwoFactorKind.TOTP, "App");
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
                    DocumentRefusal.SIGNING_FIELD_NOT_FOUND,
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
    void wrongCodesCountTowardTheStepUpLimit() throws IOException {
        var signer = member("Gabi", "Grind", true);
        twoFactorRepo.createFactor(signer.accountId(), TwoFactorKind.TOTP, "App");
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
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        object.put("startToken", started.path("startToken").asString());
        object.put("proof", proof);
        object.put("credentialJson", credentialJson);
        object.put("secret", secret);
        return client.post(
                PREFIX + "/signing/fields/" + fieldId + "/complete",
                object,
                harness.as(signedIn(signer, StationPermission.LOGIN)));
    }

    private static String startPath(int fieldId) {
        return PREFIX + "/signing/fields/" + fieldId + "/start";
    }

    private static SignatureRequest ask(StationMember member, SignatureRole... roles) throws IOException {
        var names =
                Stream.of(roles).flatMap(role -> role.fieldNames(1).stream()).toArray(String[]::new);
        var generation = generated(member, names);
        return requests.request(managing(), generation.id(), STATEMENTS);
    }

    private static int fieldOf(SignatureRequest request) {
        return requestRepo.fieldsOf(request.id()).getFirst().id();
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

    private static StationSigningKeys sealingKeys() {
        var keys = mock(StationSigningKeys.class);
        when(keys.forStation(anyInt()))
                .thenReturn(new SealingKey(mock(PrivateKey.class), List.of(mock(X509Certificate.class))));
        return keys;
    }

    private static PdfSealer sealer() {
        var sealer = mock(PdfSealer.class);
        when(sealer.seal(any(), any(), any())).thenReturn(SealedDocument.withoutTimestamp(new byte[] {1, 2, 3}));
        return sealer;
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
}

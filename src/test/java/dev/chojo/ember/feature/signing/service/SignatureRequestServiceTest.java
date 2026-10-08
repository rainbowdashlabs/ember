/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.documents.service.SealedDocumentService;
import dev.chojo.ember.feature.documents.service.SealedDocumentService.SealedFiling;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.service.pdf.PdfFiles;
import dev.chojo.ember.feature.generator.service.pdf.SignatureFields;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import dev.chojo.ember.feature.legal.service.GdprExportService;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.signing.entity.CompletedSigning;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.OpenSignature;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SealedDocument;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SignerEntry;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.entity.SigningStatements;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.entity.Variant;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.feature.twofactor.entity.CredentialKeyStamp;
import dev.chojo.ember.feature.twofactor.entity.KeyStampKind;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.sql.SqlSupport;
import dev.chojo.ember.util.sql.Transactions;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.util.PSQLException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Requests for signatures on generated documents: who each field asks for, how acts are recorded against
 * them and refused, how managers settle them, how a correction replaces a request, what a member is asked
 * to sign at home, what the data export carries, and how long all of it is kept.
 */
class SignatureRequestServiceTest extends RepositoryTestBase {
    private static final SigningStatements STATEMENTS =
            new SigningStatements("Ich stimme zu.", "Ich bin erziehungsberechtigt und stimme zu.");
    private static final AtomicInteger NAMES = new AtomicInteger();

    @TempDir
    static Path storageRoot;

    private static final DocumentGenerationRepository generations = new DocumentGenerationRepository();
    private static final DocumentTemplateRepository templates = new DocumentTemplateRepository();
    private static final SignatureRequestRepository requestRepo = new SignatureRequestRepository();
    private static final SigningEvidenceRepository evidenceRepo = new SigningEvidenceRepository();

    private static StorageService storage;
    private static DocumentService documents;
    private static SealedDocumentService sealedDocuments;
    private static SignatureRequestService requests;
    private static SignatureFieldService fields;
    private static SignatureNotices notices;
    private static Station station;
    private static StationMember manager;
    private static int legalTemplate;
    private static int plainTemplate;
    private static int loginPermission;

    @BeforeAll
    static void setup() {
        var backend = new LocalStorageBackend(storageRoot);
        storage = new StorageService(new StorageBackendResolver(backend), backend);
        documents = newDocumentService(storage);
        sealedDocuments = new SealedDocumentService(memberDocumentRepo, new SealedVersionRepository(), documents);
        var guardianPolicy = new GuardianPolicy(stationMemberRepo);
        var guards = new SigningGuards(
                new DocumentAccessService(memberDocumentRepo, documents, guardianPolicy),
                memberDocumentRepo,
                guardianPolicy);
        notices = TestNotices.notices(
                newNotifier(), emailQueueRepo, stationRepo, stationMemberRepo, accountRepo, memberDocumentRepo);
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
                notices);
        fields = new SignatureFieldService(
                requestRepo, evidenceRepo, requests, guards, guardianPolicy, memberNameResolver, notices);
        loginPermission = stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow()
                .id();
        station = stationRepo.create("Signing Request Station");
        manager = member("Maria", "Leitung", true);
        legalTemplate = template(station.id(), "Einverständnis", true);
        plainTemplate = template(station.id(), "Urkunde", false);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void aLegalTemplateKeepsSignaturesFourYearsAndAnyOtherOnlyWhileTheMemberStays() {
        assertEquals(48, retentionOf(legalTemplate));
        assertNull(retentionOf(plainTemplate));
    }

    @Test
    void eachGuardianAsksEveryGuardianInTheirOrderAndAChildWithoutLoginSignsThroughAGuardian() throws IOException {
        var child = member("Kim", "Kind", false);
        var first = member("Gerda", "Erste", true);
        var second = member("Gustav", "Zweite", true);
        guard(second, child);
        guard(first, child);
        stationMemberRepo.orderManagers(child.id(), List.of(first.id(), second.id()));
        var names = Stream.concat(
                        SignatureRole.PARTICIPANT.fieldNames(2).stream(),
                        SignatureRole.EACH_GUARDIAN.fieldNames(2).stream())
                .toArray(String[]::new);
        var generation = generated(child, legalTemplate, null, names);

        var request = requests.request(managing(), generation.id(), STATEMENTS);

        assertEquals(RequestState.OPEN, request.state());
        assertEquals(generation.fileSha256(), request.contentSha256());
        assertEquals(48, request.retentionMonths());
        assertNull(request.retainUntil());
        assertEquals(child.id(), request.memberId());
        assertEquals(memberNameResolver.official(child.id()), request.memberName());
        assertEquals(generation.documentId(), request.documentId());
        var asked = requestRepo.fieldsOf(request.id());
        assertEquals(
                List.of("participant", "guardian1", "guardian2"),
                asked.stream().map(RequestedSignature::fieldName).toList());
        var participant = asked.get(0);
        assertEquals(FieldRole.PARTICIPANT, participant.role());
        assertEquals(child.id(), participant.signerId());
        assertEquals(SignerCapacity.MEMBER_THROUGH_ACCOUNT, participant.capacity());
        assertEquals(STATEMENTS.own(), participant.statement());
        assertEquals(first.id(), asked.get(1).signerId());
        assertEquals(memberNameResolver.official(first.id()), asked.get(1).signerName());
        assertEquals(second.id(), asked.get(2).signerId());
        assertTrue(asked.stream()
                .skip(1)
                .allMatch(field -> field.role() == FieldRole.GUARDIAN
                        && field.capacity() == SignerCapacity.GUARDIAN
                        && field.statement().equals(STATEMENTS.guardian())
                        && field.state() == FieldState.OPEN));
        assertTrue(asked.stream().allMatch(field -> child.id() == field.memberId()));
    }

    @Test
    void oneGuardianMakesOneFieldAnyOfThemMaySign() throws IOException {
        var child = member("Lou", "Kind", true);
        var first = member("Ada", "Eins", true);
        var second = member("Bo", "Zwei", true);
        guard(first, child);
        guard(second, child);
        var generation = generated(
                child,
                legalTemplate,
                null,
                SignatureRole.ANY_GUARDIAN.fieldNames(2).toArray(String[]::new));
        var request = requests.request(managing(), generation.id(), STATEMENTS);

        var field = requestRepo.fieldsOf(request.id()).getFirst();
        assertEquals(FieldRole.ANY_GUARDIAN, field.role());
        assertNull(field.signerId());
        assertEquals(1, requests.openFor(at(first)).size());
        assertEquals(1, requests.openFor(at(second)).size());

        var stored = fields.record(
                at(second),
                signed(request, "anyGuardian", Signer.guardian(account(second), child.id()), STATEMENTS.guardian()));

        assertEquals(second.id(), stored.accountMemberId());
        assertEquals(child.id(), stored.memberId());
        assertNotNull(stored.guardianLink());
        assertEquals(1, stored.guardianLink().position());
        assertTrue(requests.openFor(at(first)).isEmpty());
        assertEquals(
                RequestState.COMPLETE,
                requestRepo.findById(request.id()).orElseThrow().state());
    }

    @Test
    void aSecondGuardianIsLeftOutForAMemberWithOneAndAnEmptyPlaceNamesNobody() throws IOException {
        var child = member("Ole", "Kind", true);
        var only = member("Una", "Einzig", true);
        guard(only, child);
        var names = Stream.concat(
                        SignatureRole.GUARDIAN_1.fieldNames(1).stream(),
                        SignatureRole.GUARDIAN_2.fieldNames(1).stream())
                .toArray(String[]::new);
        var request = requests.request(
                managing(), generated(child, legalTemplate, null, names).id(), STATEMENTS);

        assertEquals(
                List.of("guardian1"),
                requestRepo.fieldsOf(request.id()).stream()
                        .map(RequestedSignature::fieldName)
                        .toList());

        var orphan = member("Pia", "Waise", true);
        var empty = requests.request(
                managing(), generated(orphan, legalTemplate, null, "guardian1").id(), STATEMENTS);
        var nobody = requestRepo.fieldsOf(empty.id()).getFirst();
        assertNull(nobody.signerId());
        assertNull(nobody.signerName());
        assertTrue(requests.openFor(at(orphan)).isEmpty());
    }

    @Test
    void aChildSignsTheirOwnFieldThroughTheGuardiansAccountWithTheLinkInTheEvidence() throws IOException {
        var child = member("Mia", "Kind", false);
        var guardian = member("Hans", "Vater", true);
        guard(guardian, child);
        var request = requests.request(
                managing(), generated(child, legalTemplate, null, "participant").id(), STATEMENTS);

        var stored = fields.record(
                at(guardian),
                signed(
                        request,
                        "participant",
                        Signer.memberThroughAccount(account(guardian), child.id()),
                        STATEMENTS.own()));

        var link = stored.guardianLink();
        assertNotNull(link);
        assertEquals(0, link.position());
        assertNotNull(link.linkedAt());
        assertEquals(memberNameResolver.official(manager.id()), link.linkedByName());
        assertEquals(guardian.id(), stored.accountMemberId());
        assertEquals(child.id(), stored.memberId());
        var act = stored.evidence().act();
        assertEquals(SignerCapacity.MEMBER_THROUGH_ACCOUNT, act.signer().capacity());
        assertEquals(child.id(), act.signer().memberId());
        var field = requestRepo.fieldsOf(request.id()).getFirst();
        assertEquals(FieldState.SIGNED, field.state());
        assertEquals(guardian.id(), field.settledBy());
        assertEquals(
                RequestState.COMPLETE,
                requestRepo.findById(request.id()).orElseThrow().state());
    }

    @Test
    void aMemberWithALoginSignsTheirOwnFieldAndTheEvidenceIsKeptFaithfully() throws IOException {
        var adult = member("Eva", "Selbst", true);
        var request = requests.request(
                managing(), generated(adult, legalTemplate, null, "participant").id(), STATEMENTS);
        assertEquals(
                SignerCapacity.ACCOUNT_HOLDER,
                requestRepo.fieldsOf(request.id()).getFirst().capacity());
        var act = act(request, "participant", Signer.accountHolder(account(adult)), STATEMENTS.own());
        var evidence = new SigningEvidence.WebAuthnBound(
                act,
                StepUpProof.PASSKEY,
                "ember.test",
                bytes(32, 1),
                bytes(16, 2),
                bytes(77, 3),
                bytes(120, 4),
                bytes(37, 5),
                bytes(70, 6),
                true,
                42,
                new CredentialKeyStamp(
                        bytes(90, 7),
                        Instant.parse("2026-10-01T08:00:00Z"),
                        "http://tsa.test",
                        KeyStampKind.AT_FIRST_SIGNING));

        var stored = fields.record(at(adult), new CompletedSigning(SignatureLevel.SIMPLE, evidence));

        assertNull(stored.guardianLink());
        assertNull(stored.memberId());
        assertNull(stored.sealedSha256());
        assertEquals(SignatureLevel.SIMPLE, stored.level());
        var read = assertInstanceOf(SigningEvidence.WebAuthnBound.class, stored.evidence());
        assertEquals(StepUpProof.PASSKEY, read.proof());
        assertEquals("ember.test", read.relyingPartyId());
        assertArrayEquals(evidence.challenge(), read.challenge());
        assertArrayEquals(evidence.credentialId(), read.credentialId());
        assertArrayEquals(evidence.credentialPublicKeyCose(), read.credentialPublicKeyCose());
        assertArrayEquals(evidence.clientDataJson(), read.clientDataJson());
        assertArrayEquals(evidence.authenticatorData(), read.authenticatorData());
        assertArrayEquals(evidence.signature(), read.signature());
        assertTrue(read.userVerified());
        assertEquals(42, read.signatureCount());
        var stamp = Objects.requireNonNull(read.credentialKeyStamp());
        assertArrayEquals(bytes(90, 7), stamp.token());
        assertEquals(Instant.parse("2026-10-01T08:00:00Z"), stamp.stampedAt());
        assertEquals("http://tsa.test", stamp.service());
        assertEquals(KeyStampKind.AT_FIRST_SIGNING, stamp.kind());
        var readAct = read.act();
        assertEquals(request.uid(), readAct.requestUid());
        assertEquals(act.signer(), readAct.signer());
        assertEquals(act.accountHolderName(), readAct.accountHolderName());
        assertNull(readAct.memberName());
        assertEquals("participant", readAct.fieldName());
        assertEquals(STATEMENTS.own(), readAct.statement());
        assertArrayEquals(act.contentSha256(), readAct.contentSha256());
        assertEquals(act.entries(), readAct.entries());
        assertArrayEquals(act.nonce(), readAct.nonce());
        assertEquals(act.signedAt(), readAct.signedAt());
        assertEquals("203.0.113.0", readAct.truncatedIp());
        assertEquals("Test Browser", readAct.userAgent());
        assertTrue(read.boundToDocument());
    }

    @Test
    void anActIsRefusedForAnyoneButTheFieldsSignerAndOnAnythingButTheContentAskedFor() throws IOException {
        var child = member("Tom", "Kind", false);
        var guardian = member("Ute", "Mutter", true);
        var stranger = member("Fritz", "Fremd", true);
        guard(guardian, child);
        var request = requests.request(
                managing(),
                generated(child, legalTemplate, null, "participant", "guardian1")
                        .id(),
                STATEMENTS);
        var asGuardian = Signer.guardian(account(guardian), child.id());

        assertRefused(
                DocumentRefusal.SIGNING_FIELD_NOT_YOURS,
                () -> fields.record(
                        at(stranger),
                        signed(
                                request,
                                "guardian1",
                                Signer.guardian(account(stranger), child.id()),
                                STATEMENTS.guardian())));
        assertRefused(
                DocumentRefusal.SIGNING_FIELD_NOT_YOURS,
                () -> fields.record(at(stranger), signed(request, "guardian1", asGuardian, STATEMENTS.guardian())));
        assertRefused(
                DocumentRefusal.SIGNING_FIELD_NOT_YOURS,
                () -> fields.record(
                        at(guardian),
                        signed(request, "participant", Signer.accountHolder(account(guardian)), STATEMENTS.own())));
        assertRefused(
                DocumentRefusal.SIGNING_FIELD_NOT_YOURS,
                () -> fields.record(
                        at(guardian),
                        signed(request, "guardian1", Signer.accountHolder(account(guardian)), STATEMENTS.guardian())));
        assertRefused(
                DocumentRefusal.SIGNING_CONTENT_DIFFERS,
                () -> fields.record(at(guardian), signed(request, "guardian1", asGuardian, "Etwas anderes")));
        var otherContent = new SigningAct(
                request.uid(),
                asGuardian,
                "Ute Mutter",
                "Tom Kind",
                "guardian1",
                STATEMENTS.guardian(),
                Sha256.digest().digest(new byte[] {1}),
                List.of(),
                bytes(32, 7),
                Instant.now(),
                null,
                null);
        assertRefused(DocumentRefusal.SIGNING_CONTENT_DIFFERS, () -> fields.record(at(guardian), signed(otherContent)));
        assertRefused(
                DocumentRefusal.SIGNING_FIELD_NOT_FOUND,
                () -> fields.record(at(guardian), signed(request, "guardian2", asGuardian, STATEMENTS.guardian())));
        var unknown = new SignatureRequest(
                0,
                UUID.randomUUID(),
                station.id(),
                null,
                null,
                null,
                "",
                request.contentSha256(),
                RequestState.OPEN,
                null,
                null,
                null,
                Instant.now(),
                null,
                null,
                false);
        assertRefused(
                DocumentRefusal.SIGNING_REQUEST_NOT_FOUND,
                () -> fields.record(at(guardian), signed(unknown, "guardian1", asGuardian, STATEMENTS.guardian())));

        fields.record(at(guardian), signed(request, "guardian1", asGuardian, STATEMENTS.guardian()));

        assertRefused(
                DocumentRefusal.SIGNING_FIELD_NOT_OPEN,
                () -> fields.record(at(guardian), signed(request, "guardian1", asGuardian, STATEMENTS.guardian())));
        assertEquals(1, evidenceRepo.evidenceOf(request.id()).size());
        assertEquals(
                RequestState.OPEN,
                requestRepo.findById(request.id()).orElseThrow().state());
    }

    @Test
    void aGuardianWhoIsNoLongerLinkedCannotSign() throws IOException {
        var child = member("Jan", "Kind", false);
        var guardian = member("Vera", "Vormund", true);
        guard(guardian, child);
        var request = requests.request(
                managing(), generated(child, legalTemplate, null, "guardian1").id(), STATEMENTS);
        stationMemberRepo.removeManager(guardian.id(), child.id());

        assertRefused(
                DocumentRefusal.SIGNING_FIELD_NOT_YOURS,
                () -> fields.record(
                        at(guardian),
                        signed(
                                request,
                                "guardian1",
                                Signer.guardian(account(guardian), child.id()),
                                STATEMENTS.guardian())));
    }

    @Test
    void managersConfirmOnPaperWaiveAndWithdrawAndTheRequestCompletesWhenNothingIsOpen() throws IOException {
        var child = member("Rita", "Kind", false);
        var first = member("Paul", "Papier", true);
        var second = member("Wanda", "Weg", true);
        guard(first, child);
        guard(second, child);
        var request = requests.request(
                managing(),
                generated(child, legalTemplate, null, "participant", "guardian1", "guardian2")
                        .id(),
                STATEMENTS);

        assertRefused(
                DocumentRefusal.DOCUMENT_NOT_YOURS_TO_CHANGE,
                () -> fields.confirmOnPaper(at(first), request.uid(), "guardian1"));
        var paper = fields.confirmOnPaper(managing(), request.uid(), "guardian1");
        var waived = fields.waive(managing(), request.uid(), "guardian2");

        assertEquals(FieldState.PAPER_CONFIRMED, paper.state());
        assertEquals(manager.id(), paper.settledBy());
        assertEquals(memberNameResolver.official(manager.id()), paper.settledByName());
        assertNotNull(paper.settledAt());
        assertEquals(FieldState.WAIVED, waived.state());
        assertRefused(
                DocumentRefusal.SIGNING_FIELD_NOT_OPEN, () -> fields.waive(managing(), request.uid(), "guardian2"));
        assertEquals(
                RequestState.OPEN,
                requestRepo.findById(request.id()).orElseThrow().state());

        var withdrawn = fields.withdraw(managing(), request.uid(), "participant");

        assertEquals(FieldState.WITHDRAWN, withdrawn.state());
        var closed = requestRepo.findById(request.id()).orElseThrow();
        assertEquals(RequestState.COMPLETE, closed.state(), "a paper signature makes it complete");
        assertNotNull(closed.closedAt());
        assertRefused(
                DocumentRefusal.SIGNING_FIELD_NOT_OPEN,
                () -> fields.confirmOnPaper(managing(), request.uid(), "guardian2"));
    }

    @Test
    void withdrawingARequestWithNothingSignedLeavesItWithdrawn() throws IOException {
        var adult = member("Wim", "Weg", true);
        var request = requests.request(
                managing(), generated(adult, legalTemplate, null, "participant").id(), STATEMENTS);

        assertRefused(DocumentRefusal.DOCUMENT_NOT_YOURS_TO_CHANGE, () -> requests.withdraw(at(adult), request.uid()));
        var withdrawn = requests.withdraw(managing(), request.uid());

        assertEquals(RequestState.WITHDRAWN, withdrawn.state());
        assertEquals(
                FieldState.WITHDRAWN,
                requestRepo.fieldsOf(request.id()).getFirst().state());
        assertRefused(DocumentRefusal.SIGNING_REQUEST_NOT_OPEN, () -> requests.withdraw(managing(), request.uid()));
        assertRefused(
                DocumentRefusal.SIGNING_REQUEST_ENDED,
                () -> requests.rectify(
                        managing(),
                        request.uid(),
                        generated(adult, legalTemplate, null, "participant").id(),
                        STATEMENTS));
    }

    /**
     * Holds the request's row the way a signing act does and lets a withdrawal run into it. A withdrawal
     * that took the fields first would hold them while it waits, and they could not be locked here
     * without waiting; taking the request first, it holds nothing yet.
     */
    @Test
    void withdrawingARequestTakesTheRequestBeforeItsFields() throws Exception {
        var adult = member("Wim", "Zurueck", true);
        var request = requests.request(
                managing(), generated(adult, legalTemplate, null, "participant").id(), STATEMENTS);

        var withdrawn = whileTheRequestIsHeld(request, () -> requests.withdraw(managing(), request.uid()));

        assertEquals(RequestState.WITHDRAWN, withdrawn.state());
    }

    /** The same for a correction, which withdraws the open fields of the request it replaces. */
    @Test
    void correctingARequestTakesTheRequestBeforeItsFields() throws Exception {
        var adult = member("Kai", "Korrektur", true);
        var request = requests.request(
                managing(), generated(adult, legalTemplate, null, "participant").id(), STATEMENTS);
        int corrected = generated(adult, legalTemplate, null, "participant").id();

        var replacement = whileTheRequestIsHeld(
                request, () -> requests.rectify(managing(), request.uid(), corrected, STATEMENTS));

        assertEquals(
                RequestState.SUPERSEDED,
                requestRepo.findById(request.id()).orElseThrow().state());
        assertEquals(RequestState.OPEN, replacement.state());
    }

    /**
     * Runs a change on another thread while this one holds the request's row, checks that the change
     * holds none of the request's fields while it waits, and hands back what the change returned once
     * the row is let go.
     */
    private static <T> T whileTheRequestIsHeld(SignatureRequest request, Callable<T> change) throws Exception {
        int fieldId = requestRepo.fieldsOf(request.id()).getFirst().id();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = Transactions.call(() -> {
                requestRepo.lockRequest(request.id());
                var running = executor.submit(change);
                awaitALockSomebodyWaitsFor();
                assertEquals(
                        fieldId,
                        query("SELECT id FROM signing_request_field WHERE id = :id FOR UPDATE NOWAIT;")
                                .single(call().bind("id", fieldId))
                                .map(row -> row.getInt("id"))
                                .first()
                                .orElseThrow(),
                        "the waiting change holds no lock on the fields");
                return running;
            });
            return pending.get(1, TimeUnit.MINUTES);
        }
    }

    private static void awaitALockSomebodyWaitsFor() {
        var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (query("SELECT count(*) AS n FROM pg_locks WHERE NOT granted AND pid <> pg_backend_pid();")
                        .single(call())
                        .map(row -> row.getInt("n"))
                        .first()
                        .orElse(0)
                == 0) {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Nobody waited for the lock");
            LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
        }
    }

    @Test
    void aCorrectedDocumentSupersedesTheRequestAndKeepsWhatWasSigned() throws IOException {
        var child = member("Nora", "Kind", false);
        var guardian = member("Olaf", "Vater", true);
        guard(guardian, child);
        var old = requests.request(
                managing(),
                generated(child, legalTemplate, null, "participant", "guardian1")
                        .id(),
                STATEMENTS);
        fields.record(
                at(guardian),
                signed(old, "guardian1", Signer.guardian(account(guardian), child.id()), STATEMENTS.guardian()));
        var corrected = generated(child, legalTemplate, null, "participant", "guardian1");

        var replacement = requests.rectify(managing(), old.uid(), corrected.id(), STATEMENTS);

        var superseded = requestRepo.findById(old.id()).orElseThrow();
        assertEquals(RequestState.SUPERSEDED, superseded.state());
        assertEquals(replacement.id(), superseded.supersededBy());
        assertNotNull(superseded.closedAt());
        var oldFields = requestRepo.fieldsOf(old.id());
        assertEquals(FieldState.WITHDRAWN, oldFields.get(0).state());
        assertEquals(FieldState.SIGNED, oldFields.get(1).state());
        assertEquals(1, evidenceRepo.evidenceOf(old.id()).size(), "the evidence of the old document stays");
        assertEquals(RequestState.OPEN, replacement.state());
        assertEquals(corrected.fileSha256(), replacement.contentSha256());
        assertEquals(2, requestRepo.fieldsOf(replacement.id()).size());

        assertRefused(
                DocumentRefusal.SIGNING_REQUEST_ENDED,
                () -> requests.rectify(
                        managing(),
                        old.uid(),
                        generated(child, legalTemplate, null, "participant").id(),
                        STATEMENTS));
        var stranger = member("Xaver", "Anders", true);
        assertRefused(
                DocumentRefusal.SIGNING_CORRECTION_OTHER_MEMBER,
                () -> requests.rectify(
                        managing(),
                        replacement.uid(),
                        generated(stranger, legalTemplate, null, "participant").id(),
                        STATEMENTS));
    }

    @Test
    void openFieldsAreHouseholdAware() throws IOException {
        var child = member("Ida", "Kind", false);
        var teen = member("Ben", "Teen", true);
        var guardian = member("Emma", "Mutter", true);
        var stranger = member("Kai", "Fremd", true);
        guard(guardian, child);
        guard(guardian, teen);
        var forChild = requests.request(
                managing(),
                generated(child, legalTemplate, null, "participant", "guardian1", "anyGuardian")
                        .id(),
                STATEMENTS);
        var forTeen = requests.request(
                managing(), generated(teen, legalTemplate, null, "participant").id(), STATEMENTS);
        var forGuardian = requests.request(
                managing(),
                generated(guardian, legalTemplate, null, "participant").id(),
                STATEMENTS);

        var guardians = signersByField(requests.openFor(at(guardian)));

        assertEquals(5, guardians.size());
        assertEquals(
                Signer.memberThroughAccount(account(guardian), child.id()),
                guardians.get(forChild.uid() + "/participant"));
        assertEquals(Signer.guardian(account(guardian), child.id()), guardians.get(forChild.uid() + "/guardian1"));
        assertEquals(Signer.guardian(account(guardian), child.id()), guardians.get(forChild.uid() + "/anyGuardian"));
        assertEquals(
                Signer.memberThroughAccount(account(guardian), teen.id()),
                guardians.get(forTeen.uid() + "/participant"));
        assertEquals(Signer.accountHolder(account(guardian)), guardians.get(forGuardian.uid() + "/participant"));
        assertEquals(
                Map.of(forTeen.uid() + "/participant", Signer.accountHolder(account(teen))),
                signersByField(requests.openFor(at(teen))));
        assertTrue(requests.openFor(at(stranger)).isEmpty());

        fields.record(at(teen), signed(forTeen, "participant", Signer.accountHolder(account(teen)), STATEMENTS.own()));

        assertEquals(4, requests.openFor(at(guardian)).size());
        assertTrue(requests.openFor(at(teen)).isEmpty());
    }

    @Test
    void anIssuerIsAskedToSignTheirOwnField() throws IOException {
        var adult = member("Ina", "Mitglied", true);
        var issuer = member("Otto", "Aussteller", true);
        var request = requests.request(
                managing(),
                generated(adult, legalTemplate, issuer.id(), "issuer", "unknownField")
                        .id(),
                STATEMENTS);

        var asked = requestRepo.fieldsOf(request.id());
        assertEquals(1, asked.size(), "a field the generator never writes is no requirement");
        assertEquals(FieldRole.ISSUER, asked.getFirst().role());
        assertEquals(issuer.id(), asked.getFirst().signerId());
        assertEquals(SignerCapacity.ACCOUNT_HOLDER, asked.getFirst().capacity());
        assertRefused(
                DocumentRefusal.SIGNING_FIELD_NOT_YOURS,
                () -> fields.record(
                        at(adult), signed(request, "issuer", Signer.accountHolder(account(adult)), STATEMENTS.own())));

        fields.record(at(issuer), signed(request, "issuer", Signer.accountHolder(account(issuer)), STATEMENTS.own()));

        assertEquals(
                RequestState.COMPLETE,
                requestRepo.findById(request.id()).orElseThrow().state());
    }

    @Test
    void askingForSignaturesIsRefusedWhereTheDocumentCannotCarryThem() throws IOException {
        var adult = member("Lea", "Fehler", true);
        var generation = generated(adult, legalTemplate, null, "participant");
        requests.request(managing(), generation.id(), STATEMENTS);

        assertRefused(
                DocumentRefusal.SIGNING_ALREADY_REQUESTED,
                () -> requests.request(managing(), generation.id(), STATEMENTS));
        assertRefused(DocumentRefusal.SIGNING_GENERATION_NOT_FOUND, () -> requests.request(managing(), -1, STATEMENTS));
        assertRefused(
                DocumentRefusal.SIGNING_NO_FIELDS,
                () -> requests.request(
                        managing(), generated(adult, legalTemplate, null).id(), STATEMENTS));
        var unsigned = generated(adult, legalTemplate, null, "participant");
        assertRefused(
                DocumentRefusal.DOCUMENT_NOT_YOURS_TO_CHANGE,
                () -> requests.request(at(adult), unsigned.id(), STATEMENTS));
        var changed = log(adult, legalTemplate, null, unsigned.documentId(), "00".repeat(32));
        assertRefused(
                DocumentRefusal.SIGNING_DOCUMENT_CHANGED, () -> requests.request(managing(), changed.id(), STATEMENTS));
        var unfiled = log(adult, legalTemplate, null, null, unsigned.fileSha256());
        assertRefused(
                DocumentRefusal.SIGNING_DOCUMENT_NOT_FILED,
                () -> requests.request(managing(), unfiled.id(), STATEMENTS));
        var former = member("Fred", "Ehemalig", true);
        var formerGeneration = generated(former, legalTemplate, null, "participant");
        stationMemberRepo.setFormer(former.id(), true);
        assertRefused(
                DocumentRefusal.SIGNING_MEMBER_GONE,
                () -> requests.request(managing(), formerGeneration.id(), STATEMENTS));
        var elsewhere = stationRepo.create("Signing Elsewhere Station");
        try {
            var foreignManager = stationSession(
                    stationMemberRepo.create(
                            elsewhere.id(),
                            accountRepo.create(email(), "Al", "Anders").id()),
                    StationPermission.DOCUMENT_EDIT_MEMBER);
            assertRefused(
                    DocumentRefusal.SIGNING_GENERATION_NOT_FOUND,
                    () -> requests.request(foreignManager, unsigned.id(), STATEMENTS));
        } finally {
            stationRepo.delete(elsewhere.id());
        }
    }

    @Test
    void aRequestIsReadUnderTheReadRulesOfItsDocument() throws IOException {
        var child = member("Paula", "Kind", false);
        var guardian = member("Rolf", "Vater", true);
        var stranger = member("Sven", "Fremd", true);
        guard(guardian, child);
        var request = requests.request(
                managing(), generated(child, legalTemplate, null, "guardian1").id(), STATEMENTS);
        fields.record(
                at(guardian),
                signed(request, "guardian1", Signer.guardian(account(guardian), child.id()), STATEMENTS.guardian()));

        var seen = requests.view(at(guardian), request.uid());

        assertEquals(request.id(), seen.request().id());
        assertEquals(1, seen.fields().size());
        assertEquals(1, seen.evidence().size());
        assertEquals(
                request.id(), requests.view(managing(), request.uid()).request().id());
        assertRefused(DocumentRefusal.DOCUMENT_NOT_YOURS_TO_READ, () -> requests.view(at(stranger), request.uid()));
        assertRefused(DocumentRefusal.SIGNING_REQUEST_NOT_FOUND, () -> requests.view(managing(), UUID.randomUUID()));
    }

    @Test
    void aRequestWhoseDocumentIsGoneFallsBackToTheMemberItIsAbout() throws IOException {
        var child = member("Gina", "Kind", false);
        var guardian = member("Gerd", "Vater", true);
        var stranger = member("Sina", "Fremd", true);
        guard(guardian, child);
        var generation = generated(child, legalTemplate, null, "guardian1");
        var request = requests.request(managing(), generation.id(), STATEMENTS);
        documents.delete(memberDocumentRepo.findById(generation.documentId()).orElseThrow());

        assertNull(requestRepo.findById(request.id()).orElseThrow().documentId());
        assertEquals(
                request.id(),
                requests.view(at(guardian), request.uid()).request().id());
        assertEquals(
                request.id(), requests.view(managing(), request.uid()).request().id());
        assertRefused(DocumentRefusal.DOCUMENT_NOT_YOURS_TO_READ, () -> requests.view(at(stranger), request.uid()));
        assertRefused(
                DocumentRefusal.DOCUMENT_NOT_YOURS_TO_CHANGE,
                () -> fields.waive(at(guardian), request.uid(), "guardian1"));
        assertEquals(
                FieldState.WAIVED,
                fields.waive(managing(), request.uid(), "guardian1").state());
    }

    @Test
    void theDataExportCarriesTheRequestsFieldsAndEvidenceOfTheMemberAndOfTheirGuardian() throws IOException {
        var child = member("Lina", "Export", false);
        var guardian = member("Karl", "Export", true);
        guard(guardian, child);
        var request = requests.request(
                managing(),
                generated(child, legalTemplate, null, "participant", "guardian1")
                        .id(),
                STATEMENTS);
        fields.record(
                at(guardian),
                signed(request, "guardian1", Signer.guardian(account(guardian), child.id()), STATEMENTS.guardian()));
        var export = new GdprExportService(
                accountRepo,
                stationMemberRepo,
                memberLookupService,
                mock(KbFileStorageService.class),
                memberDocumentRepo,
                documents);

        var childTables = memberTables(export, child.id());
        var guardianTables = memberTables(export, guardian.id());

        assertEquals(1, rowsOf(childTables, "signing_request", request.id()).size());
        assertEquals(2, rowsFor(childTables, "signing_request_field", request.id()));
        assertEquals(1, childTables.getOrDefault("signing_evidence", List.of()).size());
        assertEquals(1, rowsFor(guardianTables, "signing_request_field", request.id()));
        assertEquals(
                1, guardianTables.getOrDefault("signing_evidence", List.of()).size());
        assertTrue(rowsOf(guardianTables, "signing_request", request.id()).isEmpty());
    }

    /**
     * Both exports name both people, but the device a guardian confirmed with, their credential and the
     * address and browser they signed from, are the guardian's data and reach only the guardian's export.
     */
    @Test
    void theGuardiansDeviceReachesOnlyTheGuardiansExportWhileBothCarryTheNames() throws IOException {
        var child = member("Nele", "Geraet", false);
        var guardian = member("Olli", "Geraet", true);
        guard(guardian, child);
        var request = requests.request(
                managing(), generated(child, legalTemplate, null, "guardian1").id(), STATEMENTS);
        var act = act(request, "guardian1", Signer.guardian(account(guardian), child.id()), STATEMENTS.guardian());
        fields.record(at(guardian), new CompletedSigning(SignatureLevel.SIMPLE, passkey(act)));
        var export = new GdprExportService(
                accountRepo,
                stationMemberRepo,
                memberLookupService,
                mock(KbFileStorageService.class),
                memberDocumentRepo,
                documents);

        var childsEvidence =
                memberTables(export, child.id()).get("signing_evidence").getFirst();
        var guardiansEvidence =
                memberTables(export, guardian.id()).get("signing_evidence").getFirst();

        for (var row : List.of(childsEvidence, guardiansEvidence)) {
            assertEquals("Konto Inhaber", row.get("account_holder_name"));
            assertEquals("Mitglied Name", row.get("member_name"));
            assertEquals(STATEMENTS.guardian(), row.get("statement"));
        }
        for (String device : List.of(
                "credential_id",
                "credential_public_key",
                "truncated_ip",
                "user_agent",
                "client_data_json",
                "authenticator_data",
                "signature",
                "credential_key_stamp_token")) {
            assertTrue(childsEvidence.containsKey(device), device + " is named, but empty");
            assertNull(childsEvidence.get(device), device + " is the guardian's");
            assertNotNull(guardiansEvidence.get(device), device + " reaches the guardian");
        }
        assertEquals("203.0.113.0", guardiansEvidence.get("truncated_ip"));
        assertEquals("Test Browser", guardiansEvidence.get("user_agent"));
    }

    private static SigningEvidence.WebAuthnBound passkey(SigningAct act) {
        return new SigningEvidence.WebAuthnBound(
                act,
                StepUpProof.PASSKEY,
                "ember.test",
                bytes(32, 1),
                bytes(16, 2),
                bytes(77, 3),
                bytes(120, 4),
                bytes(37, 5),
                bytes(70, 6),
                true,
                42,
                new CredentialKeyStamp(
                        bytes(90, 7),
                        Instant.parse("2026-10-01T08:00:00Z"),
                        "http://tsa.test",
                        KeyStampKind.AT_REGISTRATION));
    }

    @Test
    void retentionStartsWhenTheMemberLeavesStopsWhenTheyReturnAndTheSweepDeletesWhatIsOver() throws IOException {
        var archived = member("Arne", "Archiv", true);
        var deleted = member("Doro", "Geloescht", true);
        var plain = member("Pit", "Schlicht", true);
        var kept = requests.request(
                managing(),
                generated(archived, legalTemplate, null, "participant").id(),
                STATEMENTS);
        var gone = requests.request(
                managing(),
                generated(deleted, legalTemplate, null, "participant").id(),
                STATEMENTS);
        var brief = requests.request(
                managing(), generated(plain, plainTemplate, null, "participant").id(), STATEMENTS);
        fields.record(
                at(deleted), signed(gone, "participant", Signer.accountHolder(account(deleted)), STATEMENTS.own()));
        Instant then = Instant.now().minus(Duration.ofDays(6 * 365)).truncatedTo(ChronoUnit.SECONDS);
        var sweeper = new SignatureRetentionSweeper(
                requestRepo, memberDocumentRepo, sealedDocuments, Clock.fixed(then, ZoneOffset.UTC));
        stationMemberRepo.setFormer(archived.id(), true);
        documents.memberLeaves(deleted.id(), DocumentService.Leaving.DELETED);
        stationMemberRepo.delete(deleted.id());
        stationMemberRepo.setFormer(plain.id(), true);

        sweeper.sweep();

        var archivedUntil = requestRepo.findById(kept.id()).orElseThrow().retainUntil();
        assertNotNull(archivedUntil);
        assertTrue(archivedUntil.isAfter(Instant.now().plus(Duration.ofDays(47 * 30))), "four years from leaving");
        var goneUntil = requestRepo.findById(gone.id()).orElseThrow().retainUntil();
        assertNotNull(goneUntil);
        assertTrue(
                Duration.between(then.atOffset(ZoneOffset.UTC).plusMonths(48).toInstant(), goneUntil)
                                .abs()
                                .compareTo(Duration.ofDays(1))
                        < 0,
                "a deleted member counts from the first sweep that sees them gone");
        var briefUntil = requestRepo.findById(brief.id()).orElseThrow().retainUntil();
        assertNotNull(briefUntil);
        assertTrue(
                briefUntil.isAfter(Instant.now().plus(Duration.ofDays(11 * 30))),
                "a template without retention keeps an archived member's for the grace period");

        stationMemberRepo.setFormer(archived.id(), false);
        int removed = sweeper.sweep(Instant.now());

        assertNull(requestRepo.findById(kept.id()).orElseThrow().retainUntil(), "a returning member keeps it again");
        assertTrue(removed >= 1);
        assertTrue(requestRepo.findById(gone.id()).isEmpty());
        assertTrue(requestRepo.findById(brief.id()).isPresent(), "still within the grace period");

        sweeper.sweep(Instant.now().plus(Duration.ofDays(13 * 31)));
        assertTrue(requestRepo.findById(brief.id()).isEmpty());
        assertTrue(
                memberDocumentRepo
                        .findById(Objects.requireNonNull(brief.documentId()))
                        .isPresent(),
                "a document that is not sealed follows the rules of member documents");
        assertEquals(0, count("SELECT count(*) AS count FROM signing_request_field WHERE request_id = :id", gone.id()));
        assertEquals(0, count("""
                        SELECT count(*) AS count FROM signing_evidence e
                        JOIN signing_request_field f ON f.id = e.field_id
                        WHERE f.request_id = :id""", gone.id()));
    }

    /**
     * A template without a retention period keeps an archived member's signed document for twelve months
     * after the archiving, while a deleted member's goes with the next sweep, also where an archiving had
     * already given it the grace period.
     */
    @Test
    void withoutARetentionAnArchivedMemberKeepsAYearAndDeletingEndsItAtOnce() throws IOException {
        var recent = member("Ali", "Gnade", true);
        var longAgo = member("Olaf", "Lange", true);
        var deletedLater = member("Dana", "Spaeter", true);
        var recentSealed = sealedGeneration(recent, List.of(recent.id()), plainTemplate);
        var longAgoSealed = sealedGeneration(longAgo, List.of(longAgo.id()), plainTemplate);
        var deletedSealed = sealedGeneration(deletedLater, List.of(deletedLater.id()), plainTemplate);
        var kept = requests.request(managing(), recentSealed.id(), STATEMENTS);
        var over = requests.request(managing(), longAgoSealed.id(), STATEMENTS);
        var gone = requests.request(managing(), deletedSealed.id(), STATEMENTS);
        for (var member : List.of(recent, longAgo, deletedLater)) stationMemberRepo.setFormer(member.id(), true);
        query("UPDATE station_member SET former_at = now() - INTERVAL '13 months' WHERE id = :id;")
                .single(call().bind("id", longAgo.id()))
                .update();
        var sweeper = new SignatureRetentionSweeper(requestRepo, memberDocumentRepo, sealedDocuments);

        sweeper.sweep(Instant.now());

        assertTrue(retainedPastAYear(kept), "twelve months after the archiving");
        assertTrue(memberDocumentRepo.findById(recentSealed.documentId()).isPresent());
        assertTrue(retainedPastAYear(gone), "archived first, so the grace period started");
        assertTrue(requestRepo.findById(over.id()).isEmpty(), "archived longer than the grace period");
        assertTrue(memberDocumentRepo.findById(longAgoSealed.documentId()).isEmpty());

        documents.memberLeaves(deletedLater.id(), DocumentService.Leaving.DELETED);
        stationMemberRepo.delete(deletedLater.id());
        sweeper.sweep(Instant.now());

        assertTrue(requestRepo.findById(gone.id()).isEmpty(), "deleting the member ended the grace period at once");
        assertTrue(memberDocumentRepo.findById(deletedSealed.documentId()).isEmpty());
        assertTrue(requestRepo.findById(kept.id()).isPresent());
        assertTrue(memberDocumentRepo.findById(recentSealed.documentId()).isPresent());
    }

    /**
     * The database itself refuses to let the retention sweep delete a sealed document that is bound to a
     * member still at the station, whatever the requests on it say.
     */
    @Test
    void theDatabaseKeepsASealedDocumentBoundToACurrentMember() throws IOException {
        var leaving = member("Lea", "Geht", true);
        var staying = member("Bea", "Bleibt", true);
        var shared = sealedGeneration(leaving, List.of(leaving.id(), staying.id()));
        var request = requests.request(managing(), shared.id(), STATEMENTS);
        documents.memberLeaves(leaving.id(), DocumentService.Leaving.DELETED);
        stationMemberRepo.delete(leaving.id());
        new SignatureRetentionSweeper(requestRepo, memberDocumentRepo, sealedDocuments)
                .sweep(Instant.now().minus(Duration.ofDays(5 * 365)));
        assertNotNull(requestRepo.findById(request.id()).orElseThrow().retainUntil());

        assertFalse(retentionOver(shared.documentId()), "a current member is bound to it");
        assertGuarded("DELETE FROM member_document WHERE id = " + shared.documentId());

        stationMemberRepo.setFormer(staying.id(), true);
        assertTrue(retentionOver(shared.documentId()));
    }

    @Test
    void theSweepIsTheOneWayASealedDocumentGoesAndOnlyOnceNothingKeepsIt() throws IOException {
        var leaving = member("Siggi", "Siegel", true);
        var staying = member("Stefan", "Bleibt", true);
        var sealed = sealedGeneration(leaving, List.of(leaving.id()));
        var shared = sealedGeneration(leaving, List.of(leaving.id(), staying.id()));
        var request = requests.request(managing(), sealed.id(), STATEMENTS);
        var sharedRequest = requests.request(managing(), shared.id(), STATEMENTS);
        int documentId = sealed.documentId();
        var sha256 = sealed.fileSha256();
        assertGuarded("DELETE FROM member_document WHERE id = " + documentId);

        documents.memberLeaves(leaving.id(), DocumentService.Leaving.DELETED);
        stationMemberRepo.delete(leaving.id());
        var sweeper = new SignatureRetentionSweeper(
                requestRepo,
                memberDocumentRepo,
                sealedDocuments,
                Clock.fixed(Instant.now().minus(Duration.ofDays(5 * 365)), ZoneOffset.UTC));
        sweeper.sweep(Instant.now().minus(Duration.ofDays(5 * 365)));
        assertNotNull(requestRepo.findById(request.id()).orElseThrow().retainUntil());
        assertTrue(memberDocumentRepo.findById(documentId).isPresent());

        assertTrue(sweeper.sweep(Instant.now()) >= 2);

        assertTrue(requestRepo.findById(request.id()).isEmpty());
        assertTrue(requestRepo.findById(sharedRequest.id()).isEmpty());
        assertTrue(memberDocumentRepo.findById(documentId).isEmpty(), "the sealed document went with its request");
        assertEquals(
                0, count("SELECT count(*) AS count FROM member_document_version WHERE document_id = :id", documentId));
        assertTrue(storage.readAllBytes(
                        scope(), StorageCategory.MEMBER_DOCUMENTS, "sealed/" + sha256, new Variant("content"))
                .isEmpty());
        assertTrue(memberDocumentRepo.findById(shared.documentId()).isPresent(), "a member still here keeps the other");
    }

    @Test
    void theSweepRunsDailyAndSwallowsItsFailures() {
        var sweeper = new SignatureRetentionSweeper(requestRepo, memberDocumentRepo, sealedDocuments);
        var task = sweeper.scheduledTasks().getFirst();

        assertEquals("signature-retention-sweep", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(10), Duration.ofDays(1)), task.schedule());
        task.work().run();
        new SignatureRetentionSweeper(null, memberDocumentRepo, sealedDocuments).sweep();
    }

    @Test
    void deletingTheStationTakesItsRequestsFieldsAndEvidence() throws IOException {
        var leaving = stationRepo.create("Signing Leaving Station");
        var leavingManager =
                stationSession(member(leaving.id(), "Lars", "Leiter", true), StationPermission.DOCUMENT_EDIT_MEMBER);
        var child = member(leaving.id(), "Kira", "Kind", false);
        var guardian = member(leaving.id(), "Gabi", "Mutter", true);
        guard(guardian, child);
        var request = requests.request(
                leavingManager,
                generated(child, template(leaving.id(), "Fahrt", true), null, "participant", "guardian1")
                        .id(),
                STATEMENTS);
        fields.record(
                at(guardian),
                signed(request, "guardian1", Signer.guardian(account(guardian), child.id()), STATEMENTS.guardian()));
        fields.confirmOnPaper(leavingManager, request.uid(), "participant");

        assertTrue(stationRepo.delete(leaving.id()));

        assertTrue(requestRepo.findById(request.id()).isEmpty());
        assertEquals(
                0, count("SELECT count(*) AS count FROM signing_request_field WHERE request_id = :id", request.id()));
        assertEquals(
                0,
                count(
                        "SELECT count(*) AS count FROM signing_evidence WHERE field_id IN "
                                + "(SELECT id FROM signing_request_field WHERE request_id = :id)",
                        request.id()));
    }

    private static Map<String, Signer> signersByField(List<OpenSignature> open) {
        var signers = new HashMap<String, Signer>();
        for (var signature : open) {
            signers.put(
                    signature.pending().requestUid() + "/"
                            + signature.pending().field().fieldName(),
                    signature.signer());
        }
        return signers;
    }

    @Test
    void aRequestAsksEverySignerInTheAppAndOnceByMailPerAddress() throws IOException {
        var child = member("Nele", "Nachricht", false);
        var guardian = member("Gina", "Nachricht", true);
        guard(guardian, child);
        var generation = generated(child, legalTemplate, manager.id(), "participant", "guardian1", "issuer");

        var request = requests.request(managing(), generation.id(), STATEMENTS);

        var participant = fieldId(request, "participant");
        var guardianField = fieldId(request, "guardian1");
        assertEquals(
                Set.of(participant, guardianField),
                askedFields(guardian, NotificationType.SIGNATURE_REQUESTED, request),
                "the child's field through the account and the guardian's own");
        assertEquals(Set.of(participant), askedFields(child, NotificationType.SIGNATURE_REQUESTED, request));
        assertEquals(
                Set.of(fieldId(request, "issuer")),
                askedFields(manager, NotificationType.SIGNATURE_REQUESTED, request));
        var params = (NotificationParams.SignatureRequested) unread(guardian, NotificationType.SIGNATURE_REQUESTED)
                .getFirst()
                .data()
                .params();
        assertEquals(new NotificationParams.SignatureRequested("Einverstaendnis", "Nele Nachricht"), params);

        assertEquals(1, mailsTo(guardian, "Bitte unterschreiben: Einverstaendnis"), "one mail per address");
        assertEquals(
                0,
                mailsTo(child, "Bitte unterschreiben: Einverstaendnis"),
                "a child without login signs through a guardian");
        var mail = latestMailTo(guardian);
        assertTrue(mail.body()
                .contains(TestNotices.BASE_URL + "/station/signing/" + participant + "?station=" + station.uid()));
        assertTrue(mail.body().contains("Nele Nachricht"));
        assertTrue(mail.body().contains("Signing Request Station"));
        assertTrue(latestMailTo(manager).body().contains("/station/signing/" + fieldId(request, "issuer") + "?"));
    }

    @Test
    void aSignatureTakesBackItsRequestAndTellsWhoAskedForIt() throws IOException {
        var child = member("Sara", "Signiert", false);
        var guardian = member("Sven", "Signiert", true);
        guard(guardian, child);
        var request = requests.request(
                managing(),
                generated(child, legalTemplate, null, "participant", "guardian1")
                        .id(),
                STATEMENTS);

        fields.record(
                at(guardian),
                signed(request, "guardian1", Signer.guardian(account(guardian), child.id()), STATEMENTS.guardian()));

        assertEquals(
                Set.of(fieldId(request, "participant")),
                askedFields(guardian, NotificationType.SIGNATURE_REQUESTED, request));
        assertEquals(
                List.of(new NotificationParams.DocumentSigned("Einverstaendnis", "Konto Inhaber", "Sara Signiert")),
                signedNotices(child));

        fields.record(
                at(guardian),
                signed(
                        request,
                        "participant",
                        Signer.memberThroughAccount(account(guardian), child.id()),
                        STATEMENTS.own()));

        assertEquals(Set.of(), askedFields(guardian, NotificationType.SIGNATURE_REQUESTED, request));
        assertEquals(Set.of(), askedFields(child, NotificationType.SIGNATURE_REQUESTED, request));
        assertEquals(
                List.of(
                        new NotificationParams.DocumentSigned("Einverstaendnis", "Mitglied Name", null),
                        new NotificationParams.DocumentSigned("Einverstaendnis", "Konto Inhaber", "Sara Signiert")),
                signedNotices(child),
                "the member signing their own field is not named twice");
    }

    @Test
    void settlingOrWithdrawingTakesTheRequestsBack() throws IOException {
        var signer = member("Theo", "Zurueck", true);
        var request = requests.request(
                managing(),
                generated(signer, plainTemplate, manager.id(), "participant", "issuer")
                        .id(),
                STATEMENTS);

        fields.confirmOnPaper(managing(), request.uid(), "participant");
        assertEquals(Set.of(), askedFields(signer, NotificationType.SIGNATURE_REQUESTED, request));
        assertEquals(
                Set.of(fieldId(request, "issuer")),
                askedFields(manager, NotificationType.SIGNATURE_REQUESTED, request));

        requests.withdraw(managing(), request.uid());
        assertEquals(Set.of(), askedFields(manager, NotificationType.SIGNATURE_REQUESTED, request));
    }

    @Test
    void aCorrectionAsksAgainForTheNewDocumentAndTakesBackTheOld() throws IOException {
        var signer = member("Clara", "Korrektur", true);
        var old = requests.request(
                managing(),
                generated(signer, plainTemplate, null, "participant").id(),
                STATEMENTS);

        var corrected = requests.rectify(
                managing(),
                old.uid(),
                generated(signer, plainTemplate, null, "participant").id(),
                STATEMENTS);

        assertEquals(Set.of(), askedFields(signer, NotificationType.SIGNATURE_REQUESTED, old));
        assertEquals(
                Set.of(fieldId(corrected, "participant")),
                askedFields(signer, NotificationType.SIGNATURE_REQUESTED, corrected));
        assertEquals(2, mailsTo(signer, "Bitte unterschreiben: Einverstaendnis"));
    }

    @Test
    void anOpenFieldIsRemindedOfWeeklyThreeTimesAtMostAndASettledOneNever() throws IOException {
        var signer = member("Rosa", "Erinnerung", true);
        var settled = member("Rolf", "Erinnerung", true);
        var request = requests.request(
                managing(),
                generated(signer, plainTemplate, null, "participant").id(),
                STATEMENTS);
        var other = requests.request(
                managing(),
                generated(settled, plainTemplate, null, "participant").id(),
                STATEMENTS);
        fields.waive(managing(), other.uid(), "participant");
        var reminders = new SignatureReminders(requestRepo, notices);
        var asked = request.createdAt();
        String subject = "Erinnerung: Einverstaendnis wartet auf eine Unterschrift";

        reminders.sweep(asked.plus(Duration.ofDays(6)));
        assertEquals(0, remindersSent(request));

        reminders.sweep(asked.plus(Duration.ofDays(7)).plusSeconds(60));
        assertEquals(1, remindersSent(request));
        assertEquals(1, mailsTo(signer, subject));
        assertEquals(
                Set.of(fieldId(request, "participant")),
                askedFields(signer, NotificationType.SIGNATURE_REMINDER, request));
        assertTrue(latestMailTo(signer).body().contains("wartet noch auf eine Unterschrift"));

        reminders.sweep(asked.plus(Duration.ofDays(8)));
        assertEquals(1, remindersSent(request), "not again within the week");

        reminders.sweep(asked.plus(Duration.ofDays(15)));
        reminders.sweep(asked.plus(Duration.ofDays(23)));
        reminders.sweep(asked.plus(Duration.ofDays(31)));
        assertEquals(3, remindersSent(request));
        assertEquals(3, mailsTo(signer, subject));
        assertEquals(1, unread(signer, NotificationType.SIGNATURE_REMINDER).size(), "one unread reminder at a time");

        assertEquals(0, remindersSent(other));
        assertEquals(0, mailsTo(settled, subject));
    }

    @Test
    void remindersRunHourlyAndSwallowTheirFailures() {
        var task = new SignatureReminders(requestRepo, notices).scheduledTasks().getFirst();

        assertEquals("signature-reminders", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(20), Duration.ofHours(1)), task.schedule());
        task.work().run();
        new SignatureReminders(null, notices).sweep();
    }

    private static int fieldId(SignatureRequest request, String fieldName) {
        return requestRepo.fieldsOf(request.id()).stream()
                .filter(field -> field.fieldName().equals(fieldName))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static List<Notification> unread(StationMember member, NotificationType type) {
        return notificationRepo.findUnacknowledged(Recipient.stationMember(member.id())).stream()
                .filter(notification -> notification.type() == type)
                .toList();
    }

    /** The fields of one request a member's unread notifications of a type lead to. */
    private static Set<Integer> askedFields(StationMember member, NotificationType type, SignatureRequest request) {
        var ofRequest = requestRepo.fieldsOf(request.id()).stream()
                .map(RequestedSignature::id)
                .collect(Collectors.toSet());
        return unread(member, type).stream()
                .map(notification -> Objects.requireNonNull(notification.data().link()))
                .map(link -> ((Number) link.routeParams().get("fieldId")).intValue())
                .filter(ofRequest::contains)
                .collect(Collectors.toSet());
    }

    /** What the manager was told about the documents of one member, newest first. */
    private static List<NotificationParams> signedNotices(StationMember member) {
        return unread(manager, NotificationType.DOCUMENT_SIGNED).stream()
                .filter(notification -> {
                    var link = Objects.requireNonNull(notification.data().link());
                    return ((Number) link.routeParams().get("id")).intValue() == member.id();
                })
                .sorted(Comparator.comparing(Notification::id).reversed())
                .map(notification -> notification.data().params())
                .toList();
    }

    private static String emailOf(StationMember member) {
        return accountRepo.findById(account(member)).orElseThrow().email();
    }

    private static int mailsTo(StationMember member, String subject) {
        return SqlSupport.count(
                "SELECT count(*) FROM email_queue WHERE recipient = :recipient AND subject = :subject;",
                call().bind("recipient", emailOf(member)).bind("subject", subject));
    }

    private static EmailQueueRepository.QueuedEmail latestMailTo(StationMember member) {
        return emailQueueRepo.findLatestFor(emailOf(member), null, null).orElseThrow();
    }

    private static int remindersSent(SignatureRequest request) {
        return count("SELECT reminders_sent AS count FROM signing_request_field WHERE request_id = :id;", request.id());
    }

    private static DocumentGeneration sealedGeneration(StationMember member, List<Integer> memberIds)
            throws IOException {
        return sealedGeneration(member, memberIds, legalTemplate);
    }

    private static DocumentGeneration sealedGeneration(StationMember member, List<Integer> memberIds, int templateId)
            throws IOException {
        byte[] pdf = pdfWith("participant");
        var document = sealedDocuments.file(
                station.id(),
                new SealedFiling(memberIds, "Einverstaendnis", "e.pdf", false, Uploader.nobody(), List.of()),
                SealedDocument.withoutTimestamp(pdf));
        return log(member, templateId, null, document.id(), Sha256.hex(pdf));
    }

    private static boolean retainedPastAYear(SignatureRequest request) {
        Instant until = requestRepo.findById(request.id()).orElseThrow().retainUntil();
        return until != null && until.isAfter(Instant.now().plus(Duration.ofDays(360)));
    }

    private static boolean retentionOver(int documentId) {
        return query("SELECT member_document_retention_over(:id) AS over;")
                .single(call().bind("id", documentId))
                .map(row -> row.getBoolean("over"))
                .first()
                .orElseThrow();
    }

    private static DocumentGeneration generated(
            StationMember member, int templateId, @Nullable Integer issuerId, String... fieldNames) throws IOException {
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
        return log(member, templateId, issuerId, document.id(), Sha256.hex(pdf));
    }

    private static DocumentGeneration log(
            StationMember member,
            int templateId,
            @Nullable Integer issuerId,
            @Nullable Integer documentId,
            String sha256) {
        return generations.log(
                new DocumentGeneration(
                        0,
                        member.stationId(),
                        templateId,
                        1,
                        member.id(),
                        null,
                        Instant.now(),
                        false,
                        documentId,
                        sha256,
                        null,
                        null,
                        null,
                        issuerId,
                        null,
                        false,
                        issuerId != null),
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

    private static CompletedSigning signed(
            SignatureRequest request, String fieldName, Signer signer, String statement) {
        return signed(act(request, fieldName, signer, statement));
    }

    private static CompletedSigning signed(SigningAct act) {
        return new CompletedSigning(SignatureLevel.SIMPLE, new SigningEvidence.TotpUnbound(act));
    }

    private static SigningAct act(SignatureRequest request, String fieldName, Signer signer, String statement) {
        return new SigningAct(
                request.uid(),
                signer,
                "Konto Inhaber",
                signer.memberId() == null ? null : "Mitglied Name",
                fieldName,
                statement,
                HexFormat.of().parseHex(request.contentSha256()),
                List.of(new SignerEntry("phone", "0123")),
                bytes(32, 8),
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                "203.0.113.0",
                "Test Browser");
    }

    private static StationMember member(String first, String last, boolean login) {
        return member(station.id(), first, last, login);
    }

    private static StationMember member(int stationId, String first, String last, boolean login) {
        var account = accountRepo.create(email(), first, last);
        var member = stationMemberRepo.create(stationId, account.id());
        if (login) stationMemberRepo.grantPermission(member.id(), loginPermission);
        return member;
    }

    private static void guard(StationMember guardian, StationMember child) {
        stationMemberRepo.addManager(guardian.id(), child.id(), manager.id());
    }

    private static int account(StationMember member) {
        return member.accountId();
    }

    private static StationSession at(StationMember member) {
        return stationSession(member, StationPermission.LOGIN);
    }

    private static StationSession managing() {
        return stationSession(manager, StationPermission.DOCUMENT_EDIT_MEMBER, StationPermission.DOCUMENT_READ_MEMBER);
    }

    private static int template(int stationId, String name, boolean legal) {
        int author = accountRepo.create(email(), "Vor", "Lage").id();
        return templates
                .create(new Owner.Station(stationId), draft(name, legal), author)
                .id();
    }

    private static DocumentTemplateDraft draft(String name, boolean legal) {
        return new DocumentTemplateDraft(
                name,
                name,
                name,
                List.of(),
                false,
                true,
                legal,
                false,
                false,
                30,
                RestrictionMode.AND,
                DocumentLanguage.DE,
                null,
                null,
                LetterContent.blank());
    }

    private static @Nullable Integer retentionOf(int templateId) {
        return query("SELECT signature_retention_months FROM document_template WHERE id = :id;")
                .single(call().bind("id", templateId))
                .map(row -> row.getObject("signature_retention_months", Integer.class))
                .first()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<Map<String, Object>>> memberTables(GdprExportService export, int memberId) {
        return (Map<String, List<Map<String, Object>>>)
                export.exportMemberData(memberId).get("memberTables");
    }

    private static List<Map<String, Object>> rowsOf(
            Map<String, List<Map<String, Object>>> tables, String table, int id) {
        return tables.getOrDefault(table, List.of()).stream()
                .filter(row -> Integer.valueOf(id).equals(row.get("id")))
                .toList();
    }

    private static long rowsFor(Map<String, List<Map<String, Object>>> tables, String table, int requestId) {
        return tables.getOrDefault(table, List.of()).stream()
                .filter(row -> Integer.valueOf(requestId).equals(row.get("request_id")))
                .count();
    }

    private static int count(String sql, int id) {
        return query(sql)
                .single(call().bind("id", id))
                .map(row -> row.getInt("count"))
                .first()
                .orElseThrow();
    }

    private static StorageScope.Station scope() {
        return new StorageScope.Station(station.id(), stationRepo.requireUid(station.id()));
    }

    private static byte[] bytes(int length, int seed) {
        var data = new byte[length];
        for (int i = 0; i < length; i++) data[i] = (byte) (seed * 31 + i);
        return data;
    }

    private static String email() {
        return "signing-" + NAMES.incrementAndGet() + "-" + System.nanoTime() + "@test.com";
    }

    private static void assertRefused(Refusal refusal, Executable call) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, call).refusal());
    }

    private static void assertGuarded(String sql) {
        var refused = assertThrows(PSQLException.class, () -> {
            try (var connection = dataSource.getConnection();
                    var statement = connection.createStatement()) {
                statement.execute(sql);
            }
        });
        assertEquals("23001", refused.getSQLState(), refused.getMessage());
    }
}

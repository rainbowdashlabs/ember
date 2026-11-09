/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.SigningAgreementWithdrawn;
import dev.chojo.ember.event.events.SigningRequestClosed;
import dev.chojo.ember.event.events.SigningRequestReplaced;
import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.service.GeneratorTestBase;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.procedure.service.StepDocumentState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The documents steps of procedures ask to sign: generated for the member a procedure is about, filed with
 * them where they see it, asked for on behalf of whoever set the procedure up, and withdrawn again.
 */
class ProcedureSignaturesTest extends GeneratorTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final SignatureRequestRepository requestRepo = new SignatureRequestRepository();

    private static Wiring wiring;
    private static final List<DomainEvent> EVENTS = new CopyOnWriteArrayList<>();

    private static ProcedureSignatures signatures;
    private static SignatureFieldService fields;
    private static SignatureRequestService requests;
    private static SignatureWithdrawals withdrawals;
    private static StationMember guardian;
    private static StationMember child;
    private static StationMember manager;
    private static StationMember member;
    private static int loginPermission;

    @BeforeAll
    static void setup() {
        wiring = wire(stationRepo.create("Procedure Signing Station"));
        loginPermission = stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow()
                .id();
        manager = member("Maria", "Leitung");
        member = member("Alex", "Albers");
        var guardianPolicy = new GuardianPolicy(stationMemberRepo);
        var guards = new SigningGuards(
                new DocumentAccessService(memberDocumentRepo, wiring.documents(), guardianPolicy),
                memberDocumentRepo,
                guardianPolicy);
        var notices = TestNotices.notices(
                newNotifier(),
                emailQueueRepo,
                stationRepo,
                stationMemberRepo,
                accountRepo,
                memberDocumentRepo,
                new DomainEventBus(Set.of(
                        new Recording<>(SigningRequestClosed.class),
                        new Recording<>(SigningAgreementWithdrawn.class),
                        new Recording<>(SigningRequestReplaced.class))));
        requests = new SignatureRequestService(
                requestRepo,
                new SigningEvidenceRepository(),
                wiring.log(),
                memberDocumentRepo,
                wiring.documents(),
                stationMemberRepo,
                memberNameResolver,
                guardianPolicy,
                new SignerResolver(stationMemberRepo, memberNameResolver, memberPermissionResolver),
                guards,
                notices,
                new TemplateDocumentStatements(wiring.generator()));
        fields = new SignatureFieldService(
                requestRepo,
                new SigningEvidenceRepository(),
                requests,
                guards,
                guardianPolicy,
                memberNameResolver,
                notices,
                completed -> {},
                new WitnessReach(stationMemberRepo, restrictionService));
        signatures = new ProcedureSignatures(
                wiring.generation(), requests, stationMemberRepo, memberPermissionResolver, wiring.log(), requestRepo);
        withdrawals = new SignatureWithdrawals(
                requestRepo,
                requests,
                new WithdrawalRights(guardianPolicy),
                memberNameResolver,
                mock(SigningStateSealer.class),
                notices,
                eventRepo,
                eventRegistrationRepo,
                mock(AppointmentSignatures.class),
                mock(PartnerSignatures.class));
        guardian = member("Gabi", "Gerber");
        child = member("Kim", "Gerber");
        stationMemberRepo.addManager(guardian.id(), child.id(), manager.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(wiring.station().id());
    }

    /**
     * The document is generated about the member, filed with them and not hidden, and its signatures are
     * asked for in the name of whoever set the procedure up. Withdrawing it ends what it still waits for.
     */
    @Test
    void aStepsDocumentIsAskedForAndWithdrawn() {
        int consent = template(
                "Aufnahmeantrag",
                row(text("{{member.fullName}} tritt bei.")),
                row(signature(SignatureRole.PARTICIPANT, "Unterschrift")));

        int generationId = signatures.generate(wiring.station().id(), consent, member.id(), manager.id());
        signatures.request(generationId, manager.id());

        var generation = wiring.log().findById(generationId).orElseThrow();
        assertEquals(member.id(), generation.memberId());
        var document = memberDocumentRepo.findById(generation.documentId()).orElseThrow();
        assertFalse(document.hidden(), "the member sees what they are asked to sign");
        assertTrue(memberDocumentRepo.isBoundTo(document.id(), member.id()));
        var request = requestRepo.findLiveFor(generationId).orElseThrow();
        assertEquals(RequestState.OPEN, request.state());
        assertEquals(manager.id(), request.createdBy());
        assertFalse(requestRepo.fieldsOf(request.id()).isEmpty());

        signatures.withdraw(generationId, manager.id());

        assertFalse(requestRepo.findById(request.id()).orElseThrow().open(), "nothing is waited for any more");
        signatures.withdraw(generationId, manager.id());
    }

    /** Waiving every field of a step's document closes it as agreed, which is what ticks the step. */
    @Test
    void waivingEveryFieldClosesTheDocumentAsAgreed() {
        int consent = template("Erlaubnis", row(signature(SignatureRole.PARTICIPANT, "Unterschrift")));
        int generationId = signatures.generate(wiring.station().id(), consent, member.id(), manager.id());
        signatures.request(generationId, manager.id());
        var request = requestRepo.findLiveFor(generationId).orElseThrow();
        var managing =
                stationSession(manager, StationPermission.DOCUMENT_EDIT_MEMBER, StationPermission.DOCUMENT_READ_MEMBER);

        for (var field : requestRepo.fieldsOf(request.id())) {
            fields.waive(managing, request.uid(), field.fieldName());
        }

        var closed = closedFor(generationId);
        assertEquals(1, closed.size());
        assertTrue(closed.getFirst().agreed());
        assertEquals(manager.id(), closed.getFirst().settledBy());
    }

    /** A step's document reads as not asked, then waiting with an open field, then agreed. */
    @Test
    void aStepsDocumentReadsWhereItStands() {
        int consent = template("Stand", row(signature(SignatureRole.PARTICIPANT, "Unterschrift")));
        int generationId = signatures.generate(wiring.station().id(), consent, member.id(), manager.id());

        var made = signatures.stateOf(7, generationId);
        assertEquals(StepDocumentState.Progress.NOT_ASKED, made.progress());
        assertEquals(7, made.itemId());
        assertEquals(wiring.log().findById(generationId).orElseThrow().documentId(), made.documentId());

        signatures.request(generationId, manager.id());
        var asked = signatures.stateOf(7, generationId);
        assertEquals(StepDocumentState.Progress.WAITING, asked.progress());
        assertEquals(1, asked.fields());
        assertEquals(0, asked.settled());
        var request = requestRepo.findLiveFor(generationId).orElseThrow();
        var field = requestRepo.fieldsOf(request.id()).getFirst();
        assertEquals(field.id(), asked.openFieldId());

        fields.waive(managing(), request.uid(), field.fieldName());
        var agreed = signatures.stateOf(7, generationId);
        assertEquals(StepDocumentState.Progress.AGREED, agreed.progress());
        assertEquals(1, agreed.settled());
        assertNull(agreed.openFieldId());
    }

    /** A document withdrawn whole reads as not agreed; one whose agreement was withdrawn reads as withdrawn. */
    @Test
    void aStepsDocumentReadsHowItEnded() {
        int consent = template("Ende", row(signature(SignatureRole.PARTICIPANT, "Unterschrift")));
        int dropped = signatures.generate(wiring.station().id(), consent, member.id(), manager.id());
        signatures.request(dropped, manager.id());
        requests.withdraw(
                managing(), requestRepo.findLiveFor(dropped).orElseThrow().uid());
        assertEquals(
                StepDocumentState.Progress.NOT_AGREED,
                signatures.stateOf(7, dropped).progress());

        int revoked = signatures.generate(wiring.station().id(), consent, child.id(), manager.id());
        signatures.request(revoked, manager.id());
        var request = requestRepo.findLiveFor(revoked).orElseThrow();
        fields.confirmOnPaper(
                managing(),
                request.uid(),
                requestRepo.fieldsOf(request.id()).getFirst().fieldName());
        withdrawals.requireOwnedThenWithdraw(
                stationSession(guardian), request.uid(), null, new SigningCircumstances(null, null));
        assertEquals(
                StepDocumentState.Progress.WITHDRAWN,
                signatures.stateOf(7, revoked).progress());
    }

    /** A field waived beside one withdrawn still lets the document stand as agreed. */
    @Test
    void aWaivedFieldBesideAWithdrawnOneStandsAsAgreed() {
        int consent = template(
                "Eltern und Kind",
                row(signature(SignatureRole.PARTICIPANT, "Kind")),
                row(signature(SignatureRole.GUARDIAN_1, "Eltern")));
        int generationId = signatures.generate(wiring.station().id(), consent, child.id(), manager.id());
        signatures.request(generationId, manager.id());
        var request = requestRepo.findLiveFor(generationId).orElseThrow();
        var both = requestRepo.fieldsOf(request.id());
        assertEquals(2, both.size());

        fields.waive(managing(), request.uid(), both.get(0).fieldName());
        fields.withdraw(managing(), request.uid(), both.get(1).fieldName());

        var closed = closedFor(generationId);
        assertEquals(1, closed.size());
        assertTrue(closed.getFirst().agreed());
    }

    /** A request a manager withdraws whole, nothing signed on it, closes without agreement, and says so. */
    @Test
    void aRequestWithdrawnWholeIsNotAgreed() {
        int consent = template("Zurückgezogen", row(signature(SignatureRole.PARTICIPANT, "Unterschrift")));
        int generationId = signatures.generate(wiring.station().id(), consent, member.id(), manager.id());
        signatures.request(generationId, manager.id());
        var request = requestRepo.findLiveFor(generationId).orElseThrow();

        requests.withdraw(managing(), request.uid());

        var closed = closedFor(generationId);
        assertEquals(1, closed.size());
        assertFalse(closed.getFirst().agreed());
        assertEquals(manager.id(), closed.getFirst().settledBy());
    }

    /** An agreement confirmed on paper and withdrawn by the guardian afterwards says which document it was. */
    @Test
    void aWithdrawnAgreementNamesItsDocument() {
        int consent = template("Widerruf", row(signature(SignatureRole.PARTICIPANT, "Unterschrift")));
        int generationId = signatures.generate(wiring.station().id(), consent, child.id(), manager.id());
        signatures.request(generationId, manager.id());
        var request = requestRepo.findLiveFor(generationId).orElseThrow();
        fields.confirmOnPaper(
                managing(),
                request.uid(),
                requestRepo.fieldsOf(request.id()).getFirst().fieldName());

        withdrawals.requireOwnedThenWithdraw(
                stationSession(guardian), request.uid(), null, new SigningCircumstances(null, null));

        var withdrawn = EVENTS.stream()
                .filter(SigningAgreementWithdrawn.class::isInstance)
                .map(SigningAgreementWithdrawn.class::cast)
                .filter(event -> Objects.equals(event.generationId(), generationId))
                .toList();
        assertEquals(1, withdrawn.size());
        assertEquals(guardian.id(), withdrawn.getFirst().withdrawnBy());
    }

    /** A corrected document replaces the one asked for, naming both. */
    @Test
    void aCorrectedDocumentNamesTheOneItReplaces() {
        int consent = template("Korrektur", row(signature(SignatureRole.PARTICIPANT, "Unterschrift")));
        int first = signatures.generate(wiring.station().id(), consent, member.id(), manager.id());
        signatures.request(first, manager.id());
        int corrected = signatures.generate(wiring.station().id(), consent, member.id(), manager.id());

        requests.rectify(
                managing(), requestRepo.findLiveFor(first).orElseThrow().uid(), corrected);

        assertTrue(EVENTS.contains(new SigningRequestReplaced(wiring.station().id(), first, corrected)));
        assertTrue(closedFor(first).isEmpty(), "a replaced request does not close as agreed or not");
    }

    /** A template nobody of the member's side signs is not a step's document. */
    @Test
    void aDocumentOnlyTheIssuerSignsIsRefused() {
        int issued = template("Bescheinigung", row(signature(SignatureRole.ISSUER, "Unterschrift")));

        var refused = assertThrows(
                RefusalResponse.class,
                () -> signatures.generate(wiring.station().id(), issued, member.id(), manager.id()));

        assertEquals(DocumentRefusal.SIGNING_NO_FIELDS, refused.refusal());
    }

    /** A member who left is not asked about, and nothing is filed for them. */
    @Test
    void nothingIsGeneratedForSomebodyWhoLeft() {
        int consent = template("Abschied", row(signature(SignatureRole.PARTICIPANT, "Unterschrift")));
        var left = member("Lou", "Ludwig");
        stationMemberRepo.setFormer(left.id(), true);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> signatures.generate(wiring.station().id(), consent, left.id(), manager.id()));

        assertEquals(DocumentRefusal.SIGNING_MEMBER_GONE, refused.refusal());
        assertTrue(memberDocumentRepo
                .findByMember(wiring.station().id(), left.id(), true)
                .isEmpty());
    }

    /** Only a current member who generates documents for members asks in a procedure's name. */
    @Test
    void onlyThoseWhoGenerateDocumentsAsk() {
        assertFalse(signatures.mayAsk(wiring.station().id(), member.id()));
        assertFalse(signatures.mayAsk(wiring.station().id() + 1000, manager.id()));
    }

    private static StationMember member(String first, String last) {
        var account = accountRepo.create("procedure-signing-" + NAMES.incrementAndGet() + "@test.com", first, last);
        var created = stationMemberRepo.create(wiring.station().id(), account.id());
        stationMemberRepo.grantPermission(created.id(), loginPermission);
        return created;
    }

    private static int template(String name, BlockRowRequest... rows) {
        return wiring.templates()
                .create(wiring.owner(), letter(name).body(List.of(rows)).build(), manager.id())
                .id();
    }

    private static List<SigningRequestClosed> closedFor(int generationId) {
        return EVENTS.stream()
                .filter(SigningRequestClosed.class::isInstance)
                .map(SigningRequestClosed.class::cast)
                .filter(event -> Objects.equals(event.generationId(), generationId))
                .toList();
    }

    private static StationSession managing() {
        return stationSession(manager, StationPermission.DOCUMENT_EDIT_MEMBER, StationPermission.DOCUMENT_READ_MEMBER);
    }

    /**
     * Keeps every event of one type it is told of.
     *
     * @param type the type
     */
    private record Recording<T extends DomainEvent>(Class<T> type) implements DomainEventHandler<T> {
        @Override
        public Class<T> eventType() {
            return type;
        }

        @Override
        public void handle(T event) {
            EVENTS.add(event);
        }
    }
}

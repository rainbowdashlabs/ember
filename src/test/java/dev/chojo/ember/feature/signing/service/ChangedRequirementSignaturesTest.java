/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.generator.repository.PaperSubmissionRepository;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.generator.service.GeneratorTestBase;
import dev.chojo.ember.feature.generator.service.MemberNeutralTemplates;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.signing.entity.AppointmentRequest;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.handler.RegistrationSignaturesHandler;
import dev.chojo.ember.feature.signing.handler.RequirementChangeSignaturesHandler;
import dev.chojo.ember.feature.signing.repository.IssuerSignatureRepository;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A document added to an appointment after members registered asks each of them for its signatures, on the
 * dates still ahead and only where nothing stands asked yet; a document taken off withdraws what is still open
 * on its copies and keeps what was signed.
 */
class ChangedRequirementSignaturesTest extends GeneratorTestBase {
    private static final Instant START = Instant.parse("2027-03-06T09:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2027-03-06");
    private static final Clock BEFORE_THE_DAY = Clock.fixed(Instant.parse("2027-03-01T12:00:00Z"), ZoneOffset.UTC);
    private static final Clock AFTER_THE_DAY = Clock.fixed(Instant.parse("2027-03-07T12:00:00Z"), ZoneOffset.UTC);
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static final SignatureRequestRepository requestRepo = new SignatureRequestRepository();

    private static Wiring wiring;
    private static EventRequirementService requirements;
    private static EventRegistrationService registrations;
    private static SignatureRequestService requests;
    private static AppointmentDocumentService appointments;
    private static AppointmentSignatures signatures;
    private static ChangedRequirementSignatures changes;
    private static StationMember manager;
    private static StationMember guardian;
    private static StationMember child;
    private static StationMember adult;
    private static int loginPermission;

    private StationEvent camp;
    private int consent;

    @BeforeAll
    static void setup() {
        wiring = wire(stationRepo.create("Changed Requirements Station"));
        loginPermission = stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow()
                .id();
        manager = member("Maria", "Leitung", true);
        guardian = member("Gabi", "Gerber", true);
        child = member("Kim", "Gerber", false);
        adult = member("Alex", "Albers", true);
        stationMemberRepo.addManager(guardian.id(), child.id(), manager.id());

        var guardianPolicy = new GuardianPolicy(stationMemberRepo);
        var guards = new SigningGuards(
                new DocumentAccessService(memberDocumentRepo, wiring.documents(), guardianPolicy),
                memberDocumentRepo,
                guardianPolicy);
        var notices = TestNotices.notices(
                newNotifier(), emailQueueRepo, stationRepo, stationMemberRepo, accountRepo, memberDocumentRepo);
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

        var requirementRepo = new EventRequirementRepository();
        var submissions = new PaperSubmissionRepository();
        requirements = new EventRequirementService(
                requirementRepo,
                wiring.templates(),
                new MemberNeutralTemplates(wiring.templates()),
                new EventFederationRepository(),
                new DomainEventBus(Set.of(new RequirementChangeSignaturesHandler(() -> changes))));
        appointments = new AppointmentDocumentService(
                requirementRepo,
                submissions,
                wiring.templates(),
                wiring.generator(),
                wiring.generation(),
                guardianPolicy,
                eventRegistrationRepo,
                eventFieldRepo,
                memberNameResolver,
                wiring.issuers(),
                new EventRestrictionService(eventRepo, restrictionService),
                new RequirementSignatureStates(requestRepo, requests, new WithdrawalRights(guardianPolicy)));
        signatures = new AppointmentSignatures(
                eventRepo,
                eventRegistrationRepo,
                appointments,
                requirementRepo,
                submissions,
                requestRepo,
                requests,
                new DocumentGenerationRepository(),
                new IssuerSignatureRepository(),
                mock(IssuedLetterSigner.class));
        registrations = new EventRegistrationService(
                eventRegistrationRepo,
                new EventRegistrationFieldRepository(),
                eventRepo,
                new DomainEventBus(Set.of(new RegistrationSignaturesHandler(() -> signatures))),
                memberNameResolver);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(wiring.station().id());
    }

    @BeforeEach
    void appointment() {
        changesAsOf(BEFORE_THE_DAY);
        camp = eventRepo.create(
                wiring.station().id(),
                "Zeltlager " + NAMES.incrementAndGet(),
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                START,
                START.plus(Duration.ofHours(8)),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        consent = template("Einverständnis " + NAMES.incrementAndGet());
    }

    /** Both members registered before the document was added are asked for it, each on a copy of their own. */
    @Test
    void addingADocumentAsksEveryoneRegistered() {
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        registrations.register(camp.id(), adult.id(), DAY, true, null);
        assertTrue(live(child).isEmpty());

        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));

        var childs = live(child);
        var adults = live(adult);
        assertEquals(1, childs.size());
        assertEquals(1, adults.size());
        assertEquals(consent, childs.getFirst().templateId());
        assertEquals(RequestState.OPEN, childs.getFirst().request().state());
        assertEquals(
                guardian.id(),
                wiring.log()
                        .findById(childs.getFirst().request().generationId())
                        .orElseThrow()
                        .generatedBy());
    }

    /** A date already past is neither asked for a document added nor loses what is open on one taken off. */
    @Test
    void aPastDateIsNotTouched() {
        registrations.register(camp.id(), adult.id(), DAY, true, null);
        changesAsOf(AFTER_THE_DAY);

        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));
        assertTrue(live(adult).isEmpty());

        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var open = onlyRequest(child);

        requirements.setForEvent(wiring.owner(), camp.id(), List.of());

        assertEquals(
                RequestState.OPEN, requestRepo.findById(open.id()).orElseThrow().state());
    }

    /** A document whose signatures already stand asked for is not asked again when another one is added. */
    @Test
    void anOpenRequestIsNotDoubled() {
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var first = onlyRequest(child);
        int second = template("Fotoerlaubnis " + NAMES.incrementAndGet());

        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent, second));

        var asked = live(child);
        assertEquals(2, asked.size());
        assertEquals(
                List.of(first.uid()),
                asked.stream()
                        .filter(request -> request.templateId() == consent)
                        .map(request -> request.request().uid())
                        .toList());
        assertEquals(0, changes.added(wiring.station().id(), camp.id(), List.of(consent, second)));
        assertEquals(2, live(child).size());
    }

    /** Taking a document off withdraws what is still open on its copies; a field already signed stays. */
    @Test
    void removingADocumentWithdrawsTheOpenRequestsAndKeepsTheSigned() {
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        registrations.register(camp.id(), adult.id(), DAY, true, null);
        var partlySigned = onlyRequest(child);
        var untouched = onlyRequest(adult);
        requestRepo.settle(field(partlySigned, "participant").id(), FieldState.SIGNED, child.id(), "Kim Gerber");

        requirements.setForEvent(wiring.owner(), camp.id(), List.of());

        assertEquals(
                RequestState.COMPLETE,
                requestRepo.findById(partlySigned.id()).orElseThrow().state());
        assertEquals(FieldState.SIGNED, field(partlySigned, "participant").state());
        assertEquals(FieldState.WITHDRAWN, field(partlySigned, "guardian1").state());
        assertEquals(
                RequestState.WITHDRAWN,
                requestRepo.findById(untouched.id()).orElseThrow().state());
        assertTrue(requestRepo.fieldsOf(untouched.id()).stream().noneMatch(field -> field.state() == FieldState.OPEN));
    }

    /**
     * A document added after the child registered is asked for on a copy of its own; a manager asking again
     * on a second copy the guardian fetches for the same date is refused, and the child stays asked once.
     */
    @Test
    void aManagerCannotAskASecondCopyOfAnAddedDocument() {
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));
        var first = onlyRequest(child);
        var second = appointments.generate(stationSession(guardian), camp, DAY, consent, child.id());
        var editor =
                stationSession(manager, StationPermission.DOCUMENT_EDIT_MEMBER, StationPermission.DOCUMENT_READ_MEMBER);

        var refused = assertThrows(
                RefusalResponse.class, () -> requests.requireOwnedThenRequest(editor, second.generationId()));

        assertEquals(DocumentRefusal.SIGNING_ALREADY_REQUESTED, refused.refusal());
        assertEquals(first.uid(), onlyRequest(child).uid());
    }

    /** A member registering after the document was added is asked for it once, by registering. */
    @Test
    void aRegistrationAfterTheChangeIsAskedOnce() {
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));

        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());

        assertEquals(1, live(child).size());
        assertEquals(
                1,
                wiring.log().forStation(wiring.station().id(), 500, 0).stream()
                        .filter(entry -> Integer.valueOf(child.id()).equals(entry.memberId()))
                        .flatMap(entry -> wiring.log().findById(entry.id()).stream())
                        .filter(generation -> Integer.valueOf(camp.id()).equals(generation.eventId()))
                        .count());
    }

    private static void changesAsOf(Clock clock) {
        changes = new ChangedRequirementSignatures(
                eventRepo, eventRegistrationRepo, stationRepo, signatures, requestRepo, requests, clock);
    }

    private List<AppointmentRequest> live(StationMember participant) {
        return requestRepo.liveForAppointment(camp.id(), DAY, participant.id());
    }

    private SignatureRequest onlyRequest(StationMember participant) {
        var live = live(participant);
        assertEquals(1, live.size());
        return live.getFirst().request();
    }

    private static RequestedSignature field(SignatureRequest request, String name) {
        return requestRepo.fieldsOf(request.id()).stream()
                .filter(field -> field.fieldName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static StationMember member(String first, String last, boolean login) {
        var account = accountRepo.create("changed-requirements-" + NAMES.incrementAndGet() + "@test.com", first, last);
        var created = stationMemberRepo.create(wiring.station().id(), account.id());
        if (login) stationMemberRepo.grantPermission(created.id(), loginPermission);
        return created;
    }

    private static int template(String name) {
        BlockRowRequest[] rows = {
            row(text("Ich darf ins Zeltlager.")),
            row(
                    signature(SignatureRole.PARTICIPANT, "Teilnehmende Person"),
                    signature(SignatureRole.GUARDIAN_1, "Erziehungsberechtigte Person"))
        };
        return wiring.templates()
                .create(
                        wiring.owner(),
                        letter(name).body(List.of(rows)).forAppointments(true).build(),
                        manager.id())
                .id();
    }
}

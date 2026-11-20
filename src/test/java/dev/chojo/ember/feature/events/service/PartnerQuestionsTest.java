/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventAttachmentRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.PartnerAnswerRepository;
import dev.chojo.ember.feature.events.route.FederatedEventRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestFederationServices;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A partner station's member answers the registration questions of a shared appointment as this
 * station's own members do, and the shared appointment tells the partner how its places stand. Station
 * B signs its members up for appointments of station A, both on this instance, through the same serving
 * functions a partner elsewhere reaches.
 */
class PartnerQuestionsTest extends RepositoryTestBase {
    private static EventFederationService service;
    private static FederationService federationService;
    private static FederationRepository federationRepo;
    private static EventRegistrationService registrations;
    private static Station stationA;
    private static Station stationB;

    private StationEvent event;
    private Map<String, Integer> questionIds;

    @BeforeAll
    static void setup() {
        var media = mock(MediaLibraryService.class);
        federationRepo = new FederationRepository();
        var eventFederationRepo = new EventFederationRepository();
        federationService = TestFederationServices.of(federationRepo, stationRepo);
        var httpClient = mock(FederationHttpClient.class);
        when(httpClient.canSign(anyInt())).thenReturn(true);
        var transport = new FederationTestTransport(httpClient, federationRepo, stationRepo);
        var memberSvc = newStationMemberService(accountRepo, mock(AuthService.class));
        registrations = newEventServices(mock(DomainEventBus.class)).registration();
        service = new EventFederationService(
                eventFederationRepo,
                federationService,
                transport.transport(),
                federationRepo,
                stationRepo,
                newEventServices(new DomainEventBus(Set.of())).crud(),
                newCommentService(mock(DomainEventBus.class)),
                new MemberNameResolver(
                        memberSvc,
                        accountRepo,
                        eventFederationRepo,
                        federationRepo,
                        stationRepo,
                        mock(MemberGroupService.class),
                        mock(UserTagService.class)),
                new FederationFanout(new TaskScheduler()),
                new FederationEntityResolver(federationRepo),
                new EventAttachmentService(new EventAttachmentRepository(), media),
                new EventFieldService(
                        eventFieldRepo,
                        stationMemberRepo,
                        memberEligibility,
                        eventRepo,
                        attendanceRepo,
                        eventFieldRegistrationService,
                        new EventGroupRepository(),
                        occurrenceCalendar),
                occurrenceCalendar,
                media,
                new Api(),
                new EventGroupRepository(),
                registrations,
                new PartnerQuestionService(
                        new EventRegistrationFieldRepository(), new PartnerAnswerRepository(), memberEligibility));
        transport.serve(service);

        stationA = stationRepo.create("PartnerQuestionsStationA");
        stationB = stationRepo.create("PartnerQuestionsStationB");
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                stationA.id(), stationB.id(), federationService.encodePublicKey(keyPair), null, null);
    }

    @AfterAll
    static void cleanup() {
        for (var station : List.of(stationA, stationB)) {
            for (var partner : federationService.findPartners(station.id())) federationRepo.deletePartner(partner.id());
            stationRepo.delete(station.id());
        }
    }

    @BeforeEach
    void anAppointmentWithQuestions() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        event = eventRepo.create(
                stationA.id(),
                "Atemschutz",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                1,
                null,
                null);
        new EventRegistrationFieldService(new EventRegistrationFieldRepository(), memberEligibility)
                .replaceFields(
                        event.id(),
                        List.of(
                                draft("Größe", FieldType.TEXT, true, null),
                                draft("Essen", FieldType.CHOICE, false, "Fleisch"),
                                draft("Fahrer", FieldType.MEMBER, true, null)));
        questionIds = new EventRegistrationFieldRepository()
                .findByEvent(event.id()).stream()
                        .collect(Collectors.toMap(EventRegistrationField::name, EventRegistrationField::id));
        service.setShare(event.id(), ShareScope.ALL_PARTNERS, List.of());
    }

    @AfterEach
    void removeTheAppointment() {
        eventRepo.delete(event.id());
    }

    private static RegistrationFieldDraft draft(
            String name, FieldType type, boolean required, @Nullable String fallback) {
        var options = type == FieldType.CHOICE ? List.of("Fleisch", "Vegetarisch") : null;
        return new RegistrationFieldDraft(
                name,
                type,
                new EventQuestionSettings(
                        options, null, null, null, null, false, false, required, fallback, null, null, false, null),
                true);
    }

    private LocalDate day() {
        return event.startTime()
                .atZone(occurrenceCalendar.zoneOf(stationA.id()))
                .toLocalDate();
    }

    private FederationPartner servingPartner() {
        return federationRepo
                .findPartnerByStationAndRemoteUid(stationA.id(), stationB.uid())
                .orElseThrow();
    }

    /** The partner is shown every question but the one naming members of the host, with the places of the date. */
    @Test
    void theSharedAppointmentCarriesItsQuestionsAndPlaces() {
        var detail = FederatedEventRoutes.PartnerEventDetail.of(
                service.getFederatedEvent(stationB.id(), stationA.uid(), event.id()));

        assertEquals(
                List.of("Größe", "Essen"),
                detail.questions().stream().map(EventRegistrationField::name).toList());
        assertEquals(FieldType.CHOICE, detail.questions().get(1).fieldType());
        assertTrue(detail.questions().get(0).config().required());
        var places = detail.datePlaces();
        assertNotNull(places);
        assertEquals(day(), places.date());
        assertEquals(1, places.limit());
        assertEquals(0, places.taken());
    }

    /** The answers travel with the sign-up and are kept beside the partner's registration, defaults filled in. */
    @Test
    void answersAreStoredBesideThePartnersRegistration() {
        var member = UUID.randomUUID();

        var status = service.registerForFederatedEvent(
                stationB.id(),
                stationA.uid(),
                event.id(),
                member,
                day(),
                List.of(new EventRegistrationFieldValue(questionIds.get("Größe"), "L")));

        assertEquals(RegistrationStatus.ACCEPTED, status);
        var registration = service.findRegistration(event.id(), servingPartner().id(), member, day())
                .orElseThrow();
        var stored = new PartnerQuestionService(
                        new EventRegistrationFieldRepository(), new PartnerAnswerRepository(), memberEligibility)
                .answers(stationA.id(), event.id(), true);
        assertEquals(
                Set.of(
                        new EventRegistrationFieldValue(questionIds.get("Größe"), "L"),
                        new EventRegistrationFieldValue(questionIds.get("Essen"), "Fleisch")),
                Set.copyOf(stored.get(registration.id())));
        assertTrue(
                new PartnerQuestionService(
                                new EventRegistrationFieldRepository(),
                                new PartnerAnswerRepository(),
                                memberEligibility)
                        .answers(stationB.id(), event.id(), true)
                        .isEmpty(),
                "another station reads no answers of an appointment it does not hold");
    }

    /** An answer to a question kept for organisers is read only by a reader who may read such answers. */
    @Test
    void anAnswerKeptForOrganisersIsLeftOutForOtherReaders() {
        var hidden = new EventQuestionSettings(
                null, null, null, null, null, false, false, false, null, null, null, true, null);
        new EventRegistrationFieldService(new EventRegistrationFieldRepository(), memberEligibility)
                .replaceFields(
                        event.id(), List.of(new RegistrationFieldDraft("Allergie", FieldType.TEXT, hidden, false)));
        int allergy = new EventRegistrationFieldRepository()
                .findByEvent(event.id())
                .getFirst()
                .id();
        var registration = service.registerFederated(
                event.id(),
                servingPartner().id(),
                UUID.randomUUID(),
                day(),
                List.of(new EventRegistrationFieldValue(allergy, "Nüsse")));
        var questions = new PartnerQuestionService(
                new EventRegistrationFieldRepository(), new PartnerAnswerRepository(), memberEligibility);

        assertTrue(questions.answers(stationA.id(), event.id(), false).isEmpty());
        assertEquals(
                List.of(new EventRegistrationFieldValue(allergy, "Nüsse")),
                questions.answers(stationA.id(), event.id(), true).get(registration.id()));
    }

    /** A required question left open, as a partner on an older version leaves it, refuses the sign-up. */
    @Test
    void aRequiredQuestionLeftOpenRefusesTheSignUp() {
        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.registerFederated(event.id(), servingPartner().id(), UUID.randomUUID(), day()));

        assertEquals(EventRefusal.PARTNER_REQUIRED_ANSWER_MISSING, refused.refusal());
    }

    /** Without a required question, a sign-up carrying no answers is taken as before. */
    @Test
    void aSignUpWithoutAnswersIsTakenWhereNothingIsRequired() {
        new EventRegistrationFieldService(new EventRegistrationFieldRepository(), memberEligibility)
                .replaceFields(event.id(), List.of(draft("Essen", FieldType.CHOICE, false, null)));

        var registration =
                service.registerFederated(event.id(), servingPartner().id(), UUID.randomUUID(), day());

        assertEquals(RegistrationStatus.ACCEPTED, registration.status());
    }

    /** The question naming members of the host is no partner's to answer. */
    @Test
    void aQuestionNamingMembersCannotBeAnsweredByAPartner() {
        var answers = List.of(
                new EventRegistrationFieldValue(questionIds.get("Größe"), "M"),
                new EventRegistrationFieldValue(questionIds.get("Fahrer"), "1"));

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.registerFederated(event.id(), servingPartner().id(), UUID.randomUUID(), day(), answers));

        assertEquals(EventRefusal.REGISTRATION_ANSWER_TO_UNKNOWN_QUESTION, refused.refusal());
    }

    /** A full date says so, and a partner's member signing up then waits on the waiting list. */
    @Test
    void aFullDateIsToldAndTheSignUpWaits() {
        var answers = List.of(new EventRegistrationFieldValue(questionIds.get("Größe"), "S"));
        service.registerForFederatedEvent(stationB.id(), stationA.uid(), event.id(), UUID.randomUUID(), day(), answers);

        var places = service.getFederatedEvent(stationB.id(), stationA.uid(), event.id())
                .datePlaces();
        assertNotNull(places);
        assertEquals(1, places.taken());
        assertEquals(
                RegistrationStatus.QUEUED,
                service.registerForFederatedEvent(
                        stationB.id(), stationA.uid(), event.id(), UUID.randomUUID(), day(), answers));
        assertEquals(
                1,
                service.getFederatedEvent(stationB.id(), stationA.uid(), event.id())
                        .datePlaces()
                        .waiting());
    }

    /** What travels is the wire question, which reads back as the same question on the partner. */
    @Test
    void aQuestionReadsTheSameAfterTravelling() {
        var field = new EventRegistrationFieldRepository()
                .findByEvent(event.id()).stream()
                        .filter(found -> found.name().equals("Essen"))
                        .findFirst()
                        .orElseThrow();

        var travelled = RemoteEventRoutes.RemoteQuestion.of(field).asField(event.id());

        assertEquals(field.fieldType(), travelled.fieldType());
        assertEquals(field.config().options(), travelled.config().options());
        assertEquals("Fleisch", travelled.config().defaultValue());
    }
}

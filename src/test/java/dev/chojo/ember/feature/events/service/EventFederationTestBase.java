/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.PartnerPlaces;
import dev.chojo.ember.feature.events.entity.PlacesKind;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventAttachmentRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.PartnerAnswerRepository;
import dev.chojo.ember.feature.events.repository.PartnerPlacesRepository;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestEventFederation;
import dev.chojo.ember.util.TestFederationServices;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Two stations sharing appointments. Station A has two partners: station B on this instance, and
 * station C, which invited it and is reached over HTTP as a remote partner. Holds the stations, the
 * appointment and the four services that share appointments, which the tests of each service share.
 */
abstract class EventFederationTestBase extends RepositoryTestBase {
    protected static final UUID REMOTE_MEMBER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    protected static final UUID REMOTE_MEMBER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    protected static final UUID REMOTE_MEMBER_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    protected static final DomainEventBus COMMENT_BUS = mock(DomainEventBus.class);

    protected static EventShareService shareService;
    protected static EventPartnerRegistrationService hostService;
    protected static PartnerEventService clientService;
    protected static EventCommentFederation commentFederation;
    protected static PartnerPlacesService placesService;
    protected static EventRequirementService requirements;
    protected static FederationService federationService;
    protected static FederationRepository federationRepo;
    protected static FederationHttpClient httpClient;
    protected static FederationTestTransport transport;
    protected static CommentService commentService;

    protected static Station stationA;
    protected static Station stationB;
    protected static Station stationC;
    protected static int partnerId;
    protected static int eventId;
    protected static FederationPartner localPartner;
    protected static MemberIdentity testMemberIdentity;
    protected static EventCrudService crudService;
    protected static EventAttachmentService attachmentService;
    protected static MediaLibraryService media;

    private static Account testAccount;

    @BeforeAll
    static void setup() {
        media = mock(MediaLibraryService.class);
        requirements = mock(EventRequirementService.class);
        attachmentService = new EventAttachmentService(new EventAttachmentRepository(), media);
        federationRepo = new FederationRepository();
        EventFederationRepository eventFederationRepo = new EventFederationRepository();
        federationService = TestFederationServices.of(federationRepo, stationRepo);
        httpClient = mock(FederationHttpClient.class);
        var eventBus = new DomainEventBus(Set.of());
        crudService = newEventServices(eventBus).crud();
        var memberSvc = newStationMemberService(accountRepo, mock(AuthService.class));
        commentService = newCommentService(COMMENT_BUS);
        when(httpClient.canSign(anyInt())).thenReturn(true);
        transport = new FederationTestTransport(httpClient, federationRepo, stationRepo);
        var federation = TestEventFederation.of(new TestEventFederation.Parts(
                eventFederationRepo,
                federationService,
                transport.transport(),
                federationRepo,
                stationRepo,
                crudService,
                commentService,
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
                attachmentService,
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
                newEventServices(mock(DomainEventBus.class)).registration(),
                new PartnerQuestionService(
                        new EventRegistrationFieldRepository(), new PartnerAnswerRepository(), memberEligibility),
                requirements));
        shareService = federation.shares();
        hostService = federation.host();
        clientService = federation.client();
        commentFederation = federation.comments();
        placesService = federation.places();
        transport.serve(federation.servers());

        String run = UUID.randomUUID().toString();
        stationA = stationRepo.create("EventFedSvcStationA-" + run);
        stationB = stationRepo.create("EventFedSvcStationB-" + run);
        stationC = stationRepo.create("EventFedSvcStationC-" + run);

        var keyPair = federationService.generateKeyPair();
        localPartner = federationService.acceptInvite(
                stationA.id(), stationB.id(), federationService.encodePublicKey(keyPair), null, null);
        partnerId = localPartner.id();

        var keyPairC = federationService.generateKeyPair();
        federationService.acceptInvite(
                stationA.id(),
                stationC.id(),
                federationService.encodePublicKey(keyPairC),
                "https://remote-event.example.com",
                null);

        testAccount = accountRepo.create("eventfed-" + run + "@test.com", "Test", "Author");
        StationMember testMember = stationMemberRepo.create(stationA.id(), testAccount.id());
        testMemberIdentity = memberIdentityFactory.local(stationA.id(), testMember.id());

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        var event = eventRepo.create(
                stationA.id(),
                "Federated Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        eventId = event.id();
    }

    @AfterAll
    static void cleanup() {
        for (var p : federationService.findPartners(stationA.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(stationB.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(stationC.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
        stationRepo.delete(stationC.id());
        accountRepo.delete(testAccount.id());
    }

    /**
     * Writes an arrangement straight into storage, without asking whether the appointment is shared with
     * the partner, for the tests whose subject is what an arrangement does rather than who may make one.
     */
    protected static void givePlaces(
            int eventId, int partnerId, @Nullable Integer slotBudget, boolean partnerConfirms) {
        new PartnerPlacesRepository()
                .set(PlacesKind.APPOINTMENT, PartnerPlaces.requested(eventId, partnerId, slotBudget, partnerConfirms));
    }

    /** A comment a member of station A wrote on the shared appointment. */
    protected static Comment localComment(String content) {
        return commentRepo.create(CommentEntityType.EVENT, eventId, null, null, testMemberIdentity, content);
    }

    /** The given station's own partner row for the other one. */
    protected static FederationPartner partnerOf(Station station, Station other) {
        return federationRepo
                .findPartnerByStationAndRemoteUid(station.id(), other.uid())
                .orElseThrow();
    }

    /** A one-off of station A with one place, starting tomorrow, on its first day. */
    protected static StationEvent onePlace() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        return eventRepo.create(
                stationA.id(),
                "Eine Stelle",
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
    }

    /** The day an appointment of station A starts on. */
    protected static LocalDate dayOf(StationEvent event) {
        return event.startTime()
                .atZone(occurrenceCalendar.zoneOf(stationA.id()))
                .toLocalDate();
    }

    /** Where station B's member stands on the one day of an appointment of station A. */
    protected static RegistrationStatus statusOf(StationEvent event, UUID member) {
        return hostService
                .findRegistration(event.id(), partnerId, member, dayOf(event))
                .orElseThrow()
                .status();
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.PartnerPlacesRepository;
import dev.chojo.ember.feature.events.service.EventAttachmentService;
import dev.chojo.ember.feature.events.service.EventCommentFederation;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventPartnerRegistrationService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventShareService;
import dev.chojo.ember.feature.events.service.EventSharingGuards;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.events.service.PartnerEventService;
import dev.chojo.ember.feature.events.service.PartnerPlacesService;
import dev.chojo.ember.feature.events.service.PartnerQuestionService;
import dev.chojo.ember.feature.events.service.PartnerStatusNotices;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.signing.repository.PartnerSigningRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;

import java.util.Set;

import static org.mockito.Mockito.mock;

/**
 * The four services that share this station's appointments with partners and reach the appointments
 * partners share, built over one set of collaborators the way the injector wires them.
 *
 * @param shares   the station's own sharing
 * @param host     what this station serves partners and the registrations they hold here
 * @param client   what this station's members do at partners
 * @param comments the comments on shared appointments, from both ends
 * @param places   what each partner was given
 */
public record TestEventFederation(
        EventShareService shares,
        EventPartnerRegistrationService host,
        PartnerEventService client,
        EventCommentFederation comments,
        PartnerPlacesService places) {

    /**
     * The collaborators the services are built over.
     *
     * @param federationRepository the share, registration and name storage
     * @param federationService    the partnerships
     * @param transport            how partners are asked
     * @param partnerRepository    the partner storage
     * @param stationRepository    the station storage
     * @param crudService          the appointments
     * @param commentService       the comments
     * @param memberNameResolver   how authors are named
     * @param fanout               how every partner is asked at once
     * @param entityResolver       how a partner is found by its station
     * @param attachmentService    the files of appointments
     * @param fieldService         the open questions of appointments
     * @param occurrenceCalendar   the dates of appointments
     * @param media                the stored files
     * @param apiConfig            the size a file may travel at
     * @param eventGroupRepository the event groups
     * @param registrationService  the registrations of this station's own members
     * @param partnerQuestions     the registration questions partners answer
     * @param requirements         the documents an appointment asks for
     */
    public record Parts(
            EventFederationRepository federationRepository,
            FederationService federationService,
            FederationTransport transport,
            FederationRepository partnerRepository,
            StationRepository stationRepository,
            EventCrudService crudService,
            CommentService commentService,
            MemberNameResolver memberNameResolver,
            FederationFanout fanout,
            FederationEntityResolver entityResolver,
            EventAttachmentService attachmentService,
            EventFieldService fieldService,
            OccurrenceCalendar occurrenceCalendar,
            MediaLibraryService media,
            Api apiConfig,
            EventGroupRepository eventGroupRepository,
            EventRegistrationService registrationService,
            PartnerQuestionService partnerQuestions,
            EventRequirementService requirements) {}

    /**
     * Builds the services over the given collaborators.
     *
     * @param parts the collaborators
     * @return the services
     */
    public static TestEventFederation of(Parts parts) {
        return of(parts, new DomainEventBus(Set.of()), mock(Notifier.class));
    }

    /**
     * Builds the services over the given collaborators, announcing decisions about partners' members on the
     * given bus and notifying members through the given notifier.
     *
     * @param parts    the collaborators
     * @param eventBus where the host announces its decisions about partners' members
     * @param notifier how the partner side notifies its members
     * @return the services
     */
    public static TestEventFederation of(Parts parts, DomainEventBus eventBus, Notifier notifier) {
        var places = new PartnerPlacesService(
                new PartnerPlacesRepository(),
                parts.federationRepository(),
                parts.partnerRepository(),
                new EventGroupPartnerRepository());
        var shares = new EventShareService(
                parts.federationRepository(),
                new EventSharingGuards(parts.crudService()),
                parts.requirements(),
                places);
        var host = new EventPartnerRegistrationService(
                parts.federationRepository(),
                shares,
                places,
                parts.crudService(),
                parts.attachmentService(),
                parts.fieldService(),
                parts.occurrenceCalendar(),
                parts.media(),
                parts.apiConfig(),
                parts.eventGroupRepository(),
                parts.registrationService(),
                parts.partnerQuestions(),
                eventBus);
        var client = new PartnerEventService(
                parts.federationService(),
                parts.transport(),
                parts.partnerRepository(),
                parts.stationRepository(),
                parts.fanout(),
                parts.entityResolver(),
                new PartnerSigningRepository(),
                new StationMemberRepository(),
                new PartnerStatusNotices(notifier, parts.memberNameResolver()));
        var comments = new EventCommentFederation(
                parts.commentService(), shares, parts.memberNameResolver(), parts.transport(), parts.entityResolver());
        return new TestEventFederation(shares, host, client, comments, places);
    }

    /**
     * The services that answer partners, for a test transport to serve.
     *
     * @return the host side, the comments and the client side, which takes the host's notices in
     */
    public FederationServer[] servers() {
        return new FederationServer[] {host, comments, client};
    }
}

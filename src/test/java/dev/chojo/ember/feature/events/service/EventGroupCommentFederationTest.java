/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestFederationServices;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The thread of an event group station A shares with station B on the same instance: B's members
 * read and write it at A, only under the event groups A shares with B, and change or remove only
 * what they wrote themselves.
 */
class EventGroupCommentFederationTest extends RepositoryTestBase {
    private static final DomainEventBus BUS = mock(DomainEventBus.class);

    private static Station holder;
    private static Station partner;
    private static StationMember mara;
    private static StationMember ben;
    private static StationMember lea;
    private static final List<Integer> accounts = new ArrayList<>();

    private EventGroupService eventGroups;
    private EventGroupPartnerService partners;
    private CommentService comments;
    private EventGroupCommentFederation federation;
    private int shared;
    private int unshared;

    @BeforeAll
    static void setup() {
        holder = stationRepo.create("Event group Comment Holder");
        partner = stationRepo.create("Event group Comment Partner");
        var federationService = TestFederationServices.of(new FederationRepository(), stationRepo);
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                holder.id(), partner.id(), federationService.encodePublicKey(keyPair), null, null);
        mara = member(partner, "mara@group-comment.test", "Mara");
        ben = member(partner, "ben@group-comment.test", "Ben");
        lea = member(holder, "lea@group-comment.test", "Lea");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(holder.id());
        stationRepo.delete(partner.id());
        accounts.forEach(accountRepo::delete);
    }

    private static StationMember member(Station at, String mail, String name) {
        var account = accountRepo.create(mail, name, "Kommentar");
        accounts.add(account.id());
        return stationMemberRepo.create(at.id(), account.id());
    }

    @BeforeEach
    void wire() {
        reset(BUS);
        var federationRepo = new FederationRepository();
        var eventGroupRepo = new EventGroupRepository();
        var partnerRepo = new EventGroupPartnerRepository();
        var questions = new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility);
        eventGroups = newEventGroupService();
        var signUps = new EventGroupRegistrationService(
                eventGroupRepo,
                new EventGroupRegistrationRepository(),
                eventRegistrationRepo,
                newEventServices(new DomainEventBus(Set.of())).registration(),
                mock(DomainEventBus.class),
                memberNameResolver,
                questions,
                newEventGroupSignUpRules());
        var httpClient = mock(FederationHttpClient.class);
        when(httpClient.canSign(anyInt())).thenReturn(true);
        var transport = new FederationTestTransport(httpClient, federationRepo, stationRepo);
        partners = new EventGroupPartnerService(
                eventGroups,
                newEventGroupSignUpRules(),
                partnerRepo,
                new EventFederationRepository(),
                mock(EventRequirementService.class),
                mock(FederatedRegistrantService.class),
                federationRepo,
                transport.transport());
        comments = newCommentService(BUS);
        federation = new EventGroupCommentFederation(
                comments,
                partners,
                memberNameResolver,
                new EventFederationRepository(),
                transport.transport(),
                new FederationEntityResolver(federationRepo));
        transport.serve(federation);
        shared = eventGroup("Jugendflamme 1");
        partners.share(holder.id(), shared, ShareScope.ALL_PARTNERS, List.of());
        unshared = eventGroup("Nur intern");
    }

    private int eventGroup(String name) {
        return eventGroups
                .create(
                        holder.id(),
                        new EventGroupDetails(name, null, null, null, false, null),
                        List.of(new EventGroupSetDraft(null, "Übung", 1)),
                        RestrictionAudience.empty(),
                        RestrictionAudience.empty(),
                        null)
                .id();
    }

    private CommentResponse write(StationMember writer, int eventGroupId, String content) {
        return federation.createAtPartner(
                partner.id(), holder.uid(), eventGroupId, writer.uid(), "Mara Partner", null, content);
    }

    private static List<DomainEvent> published() {
        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(BUS, atLeast(0)).publish(captor.capture());
        return captor.getAllValues();
    }

    private static void assertRefused(Refusal refusal, Runnable call) {
        var thrown = assertThrows(RefusalResponse.class, call::run);
        assertEquals(refusal, thrown.refusal());
    }

    @Test
    void aPartnersMemberWritesAndReadsTheThreadOfASharedEventGroup() {
        var written = write(mara, shared, "Gibt es Mittagessen?");

        var stored =
                comments.findById(CommentEntityType.EVENT_GROUP, written.id()).orElseThrow();
        assertEquals(shared, stored.targetId());
        assertTrue(new MemberIdentity(partner.uid(), mara.uid()).sameMember(stored.author()));
        assertEquals(
                List.of("Gibt es Mittagessen?"),
                federation.listAtPartner(partner.id(), holder.uid(), shared).stream()
                        .map(CommentResponse::content)
                        .toList());
    }

    @Test
    void aPartnersNewThreadTellsTheManagersAndNobodyElse() {
        write(mara, shared, "Frage");

        var created = published().stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .toList();
        assertEquals(1, created.size());
        assertEquals(holder.id(), created.getFirst().stationId());
        assertNull(created.getFirst().alsoTold());
        assertEquals(
                StationAudience.holders(holder.id(), StationPermission.EVENT_MANAGER),
                created.getFirst().toldOfNewThread());
    }

    @Test
    void anEventGroupNotSharedWithThePartnerStaysClosed() {
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                () -> federation.listAtPartner(partner.id(), holder.uid(), unshared));
        assertRefused(EventRefusal.EVENT_GROUP_NOT_HERE, () -> write(mara, unshared, "rein?"));
    }

    @Test
    void aShareThatEndedClosesTheThread() {
        var written = write(mara, shared, "vorher");
        partners.unshare(holder.id(), shared);

        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE, () -> federation.listAtPartner(partner.id(), holder.uid(), shared));
        assertRefused(
                EventRefusal.EVENT_GROUP_NOT_HERE,
                () -> federation.deleteAtPartner(partner.id(), holder.uid(), shared, written.id(), mara.uid()));
    }

    @Test
    void anEmptyCommentIsRefused() {
        assertRefused(EventRefusal.PARTNER_COMMENT_NEEDS_TEXT, () -> write(mara, shared, " "));
    }

    @Test
    void onlyTheAuthorChangesOrRemovesTheirComment() {
        var written = write(mara, shared, "alt");

        assertRefused(
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_YOURS_TO_EDIT,
                () -> federation.updateAtPartner(partner.id(), holder.uid(), shared, written.id(), ben.uid(), "x"));
        assertRefused(
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_YOURS_TO_DELETE,
                () -> federation.deleteAtPartner(partner.id(), holder.uid(), shared, written.id(), ben.uid()));
        assertRefused(
                EventRefusal.PARTNER_COMMENT_CHANGE_NEEDS_TEXT,
                () -> federation.updateAtPartner(partner.id(), holder.uid(), shared, written.id(), mara.uid(), " "));

        var changed = federation.updateAtPartner(partner.id(), holder.uid(), shared, written.id(), mara.uid(), "neu");
        assertEquals("neu", changed.content());
        federation.deleteAtPartner(partner.id(), holder.uid(), shared, written.id(), mara.uid());
        assertTrue(
                comments.findById(CommentEntityType.EVENT_GROUP, written.id()).isEmpty());
    }

    @Test
    void aLocalMembersCommentIsNotThePartnersToChange() {
        var target = comments.target(CommentEntityType.EVENT_GROUP, shared).orElseThrow();
        var local = comments.createOn(
                target,
                CommentWriter.local(memberIdentityFactory.local(holder.id(), lea.id()), "Lea"),
                new NewComment(null, null, "von hier"));

        assertRefused(
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_YOURS_TO_EDIT,
                () -> federation.updateAtPartner(partner.id(), holder.uid(), shared, local.id(), mara.uid(), "x"));
        assertRefused(
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_YOURS_TO_DELETE,
                () -> federation.deleteAtPartner(partner.id(), holder.uid(), shared, local.id(), mara.uid()));
    }

    @Test
    void aCommentIsOnlyReachedThroughItsOwnEventGroup() {
        int other = eventGroup("Zweite");
        partners.share(holder.id(), other, ShareScope.ALL_PARTNERS, List.of());
        var written = write(mara, shared, "hier");

        assertRefused(
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_HERE_ON_UPDATE,
                () -> federation.updateAtPartner(partner.id(), holder.uid(), other, written.id(), mara.uid(), "x"));
        assertRefused(
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_HERE_ON_DELETE,
                () -> federation.deleteAtPartner(partner.id(), holder.uid(), other, written.id(), mara.uid()));
    }
}

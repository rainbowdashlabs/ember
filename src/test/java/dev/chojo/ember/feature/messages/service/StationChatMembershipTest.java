/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.StationAudienceChanged;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.messages.entity.ChatAudience;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.SystemKind;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessagePeopleRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskScheduler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Who a station chat's members are, and how they follow the station, its groups and its tags. */
class StationChatMembershipTest extends MessageTestBase {
    private final StationChatRepository chats = new StationChatRepository();
    private final ConversationRepository conversations = new ConversationRepository();
    private final MessageRepository messages = new MessageRepository();
    private MessageFixtures services;
    private StationChatMembership membership;
    private Station station;

    @BeforeEach
    void setUp() {
        services = MessageFixtures.build();
        membership = new StationChatMembership(chats, conversations, messages, new TaskScheduler());
        station = station();
    }

    @Test
    void aGroupChatHoldsTheGroupsMembersAndFollowsIt() {
        var anna = memberOf(station, "Anna");
        var ben = memberOf(station, "Ben");
        var cora = memberOf(station, "Cora");
        MemberGroup group = memberGroupRepo.create(station.id(), "Jugend");
        memberGroupRepo.addMember(group.id(), member(anna).id());
        memberGroupRepo.addMember(group.id(), member(ben).id());
        Conversation chat = groupChat(group, false);

        membership.reconcile(station.id());
        assertEquals(Set.of(anna.id(), ben.id()), activeIn(chat));

        memberGroupRepo.removeMember(group.id(), member(ben).id());
        memberGroupRepo.addMember(group.id(), member(cora).id());
        var changes = membership.reconcile(station.id());

        assertEquals(Set.of(anna.id(), cora.id()), activeIn(chat));
        assertEquals(1, changes.size());
        assertEquals(Set.of(cora.id()), changes.getFirst().added());
        assertEquals(Set.of(ben.id()), changes.getFirst().removed());
    }

    @Test
    void aTagChatHoldsTheTagsMembersTrialMembersIncluded() {
        var anna = memberOf(station, "Anna");
        var trial = memberOf(station, "Tim");
        stationMemberRepo.setUserType(member(trial).id(), StationUserType.TRIAL);
        memberOf(station, "Ben");
        UserTag tag = userTagRepo.create(station.id(), "Atemschutz");
        userTagRepo.addMember(tag.id(), member(anna).id());
        userTagRepo.addMember(tag.id(), member(trial).id());
        Conversation chat = chats.create(station.id(), "Atemschutz", ChatAudience.TAG, null, tag.id(), false);

        membership.reconcile(station.id());

        assertEquals(Set.of(anna.id(), trial.id()), activeIn(chat));
    }

    @Test
    void theStationWideChatLeavesTrialMembersFormerMembersAndOtherStationsOut() {
        var anna = memberOf(station, "Anna");
        var trial = memberOf(station, "Tim");
        stationMemberRepo.setUserType(member(trial).id(), StationUserType.TRIAL);
        var former = memberOf(station, "Fritz");
        stationMemberRepo.setFormer(member(former).id(), true);
        memberOf(station(), "Elsewhere");
        Conversation chat = stationWide();

        membership.reconcile(station.id());

        assertEquals(Set.of(anna.id()), activeIn(chat));
    }

    @Test
    void somebodyWithAGuardianIsInOnlyOnceMessagesAreTurnedOn() {
        var child = memberOf(station, "Kim");
        var parent = memberOf(station, "Paula");
        guard(member(parent), member(child));
        Conversation chat = stationWide();

        membership.reconcile(station.id());
        assertEquals(Set.of(parent.id()), activeIn(chat));

        services.settings().setGuardianAllowed(child.id(), true);
        membership.reconcile(station.id());
        assertEquals(Set.of(parent.id(), child.id()), activeIn(chat));

        services.settings().setGuardianAllowed(child.id(), false);
        membership.reconcile(station.id());
        assertEquals(Set.of(parent.id()), activeIn(chat));
    }

    @Test
    void includedGuardiansAreInOnceAndLeaveWithTheirLastMember() {
        var first = memberOf(station, "Kim");
        var second = memberOf(station, "Lou");
        var parent = memberOf(station, "Paula");
        stationMemberRepo.setUserType(member(parent).id(), StationUserType.GUARDIAN);
        guard(member(parent), member(first));
        guard(member(parent), member(second));
        MemberGroup group = memberGroupRepo.create(station.id(), "Jugend");
        memberGroupRepo.addMember(group.id(), member(first).id());
        memberGroupRepo.addMember(group.id(), member(second).id());
        Conversation chat = groupChat(group, true);

        membership.reconcile(station.id());
        assertEquals(Set.of(parent.id()), activeIn(chat), "the children have no messages, their guardian is in");

        memberGroupRepo.removeMember(group.id(), member(first).id());
        membership.reconcile(station.id());
        assertEquals(Set.of(parent.id()), activeIn(chat), "one of the guardian's members is still in the group");

        memberGroupRepo.removeMember(group.id(), member(second).id());
        membership.reconcile(station.id());
        assertEquals(Set.of(), activeIn(chat));
    }

    @Test
    void guardiansStayOutOfAChatThatDoesNotIncludeThem() {
        var child = memberOf(station, "Kim");
        var parent = memberOf(station, "Paula");
        guard(member(parent), member(child));
        services.settings().setGuardianAllowed(child.id(), true);
        MemberGroup group = memberGroupRepo.create(station.id(), "Jugend");
        memberGroupRepo.addMember(group.id(), member(child).id());
        Conversation chat = groupChat(group, false);

        membership.reconcile(station.id());

        assertEquals(Set.of(child.id()), activeIn(chat));
    }

    @Test
    void aGuardianOfAMemberWithoutAccountIsIncludedToo() {
        StationMember waiting = stationMemberRepo.createWithoutAccount(station.id(), "Kim Klein");
        var parent = memberOf(station, "Paula");
        guard(member(parent), waiting);
        UserTag tag = userTagRepo.create(station.id(), "Bambini");
        userTagRepo.addMember(tag.id(), waiting.id());
        Conversation chat = chats.create(station.id(), "Bambini", ChatAudience.TAG, null, tag.id(), true);

        membership.reconcile(station.id());

        assertEquals(Set.of(parent.id()), activeIn(chat));
    }

    @Test
    void somebodyRemovedFromTheStationLosesTheChatAndItsHistory() {
        var anna = memberOf(station, "Anna");
        var ben = memberOf(station, "Ben");
        Conversation chat = stationWide();
        membership.reconcile(station.id());
        services.messages().send(anna.id(), chat.uid(), "Hallo", null);

        StationMember leaving = member(ben);
        stationMemberRepo.setDisplayNameAndClearAccount(leaving.id(), "Ben Muster");
        stationMemberRepo.setFormer(leaving.id(), true);
        membership.reconcile(station.id());

        assertEquals(Set.of(anna.id()), activeIn(chat));
        assertRefused(
                MessageRefusal.CONVERSATION_NOT_HERE, () -> services.messages().page(ben.id(), chat.uid(), null, null));
    }

    @Test
    void somebodyTakenInLaterReadsTheWholeHistoryWithNothingUnread() {
        var anna = memberOf(station, "Anna");
        Conversation chat = stationWide();
        membership.reconcile(station.id());
        services.messages().send(anna.id(), chat.uid(), "Vor deiner Zeit", null);

        var ben = memberOf(station, "Ben");
        membership.reconcile(station.id());

        var page = services.messages().page(ben.id(), chat.uid(), null, null);
        assertEquals(1, page.messages().size());
        assertEquals(0, services.conversations().unread(ben.id()));
    }

    @Test
    void joiningAndLeavingWriteNoLineIntoTheChat() {
        memberOf(station, "Anna");
        Conversation chat = stationWide();

        membership.reconcile(station.id());

        assertTrue(messages.before(chat.id(), null, 10).isEmpty());
    }

    @Test
    void aClosedChatStillLosesThoseWhoLeftTheStation() {
        var anna = memberOf(station, "Anna");
        var ben = memberOf(station, "Ben");
        Conversation chat = stationWide();
        membership.reconcile(station.id());
        chats.close(chat.id(), anna.id());

        stationMemberRepo.setFormer(member(ben).id(), true);
        membership.reconcile(station.id());

        assertEquals(Set.of(anna.id()), activeIn(chat));
    }

    @Test
    void aChatWhoseGroupIsDeletedIsClosedOnceAndKeepsItsMembers() {
        var anna = memberOf(station, "Anna");
        MemberGroup group = memberGroupRepo.create(station.id(), "Jugend");
        memberGroupRepo.addMember(group.id(), member(anna).id());
        Conversation chat = groupChat(group, false);
        membership.reconcile(station.id());

        memberGroupRepo.delete(group.id());
        var changes = membership.reconcile(station.id());
        membership.reconcile(station.id());

        Conversation closed = conversations.findByUid(chat.uid()).orElseThrow();
        assertNotNull(Objects.requireNonNull(closed.station()).closedAt());
        assertEquals(null, closed.station().closedByAccountId());
        assertTrue(changes.getFirst().closed());
        assertEquals(Set.of(anna.id()), activeIn(chat));
        var lines = messages.before(chat.id(), null, 10);
        assertEquals(1, lines.size(), "the chat is closed once");
        Message line = lines.getFirst();
        assertEquals(SystemKind.CLOSED, line.systemKind());
        assertEquals(null, line.author());
    }

    @Test
    void aChatWhoseTagIsDeletedIsClosedToo() {
        UserTag tag = userTagRepo.create(station.id(), "Atemschutz");
        Conversation chat = chats.create(station.id(), "Atemschutz", ChatAudience.TAG, null, tag.id(), false);

        userTagRepo.delete(tag.id());
        membership.reconcile(station.id());

        assertTrue(Objects.requireNonNull(
                        conversations.findByUid(chat.uid()).orElseThrow().station())
                .closed());
    }

    @Test
    void deletingAnAccountLeavesTheStationsChatStanding() {
        var anna = memberOf(station, "Anna");
        Conversation chat = stationWide();
        membership.reconcile(station.id());

        conversations.forgetAccount(anna.id());

        assertTrue(conversations.findByUid(chat.uid()).isPresent());
    }

    @Test
    void theSweepReconcilesEveryStationWithChats() {
        var anna = memberOf(station, "Anna");
        Conversation chat = stationWide();

        membership.sweep();

        assertEquals(Set.of(anna.id()), activeIn(chat));
    }

    @Test
    void reconcilingSoonPlansOneRunPerStationUntilItRuns() {
        var scheduler = mock(TaskScheduler.class);
        var planned = new StationChatMembership(chats, conversations, messages, scheduler);

        planned.soon(station.id());
        planned.soon(station.id());

        verify(scheduler, times(1)).later(eq("station-chat-membership"), eq(StationChatMembership.DEBOUNCE), any());
    }

    @Test
    void anAccountsChangeReachesEveryStationItIsAMemberOf() {
        var scheduler = mock(TaskScheduler.class);
        var planned = new StationChatMembership(chats, conversations, messages, scheduler);
        var anna = memberOf(station, "Anna");
        join(station(), anna);

        planned.soonForAccount(anna.id());

        verify(scheduler, times(2)).later(eq("station-chat-membership"), eq(StationChatMembership.DEBOUNCE), any());
    }

    @Test
    void theSweepRunsOnceADay() {
        ScheduledTask task = membership.scheduledTasks().getFirst();

        assertEquals("station-chat-membership-sweep", task.name());
        assertEquals(Duration.ofDays(1), StationChatMembership.SWEEP_INTERVAL);
        task.work().run();
    }

    @Test
    void aGroupChatTakesTrialMembersOfItsGroup() {
        var trial = memberOf(station, "Tim");
        stationMemberRepo.setUserType(member(trial).id(), StationUserType.TRIAL);
        MemberGroup group = memberGroupRepo.create(station.id(), "Probe");
        memberGroupRepo.addMember(group.id(), member(trial).id());
        Conversation chat = groupChat(group, false);

        membership.reconcile(station.id());

        assertEquals(Set.of(trial.id()), activeIn(chat));
    }

    @Test
    void aFormerGuardianAndAGuardianWithoutMessagesStayOut() {
        var child = memberOf(station, "Kim");
        var former = memberOf(station, "Fritz");
        var unusable = memberOf(station, "Uwe");
        var unusablesGuardian = memberOf(station, "Gerd");
        guard(member(former), member(child));
        guard(member(unusable), member(child));
        guard(member(unusablesGuardian), member(unusable));
        stationMemberRepo.setFormer(member(former).id(), true);
        MemberGroup group = memberGroupRepo.create(station.id(), "Jugend");
        memberGroupRepo.addMember(group.id(), member(child).id());
        Conversation chat = groupChat(group, true);

        membership.reconcile(station.id());

        assertEquals(Set.of(), activeIn(chat));
    }

    @Test
    void aMutedChatStaysMutedForSomebodyTakenOutAndBackIn() {
        var anna = memberOf(station, "Anna");
        memberOf(station, "Ben");
        MemberGroup group = memberGroupRepo.create(station.id(), "Jugend");
        memberGroupRepo.addMember(group.id(), member(anna).id());
        Conversation chat = groupChat(group, false);
        membership.reconcile(station.id());
        services.conversations().mute(anna.id(), chat.uid(), true);

        memberGroupRepo.removeMember(group.id(), member(anna).id());
        membership.reconcile(station.id());
        memberGroupRepo.addMember(group.id(), member(anna).id());
        membership.reconcile(station.id());

        assertTrue(conversations.membership(chat.id(), anna.id()).orElseThrow().muted());
    }

    @Test
    void aClosedChatTakesNobodyNewIn() {
        var anna = memberOf(station, "Anna");
        Conversation chat = stationWide();
        membership.reconcile(station.id());
        chats.close(chat.id(), anna.id());

        memberOf(station, "Ben");
        membership.reconcile(station.id());

        assertEquals(Set.of(anna.id()), activeIn(chat));
    }

    @Test
    void aChatWithoutItsGroupStillLetsGoOfThoseWhoLeftTheStation() {
        var anna = memberOf(station, "Anna");
        var ben = memberOf(station, "Ben");
        MemberGroup group = memberGroupRepo.create(station.id(), "Jugend");
        memberGroupRepo.addMember(group.id(), member(anna).id());
        memberGroupRepo.addMember(group.id(), member(ben).id());
        Conversation chat = groupChat(group, false);
        membership.reconcile(station.id());
        memberGroupRepo.delete(group.id());
        membership.reconcile(station.id());

        stationMemberRepo.setFormer(member(ben).id(), true);
        membership.reconcile(station.id());

        assertEquals(Set.of(anna.id()), activeIn(chat));
    }

    @Test
    void oneFailingChatLeavesTheStationsOtherChatsReconciled() {
        var anna = memberOf(station, "Anna");
        Conversation broken = stationWide();
        UserTag tag = userTagRepo.create(station.id(), "Alle");
        userTagRepo.addMember(tag.id(), member(anna).id());
        Conversation working = chats.create(station.id(), "Alle", ChatAudience.TAG, null, tag.id(), false);
        var failing = spy(chats);
        doThrow(new IllegalStateException("broken")).when(failing).audience(broken.id());

        new StationChatMembership(failing, conversations, messages, new TaskScheduler()).reconcile(station.id());

        assertEquals(Set.of(), activeIn(broken));
        assertEquals(Set.of(anna.id()), activeIn(working));
    }

    @Test
    void turningMessagesOnOrOffForSomebodySaysSoForTheirAccount() {
        var bus = mock(DomainEventBus.class);
        var settings = new MessageSettingsService(
                new MessagePeopleRepository(),
                conversations,
                MessageFixtures.build().guards(),
                bus);
        var child = memberOf(station, "Kim");

        settings.setGuardianAllowed(child.id(), true);

        verify(bus).publish(StationAudienceChanged.ofAccount(child.id()));
    }

    private Conversation stationWide() {
        return chats.create(station.id(), "Alle", ChatAudience.STATION, null, null, false);
    }

    private Conversation groupChat(MemberGroup group, boolean includeGuardians) {
        return chats.create(station.id(), group.name(), ChatAudience.GROUP, group.id(), null, includeGuardians);
    }

    private StationMember member(Account account) {
        return stationMemberRepo
                .findByStationAndAccount(station.id(), account.id())
                .orElseThrow();
    }

    private Set<Integer> activeIn(@Nullable Conversation chat) {
        return conversations.activeMembers(Objects.requireNonNull(chat).id()).stream()
                .map(Membership::accountId)
                .collect(Collectors.toSet());
    }
}

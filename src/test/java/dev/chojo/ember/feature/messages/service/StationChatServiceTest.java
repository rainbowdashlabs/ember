/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.ConversationKind;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.entity.SystemKind;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a station's managers open and handle its chats, and what a chat's members may do in one. */
class StationChatServiceTest extends MessageTestBase {
    private final ConversationRepository conversations = new ConversationRepository();
    private final MessageRepository messages = new MessageRepository();
    private MessageFixtures services;
    private StationChatService chats;
    private Station station;
    private Account manager;
    private Account anna;
    private MemberGroup group;

    @BeforeEach
    void setUp() {
        services = MessageFixtures.build();
        chats = services.stationChats();
        station = station();
        manager = memberOf(station, "Mia");
        anna = memberOf(station, "Anna");
        group = memberGroupRepo.create(station.id(), "Jugend");
        memberGroupRepo.addMember(group.id(), member(anna).id());
    }

    @Test
    void openingAChatTakesItsAudienceInAndSaysSo() {
        Conversation chat = chats.open(station.id(), manager.id(), " Alle ", null, false);

        assertEquals(ConversationKind.STATION, chat.kind());
        assertEquals("Alle", chat.name());
        assertEquals(Set.of(manager.id(), anna.id()), activeIn(chat));
        Message opened = messages.before(chat.id(), null, 5).getFirst();
        assertEquals(SystemKind.OPENED, opened.systemKind());
        assertEquals("Alle", opened.content());
        assertEquals(2, chats.list(station.id()).getFirst().members());
    }

    @Test
    void severalChatsMayBeOpenForTheSameAudience() {
        chats.open(station.id(), manager.id(), "Jugend", groups(group.id()), false);
        chats.open(station.id(), manager.id(), "Jugend 2", groups(group.id()), false);
        chats.open(station.id(), manager.id(), "Alle", null, false);
        chats.open(station.id(), manager.id(), "Alle 2", RestrictionAudience.empty(), false);

        assertEquals(4, chats.list(station.id()).size());
    }

    @Test
    void anAudienceReachingIntoAnotherStationIsRefused() {
        Station other = station();
        MemberGroup foreignGroup = memberGroupRepo.create(other.id(), "Fremd");
        var foreignTag = userTagRepo.create(other.id(), "Fremd");

        assertRefused(
                MessageRefusal.STATION_CHAT_AUDIENCE_NOT_HERE,
                () -> chats.open(station.id(), manager.id(), "Fremd", groups(foreignGroup.id()), false));
        assertRefused(
                MessageRefusal.STATION_CHAT_AUDIENCE_NOT_HERE,
                () -> chats.open(station.id(), manager.id(), "Fremd", tags(foreignTag.id()), false));
        assertEquals(List.of(), chats.list(station.id()));
    }

    @Test
    void aPrivateTagHasNoChatAndATagWithAChatStaysVisible() {
        var hidden = userTagRepo.create(station.id(), "Vertraulich", null, TagVisibility.PRIVATE);
        var shown = userTagRepo.create(station.id(), "Atemschutz");

        assertRefused(
                MemberRefusal.TAG_PRIVATE_CHOOSES_NOBODY,
                () -> chats.open(station.id(), manager.id(), "Vertraulich", tags(hidden.id()), false));
        assertEquals(List.of(), chats.list(station.id()), "the refused chat is not kept half made");
        chats.open(station.id(), manager.id(), "Atemschutz", tags(shown.id()), false);
        assertEquals(1, userTagRepo.countSelectingUses(shown.id()));
    }

    @Test
    void aNamelessChatAndALongNameAreRefused() {
        assertRefused(
                MessageRefusal.STATION_CHAT_NAME_MISSING,
                () -> chats.open(station.id(), manager.id(), "  ", null, false));
        assertRefused(
                MessageRefusal.STATION_CHAT_NAME_TOO_LONG,
                () -> chats.open(station.id(), manager.id(), "x".repeat(101), null, false));
    }

    @Test
    void aTagChatHoldsThePeopleWithTheTag() {
        var tag = userTagRepo.create(station.id(), "Atemschutz");
        userTagRepo.addMember(tag.id(), member(anna).id());

        Conversation chat = chats.open(station.id(), manager.id(), "Atemschutz", tags(tag.id()), false);

        assertEquals(Set.of(anna.id()), activeIn(chat));
        assertEquals(
                List.of(tag.id()),
                chats.one(station.id(), chat.uid()).audience().tagIds());
    }

    @Test
    void aGroupTogetherWithItsLeadersIsOneChat() {
        var leader = memberOf(station, "Lea");
        var leaders = userTagRepo.create(station.id(), "Betreuer");
        userTagRepo.addMember(leaders.id(), member(leader).id());
        var audience = new RestrictionAudience(
                List.of(), List.of(group.id()), List.of(leaders.id()), List.of(), RestrictionMode.OR);

        Conversation chat = chats.open(station.id(), manager.id(), "Jugend und Betreuer", audience, false);

        assertEquals(Set.of(anna.id(), leader.id()), activeIn(chat));
        var listed = chats.one(station.id(), chat.uid()).audience();
        assertEquals(RestrictionMode.OR, listed.mode());
        assertEquals(List.of(group.id()), listed.groupIds());
        assertEquals(List.of(leaders.id()), listed.tagIds());
    }

    @Test
    void theWholeStationLeavesTrialMembersOutUnlessTheAudienceNamesThem() {
        var trial = memberOf(station, "Tim");
        stationMemberRepo.setUserType(member(trial).id(), StationUserType.TRIAL);

        Conversation everybody = chats.open(station.id(), manager.id(), "Alle", null, false);
        Conversation trials = chats.open(
                station.id(),
                manager.id(),
                "Probezeit",
                new RestrictionAudience(
                        List.of(StationUserType.TRIAL), List.of(), List.of(), List.of(), RestrictionMode.AND),
                false);

        assertFalse(activeIn(everybody).contains(trial.id()));
        assertEquals(Set.of(trial.id()), activeIn(trials));
    }

    @Test
    void renamingSaysSoAndAnotherStationCannotReachTheChat() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", null, false);

        chats.edit(station.id(), manager.id(), chat.uid(), "Wache", RestrictionAudience.empty(), false);
        chats.edit(station.id(), manager.id(), chat.uid(), "Wache", RestrictionAudience.empty(), false);

        assertEquals("Wache", conversations.findByUid(chat.uid()).orElseThrow().name());
        assertEquals(
                List.of(SystemKind.OPENED, SystemKind.RENAMED),
                messages.before(chat.id(), null, 5).stream()
                        .map(Message::systemKind)
                        .toList());
        assertRefused(
                MessageRefusal.STATION_CHAT_NOT_HERE,
                () -> chats.edit(
                        station().id(), manager.id(), chat.uid(), "Fremd", RestrictionAudience.empty(), false));
        assertRefused(
                MessageRefusal.STATION_CHAT_NOT_HERE, () -> chats.close(station.id(), manager.id(), UUID.randomUUID()));
    }

    @Test
    void aClosedChatStaysReadableAndTakesNothingNew() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", null, false);
        var written = services.messages().send(anna.id(), chat.uid(), "Hallo", null);
        int id = written.message().id();

        chats.close(station.id(), manager.id(), chat.uid());
        chats.close(station.id(), manager.id(), chat.uid());

        var closed = Objects.requireNonNull(
                conversations.findByUid(chat.uid()).orElseThrow().station());
        assertTrue(closed.closed());
        assertEquals(manager.id(), closed.closedByAccountId());
        assertEquals(
                3,
                services.messages()
                        .page(anna.id(), chat.uid(), null, null)
                        .messages()
                        .size());
        assertRefused(
                MessageRefusal.CHAT_CLOSED, () -> services.messages().send(anna.id(), chat.uid(), "Noch was", null));
        assertRefused(MessageRefusal.CHAT_CLOSED, () -> services.messages().edit(anna.id(), chat.uid(), id, "Anders"));
        assertRefused(MessageRefusal.CHAT_CLOSED, () -> services.reactions().react(manager.id(), chat.uid(), id, "👍"));
        assertRefused(
                MessageRefusal.CHAT_CLOSED, () -> services.reactions().takeBack(manager.id(), chat.uid(), id, "👍"));
        services.messages().delete(anna.id(), chat.uid(), id);
    }

    @Test
    void reopeningSaysSoAndTakesInWhoJoinedMeanwhile() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", null, false);
        chats.close(station.id(), manager.id(), chat.uid());
        var ben = memberOf(station, "Ben");

        chats.reopen(station.id(), manager.id(), chat.uid());

        assertFalse(Objects.requireNonNull(
                        conversations.findByUid(chat.uid()).orElseThrow().station())
                .closed());
        assertTrue(activeIn(chat).contains(ben.id()));
        assertEquals(
                SystemKind.REOPENED,
                messages.before(chat.id(), null, 5).getLast().systemKind());
    }

    @Test
    void aChatThatLostItsAudienceIsClosedAndReopensOnlyWithANewOne() {
        Conversation groupChat = chats.open(station.id(), manager.id(), "Jugend", groups(group.id()), false);
        memberGroupRepo.delete(group.id());
        services.membership().reconcile(station.id());

        var listed = chats.one(station.id(), groupChat.uid());
        assertNotNull(listed.conversation().station());
        assertTrue(listed.conversation().station().closed(), "a lost audience does not fall back to everybody");
        assertTrue(listed.conversation().station().orphaned());
        assertRefused(
                MessageRefusal.STATION_CHAT_AUDIENCE_GONE,
                () -> chats.reopen(station.id(), manager.id(), groupChat.uid()));

        chats.edit(station.id(), manager.id(), groupChat.uid(), "Jugend", RestrictionAudience.empty(), false);
        chats.reopen(station.id(), manager.id(), groupChat.uid());
        assertTrue(activeIn(groupChat).containsAll(Set.of(manager.id(), anna.id())));
    }

    @Test
    void aChangeThatLeavesOutTheAudienceOrTheGuardiansIsRefused() {
        Conversation chat = chats.open(station.id(), manager.id(), "Jugend", groups(group.id()), false);

        assertRefused(
                MessageRefusal.STATION_CHAT_CHANGE_UNSAID,
                () -> chats.edit(station.id(), manager.id(), chat.uid(), "Alle", null, false));
        assertRefused(
                MessageRefusal.STATION_CHAT_CHANGE_UNSAID,
                () -> chats.edit(station.id(), manager.id(), chat.uid(), "Alle", groups(group.id()), null));
        assertEquals(Set.of(anna.id()), activeIn(chat), "nobody is taken in by a change left half said");
    }

    @Test
    void losingPartOfAnAudienceClosesTheChatRatherThanWideningIt() {
        var leader = memberOf(station, "Lea");
        var leaders = userTagRepo.create(station.id(), "Betreuer");
        userTagRepo.addMember(leaders.id(), member(leader).id());
        userTagRepo.addMember(leaders.id(), member(anna).id());
        var bothAudience = new RestrictionAudience(
                List.of(), List.of(group.id()), List.of(leaders.id()), List.of(), RestrictionMode.AND);
        Conversation chat = chats.open(station.id(), manager.id(), "Jugendbetreuer", bothAudience, false);
        assertEquals(Set.of(anna.id()), activeIn(chat));

        memberGroupRepo.delete(group.id());
        services.membership().reconcile(station.id());

        var scope = Objects.requireNonNull(
                conversations.findByUid(chat.uid()).orElseThrow().station());
        assertTrue(scope.closed());
        assertTrue(scope.orphaned());
        assertFalse(activeIn(chat).contains(leader.id()), "the leaders outside the group are not taken in");
    }

    @Test
    void aChatWhoseNamedPeopleAllLeftIsClosed() {
        var lea = memberOf(station, "Lea");
        var named = new RestrictionAudience(
                List.of(), List.of(), List.of(), List.of(member(lea).id()), RestrictionMode.OR);
        Conversation chat = chats.open(station.id(), manager.id(), "Lea", named, false);
        assertEquals(Set.of(lea.id()), activeIn(chat));

        stationMemberRepo.setFormer(member(lea).id(), true);
        services.membership().reconcile(station.id());

        var scope = Objects.requireNonNull(
                conversations.findByUid(chat.uid()).orElseThrow().station());
        assertTrue(scope.closed());
        assertEquals(Set.of(), activeIn(chat));
    }

    @Test
    void changingTheAudienceMovesPeopleInAndOut() {
        Conversation chat = chats.open(station.id(), manager.id(), "Jugend", groups(group.id()), false);
        assertEquals(Set.of(anna.id()), activeIn(chat));

        chats.edit(station.id(), manager.id(), chat.uid(), "Jugend", RestrictionAudience.empty(), false);
        assertEquals(Set.of(manager.id(), anna.id()), activeIn(chat));

        chats.edit(station.id(), manager.id(), chat.uid(), "Jugend", groups(group.id()), false);
        assertEquals(Set.of(anna.id()), activeIn(chat));
        assertEquals(
                List.of(SystemKind.OPENED),
                messages.before(chat.id(), null, 5).stream()
                        .map(Message::systemKind)
                        .toList(),
                "only a new name leaves a line");
    }

    @Test
    void guardiansAreTakenInAndShownWithThePeopleTheyAreThereFor() {
        var parent = memberOf(station, "Paula");
        guard(member(parent), member(anna));
        Conversation chat = chats.open(station.id(), manager.id(), "Jugend", groups(group.id()), false);
        assertEquals(Set.of(), activeIn(chat), "Anna has a guardian who has not turned messages on");

        chats.edit(station.id(), manager.id(), chat.uid(), "Jugend", groups(group.id()), true);
        assertEquals(Set.of(parent.id()), activeIn(chat));

        var detail = services.conversations().detail(parent.id(), chat.uid());
        assertEquals(List.of("Anna Muster"), detail.guardianFor().get(parent.id()));
        assertNotNull(detail.station());
        assertEquals(station.uid(), detail.station().uid());

        chats.edit(station.id(), manager.id(), chat.uid(), "Jugend", groups(group.id()), false);
        assertEquals(Set.of(), activeIn(chat));
    }

    @Test
    void renamingAndClosingTellTheTabsOfTheChat() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", null, false);
        var annasTab = new RecordingStream();
        services.signals().join(anna.id(), annasTab);

        chats.edit(station.id(), manager.id(), chat.uid(), "Wache", RestrictionAudience.empty(), false);
        chats.close(station.id(), manager.id(), chat.uid());
        chats.close(station.id(), manager.id(), chat.uid());

        var told = MessageEvent.conversation(chat.uid());
        assertEquals(List.of(told, told), annasTab.signals());
    }

    @Test
    void reopeningAndTheGuardianToggleTellTheTabsOnce() {
        var parent = memberOf(station, "Paula");
        guard(member(parent), member(anna));
        Conversation chat = chats.open(station.id(), manager.id(), "Jugend", groups(group.id()), false);
        chats.close(station.id(), manager.id(), chat.uid());
        var managersTab = new RecordingStream();
        var parentsTab = new RecordingStream();
        services.signals().join(manager.id(), managersTab);
        services.signals().join(parent.id(), parentsTab);

        chats.reopen(station.id(), manager.id(), chat.uid());
        chats.edit(station.id(), manager.id(), chat.uid(), "Jugend", groups(group.id()), true);

        var told = MessageEvent.conversation(chat.uid());
        assertEquals(List.of(told), parentsTab.signals(), "taken in once, by the reconciliation");
        assertEquals(List.of(), managersTab.signals(), "the manager is in no group chat of Jugend");
    }

    @Test
    void aChatOfAStationBeingMovedTakesNothingNewAndOneThatMovedIsGone() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", null, false);
        int own = services.messages()
                .send(anna.id(), chat.uid(), "Vorher", null)
                .message()
                .id();
        stationRepo.markReadOnlyForTransfer(station.id());

        assertRefused(
                MessageRefusal.CHAT_CLOSED, () -> services.messages().send(anna.id(), chat.uid(), "Noch da?", null));
        assertRefused(
                MessageRefusal.STATION_CHAT_MOVING, () -> services.messages().delete(anna.id(), chat.uid(), own));
        assertRefused(MessageRefusal.STATION_CHAT_MOVING, () -> services.reports()
                .report(manager.id(), chat.uid(), own, null));

        stationRepo.markMovedAway(station.id(), "https://elsewhere.example");
        assertRefused(MessageRefusal.CONVERSATION_NOT_HERE, () -> services.messages()
                .page(anna.id(), chat.uid(), null, null));
        assertTrue(services.conversations().list(anna.id()).stream()
                .noneMatch(summary -> summary.overview().conversation().id() == chat.id()));
    }

    @Test
    void theStationWideChatTakesGuardiansToo() {
        var parent = memberOf(station, "Paula");
        var child = memberOf(station, "Kim");
        guard(member(parent), member(child));
        stationMemberRepo.setUserType(member(parent).id(), StationUserType.TRIAL);

        Conversation chat = chats.open(station.id(), manager.id(), "Alle", null, true);

        assertTrue(activeIn(chat).contains(parent.id()), "a guardian comes along even when not in the audience");
    }

    @Test
    void membersCannotLeaveRenameOrAddPeopleButCanMute() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", null, false);
        var stranger = memberOf(station, "Sven");

        assertRefused(
                MessageRefusal.STATION_CHAT_NOT_LEFT, () -> services.groups().leave(anna.id(), chat.uid()));
        assertRefused(MessageRefusal.RENAME_NOT_A_GROUP, () -> services.groups().rename(anna.id(), chat.uid(), "Neu"));
        assertRefused(MessageRefusal.ADD_NOT_A_GROUP, () -> services.groups()
                .add(anna.id(), chat.uid(), List.of(stranger.uid())));
        assertRefused(MessageRefusal.REMOVE_NOT_A_GROUP, () -> services.groups()
                .remove(anna.id(), chat.uid(), manager.uid()));
        services.conversations().mute(anna.id(), chat.uid(), true);
        assertTrue(conversations.membership(chat.id(), anna.id()).orElseThrow().muted());
    }

    @Test
    void theListShowsStationChatsWithTheirStation() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", null, false);

        var row = services.conversations().list(anna.id()).stream()
                .filter(summary -> summary.overview().conversation().id() == chat.id())
                .findFirst()
                .orElseThrow();

        assertEquals(station.name(), Objects.requireNonNull(row.station()).name());
        assertNull(services.conversations()
                .detail(anna.id(), chat.uid())
                .guardianFor()
                .get(anna.id()));
        var groupChat = chats.open(station.id(), manager.id(), "Jugend", groups(group.id()), false);
        assertEquals(
                List.of(group.id()),
                chats.one(station.id(), groupChat.uid()).audience().groupIds());
    }

    private static RestrictionAudience groups(int... groupIds) {
        return new RestrictionAudience(
                List.of(), Arrays.stream(groupIds).boxed().toList(), List.of(), List.of(), RestrictionMode.AND);
    }

    private static RestrictionAudience tags(int... tagIds) {
        return new RestrictionAudience(
                List.of(), List.of(), Arrays.stream(tagIds).boxed().toList(), List.of(), RestrictionMode.AND);
    }

    private StationMember member(Account account) {
        return stationMemberRepo
                .findByStationAndAccount(station.id(), account.id())
                .orElseThrow();
    }

    private Set<Integer> activeIn(Conversation chat) {
        return conversations.activeMembers(chat.id()).stream()
                .map(Membership::accountId)
                .collect(Collectors.toSet());
    }
}

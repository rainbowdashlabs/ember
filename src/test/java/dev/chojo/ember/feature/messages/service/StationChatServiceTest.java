/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.messages.entity.ChatAudience;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.ConversationKind;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.Message;
import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.entity.SystemKind;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        Conversation chat = chats.open(station.id(), manager.id(), " Alle ", ChatAudience.STATION, null, null, false);

        assertEquals(ConversationKind.STATION, chat.kind());
        assertEquals("Alle", chat.name());
        assertEquals(Set.of(manager.id(), anna.id()), activeIn(chat));
        Message opened = messages.before(chat.id(), null, 5).getFirst();
        assertEquals(SystemKind.OPENED, opened.systemKind());
        assertEquals("Alle", opened.content());
        assertEquals(2, chats.list(station.id()).getFirst().members());
    }

    @Test
    void anAudienceHasOneOpenChatAtATime() {
        chats.open(station.id(), manager.id(), "Jugend", ChatAudience.GROUP, group.id(), null, false);

        assertRefused(
                MessageRefusal.STATION_CHAT_TAKEN,
                () -> chats.open(station.id(), manager.id(), "Nochmal", ChatAudience.GROUP, group.id(), null, false));
        chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, null, null, false);
        assertRefused(
                MessageRefusal.STATION_CHAT_TAKEN,
                () -> chats.open(station.id(), manager.id(), "Alle 2", ChatAudience.STATION, null, null, false));
    }

    @Test
    void aGroupOrTagOfAnotherStationIsRefused() {
        Station other = station();
        MemberGroup foreignGroup = memberGroupRepo.create(other.id(), "Fremd");
        var foreignTag = userTagRepo.create(other.id(), "Fremd");

        assertRefused(
                MessageRefusal.STATION_CHAT_GROUP_NOT_HERE,
                () -> chats.open(
                        station.id(), manager.id(), "Fremd", ChatAudience.GROUP, foreignGroup.id(), null, false));
        assertRefused(
                MessageRefusal.STATION_CHAT_TAG_NOT_HERE,
                () -> chats.open(station.id(), manager.id(), "Fremd", ChatAudience.TAG, null, foreignTag.id(), false));
    }

    @Test
    void aPrivateTagHasNoChatAndATagWithAChatStaysVisible() {
        var hidden = userTagRepo.create(station.id(), "Vertraulich", null, TagVisibility.PRIVATE);
        var shown = userTagRepo.create(station.id(), "Atemschutz");

        assertRefused(
                MessageRefusal.STATION_CHAT_TAG_PRIVATE,
                () -> chats.open(
                        station.id(), manager.id(), "Vertraulich", ChatAudience.TAG, null, hidden.id(), false));
        chats.open(station.id(), manager.id(), "Atemschutz", ChatAudience.TAG, null, shown.id(), false);
        assertEquals(1, userTagRepo.countSelectingUses(shown.id()));
    }

    @Test
    void anUnclearAudienceANamelessChatAndGuardiansForTheStationAreRefused() {
        assertRefused(
                MessageRefusal.STATION_CHAT_AUDIENCE_UNCLEAR,
                () -> chats.open(station.id(), manager.id(), "Jugend", ChatAudience.GROUP, null, null, false));
        assertRefused(
                MessageRefusal.STATION_CHAT_AUDIENCE_UNCLEAR,
                () -> chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, group.id(), null, false));
        assertRefused(
                MessageRefusal.STATION_CHAT_NAME_MISSING,
                () -> chats.open(station.id(), manager.id(), "  ", ChatAudience.STATION, null, null, false));
        assertRefused(
                MessageRefusal.STATION_CHAT_GUARDIANS_NOT_TAKEN,
                () -> chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, null, null, true));
    }

    @Test
    void aTagChatHoldsThePeopleWithTheTag() {
        var tag = userTagRepo.create(station.id(), "Atemschutz");
        userTagRepo.addMember(tag.id(), member(anna).id());

        Conversation chat =
                chats.open(station.id(), manager.id(), "Atemschutz", ChatAudience.TAG, null, tag.id(), false);

        assertEquals(Set.of(anna.id()), activeIn(chat));
        assertEquals("Atemschutz", chats.one(station.id(), chat.uid()).audienceName());
    }

    @Test
    void aMissingAudienceAMixedAudienceALongNameAndAnUnsaidGuardianChoiceAreRefused() {
        var tag = userTagRepo.create(station.id(), "Atemschutz");

        assertRefused(
                MessageRefusal.STATION_CHAT_AUDIENCE_UNCLEAR,
                () -> chats.open(station.id(), manager.id(), "Leer", null, null, null, false));
        assertRefused(
                MessageRefusal.STATION_CHAT_AUDIENCE_UNCLEAR,
                () -> chats.open(
                        station.id(), manager.id(), "Gemischt", ChatAudience.TAG, group.id(), tag.id(), false));
        assertRefused(
                MessageRefusal.STATION_CHAT_NAME_TOO_LONG,
                () -> chats.open(station.id(), manager.id(), "x".repeat(101), ChatAudience.STATION, null, null, false));
        Conversation chat =
                chats.open(station.id(), manager.id(), "Jugend", ChatAudience.GROUP, group.id(), null, false);
        assertRefused(
                MessageRefusal.STATION_CHAT_GUARDIANS_UNSAID,
                () -> chats.includeGuardians(station.id(), chat.uid(), null));
    }

    @Test
    void renamingSaysSoAndAnotherStationCannotReachTheChat() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, null, null, false);

        chats.rename(station.id(), manager.id(), chat.uid(), "Wache");
        chats.rename(station.id(), manager.id(), chat.uid(), "Wache");

        assertEquals("Wache", conversations.findByUid(chat.uid()).orElseThrow().name());
        assertEquals(
                List.of(SystemKind.OPENED, SystemKind.RENAMED),
                messages.before(chat.id(), null, 5).stream()
                        .map(Message::systemKind)
                        .toList());
        assertRefused(
                MessageRefusal.STATION_CHAT_NOT_HERE,
                () -> chats.rename(station().id(), manager.id(), chat.uid(), "Fremd"));
        assertRefused(
                MessageRefusal.STATION_CHAT_NOT_HERE, () -> chats.close(station.id(), manager.id(), UUID.randomUUID()));
    }

    @Test
    void aClosedChatStaysReadableAndTakesNothingNew() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, null, null, false);
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
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, null, null, false);
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
    void aChatWhoseAudienceHasAnotherOpenChatOrIsGoneStaysClosed() {
        Conversation first = chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, null, null, false);
        chats.close(station.id(), manager.id(), first.uid());
        chats.open(station.id(), manager.id(), "Alle neu", ChatAudience.STATION, null, null, false);
        assertRefused(MessageRefusal.STATION_CHAT_TAKEN, () -> chats.reopen(station.id(), manager.id(), first.uid()));

        Conversation groupChat =
                chats.open(station.id(), manager.id(), "Jugend", ChatAudience.GROUP, group.id(), null, false);
        memberGroupRepo.delete(group.id());
        services.membership().reconcile(station.id());
        assertRefused(
                MessageRefusal.STATION_CHAT_AUDIENCE_GONE,
                () -> chats.reopen(station.id(), manager.id(), groupChat.uid()));
    }

    @Test
    void guardiansAreTakenInAndShownWithThePeopleTheyAreThereFor() {
        var parent = memberOf(station, "Paula");
        guard(member(parent), member(anna));
        Conversation chat =
                chats.open(station.id(), manager.id(), "Jugend", ChatAudience.GROUP, group.id(), null, false);
        assertEquals(Set.of(), activeIn(chat), "Anna has a guardian who has not turned messages on");

        chats.includeGuardians(station.id(), chat.uid(), true);
        assertEquals(Set.of(parent.id()), activeIn(chat));

        var detail = services.conversations().detail(parent.id(), chat.uid());
        assertEquals(List.of("Anna Muster"), detail.guardianFor().get(parent.id()));
        assertNotNull(detail.station());
        assertEquals(station.uid(), detail.station().uid());

        chats.includeGuardians(station.id(), chat.uid(), false);
        assertEquals(Set.of(), activeIn(chat));
    }

    @Test
    void renamingAndClosingTellTheTabsOfTheChat() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, null, null, false);
        var annasTab = new RecordingStream();
        services.signals().join(anna.id(), annasTab);

        chats.rename(station.id(), manager.id(), chat.uid(), "Wache");
        chats.close(station.id(), manager.id(), chat.uid());
        chats.close(station.id(), manager.id(), chat.uid());

        var told = MessageEvent.conversation(chat.uid());
        assertEquals(List.of(told, told), annasTab.signals());
    }

    @Test
    void reopeningAndTheGuardianToggleTellTheTabsOnce() {
        var parent = memberOf(station, "Paula");
        guard(member(parent), member(anna));
        Conversation chat =
                chats.open(station.id(), manager.id(), "Jugend", ChatAudience.GROUP, group.id(), null, false);
        chats.close(station.id(), manager.id(), chat.uid());
        var managersTab = new RecordingStream();
        var parentsTab = new RecordingStream();
        services.signals().join(manager.id(), managersTab);
        services.signals().join(parent.id(), parentsTab);

        chats.reopen(station.id(), manager.id(), chat.uid());
        chats.includeGuardians(station.id(), chat.uid(), true);

        var told = MessageEvent.conversation(chat.uid());
        assertEquals(List.of(told), parentsTab.signals(), "taken in once, by the reconciliation");
        assertEquals(List.of(), managersTab.signals(), "the manager is in no group chat of Jugend");
    }

    @Test
    void aChatOfAStationBeingMovedTakesNothingNewAndOneThatMovedIsGone() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, null, null, false);
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
    void theStationWideChatTakesNoGuardians() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, null, null, false);

        assertRefused(
                MessageRefusal.STATION_CHAT_GUARDIANS_NOT_TAKEN,
                () -> chats.includeGuardians(station.id(), chat.uid(), true));
    }

    @Test
    void membersCannotLeaveRenameOrAddPeopleButCanMute() {
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, null, null, false);
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
        Conversation chat = chats.open(station.id(), manager.id(), "Alle", ChatAudience.STATION, null, null, false);

        var row = services.conversations().list(anna.id()).stream()
                .filter(summary -> summary.overview().conversation().id() == chat.id())
                .findFirst()
                .orElseThrow();

        assertEquals(station.name(), Objects.requireNonNull(row.station()).name());
        assertNull(services.conversations()
                .detail(anna.id(), chat.uid())
                .guardianFor()
                .get(anna.id()));
        assertEquals(
                "Jugend",
                chats.one(
                                station.id(),
                                chats.open(
                                                station.id(),
                                                manager.id(),
                                                "Jugend",
                                                ChatAudience.GROUP,
                                                group.id(),
                                                null,
                                                false)
                                        .uid())
                        .audienceName());
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

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.messages.entity.ConversationKind;
import dev.chojo.ember.feature.messages.entity.MessageEventKind;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct conversations: who may start one, that a pair has only one, the list, reading and muting,
 * and that nobody opens a conversation they are not in.
 */
class ConversationServiceTest extends MessageTestBase {
    private MessageFixtures services;
    private Station station;
    private Account anna;
    private Account ben;

    @BeforeEach
    void people() {
        services = MessageFixtures.build();
        station = station();
        anna = memberOf(station, "Anna");
        ben = memberOf(station, "Ben");
    }

    @Test
    void twoPeopleOfOneStationShareOneDirectConversation() {
        var first = services.conversations().openDirect(anna.id(), ben.uid());
        var second = services.conversations().openDirect(ben.id(), anna.uid());

        assertEquals(first.uid(), second.uid());
        assertEquals(ConversationKind.DIRECT, first.kind());
        assertEquals(
                2,
                services.conversations()
                        .detail(anna.id(), first.uid())
                        .members()
                        .size());
    }

    @Test
    void somebodyWithoutACommonStationCannotBeWrittenTo() {
        var stranger = memberOf(station(), "Fremd");

        assertRefused(MessageRefusal.PERSON_NOT_REACHABLE, () -> services.conversations()
                .openDirect(anna.id(), stranger.uid()));
    }

    @Test
    void aFormerMemberIsNoLongerReachable() {
        var other = station();
        var formerAnna = join(other, anna);
        var cora = memberOf(other, "Cora");
        stationMemberRepo.setFormer(formerAnna.id(), true);

        assertRefused(MessageRefusal.PERSON_NOT_REACHABLE, () -> services.conversations()
                .openDirect(anna.id(), cora.uid()));
    }

    @Test
    void nobodyWritesToThemselvesOrToAnUnknownAccount() {
        assertRefused(MessageRefusal.PERSON_NOT_REACHABLE, () -> services.conversations()
                .openDirect(anna.id(), anna.uid()));
        assertRefused(MessageRefusal.PERSON_NOT_REACHABLE, () -> services.conversations()
                .openDirect(anna.id(), UUID.randomUUID()));
    }

    @Test
    void aBlockingPersonCannotBeWrittenToAndTheBlockedOneIsNotTold() {
        services.blocks().block(ben.id(), anna.uid());

        assertRefused(MessageRefusal.PERSON_NOT_REACHABLE, () -> services.conversations()
                .openDirect(anna.id(), ben.uid()));
        assertTrue(services.settings().reachable(anna.id(), "").isEmpty());
    }

    @Test
    void anExistingConversationStaysWhenTheStationIsNoLongerShared() {
        var direct = services.conversations().openDirect(anna.id(), ben.uid());
        stationMemberRepo
                .findAllByAccountId(ben.id())
                .forEach(member -> stationMemberRepo.setFormer(member.id(), true));

        assertEquals(
                direct.uid(),
                services.conversations().openDirect(anna.id(), ben.uid()).uid());
        services.messages().send(anna.id(), direct.uid(), "Noch da?", null);
    }

    @Test
    void theListShowsTheOtherSideTheNewestLineAndWhatIsUnread() {
        var direct = services.conversations().openDirect(anna.id(), ben.uid());
        services.messages().send(anna.id(), direct.uid(), "Hallo @[account/%s:Ben]".formatted(ben.uid()), null);
        services.messages().send(anna.id(), direct.uid(), "Bist du da?", null);

        var bens = services.conversations().list(ben.id());
        assertEquals(1, bens.size());
        var row = bens.getFirst();
        assertEquals(anna.uid(), row.others().getFirst().uid());
        assertEquals(2, row.overview().unread());
        assertEquals("Bist du da?", row.newest().content());
        assertEquals(
                0,
                services.conversations().list(anna.id()).getFirst().overview().unread());
        assertEquals(2, services.conversations().unread(ben.id()));
    }

    @Test
    void readingMovesForwardOnlyAndClearsTheBadge() {
        var direct = services.conversations().openDirect(anna.id(), ben.uid());
        int first = services.messages()
                .send(anna.id(), direct.uid(), "Eins", null)
                .message()
                .id();
        int second = services.messages()
                .send(anna.id(), direct.uid(), "Zwei", null)
                .message()
                .id();

        services.conversations().read(ben.id(), direct.uid(), second);
        services.conversations().read(ben.id(), direct.uid(), first);

        assertEquals(0, services.conversations().unread(ben.id()));
        assertEquals(
                second,
                services.conversations().detail(ben.id(), direct.uid()).mine().lastReadMessageId());
    }

    @Test
    void aMessageOfAnotherConversationCannotBeMarkedRead() {
        var direct = services.conversations().openDirect(anna.id(), ben.uid());
        var cora = memberOf(station, "Cora");
        var other = services.conversations().openDirect(anna.id(), cora.uid());
        int elsewhere = services.messages()
                .send(anna.id(), other.uid(), "Hier", null)
                .message()
                .id();

        assertRefused(MessageRefusal.MESSAGE_NOT_HERE, () -> services.conversations()
                .read(ben.id(), direct.uid(), elsewhere));
    }

    @Test
    void aMutedConversationLeavesTheBadgeButStaysInTheList() {
        var direct = services.conversations().openDirect(anna.id(), ben.uid());
        services.messages().send(anna.id(), direct.uid(), "Hallo", null);

        services.conversations().mute(ben.id(), direct.uid(), true);

        assertEquals(0, services.conversations().unread(ben.id()));
        var row = services.conversations().list(ben.id()).getFirst();
        assertTrue(row.overview().muted());
        assertEquals(1, row.overview().unread());
    }

    @Test
    void nobodyOpensAConversationTheyAreNotIn() {
        var direct = services.conversations().openDirect(anna.id(), ben.uid());
        var cora = memberOf(station, "Cora");

        assertRefused(MessageRefusal.CONVERSATION_NOT_HERE, () -> services.conversations()
                .detail(cora.id(), direct.uid()));
        assertRefused(MessageRefusal.CONVERSATION_NOT_HERE, () -> services.conversations()
                .detail(cora.id(), UUID.randomUUID()));
        assertRefused(MessageRefusal.CONVERSATION_NOT_HERE, () -> services.messages()
                .send(cora.id(), direct.uid(), "Hallo", null));
    }

    @Test
    void aDirectConversationThatWasPutAwayComesBackWithTheNextMessage() {
        var direct = services.conversations().openDirect(anna.id(), ben.uid());
        services.messages().send(anna.id(), direct.uid(), "Erste", null);
        services.groups().leave(ben.id(), direct.uid());

        assertTrue(services.conversations().list(ben.id()).isEmpty());

        services.messages().send(anna.id(), direct.uid(), "Zweite", null);

        assertEquals(1, services.conversations().list(ben.id()).size());
    }

    @Test
    void theStreamsOfBothSidesHearOfANewMessage() {
        var annas = new RecordingStream();
        var bens = new RecordingStream();
        services.signals().join(anna.id(), annas);
        services.signals().join(ben.id(), bens);
        var direct = services.conversations().openDirect(anna.id(), ben.uid());

        int id = services.messages()
                .send(anna.id(), direct.uid(), "Hallo", null)
                .message()
                .id();

        var signal = bens.signals().getLast();
        assertEquals(MessageEventKind.MESSAGE, signal.kind());
        assertEquals(direct.uid(), signal.conversationUid());
        assertEquals(id, signal.messageId());
        assertEquals(MessageEventKind.MESSAGE, annas.signals().getLast().kind());
        assertEquals("ready", bens.events.getFirst());
    }

    @Test
    void aDirectConversationHasNoOwnerAndNoName() {
        var direct = services.conversations().openDirect(anna.id(), ben.uid());
        var detail = services.conversations().detail(anna.id(), direct.uid());

        assertNull(detail.conversation().name());
        assertNull(detail.conversation().ownerAccountId());
        assertFalse(detail.conversation().isGroup());
    }
}

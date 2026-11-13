/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.MessageEventKind;
import dev.chojo.ember.feature.messages.entity.Reaction;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reactions: once per person, emoji and message, only standard emoji, never on a system line or a
 * deleted message, never unread, and hidden from somebody who blocked the person reacting.
 */
class ReactionServiceTest extends MessageTestBase {
    private MessageFixtures services;
    private Account anna;
    private Account ben;
    private Account cora;
    private Conversation group;
    private int message;

    @BeforeEach
    void people() {
        services = MessageFixtures.build();
        Station station = station();
        anna = memberOf(station, "Anna");
        ben = memberOf(station, "Ben");
        cora = memberOf(station, "Cora");
        group = services.groups().create(anna.id(), List.of(ben.uid(), cora.uid()), "Runde");
        message = services.messages()
                .send(anna.id(), group.uid(), "Wer kommt mit?", null)
                .message()
                .id();
    }

    private List<String> emojiSeenBy(Account reader) {
        return services.messages().one(reader.id(), group.uid(), message).reactions().stream()
                .map(Reaction::emoji)
                .toList();
    }

    @Test
    void eachPersonReactsWithEachEmojiOnce() {
        services.reactions().react(ben.id(), group.uid(), message, "👍");
        services.reactions().react(ben.id(), group.uid(), message, "👍");
        services.reactions().react(cora.id(), group.uid(), message, "👍");
        services.reactions().react(ben.id(), group.uid(), message, "🎉");

        assertEquals(List.of("👍", "👍", "🎉"), emojiSeenBy(anna));
    }

    @Test
    void aReactionIsTakenBack() {
        services.reactions().react(ben.id(), group.uid(), message, "👍");

        var after = services.reactions().takeBack(ben.id(), group.uid(), message, "👍");
        services.reactions().takeBack(ben.id(), group.uid(), message, "👍");

        assertTrue(after.reactions().isEmpty());
    }

    @Test
    void sequencesFlagsAndKeycapsAreEmojiButTextIsNot() {
        services.reactions().react(ben.id(), group.uid(), message, "👨‍👩‍👧");
        services.reactions().react(ben.id(), group.uid(), message, "🇩🇪");
        services.reactions().react(ben.id(), group.uid(), message, "1️⃣");
        services.reactions().react(ben.id(), group.uid(), message, "👍🏽");

        assertEquals(4, emojiSeenBy(anna).size());
        for (String text : List.of("a", "1", "", "lol 👍", "‍")) {
            assertRefused(MessageRefusal.EMOJI_NOT_TAKEN, () -> services.reactions()
                    .react(ben.id(), group.uid(), message, text));
        }
    }

    @Test
    void systemLinesAndDeletedMessagesTakeNoReactions() {
        services.groups().rename(anna.id(), group.uid(), "Neue Runde");
        int line = services.messages()
                .page(anna.id(), group.uid(), null, null)
                .messages()
                .getLast()
                .message()
                .id();
        services.messages().delete(anna.id(), group.uid(), message);

        assertRefused(
                MessageRefusal.REACTION_NOT_TAKEN, () -> services.reactions().react(ben.id(), group.uid(), line, "👍"));
        assertRefused(MessageRefusal.REACTION_NOT_TAKEN, () -> services.reactions()
                .react(ben.id(), group.uid(), message, "👍"));
    }

    @Test
    void aReactionArrivesLiveButIsNeverUnread() {
        services.conversations().read(anna.id(), group.uid(), message);
        var annas = new RecordingStream();
        services.signals().join(anna.id(), annas);

        services.reactions().react(ben.id(), group.uid(), message, "👍");

        assertEquals(MessageEventKind.MESSAGE, annas.signals().getLast().kind());
        assertEquals(0, services.conversations().unread(anna.id()));
    }

    @Test
    void theReactionsOfABlockedPersonAreNeitherShownNorCounted() {
        services.reactions().react(ben.id(), group.uid(), message, "👍");
        services.reactions().react(cora.id(), group.uid(), message, "👍");

        services.blocks().block(anna.id(), ben.uid());

        assertEquals(List.of("👍"), emojiSeenBy(anna));
        assertEquals(List.of("👍", "👍"), emojiSeenBy(cora));
    }

    @Test
    void nobodyOutsideReactsOrTakesBack() {
        var stranger = memberOf(station(), "Fremd");

        assertRefused(MessageRefusal.CONVERSATION_NOT_HERE, () -> services.reactions()
                .react(stranger.id(), group.uid(), message, "👍"));
        assertRefused(MessageRefusal.CONVERSATION_NOT_HERE, () -> services.reactions()
                .takeBack(stranger.id(), group.uid(), message, "👍"));
    }
}

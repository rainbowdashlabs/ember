/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.api.refusal.MessageRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writing, reading in pages, answering, changing and deleting messages, and every refusal of it:
 * empty and long texts, answers to elsewhere, other people's messages, and a blocked writer.
 */
class MessageServiceTest extends MessageTestBase {
    private MessageFixtures services;
    private Station station;
    private Account anna;
    private Account ben;
    private Conversation direct;

    @BeforeEach
    void people() {
        services = MessageFixtures.build();
        station = station();
        anna = memberOf(station, "Anna");
        ben = memberOf(station, "Ben");
        direct = services.conversations().openDirect(anna.id(), ben.uid());
    }

    private int send(Account from, String text) {
        return services.messages()
                .send(from.id(), direct.uid(), text, null)
                .message()
                .id();
    }

    @Test
    void aMessageIsKeptWithoutSurroundingSpace() {
        var written = services.messages().send(anna.id(), direct.uid(), "  Hallo\nWelt  ", null);

        assertEquals("Hallo\nWelt", written.message().content());
        assertEquals(anna.uid(), written.message().author().uid());
    }

    @Test
    void anEmptyOrOverlongMessageIsRefused() {
        assertRefused(
                MessageRefusal.MESSAGE_EMPTY, () -> services.messages().send(anna.id(), direct.uid(), "  ", null));
        assertRefused(MessageRefusal.MESSAGE_TOO_LONG, () -> services.messages()
                .send(anna.id(), direct.uid(), "x".repeat(4001), null));
    }

    @Test
    void anAnswerQuotesTheMessageItAnswers() {
        int question = send(ben, "Kommst du?");

        var answer = services.messages().send(anna.id(), direct.uid(), "Ja", question);

        assertNotNull(answer.replyTo());
        assertEquals("Kommst du?", answer.replyTo().content());
    }

    @Test
    void anAnswerToAnotherConversationIsRefused() {
        var cora = memberOf(station, "Cora");
        var other = services.conversations().openDirect(anna.id(), cora.uid());
        int elsewhere = services.messages()
                .send(anna.id(), other.uid(), "Dort", null)
                .message()
                .id();

        assertRefused(MessageRefusal.REPLY_NOT_HERE, () -> services.messages()
                .send(anna.id(), direct.uid(), "Hier", elsewhere));
    }

    @Test
    void pagesReadBackAndOnWithoutGaps() {
        for (int i = 1; i <= MessageService.PAGE_SIZE + 5; i++) send(anna, "Nachricht " + i);

        var newest = services.messages().page(ben.id(), direct.uid(), null, null);
        assertTrue(newest.hasMore());
        assertEquals(MessageService.PAGE_SIZE, newest.messages().size());
        assertEquals(
                "Nachricht " + (MessageService.PAGE_SIZE + 5),
                newest.messages().getLast().message().content());

        int oldestShown = newest.messages().getFirst().message().id();
        var older = services.messages().page(ben.id(), direct.uid(), oldestShown, null);
        assertFalse(older.hasMore());
        assertEquals(5, older.messages().size());

        var onwards = services.messages()
                .page(
                        ben.id(),
                        direct.uid(),
                        null,
                        older.messages().getFirst().message().id());
        assertTrue(onwards.hasMore());
        assertEquals("Nachricht 2", onwards.messages().getFirst().message().content());
    }

    @Test
    void aPageGoesOneWayAtATime() {
        assertRefused(MessageRefusal.PAGE_BOTH_WAYS, () -> services.messages().page(ben.id(), direct.uid(), 1, 1));
    }

    @Test
    void theAuthorChangesTheirMessageAndNobodyElseDoes() {
        int id = send(anna, "Tippfeler");

        var changed = services.messages().edit(anna.id(), direct.uid(), id, "Tippfehler");

        assertEquals("Tippfehler", changed.message().content());
        assertNotNull(changed.message().editedAt());
        assertRefused(
                MessageRefusal.EDIT_NOT_YOURS, () -> services.messages().edit(ben.id(), direct.uid(), id, "Meins"));
    }

    @Test
    void theAuthorDeletesTheirMessageAndItsPlaceStays() {
        int id = send(anna, "Weg damit");

        assertRefused(MessageRefusal.DELETE_NOT_YOURS, () -> services.messages().delete(ben.id(), direct.uid(), id));
        services.messages().delete(anna.id(), direct.uid(), id);

        var kept = services.messages().one(ben.id(), direct.uid(), id).message();
        assertTrue(kept.deleted());
        assertEquals("", kept.content());
        assertRefused(MessageRefusal.EDIT_DELETED, () -> services.messages().edit(anna.id(), direct.uid(), id, "Doch"));
        assertEquals(0, services.conversations().unread(ben.id()), "a deleted message is not unread");
    }

    @Test
    void aMessageOfAnotherConversationIsNotHere() {
        int id = send(anna, "Hallo");
        var cora = memberOf(station, "Cora");
        var other = services.conversations().openDirect(cora.id(), anna.uid());

        assertRefused(MessageRefusal.MESSAGE_NOT_HERE, () -> services.messages().one(cora.id(), other.uid(), id));
    }

    @Test
    void aWriterWhomTheOtherSideBlockedIsNotDeliveredAndNotToldWhy() {
        send(anna, "Vorher");
        services.blocks().block(ben.id(), anna.uid());

        assertRefused(MessageRefusal.MESSAGE_NOT_DELIVERED, () -> send(anna, "Nachher"));
        send(ben, "Ich darf noch");
    }

    @Test
    void aDirectConversationWithADeletedAccountTakesNoMore() {
        var gone = memberOf(station, "Weg");
        var lonely = services.conversations().openDirect(anna.id(), gone.uid());
        new MessageErasure(new ConversationRepository()).forget(gone.id());
        accountRepo.delete(gone.id());

        assertRefused(MessageRefusal.MESSAGE_NOT_DELIVERED, () -> services.messages()
                .send(anna.id(), lonely.uid(), "Hallo?", null));
    }

    @Test
    void theWriterHasReadUpToTheirOwnMessage() {
        send(ben, "Frage");
        send(anna, "Antwort");

        assertEquals(0, services.conversations().unread(anna.id()));
        assertEquals(
                List.of(),
                services.conversations().list(anna.id()).stream()
                        .filter(row -> row.overview().unread() > 0)
                        .toList());
    }
}

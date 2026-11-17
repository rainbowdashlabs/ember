/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessagePeopleRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import dev.chojo.ember.feature.push.repository.PushOutboxRepository;
import dev.chojo.ember.feature.push.repository.PushSubscriptionRepository;
import dev.chojo.ember.feature.push.service.PushService;
import dev.chojo.ember.feature.push.service.VapidKeys;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A new message is pushed to everybody taking part except its writer, whoever muted the
 * conversation, whoever has Ember open and whoever switched message pushes off; the push names the
 * conversation as the reader knows it, with who wrote what.
 */
class MessagePushesTest extends MessageTestBase {
    private final PushSubscriptionRepository subscriptions = new PushSubscriptionRepository();
    private final PushOutboxRepository outbox = new PushOutboxRepository();
    private MessageFixtures services;
    private MessagePushes pushes;
    private Account anna;
    private Account ben;
    private Account mia;
    private int benDevice;
    private int miaDevice;

    @BeforeEach
    void people() {
        services = MessageFixtures.build();
        when(services.instance().pushEnabled()).thenReturn(true);
        pushes = new MessagePushes(
                new ConversationRepository(),
                new MessageRepository(),
                new StationChatRepository(),
                services.signals(),
                new PushService(
                        subscriptions,
                        outbox,
                        mock(VapidKeys.class),
                        mock(RemoteUrlValidator.class),
                        services.instance()),
                services.instance(),
                new Api());
        Station station = station();
        anna = memberOf(station, "Anna");
        ben = memberOf(station, "Ben");
        mia = memberOf(station, "Mia");
        benDevice = device(ben);
        miaDevice = device(mia);
    }

    @Test
    void theOthersArePushedAndTheWriterIsNot() {
        Conversation group = services.groups().create(anna.id(), List.of(ben.uid(), mia.uid()), "Atemschutz");
        int message = send(group, anna, "Übung morgen um sieben");
        device(anna);

        pushes.queue(group, anna.id(), message);

        assertEquals(1, queuedFor(benDevice));
        assertEquals(1, queuedFor(miaDevice));
        var push = pushes.forReader(message, ben.id()).orElseThrow();
        assertEquals("Atemschutz", push.title());
        assertTrue(push.body().startsWith("Anna") && push.body().endsWith(": Übung morgen um sieben"), push.body());
        assertTrue(push.url().endsWith("/messages/" + group.uid()), push.url());
    }

    @Test
    void aMutedConversationAnOpenWindowAndASwitchedOffAccountStayQuiet() {
        Conversation group = services.groups().create(anna.id(), List.of(ben.uid(), mia.uid()), null);
        services.conversations().mute(ben.id(), group.uid(), true);
        new MessagePeopleRepository().setPushWanted(mia.id(), false);
        int message = send(group, anna, "Hallo");

        pushes.queue(group, anna.id(), message);

        assertEquals(0, queuedFor(benDevice), "muted");
        assertEquals(0, queuedFor(miaDevice), "switched off");
        assertTrue(pushes.forReader(message, ben.id()).isEmpty(), "a muted reader is told nothing");
    }

    /** Blocking hides a writer in a shared group, so their messages do not reach the blocker's lock screen. */
    @Test
    void aBlockedWritersMessagesAreNotPushedToWhoeverBlockedThem() {
        Conversation group = services.groups().create(anna.id(), List.of(ben.uid(), mia.uid()), "Funk");
        services.blocks().block(ben.id(), anna.uid());
        int message = send(group, anna, "Hallo zusammen");

        pushes.queue(group, anna.id(), message);

        assertEquals(0, queuedFor(benDevice), "ben blocked anna");
        assertEquals(1, queuedFor(miaDevice));
        assertTrue(pushes.forReader(message, ben.id()).isEmpty());
    }

    /** A ward whose guardian has not allowed messages gets none pushed either. */
    @Test
    void aWardWithoutMessagesGetsNoPush() {
        Conversation group = services.groups().create(anna.id(), List.of(ben.uid(), mia.uid()), "Jugend");
        guard(
                stationMemberRepo.findAllByAccountId(ben.id()).getFirst(),
                stationMemberRepo.findAllByAccountId(mia.id()).getFirst());
        int message = send(group, anna, "Morgen Übung");

        pushes.queue(group, anna.id(), message);

        assertEquals(0, queuedFor(miaDevice));
        assertTrue(pushes.forReader(message, mia.id()).isEmpty());
    }

    /** A message read in Ember before its push went out is not pushed after the fact. */
    @Test
    void aMessageReadBeforeItsPushIsNotPushed() {
        Conversation direct = services.conversations().openDirect(anna.id(), ben.uid());
        int message = send(direct, anna, "Gelesen?");

        services.conversations().read(ben.id(), direct.uid(), message);

        assertTrue(pushes.forReader(message, ben.id()).isEmpty());
    }

    @Test
    void whoeverHasEmberOpenIsNotPushed() {
        Conversation direct = services.conversations().openDirect(anna.id(), ben.uid());
        services.signals().join(ben.id(), (event, data) -> true);
        int message = send(direct, anna, "Bist du da?");

        pushes.queue(direct, anna.id(), message);

        assertEquals(0, queuedFor(benDevice));
    }

    @Test
    void aDirectConversationIsNamedByTheWriterAndADeletedMessageSaysNothing() {
        Conversation direct = services.conversations().openDirect(anna.id(), ben.uid());
        int message = send(direct, anna, "Kommst du?");

        assertTrue(pushes.forReader(message, ben.id()).orElseThrow().title().startsWith("Anna"));

        services.messages().delete(anna.id(), direct.uid(), message);
        assertTrue(pushes.forReader(message, ben.id()).isEmpty());
    }

    private int send(Conversation conversation, Account writer, String text) {
        return services.messages()
                .send(writer.id(), conversation.uid(), text, null)
                .message()
                .id();
    }

    private int device(Account account) {
        return subscriptions
                .register(
                        account.id(),
                        signIn(account.id()),
                        "https://push.test/" + UUID.randomUUID(),
                        new byte[65],
                        new byte[16],
                        "")
                .id();
    }

    private static int queuedFor(int subscriptionId) {
        return query("SELECT count(*) AS rows FROM push_outbox WHERE subscription_id = :id AND kind = 'MESSAGE';")
                .single(call().bind("id", subscriptionId))
                .map(row -> row.getInt("rows"))
                .first()
                .orElse(0);
    }

    private static int signIn(int accountId) {
        return query("""
                        INSERT INTO account_session (account_id, token_hash, expires_at)
                        VALUES (:account_id, :token_hash, :expires_at) RETURNING id;""")
                .single(call().bind("account_id", accountId)
                        .bind(
                                "token_hash",
                                HexFormat.of()
                                        .formatHex(UUID.randomUUID()
                                                .toString()
                                                .repeat(2)
                                                .getBytes())
                                        .substring(0, 64))
                        .bind("expires_at", Instant.now().plus(Duration.ofDays(1)), INSTANT_TIMESTAMP))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }
}

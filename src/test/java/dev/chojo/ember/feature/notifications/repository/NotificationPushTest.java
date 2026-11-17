/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.messages.service.MessagePushes;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.notifications.service.NotificationVisibility;
import dev.chojo.ember.feature.push.repository.PushOutboxRepository;
import dev.chojo.ember.feature.push.repository.PushSubscriptionRepository;
import dev.chojo.ember.feature.push.service.EcP256;
import dev.chojo.ember.feature.push.service.PushMessages;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A notification written for somebody is queued as a push to their devices in the same statement,
 * where they want that type pushed, and the push says what the inbox says and opens what the
 * notification is about.
 */
class NotificationPushTest extends RepositoryTestBase {
    private static final NotificationData NEWS = NotificationData.of(
            new NotificationParams.NewNews("Dienstplan Dezember", "Anna", "Der Plan steht."),
            new NotificationData.NotificationLink("news-detail", Map.of("id", "7")));

    private final PushSubscriptionRepository subscriptions = new PushSubscriptionRepository();
    private final PushOutboxRepository outbox = new PushOutboxRepository();
    private final PushMessages messages = new PushMessages(
            new NotificationText(),
            applicationSettingRepo,
            notificationRepo,
            new NotificationVisibility(new ModuleAvailability(stationRepo, clusterRepo), stationMemberRepo),
            mock(MessagePushes.class),
            new Api());
    private Station station;
    private Account account;
    private int memberId;
    private int device;
    private Cluster cluster;

    @BeforeEach
    void setUp() {
        station = stationRepo.create("Push-Wache " + UUID.randomUUID());
        account = accountRepo.create("notification-push-" + UUID.randomUUID() + "@test.com", "Nina", "Push");
        memberId = stationMemberRepo.create(station.id(), account.id()).id();
        device = subscriptions
                .register(
                        account.id(),
                        signIn(account.id()),
                        "https://push.test/" + UUID.randomUUID(),
                        EcP256.uncompressed((ECPublicKey) EcP256.generate().getPublic()),
                        new byte[16],
                        "")
                .id();
    }

    @AfterEach
    void cleanup() {
        if (cluster != null) clusterRepo.delete(cluster.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aNotificationIsQueuedForTheReadersDevicesAndSaysWhatTheInboxSays() {
        notificationRepo.insertForStation(
                StationAudience.member(memberId), NotificationType.NEW_NEWS, NEWS, Delivery.EVERY_TIME);

        var queued = queued();
        assertEquals(1, queued.size());
        assertEquals("NOTIFICATION", queued.getFirst().kind());

        var message = messages.render(queued.getFirst(), account.id()).orElseThrow();
        assertTrue(message.body().contains("Dienstplan Dezember"), message.body());
        assertTrue(message.url().contains("read="), "a tap marks it read: " + message.url());
        assertTrue(message.url().contains(station.uid().toString()), "and lands on its station: " + message.url());
    }

    @Test
    void aTypeTheReaderDoesNotWantPushedIsNotQueued() {
        notificationSettingsRepo.upsert(memberId, NotificationType.NEW_NEWS, true, false, true, false);

        notificationRepo.insertForStation(
                StationAudience.member(memberId), NotificationType.NEW_NEWS, NEWS, Delivery.EVERY_TIME);

        assertTrue(queued().isEmpty());
    }

    /** A notification read in Ember before its push went out is not pushed after the fact. */
    @Test
    void aNotificationReadBeforeItsPushIsNotPushed() {
        notificationRepo.insertForStation(
                StationAudience.member(memberId), NotificationType.NEW_NEWS, NEWS, Delivery.EVERY_TIME);
        var queued = queued();
        query("UPDATE notification SET acknowledged_at = now() WHERE id = :id;")
                .single(call().bind("id", queued.getFirst().notificationId()))
                .update();

        assertTrue(messages.render(queued.getFirst(), account.id()).isEmpty());
    }

    /** A notification of a module the station switched off is kept but never shown, so it is not pushed. */
    @Test
    void aNotificationTheInboxHidesIsNotPushed() {
        stationRepo.setDisabledModules(station.id(), Set.of(StationModule.NEWS));
        notificationRepo.insertForStation(
                StationAudience.member(memberId), NotificationType.NEW_NEWS, NEWS, Delivery.EVERY_TIME);

        assertTrue(messages.render(queued().getFirst(), account.id()).isEmpty());
    }

    /** The person acting is left out, and so are their devices. */
    @Test
    void whoeverActedGetsNoPush() {
        notificationRepo.insertForStation(
                StationAudience.member(memberId).except(memberId),
                NotificationType.NEW_NEWS,
                NEWS,
                Delivery.EVERY_TIME);

        assertTrue(queued().isEmpty());
    }

    /** A notification held back as already waiting unread is not pushed again either. */
    @Test
    void aNotificationWaitingUnreadIsNotPushedTwice() {
        notificationRepo.insertForStation(
                StationAudience.member(memberId), NotificationType.NEW_NEWS, NEWS, Delivery.ONCE_WHILE_UNREAD);
        notificationRepo.insertForStation(
                StationAudience.member(memberId), NotificationType.NEW_NEWS, NEWS, Delivery.ONCE_WHILE_UNREAD);

        assertEquals(1, queued().size());
    }

    /** A device whose sign-in has run out gets nothing queued. */
    @Test
    void aDeviceWhoseSignInRanOutGetsNothing() {
        query("UPDATE account_session SET expires_at = now() - INTERVAL '1 minute' WHERE account_id = :id;")
                .single(call().bind("id", account.id()))
                .update();

        notificationRepo.insertForStation(
                StationAudience.member(memberId), NotificationType.NEW_NEWS, NEWS, Delivery.EVERY_TIME);

        assertEquals(0, rowsFor(device));
    }

    /** A guardian reached for their ward gets the push on their own devices. */
    @Test
    void aGuardianIsPushedForTheirWard() {
        var ward = stationMemberRepo.createWithoutAccount(station.id(), "Kind Push");
        stationMemberRepo.addManager(memberId, ward.id());

        notificationRepo.insertForStation(
                StationAudience.guardiansOf(List.of(ward.id())), NotificationType.NEW_NEWS, NEWS, Delivery.EVERY_TIME);

        assertEquals(1, queued().size());
    }

    @Test
    void anAssociationsNotificationFollowsTheMembersOwnSwitch() {
        cluster = clusterRepo.create("Push-Verband " + UUID.randomUUID(), null, station.id());
        var clusterMember = clusterRepo.addMember(cluster.id(), account.id(), ClusterUserType.CLUSTER_ADMIN);

        notificationRepo.insertForCluster(
                List.of(clusterMember.id()), List.of(), NotificationType.NEW_NEWS, NEWS, Delivery.EVERY_TIME);
        assertEquals(1, queued().size(), "on by default");

        clusterRepo.setPushEnabled(clusterMember.id(), false);
        notificationRepo.insertForCluster(
                List.of(clusterMember.id()), List.of(), NotificationType.NEW_NEWS, NEWS, Delivery.EVERY_TIME);
        assertTrue(queued().isEmpty(), "nothing more once switched off");
    }

    private List<PushOutboxRepository.Due> queued() {
        return outbox.claimDue(500, Duration.ofMinutes(10)).stream()
                .filter(push -> push.subscriptionId() == device)
                .toList();
    }

    private static int rowsFor(int subscriptionId) {
        return query("SELECT count(*) AS rows FROM push_outbox WHERE subscription_id = :id;")
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

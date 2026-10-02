/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.service.NotificationInbox;
import dev.chojo.ember.feature.notifications.service.NotificationPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The association's inbox, over HTTP: the same questions a station member's inbox answers, asked of
 * the caller's membership in the association.
 */
class ClusterNotificationRoutesTest {
    private static final int CLUSTER_ID = 5;
    private static final int CLUSTER_MEMBER_ID = 17;
    private static final Recipient OFFICE = Recipient.clusterMember(CLUSTER_MEMBER_ID);

    private NotificationInbox inbox;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        inbox = mock(NotificationInbox.class);
        harness = RouteHarness.serving(new ClusterNotificationRoutes(inbox, mock(NotificationPreferences.class)));
    }

    @Test
    void theUnreadNotificationsOfTheCallerAreListed() {
        var waiting = new Notification(
                3,
                null,
                CLUSTER_MEMBER_ID,
                NotificationType.CLUSTER_APPLICATION_SUBMITTED,
                NotificationData.of(
                        new NotificationParams.ClusterApplicationSubmitted("Wache Süd"),
                        NotificationLinks.clusterMembers()),
                Instant.parse("2026-10-01T08:00:00Z"),
                null);
        when(inbox.unread(OFFICE)).thenReturn(List.of(waiting));

        var answer = harness.request(
                client -> client.get(PREFIX + "/cluster/notifications/unacknowledged", harness.as(office())));

        assertEquals(200, answer.code());
        var listed = json(answer);
        assertEquals(1, listed.size());
        assertEquals(3, listed.get(0).path("id").asInt());
        assertEquals("cluster-members", listed.get(0).path("link").path("route").asString());
    }

    @Test
    void theCountAndTheAcknowledgementsReachTheCallersFeed() {
        when(inbox.countUnread(OFFICE)).thenReturn(4);

        harness.run((server, client) -> {
            var count = client.get(PREFIX + "/cluster/notifications/count", harness.as(office()));
            assertEquals(4, json(count).path("count").asInt());
            assertEquals(
                    204,
                    client.post(PREFIX + "/cluster/notifications/3/acknowledge", null, harness.as(office()))
                            .code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/cluster/notifications/acknowledge-all", null, harness.as(office()))
                            .code());
        });

        verify(inbox).acknowledge(OFFICE, 3);
        verify(inbox).acknowledgeAll(OFFICE);
    }

    @Test
    void somebodyWithoutAMembershipThereHasNoInbox() {
        var answer = harness.request(client -> client.get(
                PREFIX + "/cluster/notifications/unacknowledged",
                harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.USER))));

        assertEquals(ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_NOTIFICATIONS, refusalOf(answer));
    }

    private static UserSession office() {
        var session = TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.USER);
        return new UserSession(
                session.account(),
                session.sessionId(),
                null,
                null,
                null,
                session.permissions(),
                session.instancePermissions(),
                null,
                null,
                null,
                false,
                CLUSTER_ID,
                session.clusterUid(),
                new ClusterMember(
                        CLUSTER_MEMBER_ID, CLUSTER_ID, session.account().id(), ClusterUserType.CLUSTER_USER),
                session.clusterPermissions());
    }
}

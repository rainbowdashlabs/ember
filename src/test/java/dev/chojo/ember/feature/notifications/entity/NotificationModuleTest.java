/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import dev.chojo.ember.feature.station.entity.StationModule;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which module a notification belongs to, and what a station's switched-off modules hide. */
class NotificationModuleTest {

    /** Types name their module or part; types of the account and the station name none. */
    @Test
    void typesNameTheirModule() {
        assertEquals(Optional.of(StationModule.INVENTORY_MOVEMENTS), NotificationType.MOVEMENT_RAISED.module());
        assertEquals(Optional.of(StationModule.INVENTORY_LENDING), NotificationType.LENDING_NEW_REQUEST.module());
        assertEquals(Optional.of(StationModule.EVENTS), NotificationType.EVENT_CHANGED.module());
        assertEquals(Optional.empty(), NotificationType.NAME_CHANGE_REQUESTED.module());
        assertEquals(Optional.empty(), NotificationType.CLUSTER_MODULE_DENIED.module());
    }

    /** A mention takes the module of the page it was made on. */
    @Test
    void aMentionTakesTheModuleOfItsPage() {
        var onNews = NotificationLinks.comment(NotificationLinks.news(3), 9);
        assertEquals(Optional.of(StationModule.NEWS), NotificationType.COMMENT_MENTION.moduleOf(onNews));
        assertEquals(
                Optional.of(StationModule.KNOWLEDGE_BASE),
                NotificationType.COMMENT_MENTION.moduleOf(NotificationLinks.kbFile(4)));
        assertEquals(Optional.empty(), NotificationType.COMMENT_MENTION.moduleOf(NotificationLinks.ownProfile()));
        assertEquals(Optional.empty(), NotificationType.COMMENT_MENTION.moduleOf(null));
        assertEquals(Optional.of(StationModule.NEWS), NotificationType.NEW_NEWS.moduleOf(null));
    }

    /** A station hides the types of its switched-off modules and parts, and the mentions on their pages. */
    @Test
    void aStationHidesWhatItSwitchedOff() {
        var hidden = HiddenNotifications.of(
                StationModule.withChildren(Set.of(StationModule.INVENTORY, StationModule.EVENTS)));

        assertTrue(hidden.hides(NotificationType.SELF_CHECK_ASSIGNED, null));
        assertTrue(hidden.hides(NotificationType.NEW_EVENT, null));
        assertTrue(hidden.hides(NotificationType.COMMENT_MENTION, NotificationLinks.event(2)));
        assertFalse(hidden.hides(NotificationType.COMMENT_MENTION, NotificationLinks.news(2)));
        assertFalse(hidden.hides(NotificationType.COMMENT_MENTION, null));
        assertFalse(hidden.hides(NotificationType.NEW_NEWS, null));
        assertEquals(Set.of("event-detail"), hidden.mentionRoutes());
        assertFalse(hidden.isEmpty());
    }

    /** Nothing switched off hides nothing. */
    @Test
    void nothingOffHidesNothing() {
        assertSame(HiddenNotifications.NONE, HiddenNotifications.of(Set.of()));
        assertTrue(HiddenNotifications.NONE.isEmpty());
        assertTrue(HiddenNotifications.of(Set.of(StationModule.QUIZ)).isEmpty(), "quizzes send no notifications");
        assertEquals(
                Map.of(
                        "news-detail", StationModule.NEWS,
                        "event-detail", StationModule.EVENTS,
                        "kb-file", StationModule.KNOWLEDGE_BASE,
                        "ticket-detail", StationModule.BOARDS),
                NotificationLinks.commentPages());
    }
}

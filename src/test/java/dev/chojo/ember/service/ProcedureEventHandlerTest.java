/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.events.ProcedureAssigned;
import dev.chojo.ember.event.events.ProcedureItemChecked;
import dev.chojo.ember.event.events.ProcedureReopened;
import dev.chojo.ember.event.events.ProcedureResolved;
import dev.chojo.ember.event.handlers.ProcedureAssignedHandler;
import dev.chojo.ember.event.handlers.ProcedureItemCheckedHandler;
import dev.chojo.ember.event.handlers.ProcedureReopenedHandler;
import dev.chojo.ember.event.handlers.ProcedureResolvedHandler;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcedureEventHandlerTest {

    private NotificationService notificationService;
    private StationMemberRepository memberRepository;

    private static final int STATION_ID = 1;
    private static final int PROCEDURE_ID = 42;
    private static final int MEMBER_ID = 10;
    private static final int MEMBER_ID_2 = 20;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        memberRepository = mock(StationMemberRepository.class);
    }

    private StationMember member(int id) {
        return new StationMember(
                id, STATION_ID, UUID.randomUUID(), id, false, null, "Member " + id, StationUserType.MEMBER, null);
    }

    // ── ProcedureAssignedHandler ──

    @Test
    void assignedHandlerEventType() {
        var handler = new ProcedureAssignedHandler(notificationService, memberRepository);
        assertEquals(ProcedureAssigned.class, handler.eventType());
    }

    @Test
    void assignedHandlerNotifiesAssignees() {
        var handler = new ProcedureAssignedHandler(notificationService, memberRepository);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(MEMBER_ID)));

        var event = new ProcedureAssigned(STATION_ID, PROCEDURE_ID, "Fire Drill", List.of(MEMBER_ID_2), MEMBER_ID);
        handler.handle(event);

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(MEMBER_ID_2)),
                        eq(NotificationType.PROCEDURE_ASSIGNED),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    @Test
    void assignedHandlerFallbackName() {
        var handler = new ProcedureAssignedHandler(notificationService, memberRepository);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        var event = new ProcedureAssigned(STATION_ID, PROCEDURE_ID, "Task", List.of(MEMBER_ID_2), MEMBER_ID);
        handler.handle(event);

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(MEMBER_ID_2)),
                        eq(NotificationType.PROCEDURE_ASSIGNED),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    // ── ProcedureResolvedHandler ──

    @Test
    void resolvedHandlerEventType() {
        var handler = new ProcedureResolvedHandler(notificationService);
        assertEquals(ProcedureResolved.class, handler.eventType());
    }

    @Test
    void resolvedHandlerNotifiesAssignees() {
        var handler = new ProcedureResolvedHandler(notificationService);

        var event = new ProcedureResolved(
                STATION_ID, PROCEDURE_ID, "Fire Drill", List.of(MEMBER_ID, MEMBER_ID_2), MEMBER_ID);
        handler.handle(event);

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(MEMBER_ID, MEMBER_ID_2)),
                        eq(NotificationType.PROCEDURE_RESOLVED),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    // ── ProcedureReopenedHandler ──

    @Test
    void reopenedHandlerEventType() {
        var handler = new ProcedureReopenedHandler(notificationService);
        assertEquals(ProcedureReopened.class, handler.eventType());
    }

    @Test
    void reopenedHandlerNotifiesAssignees() {
        var handler = new ProcedureReopenedHandler(notificationService);

        var event = new ProcedureReopened(STATION_ID, PROCEDURE_ID, "Fire Drill", List.of(MEMBER_ID_2), MEMBER_ID);
        handler.handle(event);

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(MEMBER_ID_2)),
                        eq(NotificationType.PROCEDURE_REOPENED),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    // ── ProcedureItemCheckedHandler ──

    @Test
    void itemCheckedHandlerEventType() {
        var handler = new ProcedureItemCheckedHandler(notificationService, memberRepository);
        assertEquals(ProcedureItemChecked.class, handler.eventType());
    }

    @Test
    void itemCheckedHandlerNotifiesAssignees() {
        var handler = new ProcedureItemCheckedHandler(notificationService, memberRepository);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(MEMBER_ID)));

        var event = new ProcedureItemChecked(
                STATION_ID, PROCEDURE_ID, "Fire Drill", 99, "Check exits", List.of(MEMBER_ID_2), MEMBER_ID);
        handler.handle(event);

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(MEMBER_ID_2)),
                        eq(NotificationType.PROCEDURE_ITEM_CHECKED),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }

    @Test
    void itemCheckedHandlerFallbackName() {
        var handler = new ProcedureItemCheckedHandler(notificationService, memberRepository);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        var event = new ProcedureItemChecked(
                STATION_ID, PROCEDURE_ID, "Task", 99, "Step 1", List.of(MEMBER_ID_2), MEMBER_ID);
        handler.handle(event);

        verify(notificationService)
                .notifyMembersIfAbsent(
                        eq(List.of(MEMBER_ID_2)),
                        eq(NotificationType.PROCEDURE_ITEM_CHECKED),
                        any(NotificationData.class),
                        eq(MEMBER_ID));
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.events.MovementCancelled;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Everybody who was part of a movement hears that it was called off, except whoever called it off.
 */
class MovementCancelledHandlerTest extends RepositoryTestBase {
    private static MovementCancelledHandler handler;
    private static NotificationService notificationService;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        notificationService = mock(NotificationService.class);
        handler = new MovementCancelledHandler(notificationService, stationMemberRepo);
        station = stationRepo.create("CancelHandler Station");
        account = accountRepo.create("cancel-handler@test.com", "Call", "Off");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @BeforeEach
    void forgetWhatWentBefore() {
        org.mockito.Mockito.reset(notificationService);
    }

    @Test
    void itHandlesItsOwnEvent() {
        assertEquals(MovementCancelled.class, handler.eventType());
    }

    /**
     * The member it concerned is told, and the one who pressed it is handed over as the exception so
     * nobody is told about their own doing.
     */
    @Test
    void theMemberHearsAboutIt() {
        var event = new MovementCancelled(
                station.id(), 42, member.id(), 7, "Jacken", "Einsatzjacke", "Passt doch", false, 99);

        handler.handle(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Integer>> recipients = ArgumentCaptor.forClass(List.class);
        verify(notificationService)
                .notifyMembersIfAbsent(
                        recipients.capture(),
                        eq(NotificationType.MOVEMENT_CANCELLED),
                        any(NotificationData.class),
                        eq(99));
        assertTrue(recipients.getValue().contains(member.id()), "the member it was about is told");
    }

    /** A movement about no member at all still reaches whoever runs the inventory. */
    @Test
    void aMovementWithoutAMemberStillTellsTheStation() {
        var event = new MovementCancelled(station.id(), 43, null, 7, "Jacken", "Einsatzjacke", "Kein Ersatz", true, 99);

        handler.handle(event);

        verify(notificationService, org.mockito.Mockito.atLeastOnce())
                .notifyMembersIfAbsent(
                        any(), eq(NotificationType.MOVEMENT_CANCELLED), any(NotificationData.class), anyInt());
    }
}

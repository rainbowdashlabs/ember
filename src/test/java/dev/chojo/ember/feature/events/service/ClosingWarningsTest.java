/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One closing warning per lead time and thing, the nearest lead winning where several are due, and only for
 * the members the thing is meant for.
 */
class ClosingWarningsTest {
    private static final int STATION = 4;
    private static final int THING = 11;

    private Notifier notifier;
    private StationReadOnlyGuard readOnlyGuard;
    private ClosingWarnings warnings;
    private FakeKind kind;

    @BeforeEach
    void setUp() {
        notifier = mock(Notifier.class);
        when(notifier.notify(any(), any(), any(), any())).thenReturn(1);
        var names = mock(MemberNameResolver.class);
        when(names.called(anyInt())).thenReturn("Kim");
        readOnlyGuard = mock(StationReadOnlyGuard.class);
        when(readOnlyGuard.isWritable(STATION)).thenReturn(true);
        warnings = new ClosingWarnings(notifier, names, readOnlyGuard);
        kind = new FakeKind();
    }

    @Test
    void aDeadlineInsideBothLeadsIsWarnedOnceWithTheNearestAndBothAreMarked() {
        kind.closingAt(THING, 1);
        kind.unanswered = List.of(7);

        warnings.warn(kind);

        assertEquals(List.of(1), daysWarned());
        assertEquals(Set.of(1, 3), kind.warned.get(THING));
    }

    @Test
    void aDeadlineOnlyInsideTheWiderLeadIsWarnedWithItAndOnlyItIsMarked() {
        kind.closingAt(THING, 3);
        kind.unanswered = List.of(7);

        warnings.warn(kind);

        assertEquals(List.of(3), daysWarned());
        assertEquals(Set.of(3), kind.warned.get(THING));
    }

    @Test
    void onlyMembersItIsMeantForAreWarnedAndTheLeadIsMarkedAnyway() {
        kind.closingAt(THING, 3);
        kind.unanswered = List.of(7, 8);
        kind.meantFor = Set.of(8);

        warnings.warn(kind);

        verify(notifier).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), any(), eq(Delivery.EVERY_TIME));
        assertEquals(List.of(3), daysWarned());
        assertEquals(Set.of(3), kind.warned.get(THING));
    }

    @Test
    void theWarningLeadsToTheThingItIsAbout() {
        kind.closingAt(THING, 1);
        kind.unanswered = List.of(7);

        warnings.warn(kind);

        assertEquals(NotificationLinks.event(THING), sentData().getFirst().link());
    }

    @Test
    void aStationKeptReadOnlyIsLeftAlone() {
        when(readOnlyGuard.isWritable(STATION)).thenReturn(false);
        kind.closingAt(THING, 1);
        kind.unanswered = List.of(7);

        warnings.warn(kind);

        verify(notifier, never()).notify(any(), any(), any(), any());
        assertNull(kind.warned.get(THING));
    }

    private List<Integer> daysWarned() {
        return sentData().stream()
                .map(data -> ((NotificationParams.RegistrationClosing) data.params()).daysBefore())
                .toList();
    }

    private List<NotificationData> sentData() {
        var captor = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifier, atLeast(0)).notify(any(), any(), captor.capture(), any());
        return captor.getAllValues();
    }

    /**
     * A thing with a deadline kept in memory: it is due for every lead time at least as wide as the days
     * left until it closes, until that lead is marked.
     */
    private static final class FakeKind implements ClosingWarnings.Closable {
        private final Map<Integer, Integer> daysLeft = new HashMap<>();
        private final Map<Integer, Set<Integer>> warned = new HashMap<>();
        private List<Integer> unanswered = List.of();
        private Set<Integer> meantFor;

        void closingAt(int id, int days) {
            daysLeft.put(id, days);
        }

        @Override
        public List<ClosingWarnings.Closing> closingIn(int daysBefore) {
            var due = new ArrayList<ClosingWarnings.Closing>();
            daysLeft.forEach((id, days) -> {
                if (days <= daysBefore && !warned.getOrDefault(id, Set.of()).contains(daysBefore)) {
                    due.add(new ClosingWarnings.Closing(id, STATION, "Übung", null));
                }
            });
            return due;
        }

        @Override
        public List<Integer> unansweredMemberIds(ClosingWarnings.Closing closing) {
            return unanswered;
        }

        @Override
        public boolean meantFor(ClosingWarnings.Closing closing, int memberId) {
            return meantFor == null || meantFor.contains(memberId);
        }

        @Override
        public void markWarned(ClosingWarnings.Closing closing, int daysBefore) {
            warned.computeIfAbsent(closing.id(), key -> new HashSet<>()).add(daysBefore);
        }

        @Override
        public NotificationLink linkTo(int id) {
            return NotificationLinks.event(id);
        }
    }
}

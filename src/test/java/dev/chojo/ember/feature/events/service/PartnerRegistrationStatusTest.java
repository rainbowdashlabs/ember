/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.events.PartnerRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes.RemoteRegistrationStatus;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.signing.repository.PartnerSigningRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Station A notifies station B, on the same instance, when it decides about one of B's members on a date of a
 * shared appointment, and B notifies the member's household; B's own confirmation is passed on the same way.
 */
class PartnerRegistrationStatusTest extends EventFederationTestBase {
    private final PartnerSigningRepository signups = new PartnerSigningRepository();
    private StationEvent event;
    private LocalDate day;
    private int serving;
    private StationMember first;
    private StationMember second;

    /** An appointment of station A that asks for confirmation and has one place, shared with B. */
    @BeforeEach
    void anAppointment() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        event = eventRepo.create(
                stationA.id(),
                "Atemschutzübung",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                RegistrationDeadline.none(),
                true,
                null,
                1,
                null,
                null);
        day = dayOf(event);
        shareService.setShare(event.id(), ShareScope.ALL_PARTNERS, List.of());
        serving = partnerOf(stationA, stationB).id();
        first = memberOfB("first");
        second = memberOfB("second");
        clearInvocations(notifier, decisions);
    }

    @AfterEach
    void removeTheAppointment() {
        eventRepo.delete(event.id());
    }

    private static StationMember memberOfB(String name) {
        var account = accountRepo.create(name + "-" + UUID.randomUUID() + "@partner-status.test", "Mitglied", name);
        return stationMemberRepo.create(stationB.id(), account.id());
    }

    private int signUp(StationMember member) {
        clientService.registerForFederatedEvent(
                stationB.id(), stationA.uid(), event.id(), member.uid(), day, List.of());
        return hostService
                .findRegistration(event.id(), serving, member.uid(), day)
                .orElseThrow()
                .id();
    }

    private void verifyTold(StationMember member, int times) {
        verify(notifier, times(times))
                .notify(
                        eq(StationAudience.household(List.of(member.id()))),
                        eq(NotificationType.EVENT_REGISTRATION_STATUS),
                        any(),
                        any());
    }

    private RegistrationStatus known(StationMember member) {
        return signups.knownStatus(member.id(), stationA.uid(), event.id(), day).orElseThrow();
    }

    /** Accepting and then turning down a partner's member is sent to the partner, which notifies the household. */
    @Test
    void aDecisionReachesTheMembersHousehold() {
        int registration = signUp(first);
        assertEquals(RegistrationStatus.PENDING, known(first));

        hostService.decide(registration, RegistrationStatus.ACCEPTED, false);
        verifyTold(first, 1);
        assertEquals(RegistrationStatus.ACCEPTED, known(first));

        hostService.decide(registration, RegistrationStatus.DENIED, false);
        verifyTold(first, 2);
        assertEquals(RegistrationStatus.DENIED, known(first));
        verifyTold(second, 0);
    }

    /**
     * Putting a partner's member on the waiting list is sent with the place in line; reordering the list
     * is not sent; moving up into a place that fell free is sent as such.
     */
    @Test
    void theWaitingListIsToldButNotItsOrder() {
        int held = signUp(first);
        int waiting = signUp(second);
        hostService.decide(held, RegistrationStatus.ACCEPTED, false);

        hostService.moveToQueue(List.of(waiting));
        verify(decisions).publish(argThat(statusChanged(stationA.id(), waiting, RegistrationStatus.QUEUED, 1, false)));
        assertEquals(RegistrationStatus.QUEUED, known(second));
        clearInvocations(decisions);

        hostService.moveInQueue(waiting, 1);
        verify(decisions, never()).publish(argThat(published -> published instanceof PartnerRegistrationStatusChanged));

        hostService.decide(held, RegistrationStatus.DENIED, false);
        verify(decisions)
                .publish(argThat(statusChanged(stationA.id(), waiting, RegistrationStatus.ACCEPTED, null, true)));
        assertEquals(RegistrationStatus.ACCEPTED, known(second));
        verifyTold(second, 2);
    }

    /**
     * A notice older than the one taken in, a repeat, or one about a member this station did not sign up
     * changes nothing and notifies nobody.
     */
    @Test
    void aLateRepeatedOrUnknownNoticeIsIgnored() {
        signUp(first);
        var hostRow = partnerOf(stationA, stationB);
        var now = Instant.now();

        tell(hostRow, first.uid(), RegistrationStatus.ACCEPTED, now);
        tell(hostRow, first.uid(), RegistrationStatus.ACCEPTED, now);
        tell(hostRow, first.uid(), RegistrationStatus.DENIED, now.minusSeconds(60));
        tell(hostRow, first.uid(), RegistrationStatus.ACCEPTED, now.plusSeconds(60));
        tell(hostRow, second.uid(), RegistrationStatus.ACCEPTED, now);
        tell(hostRow, UUID.randomUUID(), RegistrationStatus.ACCEPTED, now);

        verifyTold(first, 1);
        assertEquals(RegistrationStatus.ACCEPTED, known(first));
        verifyTold(second, 0);
    }

    /**
     * A withdrawal keeps what the partner last sent, so a notice older than that is still ignored once the
     * member signs up again; meanwhile a withdrawn member is not believed at all.
     */
    @Test
    void aWithdrawalKeepsTheLastInstant() {
        signUp(first);
        var hostRow = partnerOf(stationA, stationB);
        var now = Instant.now();
        tell(hostRow, first.uid(), RegistrationStatus.ACCEPTED, now);
        verifyTold(first, 1);

        clientService.withdrawFederatedRegistration(stationB.id(), stationA.uid(), event.id(), first.uid(), day);
        signups.forgetSignup(first.id(), stationA.uid(), event.id(), day);
        assertFalse(signups.signedUp(first.id(), stationA.uid(), event.id(), day));
        tell(hostRow, first.uid(), RegistrationStatus.DENIED, now.plusSeconds(30));
        verifyTold(first, 1);

        signUp(first);
        assertTrue(signups.signedUp(first.id(), stationA.uid(), event.id(), day));
        tell(hostRow, first.uid(), RegistrationStatus.DENIED, now.minusSeconds(60));
        verifyTold(first, 1);
        tell(hostRow, first.uid(), RegistrationStatus.DENIED, now.plusSeconds(60));
        verifyTold(first, 2);
    }

    private void tell(FederationPartner hostRow, UUID member, RegistrationStatus status, Instant at) {
        transport
                .transport()
                .deliver(
                        hostRow,
                        RemoteEventRoutes.REGISTRATION_STATUS_WEBHOOK.at(),
                        new RemoteRegistrationStatus(event.id(), event.name(), member, day, status, null, false, at));
    }

    /** A partner confirming one of its own notifies the member's household where they stand now. */
    @Test
    void confirmingOnesOwnIsToldToTheHousehold() {
        givePlaces(event.id(), serving, 1, true);
        signUp(first);
        verifyTold(first, 0);

        clientService.confirmOwnFederatedMember(stationB.id(), stationA.uid(), event.id(), first.uid(), day);

        verifyTold(first, 1);
        assertEquals(RegistrationStatus.ACCEPTED, known(first));
        assertRefused(
                EventRefusal.PARTNER_DECIDES_ITS_OWN,
                () -> hostService.decide(
                        hostService
                                .findRegistration(event.id(), serving, first.uid(), day)
                                .orElseThrow()
                                .id(),
                        RegistrationStatus.DENIED,
                        false));
    }

    /** Matches the announcement of a decision, whatever instant it carries. */
    private static ArgumentMatcher<DomainEvent> statusChanged(
            int stationId, int id, RegistrationStatus status, Integer queuePosition, boolean movedUp) {
        return published -> published instanceof PartnerRegistrationStatusChanged changed
                && changed.stationId() == stationId
                && changed.registrationId() == id
                && changed.status() == status
                && Objects.equals(changed.queuePosition(), queuePosition)
                && changed.movedUp() == movedUp;
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.accountlink.entity.AccountLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.LinkAnswer;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The person's answer to a link request: only their own signed-in account reaches it, the mailed link
 * opens it without answering it, and a request whose time ran out is answered by nobody.
 */
class LinkAnswerServiceTest extends RepositoryTestBase {
    private static final TokenHasher HASHER = TokenHasher.forTesting(TestAccountLinks.PEPPER);
    private final String token = "the-mailed-token-" + System.nanoTime();

    private final AccountLinkRepository links = new AccountLinkRepository();
    private Notifier notifier;
    private LinkAnswerService answers;
    private Station station;
    private Account owner;
    private Account stranger;
    private StationMember waiting;
    private AccountLinkRequest request;

    @BeforeEach
    void setup() {
        notifier = mock(Notifier.class);
        answers = answersAt(Clock.systemUTC());
        station = stationRepo.create("Answer station " + System.nanoTime());
        owner = accountRepo.create("answer-owner-" + System.nanoTime() + "@test.com", "Anna", "Owner", true);
        stranger = accountRepo.create("answer-stranger-" + System.nanoTime() + "@test.com", "Sam", "Stranger", true);
        waiting = stationMemberRepo.createWithoutAccount(station.id(), "Anna Waiting");
        stationMemberRepo.grantPermission(
                waiting.id(),
                stationMemberRepo
                        .findPermissionByName(StationPermission.LOGIN)
                        .orElseThrow()
                        .id());
        request = links.create(
                station.id(),
                waiting.id(),
                owner.id(),
                LinkOrigin.INVITE,
                null,
                HASHER.hash(token),
                Instant.now().plus(Duration.ofDays(30)));
    }

    private LinkAnswerService answersAt(Clock clock) {
        var names = mock(MemberNameResolver.class);
        when(names.official(any(Integer.class))).thenReturn("Anna Owner");
        return new LinkAnswerService(
                links, stationMemberRepo, names, new TwoFactorAuditService(twoFactorRepo), notifier, HASHER, clock);
    }

    private static void assertNotOpen(Executable call) {
        assertEquals(
                MemberRefusal.LINK_REQUEST_NOT_OPEN,
                assertThrows(RefusalResponse.class, call).refusal());
    }

    @Test
    void acceptingLinksTheAccountWithWhatTheStationGaveTheMemberAndTellsTheManagers() {
        answers.accept(owner.id(), request.uid(), "agent", "DE");

        var linked = stationMemberRepo.findById(waiting.id()).orElseThrow();
        assertEquals(owner.id(), linked.accountId());
        assertEquals("", linked.displayName(), "the account's name is the member's from now on");
        assertTrue(stationMemberRepo.hasPermission(waiting.id(), StationPermission.LOGIN));
        assertEquals(
                LinkAnswer.ACCEPTED,
                links.findByUid(request.uid()).orElseThrow().answer());
        assertTrue(twoFactorRepo.findAuditLog(owner.id(), 10, 0).stream()
                .anyMatch(entry -> entry.event() == TwoFactorEvent.ACCOUNT_LINK_ACCEPTED));
        verify(notifier).notify(any(), eq(NotificationType.ACCOUNT_LINK_ACCEPTED), any(), eq(Delivery.EVERY_TIME));
        assertNotOpen(() -> answers.accept(owner.id(), request.uid(), null, null));
    }

    @Test
    void decliningLeavesTheMemberWithoutTheAccount() {
        answers.decline(owner.id(), request.uid());

        assertNull(stationMemberRepo.findById(waiting.id()).orElseThrow().accountId());
        assertEquals(
                LinkAnswer.DECLINED,
                links.findByUid(request.uid()).orElseThrow().answer());
        assertTrue(answers.waitingFor(owner.id()).isEmpty());
        assertNotOpen(() -> answers.accept(owner.id(), request.uid(), null, null));
    }

    @Test
    void theMailedLinkOpensTheRequestForItsOwnAccountOnlyAndAnswersNothing() {
        assertEquals(request.uid(), answers.opened(owner.id(), token).uid());
        assertNull(links.findByUid(request.uid()).orElseThrow().answer(), "opening the link answers nothing");

        assertNotOpen(() -> answers.opened(stranger.id(), token));
        assertNotOpen(() -> answers.opened(owner.id(), "a-token-nobody-was-sent"));
    }

    @Test
    void somebodyElsesSessionCannotAnswerTheRequest() {
        assertNotOpen(() -> answers.accept(stranger.id(), request.uid(), null, null));
        assertNotOpen(() -> answers.decline(stranger.id(), request.uid()));
        assertNotOpen(() -> answers.accept(owner.id(), UUID.randomUUID(), null, null));

        assertNull(stationMemberRepo.findById(waiting.id()).orElseThrow().accountId());
        verify(notifier, never()).notify(any(), any(), any(), any());
    }

    @Test
    void anAnsweredLinkNoLongerOpensAnything() {
        answers.decline(owner.id(), request.uid());

        assertNotOpen(() -> answers.opened(owner.id(), token));
    }

    @Test
    void aRequestWhoseTimeRanOutIsAnsweredByNobody() {
        var later = answersAt(Clock.fixed(Instant.now().plus(Duration.ofDays(31)), ZoneOffset.UTC));

        assertTrue(later.waitingFor(owner.id()).isEmpty());
        assertNotOpen(() -> later.opened(owner.id(), token));
        assertNotOpen(() -> later.accept(owner.id(), request.uid(), null, null));
        assertNull(stationMemberRepo.findById(waiting.id()).orElseThrow().accountId());
    }

    @Test
    void anAccountAlreadyAtTheStationIsNotLinkedASecondTime() {
        stationMemberRepo.create(station.id(), owner.id());

        assertEquals(
                MemberRefusal.LINK_ACCOUNT_ALREADY_AT_STATION,
                assertThrows(RefusalResponse.class, () -> answers.accept(owner.id(), request.uid(), null, null))
                        .refusal());
    }

    /** Two requests of one station accepted at the same moment link the account to one member only. */
    @Test
    void twoRequestsAcceptedAtOnceLinkTheAccountOnce() throws Exception {
        var alsoWaiting = stationMemberRepo.createWithoutAccount(station.id(), "Anna Twice");
        var second = links.create(
                station.id(),
                alsoWaiting.id(),
                owner.id(),
                LinkOrigin.INVITE,
                null,
                HASHER.hash(token + "-second"),
                Instant.now().plus(Duration.ofDays(30)));
        var start = new CountDownLatch(1);
        var refusals = new ConcurrentLinkedQueue<Refusal>();

        var threads = Stream.of(request.uid(), second.uid())
                .map(uid -> Thread.ofPlatform().start(() -> {
                    try {
                        start.await();
                        answers.accept(owner.id(), uid, null, null);
                    } catch (RefusalResponse refused) {
                        refusals.add(refused.refusal());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }))
                .toList();
        start.countDown();
        for (var thread : threads) thread.join();

        assertEquals(List.of(MemberRefusal.LINK_ACCOUNT_ALREADY_AT_STATION), List.copyOf(refusals));
        assertEquals(
                1,
                Stream.of(waiting.id(), alsoWaiting.id())
                        .filter(id -> Objects.equals(
                                owner.id(),
                                stationMemberRepo.findById(id).orElseThrow().accountId()))
                        .count());
    }
}

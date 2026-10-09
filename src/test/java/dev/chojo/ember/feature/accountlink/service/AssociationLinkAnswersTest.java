/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountAction;
import dev.chojo.ember.feature.account.service.AccountReach;
import dev.chojo.ember.feature.accountlink.entity.AssociationLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.LinkAnswer;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.accountlink.repository.AssociationLinkRepository;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The person's answer to an association's request: only their own signed-in account reaches it,
 * accepting makes the membership with the role, declining makes nothing, and until then the
 * association reaches nothing of the account.
 */
class AssociationLinkAnswersTest extends RepositoryTestBase {
    private static final TokenHasher HASHER = TokenHasher.forTesting(TestAccountLinks.PEPPER);
    private final String token = "the-association-token-" + System.nanoTime();

    private final AssociationLinkRepository requests = new AssociationLinkRepository();
    private Notifier notifier;
    private LinkAnswerService answers;
    private int clusterId;
    private Station station;
    private Account owner;
    private Account stranger;
    private AssociationLinkRequest request;

    @BeforeEach
    void setup() {
        notifier = mock(Notifier.class);
        answers = new LinkAnswerService(
                new AccountLinkRepository(),
                stationMemberRepo,
                mock(MemberNameResolver.class),
                new TwoFactorAuditService(twoFactorRepo),
                notifier,
                HASHER,
                TestAccountLinks.associationAnswers(accountRepo, clusterRepo, twoFactorRepo, notifier),
                Clock.systemUTC());
        clusterId = clusterService
                .create("Verband Antworten " + System.nanoTime(), null)
                .id();
        station = stationRepo.create("Antwortwache " + System.nanoTime());
        owner = accountRepo.create("association-answer-" + System.nanoTime() + "@test.com", "Anna", "Owner", true);
        stranger = accountRepo.create("association-stranger-" + System.nanoTime() + "@test.com", "Sam", "Fremd", true);
        stationMemberRepo.create(station.id(), owner.id());
        request = requests.create(
                clusterId,
                owner.id(),
                ClusterUserType.CLUSTER_ADMIN,
                owner.email(),
                HASHER.hash(token),
                Instant.now().plus(Duration.ofDays(30)));
    }

    private static void assertNotOpen(Executable call) {
        assertEquals(
                MemberRefusal.LINK_REQUEST_NOT_OPEN,
                assertThrows(RefusalResponse.class, call).refusal());
    }

    @Test
    void thePromptShowsTheAssociationAndTheRoleNextToAStationsRequest() {
        var elsewhere = stationRepo.create("Andere Wache " + System.nanoTime());
        var stationWaiting = stationMemberRepo.createWithoutAccount(elsewhere.id(), "Anna Dritt");
        new AccountLinkRepository()
                .create(
                        elsewhere.id(),
                        stationWaiting.id(),
                        owner.id(),
                        LinkOrigin.INVITE,
                        null,
                        null,
                        Instant.now().plus(Duration.ofDays(30)));

        var prompts = answers.waitingFor(owner.id());

        assertEquals(2, prompts.size());
        var association = prompts.getFirst();
        assertEquals(request.uid(), association.uid());
        assertEquals(LinkOrigin.ASSOCIATION_INVITE, association.origin());
        assertEquals(ClusterUserType.CLUSTER_ADMIN, association.role());
        assertEquals(clusterRepo.findById(clusterId).orElseThrow().name(), association.associationName());
        assertNull(association.stationName());
        assertEquals(elsewhere.name(), prompts.get(1).stationName());
        assertTrue(answers.waitingFor(stranger.id()).isEmpty());
    }

    @Test
    void theMailedTokenOpensTheRequestOnlyForTheAccountItNames() {
        assertEquals(request.uid(), answers.opened(owner.id(), token).uid());
        assertNotOpen(() -> answers.opened(stranger.id(), token));
    }

    @Test
    void acceptingMakesTheMembershipWithTheRoleAndTellsTheAdministrators() {
        answers.accept(owner.id(), request.uid(), "agent", "DE");

        var member = clusterRepo.findMember(clusterId, owner.id()).orElseThrow();
        assertEquals(ClusterUserType.CLUSTER_ADMIN, member.userType());
        assertEquals(
                LinkAnswer.ACCEPTED,
                requests.findByUid(request.uid()).orElseThrow().answer());
        assertTrue(twoFactorRepo.findAuditLog(owner.id(), 10, 0).stream()
                .anyMatch(entry -> entry.event() == TwoFactorEvent.ASSOCIATION_LINK_ACCEPTED));
        verify(notifier).notify(any(), eq(NotificationType.ASSOCIATION_LINK_ACCEPTED), any(), eq(Delivery.EVERY_TIME));
        assertNotOpen(() -> answers.accept(owner.id(), request.uid(), null, null));
        assertNotOpen(() -> answers.opened(owner.id(), token));
    }

    @Test
    void decliningMakesNothing() {
        answers.decline(owner.id(), request.uid());

        assertTrue(clusterRepo.findMember(clusterId, owner.id()).isEmpty());
        assertEquals(
                LinkAnswer.DECLINED,
                requests.findByUid(request.uid()).orElseThrow().answer());
        verify(notifier, never()).notify(any(), any(), any(), any());
        assertNotOpen(() -> answers.accept(owner.id(), request.uid(), null, null));
    }

    @Test
    void somebodyElseCannotAnswerAndARequestThatRanOutIsAnsweredByNobody() {
        assertNotOpen(() -> answers.accept(stranger.id(), request.uid(), null, null));
        assertNotOpen(() -> answers.decline(stranger.id(), request.uid()));

        var ranOut = requests.create(
                clusterService
                        .create("Verband Abgelaufen " + System.nanoTime(), null)
                        .id(),
                owner.id(),
                ClusterUserType.CLUSTER_USER,
                owner.email(),
                null,
                Instant.now().minus(Duration.ofMinutes(1)));
        assertNotOpen(() -> answers.accept(owner.id(), ranOut.uid(), null, null));
        assertTrue(clusterRepo.findMember(ranOut.clusterId(), owner.id()).isEmpty());
    }

    @Test
    void anAccountThatHoldsARoleThereAlreadyIsNotGivenASecond() {
        clusterService.addMember(clusterId, owner.id(), ClusterUserType.CLUSTER_USER);

        var refused = assertThrows(RefusalResponse.class, () -> answers.accept(owner.id(), request.uid(), null, null));

        assertEquals(MemberRefusal.LINK_ACCOUNT_ALREADY_IN_ASSOCIATION, refused.refusal());
        assertEquals(
                ClusterUserType.CLUSTER_USER,
                clusterRepo.findMember(clusterId, owner.id()).orElseThrow().userType());
    }

    /**
     * An association role keeps every station away from the account's address and sign-in. An
     * association that could give that role by typing an address could freeze those actions on any
     * account it knows the address of. Asking alone ties nothing; only the person's acceptance does.
     */
    @Test
    void anAssociationCannotFreezeTheAccountByAskingAlone() {
        var reach = new AccountReach(accountRepo);

        assertDoesNotThrow(() -> reach.require(station.id(), owner.id(), AccountAction.EMAIL_CHANGE));
        assertDoesNotThrow(() -> reach.require(station.id(), owner.id(), AccountAction.SECOND_FACTOR_RESET));

        answers.accept(owner.id(), request.uid(), null, null);

        assertEquals(
                MemberRefusal.ACCOUNT_HELD_BY_AN_ASSOCIATION,
                assertThrows(
                                RefusalResponse.class,
                                () -> reach.require(station.id(), owner.id(), AccountAction.EMAIL_CHANGE))
                        .refusal());
    }
}

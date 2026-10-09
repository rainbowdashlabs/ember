/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.accountlink.entity.AssociationLinkState;
import dev.chojo.ember.feature.accountlink.entity.LinkAnswer;
import dev.chojo.ember.feature.accountlink.entity.LinkStatus;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.accountlink.repository.AssociationLinkRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An association asking a person to take a role: what is recorded, what the association sees, and
 * when it may send a request again.
 */
class AssociationLinkServiceTest extends RepositoryTestBase {
    private static final TokenHasher HASHER = TokenHasher.forTesting(TestAccountLinks.PEPPER);

    private final AssociationLinkRepository requests = new AssociationLinkRepository();
    private final AccountLinkRepository links = new AccountLinkRepository();
    private EmailService mail;
    private AssociationLinkService service;
    private int clusterId;
    private Account owner;

    @BeforeEach
    void setup() {
        mail = mock(EmailService.class);
        service = at(Instant.now());
        clusterId = clusterService
                .create("Verband Anfragen " + System.nanoTime(), null)
                .id();
        owner = accountRepo.create("association-owner-" + System.nanoTime() + "@test.com", "Olga", "Owner", true);
    }

    private AssociationLinkService at(Instant now) {
        return new AssociationLinkService(
                requests,
                links,
                clusterRepo,
                accountRepo,
                mail,
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                HASHER,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    private AssociationLinkState ask() {
        return service.ask(clusterId, owner.id(), ClusterUserType.CLUSTER_ADMIN, owner.email());
    }

    @Test
    void askingMakesNoMembershipAndMailsATokenOnlyItsHashIsKeptFor() {
        when(mail.isGlobalMailConfigured()).thenReturn(true);

        var state = ask();

        var token = ArgumentCaptor.forClass(String.class);
        verify(mail)
                .sendAssociationLinkRequest(
                        eq(owner.email()), anyString(), anyString(), eq(true), token.capture(), any());
        assertEquals(LinkStatus.WAITING, state.status());
        assertEquals(ClusterUserType.CLUSTER_ADMIN, state.role());
        assertEquals(
                state.uid(),
                requests.findByTokenHash(HASHER.hash(token.getValue()))
                        .orElseThrow()
                        .uid());
        assertTrue(clusterRepo.findMember(clusterId, owner.id()).isEmpty());
        assertFalse(accountRepo.findTies(owner.id(), 0).association(), "no tie to the association before an answer");
    }

    @Test
    void anInstallationWithoutMailAsksInTheAppOnly() {
        when(mail.isGlobalMailConfigured()).thenReturn(false);

        var state = ask();

        verify(mail, never()).sendAssociationLinkRequest(any(), any(), any(), anyBoolean(), any(), any());
        assertEquals(LinkStatus.WAITING, state.status());
    }

    @Test
    void aSecondRequestWhileOneWaitsAndOneForAMemberAreRefused() {
        ask();
        assertEquals(ClusterRefusal.CLUSTER_LINK_ALREADY_WAITING, refusalOf(this::ask));

        var member = accountRepo.create("association-member-" + System.nanoTime() + "@test.com", "Mia", "Member");
        clusterService.addMember(clusterId, member.id(), ClusterUserType.CLUSTER_USER);
        assertEquals(
                ClusterRefusal.CLUSTER_ACCOUNT_ALREADY_A_MEMBER,
                refusalOf(() -> service.ask(clusterId, member.id(), ClusterUserType.CLUSTER_USER, member.email())));
    }

    @Test
    void theAssociationSeesWaitingDeclinedAndExpiredButNotAccepted() {
        var waiting = ask();
        var declined = accountRepo.create("association-declined-" + System.nanoTime() + "@test.com", "De", "Clined");
        var accepted = accountRepo.create("association-accepted-" + System.nanoTime() + "@test.com", "Ac", "Cepted");
        var declinedState = service.ask(clusterId, declined.id(), ClusterUserType.CLUSTER_USER, "typed@test.com");
        var acceptedState = service.ask(clusterId, accepted.id(), ClusterUserType.CLUSTER_USER, accepted.email());
        links.answer(requests.findByUid(declinedState.uid()).orElseThrow().id(), LinkAnswer.DECLINED);
        links.answer(requests.findByUid(acceptedState.uid()).orElseThrow().id(), LinkAnswer.ACCEPTED);
        var ranOut = accountRepo.create("association-ranout-" + System.nanoTime() + "@test.com", "Ran", "Out");
        var expired = at(Instant.now().minus(Duration.ofDays(31)))
                .ask(clusterId, ranOut.id(), ClusterUserType.CLUSTER_USER, ranOut.email());

        var states = service.statesAt(clusterId);

        assertEquals(3, states.size());
        assertEquals(LinkStatus.WAITING, stateOf(states, waiting.uid()).status());
        assertEquals(LinkStatus.DECLINED, stateOf(states, declinedState.uid()).status());
        assertEquals("typed@test.com", stateOf(states, declinedState.uid()).address(), "the address as typed");
        assertEquals(LinkStatus.EXPIRED, stateOf(states, expired.uid()).status());
    }

    private static AssociationLinkState stateOf(List<AssociationLinkState> states, UUID uid) {
        return states.stream()
                .filter(state -> state.uid().equals(uid))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aWaitingRequestIsSentAgainOnlyADayLaterAndThenWithAFreshDeadline() {
        when(mail.isGlobalMailConfigured()).thenReturn(true);
        var first = ask();
        assertEquals(first.sentAt().plus(Duration.ofDays(1)), first.sendAgainFrom());
        assertEquals(
                ClusterRefusal.CLUSTER_LINK_SENT_TOO_RECENTLY,
                refusalOf(() -> service.sendAgain(clusterId, first.uid())));

        var tomorrow = Instant.now().plus(Duration.ofHours(25)).truncatedTo(ChronoUnit.SECONDS);
        var again = at(tomorrow).sendAgain(clusterId, first.uid());

        assertEquals(first.uid(), again.uid(), "the same request");
        assertEquals(LinkStatus.WAITING, again.status());
        assertFalse(again.expiresAt().isBefore(tomorrow.plus(Duration.ofDays(30))));
        verify(mail, times(2)).sendAssociationLinkRequest(any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    void aRequestThatRanOutIsAskedAnewWithTheSameRoleAndTheSweepMarksIt() {
        var ranOut = at(Instant.now().minus(Duration.ofDays(31)))
                .ask(clusterId, owner.id(), ClusterUserType.CLUSTER_ADMIN, owner.email());
        assertEquals(LinkStatus.EXPIRED, service.statesAt(clusterId).getFirst().status());
        assertTrue(TestAccountLinks.service(accountRepo, stationRepo, stationMemberRepo)
                        .expireOverdue()
                >= 1);
        assertEquals(
                LinkAnswer.EXPIRED,
                requests.findByUid(ranOut.uid()).orElseThrow().answer());

        var renewed = at(Instant.now().plus(Duration.ofHours(25))).sendAgain(clusterId, ranOut.uid());

        assertNotEquals(ranOut.uid(), renewed.uid());
        assertEquals(LinkStatus.WAITING, renewed.status());
        assertEquals(ClusterUserType.CLUSTER_ADMIN, renewed.role());
        assertEquals(
                ClusterRefusal.CLUSTER_LINK_NOTHING_TO_SEND_AGAIN,
                refusalOf(() -> at(Instant.now().plus(Duration.ofDays(2))).sendAgain(clusterId, ranOut.uid())),
                "the replaced request is not sent again");
    }

    @Test
    void anAddressThatRanOutMayBeAddedAgain() {
        at(Instant.now().minus(Duration.ofDays(31)))
                .ask(clusterId, owner.id(), ClusterUserType.CLUSTER_USER, owner.email());

        var fresh = ask();

        assertEquals(LinkStatus.WAITING, fresh.status());
    }

    @Test
    void aDeclinedRequestIsNotSentAgain() {
        var request = ask();
        links.answer(requests.findByUid(request.uid()).orElseThrow().id(), LinkAnswer.DECLINED);

        assertEquals(
                ClusterRefusal.CLUSTER_LINK_DECLINED_NOT_SENT_AGAIN,
                refusalOf(() -> at(Instant.now().plus(Duration.ofDays(2))).sendAgain(clusterId, request.uid())));
    }

    @Test
    void anotherAssociationsRequestIsNotThere() {
        var request = ask();
        int elsewhere = clusterService
                .create("Verband Fremd " + System.nanoTime(), null)
                .id();

        assertEquals(
                ClusterRefusal.CLUSTER_LINK_REQUEST_NOT_HERE,
                refusalOf(() -> service.sendAgain(elsewhere, request.uid())));
        assertEquals(
                ClusterRefusal.CLUSTER_LINK_REQUEST_NOT_HERE,
                refusalOf(() -> service.sendAgain(clusterId, UUID.randomUUID())));
        assertTrue(service.statesAt(elsewhere).isEmpty());
    }
}

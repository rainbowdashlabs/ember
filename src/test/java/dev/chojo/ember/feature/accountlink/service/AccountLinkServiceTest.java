/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.accountlink.entity.AccountLinkRequest;
import dev.chojo.ember.feature.accountlink.entity.LinkAnswer;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.accountlink.entity.LinkStatus;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A station asking a person to link their account: what is recorded, and when a mail goes out.
 */
class AccountLinkServiceTest extends RepositoryTestBase {
    private static final TokenHasher HASHER = TokenHasher.forTesting(TestAccountLinks.PEPPER);

    private final AccountLinkRepository links = new AccountLinkRepository();
    private EmailService mail;
    private AccountLinkService service;
    private Station station;
    private Account owner;
    private StationMember waiting;

    @BeforeEach
    void setup() {
        mail = mock(EmailService.class);
        service = new AccountLinkService(
                links,
                accountRepo,
                stationRepo,
                stationMemberRepo,
                mail,
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                HASHER);
        station = stationRepo.create("Link station " + System.nanoTime());
        owner = accountRepo.create("link-owner-" + System.nanoTime() + "@test.com", "Olga", "Owner", true);
        waiting = stationMemberRepo.createWithoutAccount(station.id(), "Olga Owner");
    }

    @Test
    void anInstallationThatSendsMailsTheLinkWithATokenOnlyItsHashIsKeptFor() {
        when(mail.isGlobalMailConfigured()).thenReturn(true);
        Instant before = Instant.now();

        var request = service.ask(station.id(), waiting.id(), owner.id(), LinkOrigin.INVITE, null);

        var token = ArgumentCaptor.forClass(String.class);
        verify(mail)
                .sendAccountLinkRequest(
                        eq(owner.email()), anyString(), eq(station.name()), eq("Olga Owner"), token.capture(), any());
        assertEquals(
                request.id(),
                links.findUnansweredForMember(waiting.id()).orElseThrow().id());
        assertEquals(
                request.id(),
                links.findByTokenHash(HASHER.hash(token.getValue()))
                        .orElseThrow()
                        .id());
        assertFalse(request.expiresAt().isBefore(before.plus(Duration.ofDays(30))));
    }

    @Test
    void anInstallationWithoutMailAsksInTheAppOnly() {
        when(mail.isGlobalMailConfigured()).thenReturn(false);

        var request = service.ask(station.id(), waiting.id(), owner.id(), LinkOrigin.IMPORT, null);

        verify(mail, never()).sendAccountLinkRequest(any(), any(), any(), any(), any(), any());
        assertEquals(LinkOrigin.IMPORT, request.origin());
        assertTrue(request.waits(Instant.now()));
    }

    @Test
    void aMemberThatWaitsAlreadyKeepsTheRequestItHas() {
        var first = service.ask(station.id(), waiting.id(), owner.id(), LinkOrigin.IMPORT, null);

        var second = service.ask(station.id(), waiting.id(), owner.id(), LinkOrigin.INVITE, null);

        assertEquals(first.id(), second.id());
    }

    private AccountLinkService at(Instant now) {
        return new AccountLinkService(
                links,
                accountRepo,
                stationRepo,
                stationMemberRepo,
                mail,
                new MailLocaleService(accountRepo, new ApplicationSettingRepository()),
                HASHER,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    @Test
    void theStationSeesItsRequestWaitAndMayNotSendItAgainWithinADay() {
        service.ask(station.id(), waiting.id(), owner.id(), LinkOrigin.INVITE, null);

        var state = service.stateOf(station.id(), waiting.id()).orElseThrow();

        assertEquals(LinkStatus.WAITING, state.status());
        assertEquals(LinkOrigin.INVITE, state.origin());
        assertEquals(state.sentAt().plus(Duration.ofDays(1)), state.sendAgainFrom());
        assertEquals(
                MemberRefusal.LINK_SENT_TOO_RECENTLY,
                refusalOf(() -> service.sendAgain(station.id(), waiting.id(), waiting.id())));
        assertEquals(
                LinkStatus.WAITING,
                service.statesAt(station.id()).get(waiting.id()).status());
    }

    @Test
    void aWaitingRequestGoesOutAgainADayLaterWithAFreshDeadline() {
        when(mail.isGlobalMailConfigured()).thenReturn(true);
        var first = service.ask(station.id(), waiting.id(), owner.id(), LinkOrigin.INVITE, null);
        var tomorrow = at(Instant.now().plus(Duration.ofHours(25)));

        var state = tomorrow.sendAgain(station.id(), waiting.id(), waiting.id());

        assertEquals(LinkStatus.WAITING, state.status());
        assertEquals(
                first.id(),
                links.findLatestForMember(waiting.id()).orElseThrow().id());
        verify(mail, times(2)).sendAccountLinkRequest(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aRequestThatRanOutIsExpiredAndAskedAnewWhenSentAgain() {
        var first = ranOut();
        var tomorrow = at(Instant.now().plus(Duration.ofDays(1)));

        assertEquals(
                LinkStatus.EXPIRED,
                service.stateOf(station.id(), waiting.id()).orElseThrow().status());
        assertTrue(service.expireOverdue() >= 1);
        assertEquals(
                LinkAnswer.EXPIRED, links.findByUid(first.uid()).orElseThrow().answer());

        var state = tomorrow.sendAgain(station.id(), waiting.id(), waiting.id());

        assertEquals(LinkStatus.WAITING, state.status());
        var renewed = links.findLatestForMember(waiting.id()).orElseThrow();
        assertNotEquals(first.id(), renewed.id());
        assertEquals(LinkOrigin.IMPORT, renewed.origin());
        assertEquals(waiting.id(), renewed.createdBy());
    }

    @Test
    void aDeclinedRequestIsNotSentAgain() {
        var request = service.ask(station.id(), waiting.id(), owner.id(), LinkOrigin.INVITE, null);
        links.answer(request.id(), LinkAnswer.DECLINED);

        assertEquals(
                LinkStatus.DECLINED,
                service.stateOf(station.id(), waiting.id()).orElseThrow().status());
        assertEquals(MemberRefusal.LINK_DECLINED_NOT_SENT_AGAIN, refusalOf(() -> at(Instant.now()
                        .plus(Duration.ofDays(2)))
                .sendAgain(station.id(), waiting.id(), waiting.id())));
    }

    @Test
    void nothingIsSentAgainForAMemberWithoutARequestOrWithAnAccount() {
        assertTrue(service.stateOf(station.id(), waiting.id()).isEmpty());
        assertEquals(
                MemberRefusal.LINK_NOTHING_TO_SEND_AGAIN,
                refusalOf(() -> service.sendAgain(station.id(), waiting.id(), waiting.id())));
        var linked = stationMemberRepo.create(station.id(), owner.id());
        assertEquals(
                MemberRefusal.LINK_NOTHING_TO_SEND_AGAIN,
                refusalOf(() -> service.sendAgain(station.id(), linked.id(), linked.id())));
    }

    @Test
    void anotherStationsMemberIsNotThere() {
        var elsewhere = stationRepo.create("Link elsewhere " + System.nanoTime());

        assertEquals(
                MemberRefusal.LINK_MEMBER_NOT_HERE, refusalOf(() -> service.stateOf(elsewhere.id(), waiting.id())));
        assertEquals(
                MemberRefusal.LINK_MEMBER_NOT_HERE,
                refusalOf(() -> service.sendAgain(elsewhere.id(), waiting.id(), waiting.id())));
    }

    /**
     * A request asked thirty-one days ago, which ran out a day ago. Made with a deadline in the past
     * rather than by moving the clock, so the sweep it is checked against touches no other test's rows.
     */
    private AccountLinkRequest ranOut() {
        return links.create(
                station.id(),
                waiting.id(),
                owner.id(),
                LinkOrigin.IMPORT,
                null,
                null,
                Instant.now().minus(Duration.ofDays(1)));
    }

    @Test
    void theSweepMarksWhatRanOutAndTheTaskRunsHourly() {
        var sweeper = new AccountLinkSweeper(service);
        var request = ranOut();

        var task = sweeper.scheduledTasks().getFirst();
        task.work().run();

        assertEquals(
                LinkAnswer.EXPIRED, links.findByUid(request.uid()).orElseThrow().answer());
        assertEquals("account-link-expiry-sweep", task.name());
        assertEquals(Duration.ofHours(1), task.schedule().period());
    }
}

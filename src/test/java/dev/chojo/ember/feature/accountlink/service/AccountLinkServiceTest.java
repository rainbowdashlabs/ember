/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        assertFalse(token.getValue().isBlank());
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
}

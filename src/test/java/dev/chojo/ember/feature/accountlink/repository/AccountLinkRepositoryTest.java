/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.accountlink.entity.LinkAnswer;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Link requests in the database: one waiting per member, the prompt a person is shown, and how a
 * request ends.
 */
class AccountLinkRepositoryTest extends RepositoryTestBase {
    private final AccountLinkRepository repository = new AccountLinkRepository();

    private Station station;
    private Account owner;
    private StationMember waiting;
    private StationMember inviter;

    @BeforeEach
    void setup() {
        station = stationRepo.create("Link repository " + System.nanoTime());
        owner = accountRepo.create("link-repo-" + System.nanoTime() + "@test.com", "Ole", "Owner", true);
        waiting = stationMemberRepo.createWithoutAccount(station.id(), "Ole Waiting");
        var inviterAccount =
                accountRepo.create("link-inviter-" + System.nanoTime() + "@test.com", "Ina", "Inviter", true);
        inviter = stationMemberRepo.create(station.id(), inviterAccount.id());
    }

    private Instant inThirtyDays() {
        return Instant.now().plus(Duration.ofDays(30));
    }

    @Test
    void aRequestIsShownToItsAccountWithTheStationTheMemberAndWhoInvited() {
        var request = repository.create(
                station.id(), waiting.id(), owner.id(), LinkOrigin.INVITE, inviter.id(), "hash-shown", inThirtyDays());

        var prompts = repository.findWaitingForAccount(owner.id(), Instant.now());

        assertEquals(1, prompts.size());
        var prompt = prompts.getFirst();
        assertEquals(request.uid(), prompt.uid());
        assertEquals(station.name(), prompt.stationName());
        assertEquals("Ole Waiting", prompt.memberName());
        assertEquals(LinkOrigin.INVITE, prompt.origin());
        assertEquals("Ina Inviter", prompt.invitedBy());
        assertTrue(repository
                .findWaitingForAccount(owner.id(), inThirtyDays().plusSeconds(1))
                .isEmpty());
    }

    @Test
    void aRequestIsFoundByItsUidItsTokenAndItsMember() {
        var request = repository.create(
                station.id(), waiting.id(), owner.id(), LinkOrigin.IMPORT, null, "hash-found", inThirtyDays());

        assertEquals(
                request.id(), repository.findByUid(request.uid()).orElseThrow().id());
        assertEquals(
                request.id(),
                repository.findByTokenHash("hash-found").orElseThrow().id());
        assertEquals(
                request.id(),
                repository.findUnansweredForMember(waiting.id()).orElseThrow().id());
        assertEquals(
                request.id(),
                repository.findLatestForMember(waiting.id()).orElseThrow().id());
        assertEquals(
                request.id(),
                repository.findLatestByStation(station.id()).get(waiting.id()).id());
        assertTrue(repository.findByUid(UUID.randomUUID()).isEmpty());
        assertNull(request.createdBy());
    }

    @Test
    void anAnswerEndsTheRequestOnceAndDropsItsToken() {
        var request = repository.create(
                station.id(),
                waiting.id(),
                owner.id(),
                LinkOrigin.INVITE,
                inviter.id(),
                "hash-answered",
                inThirtyDays());

        assertTrue(repository.answer(request.id(), LinkAnswer.DECLINED));
        assertFalse(repository.answer(request.id(), LinkAnswer.ACCEPTED), "only one answer finds it waiting");

        var answered = repository.findByUid(request.uid()).orElseThrow();
        assertEquals(LinkAnswer.DECLINED, answered.answer());
        assertTrue(repository.findByTokenHash("hash-answered").isEmpty());
        assertTrue(repository.findUnansweredForMember(waiting.id()).isEmpty());
        assertFalse(repository.sendAgain(request.id(), "hash-again", inThirtyDays()));
    }

    @Test
    void aWaitingRequestIsSentAgainWithAFreshTokenAndDeadline() {
        var request = repository.create(
                station.id(),
                waiting.id(),
                owner.id(),
                LinkOrigin.INVITE,
                null,
                "hash-first",
                Instant.now().plusSeconds(60));

        assertTrue(repository.sendAgain(request.id(), "hash-second", inThirtyDays()));

        assertTrue(repository.findByTokenHash("hash-first").isEmpty());
        var sent = repository.findByTokenHash("hash-second").orElseThrow();
        assertTrue(sent.expiresAt().isAfter(Instant.now().plus(Duration.ofDays(29))));
    }

    @Test
    void anOverdueRequestExpiresAndAWaitingOneDoesNot() {
        var overdue = repository.create(
                station.id(),
                waiting.id(),
                owner.id(),
                LinkOrigin.IMPORT,
                null,
                null,
                Instant.now().minusSeconds(60));
        var other = stationMemberRepo.createWithoutAccount(station.id(), "Still Waiting");
        var fresh =
                repository.create(station.id(), other.id(), owner.id(), LinkOrigin.IMPORT, null, null, inThirtyDays());

        assertTrue(repository.expireOverdue(Instant.now()) >= 1);

        assertEquals(
                LinkAnswer.EXPIRED,
                repository.findByUid(overdue.uid()).orElseThrow().answer());
        assertNull(repository.findByUid(fresh.uid()).orElseThrow().answer());
    }
}

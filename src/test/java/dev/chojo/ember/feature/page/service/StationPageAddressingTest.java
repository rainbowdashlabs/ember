/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.page.entity.PageVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a card linking to a page is told that page now lives.
 *
 * <p>Worked out at drawing time rather than stored, so the interesting cases are the ones where the
 * answer has moved since the card was placed: the page was filed under another, or it left the
 * station's menu and is reached by its link instead.
 */
class StationPageAddressingTest extends RepositoryTestBase {
    private static StationPageAddressing addressing;
    private static Station station;
    private static StationMember member;
    private static Account account;

    @BeforeAll
    static void setup() {
        addressing = new StationPageAddressing(pageRepo, stationRepo);
        station = stationRepo.create("AddressingStation");
        account = accountRepo.create("addressing@test.com", "Addr", "Essing");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void aPublicPageIsReachedAtThePathItsSlugsSpell() {
        var parent = pageRepo.create(station.id(), "Über uns", "ueber-uns", null, member.id());
        var child = pageRepo.create(station.id(), "Das Team", "das-team", parent.id(), member.id());
        pageRepo.setVisibility(parent.id(), PageVisibility.PUBLIC, null);
        pageRepo.setVisibility(child.id(), PageVisibility.PUBLIC, null);

        var address =
                addressing.addressOf(station.id(), child.publicUid().toString()).orElseThrow();

        assertEquals("Das Team", address.title());
        assertTrue(address.href().endsWith("/page/ueber-uns/das-team"), address.href());

        pageRepo.delete(parent.id());
    }

    @Test
    void aPageReachedByItsLinkIsReachedAtThatLink() {
        var page = pageRepo.create(station.id(), "Einladung", "einladung", null, member.id());
        pageRepo.setVisibility(page.id(), PageVisibility.UNLISTED, "ein-langer-zufaelliger-token");

        var address =
                addressing.addressOf(station.id(), page.publicUid().toString()).orElseThrow();

        assertEquals("/s/ein-langer-zufaelliger-token", address.href());

        pageRepo.delete(page.id());
    }

    @Test
    void aDraftIsNoAddressAtAll() {
        var draft = pageRepo.create(station.id(), "Entwurf", "entwurf", null, member.id());

        assertTrue(
                addressing.addressOf(station.id(), draft.publicUid().toString()).isEmpty());

        pageRepo.delete(draft.id());
    }

    @Test
    void aPageOfAnotherStationIsNotAnswered() {
        var other = stationRepo.create("AddressingOtherStation");
        var otherMember = stationMemberRepo.create(other.id(), account.id());
        var page = pageRepo.create(other.id(), "Fremde Seite", "fremde-seite", null, otherMember.id());
        pageRepo.setVisibility(page.id(), PageVisibility.PUBLIC, null);

        assertTrue(
                addressing.addressOf(station.id(), page.publicUid().toString()).isEmpty(),
                "a card in one station cannot name a page of another");
        assertTrue(
                addressing.addressOf(null, page.publicUid().toString()).isPresent(),
                "content the instance owns belongs to no station and may name any public page");

        stationRepo.delete(other.id());
    }

    @Test
    void somethingThatIsNotAPageIdentifierAnswersNothing() {
        assertTrue(addressing.addressOf(station.id(), "gar-keine-kennung").isEmpty());
        assertTrue(
                addressing.addressOf(station.id(), UUID.randomUUID().toString()).isEmpty());
    }
}

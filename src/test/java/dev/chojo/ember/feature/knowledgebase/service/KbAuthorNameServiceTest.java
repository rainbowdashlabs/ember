/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KbAuthorNameServiceTest extends RepositoryTestBase {
    private static KbAuthorNameService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new KbAuthorNameService(stationMemberRepo, accountRepo);
        station = stationRepo.create("KbAuthorStation");
        account = accountRepo.create("kb-author@test.com", "Kb", "AuthorTester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    void theAccountNameIsUsedWhenTheStationKnowsNoDisplayName() {
        assertEquals(account.fullName(), service.resolveMemberName(member.id()));
    }

    /**
     * A member appears under the name their station gave them, even when their account carries a
     * different one.
     */
    @Test
    void aStationDisplayNameWinsOverTheAccountName() {
        var other = accountRepo.create("kb-author-named@test.com", "Other", "Account");
        var named = stationMemberRepo.create(station.id(), other.id());
        stationMemberRepo.setDisplayNameAndClearAccount(named.id(), "Gerätewart");

        assertEquals("Gerätewart", service.resolveMemberName(named.id()));

        stationMemberRepo.delete(named.id());
        accountRepo.delete(other.id());
    }

    @Test
    void aMemberWithoutAnyNameFallsBackToThePlaceholder() {
        var other = accountRepo.create("kb-author-nameless@test.com", "Nameless", "Account");
        var nameless = stationMemberRepo.create(station.id(), other.id());
        stationMemberRepo.setDisplayNameAndClearAccount(nameless.id(), "");

        assertEquals("Unbekannt", service.resolveMemberName(nameless.id()));

        stationMemberRepo.delete(nameless.id());
        accountRepo.delete(other.id());
    }

    @Test
    void anUnknownMemberFallsBackToThePlaceholder() {
        assertEquals("Unbekannt", service.resolveMemberName(999999));
    }
}

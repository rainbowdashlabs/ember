/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemberIdentityFactoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("IdentityFactory Station");
        account = accountRepo.create("identity-factory@test.com", "Identity", "Factory");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationMemberRepo.delete(member.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void localCreatesEnrichedIdentity() {
        var identity = memberIdentityFactory.local(station.id(), member.id());
        assertNotNull(identity);
        assertEquals(station.uid(), identity.stationUid());
        assertNotNull(identity.memberUid());
    }

    @Test
    @Order(2)
    void federatedCreatesRawIdentity() {
        var stationUid = UUID.randomUUID();
        var memberUid = UUID.randomUUID();
        var identity = memberIdentityFactory.federated(stationUid, memberUid);
        assertNotNull(identity);
        assertEquals(stationUid, identity.stationUid());
        assertEquals(memberUid, identity.memberUid());
    }

    @Test
    @Order(3)
    void fromMemberIdReturnsEnrichedIdentity() {
        var identity = memberIdentityFactory.fromMemberId(member.id());
        assertNotNull(identity);
        assertNotNull(identity.stationUid());
        assertNotNull(identity.memberUid());
    }

    @Test
    @Order(4)
    void fromMemberIdNonExistentReturnsNull() {
        var identity = memberIdentityFactory.fromMemberId(99999);
        assertNull(identity);
    }

    @Test
    @Order(5)
    void enrichAddsDisplayMetadata() {
        var raw = new MemberIdentity(station.uid(), member.uid());
        var enriched = memberIdentityFactory.enrich(raw);
        assertNotNull(enriched);
        assertEquals(station.uid(), enriched.stationUid());
        assertEquals(member.uid(), enriched.memberUid());
    }

    @Test
    @Order(6)
    void enrichCompletionsAddsMetadata() {
        var completions = List.of(
                new MemberCompletion(member.id(), "Identity Factory", station.uid(), member.uid(), null, null, null));
        var enriched = memberIdentityFactory.enrichCompletions(completions);
        assertNotNull(enriched);
        assertEquals(1, enriched.size());
        assertEquals(member.id(), enriched.getFirst().id());
        assertEquals("Identity Factory", enriched.getFirst().name());
        // Station name should be enriched
        assertNotNull(enriched.getFirst().stationName());
    }

    @Test
    @Order(7)
    void enrichCompletionsEmptyList() {
        var enriched = memberIdentityFactory.enrichCompletions(List.of());
        assertTrue(enriched.isEmpty());
    }
}

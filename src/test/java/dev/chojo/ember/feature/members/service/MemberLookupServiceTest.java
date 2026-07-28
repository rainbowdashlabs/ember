/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MemberLookupServiceTest extends RepositoryTestBase {
    private static final int UNKNOWN_MEMBER_ID = -4711;

    private static MemberLookupService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new MemberLookupService(stationMemberRepo, stationRepo);
        station = stationRepo.create("MemberLookupStation");
        account = accountRepo.create("member-lookup@test.com", "Look", "Up");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void resolveUidServesRepeatedLookupsFromCache() {
        var uid = service.resolveUid(member.id());
        assertNotNull(uid);
        assertEquals(uid, service.resolveUid(member.id()));
        assertEquals(uid, stationMemberRepo.selectUid(member.id()).orElseThrow());
    }

    @Test
    @Order(2)
    void resolveUidOfUnknownMemberIsNull() {
        assertNull(service.resolveUid(UNKNOWN_MEMBER_ID));
    }

    @Test
    @Order(3)
    void resolveIdServesRepeatedLookupsFromCache() {
        var uid = service.resolveUid(member.id());
        var id = service.resolveId(station.id(), uid);
        assertTrue(id.isPresent());
        assertEquals(member.id(), id.orElseThrow());
        assertEquals(id, service.resolveId(station.id(), uid));
    }

    @Test
    @Order(4)
    void resolveIdOfUnknownUidIsEmpty() {
        assertTrue(service.resolveId(station.id(), UUID.randomUUID()).isEmpty());
    }

    @Test
    @Order(5)
    void resolveIdentityCarriesBothUids() {
        var identity = service.resolveIdentity(member.id());
        assertNotNull(identity);
        assertEquals(stationRepo.resolveUid(station.id()), identity.stationUid());
        assertEquals(service.resolveUid(member.id()), identity.memberUid());
        assertNull(service.resolveIdentity(UNKNOWN_MEMBER_ID));
    }

    @Test
    @Order(6)
    void resolveMemberIdWalksBackFromTheIdentity() {
        var identity = service.resolveIdentity(member.id());
        assertEquals(member.id(), service.resolveMemberId(identity).orElseThrow());
    }

    @Test
    @Order(7)
    void resolveMemberIdRejectsIncompleteOrForeignIdentities() {
        assertTrue(service.resolveMemberId(null).isEmpty());
        assertTrue(service.resolveMemberId(new MemberIdentity(null, UUID.randomUUID()))
                .isEmpty());
        assertTrue(service.resolveMemberId(new MemberIdentity(UUID.randomUUID(), null))
                .isEmpty());
        assertTrue(service.resolveMemberId(new MemberIdentity(UUID.randomUUID(), UUID.randomUUID()))
                .isEmpty());
        assertTrue(service.resolveMemberId(new MemberIdentity(stationRepo.resolveUid(station.id()), UUID.randomUUID()))
                .isEmpty());
    }

    @Test
    @Order(8)
    void findCompletionsStampsTheStationUid() {
        var completions = service.findCompletions(station.id());
        assertEquals(1, completions.size());
        var completion = completions.getFirst();
        assertEquals(member.id(), completion.id());
        assertEquals(stationRepo.resolveUid(station.id()), completion.stationUid());
        assertEquals(service.resolveUid(member.id()), completion.memberUid());
        assertNotNull(completion.name());
    }

    @Test
    @Order(9)
    void setUidDropsTheStaleCacheEntries() {
        var oldUid = service.resolveUid(member.id());
        assertEquals(member.id(), service.resolveId(station.id(), oldUid).orElseThrow());

        var newUid = UUID.randomUUID();
        service.setUid(member.id(), newUid);

        assertEquals(newUid, service.resolveUid(member.id()));
        assertEquals(member.id(), service.resolveId(station.id(), newUid).orElseThrow());
        assertTrue(service.resolveId(station.id(), oldUid).isEmpty());
    }

    @Test
    @Order(10)
    void invalidateOfAnUncachedMemberIsANoOp() {
        assertDoesNotThrow(() -> service.invalidate(UNKNOWN_MEMBER_ID));
    }
}

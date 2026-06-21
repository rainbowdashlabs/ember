/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.twofactor.service.TwoFactorPolicyService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TwoFactorPolicyServiceTest extends RepositoryTestBase {

    private static TwoFactorPolicyService service;

    @BeforeAll
    static void initService() {
        service = new TwoFactorPolicyService(twoFactorRepo, stationMemberRepo, accountRepo);
    }

    @Test
    void assignableUserTypesIsStable() {
        var types = service.assignableUserTypes();
        assertTrue(types.contains(StationUserType.MEMBER));
        assertTrue(types.contains(StationUserType.MANAGER));
        assertFalse(types.contains(StationUserType.TRIAL));
    }

    @Test
    void policiesRoundTrip() {
        var station = stationRepo.create("policy-svc-" + UUID.randomUUID());
        // Instance scope
        var instance = service.setInstancePolicy(StationUserType.MEMBER, true, (short) 7, null);
        assertEquals(1, service.listInstancePolicies().size());
        // Idempotent upsert returns the same row id
        var instance2 = service.setInstancePolicy(StationUserType.MEMBER, false, (short) 3, null);
        assertEquals(instance.id(), instance2.id());

        // Station scope
        var stationPolicy = service.setStationPolicy(station.id(), StationUserType.MANAGER, true, (short) 5, null);
        assertEquals(1, service.listStationPolicies(station.id()).size());
        assertTrue(service.deletePolicy(stationPolicy.id()));
        assertTrue(service.deletePolicy(instance.id()));
    }

    @Test
    void clampsGraceDays() {
        var stationA = stationRepo.create("policy-clamp-a-" + UUID.randomUUID());
        var p1 = service.setStationPolicy(stationA.id(), StationUserType.MEMBER, true, (short) 99, null);
        assertEquals((short) 7, p1.graceDays(), "above 7 clamps to 7");
        var p2 = service.setStationPolicy(stationA.id(), StationUserType.MEMBER, true, (short) -2, null);
        assertEquals((short) 0, p2.graceDays(), "below 0 clamps to 0");
        service.deletePolicy(p2.id());
    }

    @Test
    void instanceAdminAlwaysMandated() {
        var station = stationRepo.create("policy-instadmin-" + UUID.randomUUID());
        var adminAccount = accountRepo.create("ia-" + UUID.randomUUID() + "@test.com", "Instance", "Admin", true);
        accountRepo.setInstanceUserType(adminAccount.id(), InstanceUserType.ADMINISTRATOR);
        stationMemberRepo.create(station.id(), adminAccount.id());

        var status = service.listStationMemberStatus(station.id());
        assertEquals(1, status.size());
        assertTrue(status.get(0).mandated(), "instance administrators are always mandated");
    }

    @Test
    void instanceWildcardPolicyMandatesEveryone() {
        var station = stationRepo.create("policy-wildcard-" + UUID.randomUUID());
        var account = accountRepo.create("w-" + UUID.randomUUID() + "@test.com", "Wild", "Card", true);
        stationMemberRepo.create(station.id(), account.id());
        var wildcard = twoFactorRepo.upsertPolicy(
                dev.chojo.ember.feature.twofactor.entity.TwoFactorPolicy.Scope.INSTANCE,
                null,
                null,
                true,
                (short) 7,
                null);

        var status = service.listStationMemberStatus(station.id());
        assertTrue(status.get(0).mandated(), "wildcard policy applies to every user type");
        service.deletePolicy(wildcard.id());
    }

    @Test
    void mandateDerivationFromPolicyAndElevatedPermission() {
        // Set up: station with 3 members — plain member, station admin (elevated permission),
        // and a member matched by a station-scoped policy on MEMBER.
        var station = stationRepo.create("policy-mand-" + UUID.randomUUID());

        var plainAccount = accountRepo.create("p-" + UUID.randomUUID() + "@test.com", "Plain", "Member", true);
        var plain = stationMemberRepo.create(station.id(), plainAccount.id());

        var adminAccount = accountRepo.create("a-" + UUID.randomUUID() + "@test.com", "Admin", "Member", true);
        var admin = stationMemberRepo.create(station.id(), adminAccount.id());
        // Grant STATION_ADMINISTRATOR directly so the mandate derivation picks it up.
        var permId = stationMemberRepo
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow()
                .id();
        stationMemberRepo.grantPermission(admin.id(), permId);

        var mandatedAccount = accountRepo.create("m-" + UUID.randomUUID() + "@test.com", "Mandated", "Member", true);
        var mandated = stationMemberRepo.create(station.id(), mandatedAccount.id());
        var policy = service.setStationPolicy(station.id(), StationUserType.MEMBER, true, (short) 3, null);

        var status = service.listStationMemberStatus(station.id());
        assertEquals(3, status.size());

        boolean plainMandated = status.stream()
                .filter(s -> s.memberId() == plain.id())
                .findFirst()
                .orElseThrow()
                .mandated();
        boolean adminMandated = status.stream()
                .filter(s -> s.memberId() == admin.id())
                .findFirst()
                .orElseThrow()
                .mandated();
        boolean mandatedRowMandated = status.stream()
                .filter(s -> s.memberId() == mandated.id())
                .findFirst()
                .orElseThrow()
                .mandated();

        assertTrue(plainMandated, "policy applies to every MEMBER user-type in this station");
        assertTrue(adminMandated, "elevated permission triggers mandate independent of policies");
        assertTrue(mandatedRowMandated, "explicit MEMBER policy triggers mandate");

        service.deletePolicy(policy.id());
    }
}

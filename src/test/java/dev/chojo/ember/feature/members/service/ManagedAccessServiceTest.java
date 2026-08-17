/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A guardian speaks for a child: they may give it an address and switch its access on and off.
 * They may not do either for anyone else.
 */
class ManagedAccessServiceTest extends RepositoryTestBase {

    private static ManagedAccessService service;
    private static Station station;
    private static Account guardianAccount;
    private static Account childAccount;
    private static Account strangerAccount;
    private static StationMember guardian;
    private static StationMember child;
    private static StationMember stranger;

    @BeforeAll
    static void setup() {
        var memberService = new StationMemberService(
                stationMemberRepo, stationRepo, accountRepo, mock(AuthService.class), mock(MemberLookupService.class));
        service = new ManagedAccessService(
                stationMemberRepo, accountRepo, memberService, mock(AuthService.class), mock(EmailService.class));

        station = stationRepo.create("Managed Access Station");
        guardianAccount = accountRepo.create("guardian@test.com", "Petra", "Sommer");
        childAccount = accountRepo.create("child-1@managed.local", "Lena", "Sommer");
        strangerAccount = accountRepo.create("stranger@test.com", "Fremd", "Person");

        guardian = stationMemberRepo.create(station.id(), guardianAccount.id());
        child = stationMemberRepo.create(station.id(), childAccount.id());
        stranger = stationMemberRepo.create(station.id(), strangerAccount.id());
        stationMemberRepo.setUserType(guardian.id(), StationUserType.GUARDIAN);
        stationMemberRepo.setUserType(child.id(), StationUserType.MEMBER);
        stationMemberRepo.setUserType(stranger.id(), StationUserType.MEMBER);
        stationMemberRepo.addManager(guardian.id(), child.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(guardianAccount.id());
        accountRepo.delete(childAccount.id());
        accountRepo.delete(strangerAccount.id());
    }

    @BeforeEach
    void resetChild() {
        accountRepo.updateEmail(childAccount.id(), "child-1@managed.local");
        stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(permission -> stationMemberRepo.revokePermission(child.id(), permission.id()));
    }

    @Test
    void aSyntheticAddressCountsAsNoAddress() {
        var access = service.get(guardian.id(), child.id());

        assertNull(access.email());
        assertFalse(access.canSignIn());
        assertFalse(access.loginEnabled());
    }

    @Test
    void theGuardianGivesTheChildAnAddress() {
        var access = service.setEmail(guardian.id(), child.id(), "  Lena@Example.ORG ");

        assertEquals("lena@example.org", access.email());
        assertTrue(access.canSignIn());
        assertEquals(
                "lena@example.org",
                accountRepo.findById(childAccount.id()).orElseThrow().email());
    }

    @Test
    void anAddressThatBelongsToSomeoneElseIsRefused() {
        assertThrows(BadRequestResponse.class, () -> service.setEmail(guardian.id(), child.id(), "stranger@test.com"));
    }

    @Test
    void nonsenseIsNotAnAddress() {
        assertThrows(BadRequestResponse.class, () -> service.setEmail(guardian.id(), child.id(), "keine-adresse"));
    }

    @Test
    void signingInNeedsAnAddressFirst() {
        assertThrows(BadRequestResponse.class, () -> service.setLogin(guardian.id(), child.id(), true));
    }

    @Test
    void accessIsSwitchedOnAndOffAgain() {
        service.setEmail(guardian.id(), child.id(), "lena@example.org");

        assertTrue(service.setLogin(guardian.id(), child.id(), true).loginEnabled());
        assertFalse(service.setLogin(guardian.id(), child.id(), false).loginEnabled());
    }

    @Test
    void aMemberTheyDoNotManageIsNoneOfTheirBusiness() {
        assertThrows(ForbiddenResponse.class, () -> service.get(guardian.id(), stranger.id()));
        assertThrows(ForbiddenResponse.class, () -> service.setEmail(guardian.id(), stranger.id(), "neu@example.org"));
        assertThrows(ForbiddenResponse.class, () -> service.setLogin(guardian.id(), stranger.id(), true));
    }

    @Test
    void aManagedMemberOfAnotherTypeIsRefused() {
        stationMemberRepo.setUserType(child.id(), StationUserType.TEAM);
        try {
            assertThrows(ForbiddenResponse.class, () -> service.setLogin(guardian.id(), child.id(), true));
        } finally {
            stationMemberRepo.setUserType(child.id(), StationUserType.MEMBER);
        }
    }
}

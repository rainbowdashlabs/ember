/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.legal.service.GdprDeletionService;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GdprDeletionServiceTest extends RepositoryTestBase {
    private static GdprDeletionService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new GdprDeletionService(accountRepo, stationMemberRepo, new ImageService());
        station = stationRepo.create("GdprStation");
        account = accountRepo.create("gdpr-del@test.com", "Delete", "Me");
        accountRepo.createCredential(account.id(), "hash");
        member = stationMemberRepo.create(station.id(), account.id());

        // Add roles
        stationMemberRepo
                .findPermissionByName(StationPermission.USER)
                .ifPresent(r -> stationMemberRepo.grantPermission(member.id(), r.id()));
        stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .ifPresent(r -> stationMemberRepo.grantPermission(member.id(), r.id()));

        // Add profile field value
        var field = profileFieldRepo.create(
                station.id(),
                "Phone",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                0,
                ProfileFieldScope.MEMBER);
        profileFieldRepo.setValue(member.id(), field.id(), "\"0123456789\"");

        // Add to a group
        var group = memberGroupRepo.create(station.id(), "TestGroup");
        memberGroupRepo.addMember(group.id(), member.id());
    }

    @Test
    @Order(1)
    void memberExistsBeforeDeletion() {
        assertTrue(stationMemberRepo.findById(member.id()).isPresent());
        assertFalse(stationMemberRepo.findPermissions(member.id()).isEmpty());
    }

    @Test
    @Order(10)
    void anonymizeMemberRemovesPersonalData() {
        service.anonymizeMember(member.id());

        // station_member.id is declared DELETE_EXPLICIT in data_tracking.json; the engine now
        // honours that — the row is gone after anonymisation. CASCADE FKs on the station_member
        // table take care of dependent rows (groups, tags, etc.).
        assertTrue(stationMemberRepo.findById(member.id()).isEmpty());
    }

    @Test
    @Order(11)
    void anonymizedMemberHasNoRolesOrGroups() {
        // Profile field values deleted (FK CASCADE from station_member)
        var values = profileFieldRepo.findValues(member.id());
        assertTrue(values.isEmpty());

        // Group memberships removed (FK CASCADE)
        var groups = memberGroupRepo.findGroupsForMember(member.id());
        assertTrue(groups.isEmpty());
    }

    @Test
    @Order(20)
    void deleteAccountRemovesAccountData() {
        // Create a fresh account to delete entirely
        var acc2 = accountRepo.create("gdpr-del2@test.com", "Also", "Delete");
        accountRepo.createCredential(acc2.id(), "hash2");
        var member2 = stationMemberRepo.create(station.id(), acc2.id());

        service.deleteAccount(acc2.id());

        // Account should be gone — engine deletes via DELETE_EXPLICIT on account.id and CASCADE
        // takes out account_credential / account_session / account_external_auth / saved_filter.
        assertTrue(accountRepo.findById(acc2.id()).isEmpty());
        // The membership row is also gone (DELETE_EXPLICIT on station_member.id).
        assertTrue(stationMemberRepo.findById(member2.id()).isEmpty());
    }
}

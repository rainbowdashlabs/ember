/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberPermissionResolverTest extends RepositoryTestBase {

    private Station station;

    @BeforeEach
    void freshFixture() {
        station = stationRepo.create("Guardian Station " + System.nanoTime());
    }

    private StationMember memberOfType(StationUserType userType, String name) {
        var account = accountRepo.create(name + "-" + System.nanoTime() + "@test.com", name, "Person");
        var member = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo.setUserType(member.id(), userType);
        return stationMemberRepo.findById(member.id()).orElseThrow();
    }

    private boolean actsForOthers(StationMember member) {
        return memberPermissionResolver.resolve(member.id()).contains(StationPermission.MEMBER_GUARDIAN);
    }

    @Test
    void team_member_put_in_charge_of_somebody_may_act_for_them() {
        var teamMember = memberOfType(StationUserType.TEAM, "Team");
        var child = memberOfType(StationUserType.MEMBER, "Child");
        assertFalse(actsForOthers(teamMember));

        stationMemberRepo.addManager(teamMember.id(), child.id());

        assertTrue(actsForOthers(teamMember));
    }

    @Test
    void station_manager_put_in_charge_of_somebody_may_act_for_them() {
        var manager = memberOfType(StationUserType.MANAGER, "Manager");
        var child = memberOfType(StationUserType.MEMBER, "Child");

        stationMemberRepo.addManager(manager.id(), child.id());

        assertTrue(actsForOthers(manager));
    }

    @Test
    void guardian_type_may_act_for_others_with_nobody_assigned() {
        var guardian = memberOfType(StationUserType.GUARDIAN, "Guardian");

        assertTrue(actsForOthers(guardian));
    }

    @Test
    void nobody_in_their_care_means_nobody_to_act_for() {
        var teamMember = memberOfType(StationUserType.TEAM, "Alone");

        assertFalse(actsForOthers(teamMember));
    }

    @Test
    void the_right_ends_with_the_last_person_in_their_care() {
        var teamMember = memberOfType(StationUserType.TEAM, "Team");
        var child = memberOfType(StationUserType.MEMBER, "Child");
        stationMemberRepo.addManager(teamMember.id(), child.id());
        assertTrue(actsForOthers(teamMember));

        stationMemberRepo.removeManager(teamMember.id(), child.id());

        assertFalse(actsForOthers(teamMember));
    }

    @Test
    void a_guardian_type_keeps_the_right_after_the_last_person_leaves_their_care() {
        var guardian = memberOfType(StationUserType.GUARDIAN, "Guardian");
        var child = memberOfType(StationUserType.MEMBER, "Child");
        stationMemberRepo.addManager(guardian.id(), child.id());

        stationMemberRepo.removeManager(guardian.id(), child.id());

        assertTrue(actsForOthers(guardian));
    }

    @Test
    void somebody_whose_only_charge_left_the_station_acts_for_nobody() {
        var teamMember = memberOfType(StationUserType.TEAM, "Team");
        var child = memberOfType(StationUserType.MEMBER, "Child");
        stationMemberRepo.addManager(teamMember.id(), child.id());

        stationMemberRepo.setFormer(child.id(), true);

        assertFalse(actsForOthers(teamMember));
    }

    @Test
    void no_such_member_holds_nothing() {
        assertFalse(memberPermissionResolver.resolve(-1).contains(StationPermission.MEMBER_GUARDIAN));
    }

    /**
     * A module the station switched off grants nothing, and switching it back on gives back what was
     * granted, which stayed stored all along.
     */
    @Test
    void a_switched_off_module_grants_nothing_until_it_is_back() {
        var manager = memberOfType(StationUserType.MANAGER, "Manager");
        stationRepo.setDisabledModules(station.id(), Set.of(StationModule.INVENTORY_LENDING, StationModule.NEWS));

        var held = memberPermissionResolver.resolve(manager);
        assertFalse(held.contains(StationPermission.INVENTORY_LENDING_MANAGER));
        assertFalse(held.contains(StationPermission.NEWS_EDIT));
        assertTrue(held.contains(StationPermission.INVENTORY_READ));
        assertTrue(held.contains(StationPermission.MEMBER_MANAGER));

        stationRepo.setDisabledModules(station.id(), Set.of());

        assertTrue(
                memberPermissionResolver.resolve(manager.id()).contains(StationPermission.INVENTORY_LENDING_MANAGER));
    }

    /** A module the station's cluster denies grants nothing either, whatever the station says. */
    @Test
    void a_module_the_cluster_denies_grants_nothing() {
        var manager = memberOfType(StationUserType.MANAGER, "Manager");
        var home = stationRepo.create("Träger Rechte " + System.nanoTime());
        var cluster = clusterRepo.create("Kreisverband Rechte " + System.nanoTime(), null, home.id());
        stationRepo.setCluster(station.id(), cluster.id());
        clusterRepo.setDeniedModules(cluster.id(), null, Set.of(StationModule.INVENTORY));
        try {
            var held = memberPermissionResolver.resolve(manager);
            assertFalse(held.contains(StationPermission.INVENTORY_READ));
            assertFalse(held.contains(StationPermission.INVENTORY_MOVEMENTS), "the parts go with the module");
            assertTrue(held.contains(StationPermission.EVENT_MANAGER));
        } finally {
            stationRepo.setCluster(station.id(), null);
            clusterRepo.delete(cluster.id());
            stationRepo.delete(home.id());
        }
    }
}

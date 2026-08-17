/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that deleting a station also sweeps accounts that no longer belong to anyone. The
 * rule (see {@code StationRepository.delete}): a user-typed account with zero remaining
 * {@code station_member} rows is removed; administrators and accounts still linked to at least
 * one other station are kept.
 */
@Tag("database")
class StationRepositoryOrphanSweepTest extends RepositoryTestBase {

    @Test
    void deletingStationRemovesOrphanedUserAccount() {
        var station = stationRepo.create("Sole Station");
        Account account = accountRepo.create("orphan-single@example.com", "Solo", "User", true);
        stationMemberRepo.create(station.id(), account.id());

        stationRepo.delete(station.id());

        assertTrue(
                accountRepo.findById(account.id()).isEmpty(), "account whose only station was deleted must be swept");
    }

    @Test
    void deletingStationKeepsAccountThatStillBelongsToAnotherStation() {
        var firstStation = stationRepo.create("First Station");
        var secondStation = stationRepo.create("Second Station");
        Account account = accountRepo.create("multi@example.com", "Multi", "User", true);
        stationMemberRepo.create(firstStation.id(), account.id());
        stationMemberRepo.create(secondStation.id(), account.id());

        stationRepo.delete(firstStation.id());

        assertTrue(
                accountRepo.findById(account.id()).isPresent(),
                "account with a remaining station membership must survive");

        stationRepo.delete(secondStation.id());
        assertFalse(
                accountRepo.findById(account.id()).isPresent(),
                "last remaining membership gone - account is now swept");
    }

    @Test
    void deletingStationKeepsAdministratorEvenWithoutMembership() {
        var station = stationRepo.create("Admin Station");
        Account admin = accountRepo.create("admin@example.com", "Inst", "Admin", true);
        accountRepo.setInstanceUserType(admin.id(), InstanceUserType.ADMINISTRATOR);
        stationMemberRepo.create(station.id(), admin.id());

        stationRepo.delete(station.id());

        assertTrue(
                accountRepo.findById(admin.id()).isPresent(),
                "instance administrators must survive a station delete even if they had no other membership");
    }

    @Test
    void deletingStationRemovesBlankEmailOrphan() {
        var station = stationRepo.create("Waitlist Source");
        Account blank = accountRepo.create(null, "Tim", "Bauer", true);
        stationMemberRepo.create(station.id(), blank.id());

        stationRepo.delete(station.id());

        assertTrue(
                accountRepo.findById(blank.id()).isEmpty(),
                "blank-email applicant accounts are swept alongside their only station");
    }
}

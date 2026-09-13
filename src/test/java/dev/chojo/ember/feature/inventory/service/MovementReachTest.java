/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Every combination a station starts with can actually be started.
 *
 * <p>The engine has always held more chains than anything could set going: one screen raised swaps and
 * nothing raised the rest, so ten of the eleven presets were written, seeded and never walked. These
 * cases go the way the wizard goes, from the answers a person gives to the movement that comes back,
 * and they fail if a combination has no chain, no way in, or lands on the wrong one.
 */
class MovementReachTest extends RepositoryTestBase {
    private static final AtomicInteger CODES = new AtomicInteger();

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int ours;
    private static int theirs;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("ReachStation");
        account = accountRepo.create("reach@test.com", "Rea", "Ch");
        member = stationMemberRepo.create(station.id(), account.id());
        ours = inventoryRepo
                .create(station.id(), "Eigenes", InventoryType.INTERNAL, false)
                .id();
        theirs = inventoryRepo
                .create(station.id(), "Vom Träger", InventoryType.EXTERNAL, false)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private int piece(int inventoryId, ItemOwner owner) {
        return inventoryRepo
                .createItem(inventoryId, "R-" + CODES.incrementAndGet(), "Teil", null, null, owner, null)
                .id();
    }

    private int heldByTheMember(int inventoryId, ItemOwner owner) {
        int id = piece(inventoryId, owner);
        itemCustodyService.assignToMember(id, member.id(), "");
        return id;
    }

    private ItemMovementService.Actor theStation() {
        return new ItemMovementService.Actor(member.id(), true);
    }

    /**
     * Starts one, the way a person answering the wizard starts one.
     *
     * @param memberId the member it is with, or null where it is about the store
     * @param outgoing the piece leaving, or null where nothing leaves
     */
    private void reaches(
            MovementPurpose purpose,
            Integer memberId,
            Integer outgoing,
            int inventoryId,
            ItemOwner owner,
            MovementParty party) {
        var target = movementTargeting.resolve(station.id(), purpose, memberId, outgoing, null, inventoryId);
        assertEquals(owner, target.ownerKind());
        assertEquals(party, target.party());

        var movement = itemMovementService.create(
                station.id(),
                purpose,
                memberId,
                memberId == null ? null : "Rea Ch",
                outgoing,
                inventoryId,
                null,
                null,
                "Auf dem Weg",
                theStation(),
                null);

        assertNotNull(movement);
        assertEquals(target.flowId(), movement.flowId(), "the chain the answers resolved to is the one it walks");
        assertEquals(MovementState.OPEN, movement.state());
        assertEquals(purpose, movement.purpose());
    }

    @Test
    void theBodyAboveSendsSomethingForTheStore() {
        reaches(MovementPurpose.ISSUE, null, null, theirs, ItemOwner.CLUSTER, MovementParty.STORE);
    }

    @Test
    void theBodyAboveSendsSomethingForAMember() {
        reaches(MovementPurpose.ISSUE, member.id(), null, theirs, ItemOwner.CLUSTER, MovementParty.MEMBER);
    }

    /** The planned hand-out: the station's own gear, promised to a member rather than handed over. */
    @Test
    void theStationPlansToHandItsOwnGearOut() {
        reaches(MovementPurpose.ISSUE, member.id(), null, ours, ItemOwner.STATION, MovementParty.MEMBER);
    }

    @Test
    void theStoreSendsThingsBackToTheBodyAbove() {
        reaches(
                MovementPurpose.RETURN,
                null,
                piece(theirs, ItemOwner.CLUSTER),
                theirs,
                ItemOwner.CLUSTER,
                MovementParty.STORE);
    }

    @Test
    void aMemberSendsTheBodysGearBack() {
        reaches(
                MovementPurpose.RETURN,
                member.id(),
                heldByTheMember(theirs, ItemOwner.CLUSTER),
                theirs,
                ItemOwner.CLUSTER,
                MovementParty.MEMBER);
    }

    @Test
    void aMemberHandsTheStationsOwnGearBack() {
        reaches(
                MovementPurpose.RETURN,
                member.id(),
                heldByTheMember(ours, ItemOwner.STATION),
                ours,
                ItemOwner.STATION,
                MovementParty.MEMBER);
    }

    @Test
    void aMemberSwapsTheStationsOwnGear() {
        reaches(
                MovementPurpose.EXCHANGE,
                member.id(),
                heldByTheMember(ours, ItemOwner.STATION),
                ours,
                ItemOwner.STATION,
                MovementParty.MEMBER);
    }

    @Test
    void aMemberSwapsTheBodysGear() {
        reaches(
                MovementPurpose.EXCHANGE,
                member.id(),
                heldByTheMember(theirs, ItemOwner.CLUSTER),
                theirs,
                ItemOwner.CLUSTER,
                MovementParty.MEMBER);
    }

    @Test
    void theStoreSwapsTheBodysGear() {
        reaches(
                MovementPurpose.EXCHANGE,
                null,
                piece(theirs, ItemOwner.CLUSTER),
                theirs,
                ItemOwner.CLUSTER,
                MovementParty.STORE);
    }

    @Test
    void theStationAsksTheBodyAboveForTheStore() {
        reaches(MovementPurpose.REQUEST, null, null, theirs, ItemOwner.CLUSTER, MovementParty.STORE);
    }

    @Test
    void theStationAsksTheBodyAboveForAMember() {
        reaches(MovementPurpose.REQUEST, member.id(), null, theirs, ItemOwner.CLUSTER, MovementParty.MEMBER);
    }
}

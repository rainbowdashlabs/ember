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
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Whose gear a movement is about, who it is with, and the chain those two resolve to.
 *
 * <p>The cases worth holding are the ones a wrong answer sends down the wrong chain: a piece that names
 * its owner outright, a movement that names only what is arriving, a mixed inventory where only the
 * piece can say, and the difference between a movement with a member and one about the store.
 */
class MovementTargetingTest extends RepositoryTestBase {
    private static final AtomicInteger CODES = new AtomicInteger();

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int mixed;
    private static int internal;
    private static int external;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("TargetStation");
        account = accountRepo.create("target@test.com", "Tar", "Get");
        member = stationMemberRepo.create(station.id(), account.id());
        mixed = inventoryRepo
                .create(station.id(), "Gemischt", InventoryType.MIXED, false)
                .id();
        internal = inventoryRepo
                .create(station.id(), "Eigenes", InventoryType.INTERNAL, false)
                .id();
        external = inventoryRepo
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
                .createItem(inventoryId, "T-" + CODES.incrementAndGet(), "Teil", null, null, owner, null)
                .id();
    }

    @Test
    void aPieceSaysWhoseItIs() {
        int stationsOwn = piece(mixed, ItemOwner.STATION);
        int theBodysAbove = piece(mixed, ItemOwner.CLUSTER);

        assertEquals(ItemOwner.STATION, movementTargeting.ownerOf(stationsOwn, null, mixed));
        assertEquals(ItemOwner.CLUSTER, movementTargeting.ownerOf(theBodysAbove, null, mixed));
    }

    /** An issue names only what is arriving, so asking the outgoing side alone would miss the answer. */
    @Test
    void theArrivingPieceAnswersWhenNothingLeaves() {
        int arriving = piece(mixed, ItemOwner.CLUSTER);

        assertEquals(ItemOwner.CLUSTER, movementTargeting.ownerOf(null, arriving, mixed));
    }

    @Test
    void withoutAPieceTheInventoryAnswersAsFarAsItCan() {
        assertEquals(ItemOwner.STATION, movementTargeting.ownerOf(null, null, internal));
        assertEquals(ItemOwner.CLUSTER, movementTargeting.ownerOf(null, null, external));
        assertEquals(
                ItemOwner.CLUSTER,
                movementTargeting.ownerOf(null, null, mixed),
                "a mixed inventory cannot say, and a movement about no piece yet is one asking for one");
        assertEquals(ItemOwner.STATION, movementTargeting.ownerOf(null, null, null));
    }

    /**
     * The body's gear goes back from a member and from the shelf alike, and those are two chains. The
     * station's own gear has no such pair: it never leaves, so there is no chain for returning it to
     * itself and resolving one is refused rather than guessed.
     */
    @Test
    void namingAMemberIsWhatMakesItAMembersMovement() {
        int theirs = piece(external, ItemOwner.CLUSTER);

        var withMember =
                movementTargeting.resolve(station.id(), MovementPurpose.RETURN, member.id(), theirs, null, external);
        var aboutTheStore =
                movementTargeting.resolve(station.id(), MovementPurpose.RETURN, null, theirs, null, external);

        assertEquals(MovementParty.MEMBER, withMember.party());
        assertEquals(MovementParty.STORE, aboutTheStore.party());
        assertNotEquals(withMember.flowId(), aboutTheStore.flowId());
    }

    /** A combination nothing is bound to is refused, which is what the wizard shows instead of starting one. */
    @Test
    void aCombinationWithNoChainIsRefused() {
        int own = piece(internal, ItemOwner.STATION);

        assertThrows(
                BadRequestResponse.class,
                () -> movementTargeting.resolve(station.id(), MovementPurpose.RETURN, null, own, null, internal),
                "the station does not hand its own gear back to itself");
    }

    /** Two combinations are two chains, which is the whole reason the party is not folded into the purpose. */
    @Test
    void eachCombinationResolvesToItsOwnChain() {
        int own = piece(internal, ItemOwner.STATION);
        int theirs = piece(external, ItemOwner.CLUSTER);

        var returnToStation =
                movementTargeting.resolve(station.id(), MovementPurpose.RETURN, member.id(), own, null, internal);
        var returnToTheBody =
                movementTargeting.resolve(station.id(), MovementPurpose.RETURN, member.id(), theirs, null, external);

        assertEquals(ItemOwner.STATION, returnToStation.ownerKind());
        assertEquals(ItemOwner.CLUSTER, returnToTheBody.ownerKind());
        assertNotEquals(
                returnToStation.flowId(), returnToTheBody.flowId(), "the station's own gear walks its own chain");
    }

    /**
     * A station under a body that keeps its gear here walks the body's own chain, and the chain that
     * comes back is a whole one.
     *
     * <p>The wizard draws this before anything is written, so a chain resolved but not readable is a
     * screen that stops with an error where it should have drawn a picture.
     */
    @Test
    void theBodysOwnChainAnswersWhereTheBodyKeepsItsGearHere() {
        var home = stationRepo.create("TargetTräger");
        var cluster = clusterRepo.create("Kreisverband Ziel", null, home.id());
        clusterRepo.setUsesInventory(cluster.id(), true);
        stationRepo.setCluster(station.id(), cluster.id());
        try {
            var target = movementTargeting.resolve(station.id(), MovementPurpose.ISSUE, null, null, null, mixed);

            assertEquals(ItemOwner.CLUSTER, target.ownerKind());
            assertEquals(MovementParty.STORE, target.party());
            assertEquals(
                    cluster.id(),
                    target.ownerClusterId(),
                    "a movement with no piece yet is one asking the body above for one");
            assertFalse(
                    movementFlowService.findAllSteps(target.flowId()).isEmpty(),
                    "the chain it resolved to is one that can be drawn");
        } finally {
            stationRepo.setCluster(station.id(), null);
        }
    }

    /** A movement reads the same way whether it is about to be started or is already running. */
    @Test
    void anOpenMovementResolvesToWhatStartingItWouldHave() {
        int own = piece(internal, ItemOwner.STATION);
        var movement = itemMovementService.create(
                station.id(),
                MovementPurpose.RETURN,
                member.id(),
                "Tar Get",
                own,
                internal,
                null,
                null,
                "Passt nicht",
                new ItemMovementService.Actor(member.id(), true),
                null);

        var asRunning = movementTargeting.of(movement);

        assertEquals(movement.flowId(), asRunning.flowId());
        assertEquals(MovementParty.MEMBER, asRunning.party());
        assertEquals(ItemOwner.STATION, asRunning.ownerKind());
    }
}

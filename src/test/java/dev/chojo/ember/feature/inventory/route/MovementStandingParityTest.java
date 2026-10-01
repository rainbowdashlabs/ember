/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementStanding;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.MyInventoryItem;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.route.MovementRoutes.MovementResponse;
import dev.chojo.ember.feature.inventory.service.BorrowedGearService;
import dev.chojo.ember.feature.inventory.service.GlyphResolver;
import dev.chojo.ember.feature.inventory.service.InventoryCheckService;
import dev.chojo.ember.feature.inventory.service.InventoryContainerService;
import dev.chojo.ember.feature.inventory.service.InventoryExportService;
import dev.chojo.ember.feature.inventory.service.InventoryIntakeService;
import dev.chojo.ember.feature.inventory.service.InventoryLossService;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.inventory.service.LossReportService;
import dev.chojo.ember.feature.inventory.service.MemberGearService;
import dev.chojo.ember.feature.inventory.service.MovementExportService;
import dev.chojo.ember.feature.inventory.service.MovementGuards;
import dev.chojo.ember.feature.inventory.service.SelfCheckService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.HttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.read;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A member's gear on their page and the movement in the movement list tell the same story about the
 * same piece, at every step of the chain and however the movement ends.
 *
 * <p>Both answers are read over HTTP from the routes the two screens call. The gear used to wear the
 * label of the step being waited on, which has not happened yet, while the list said which step had
 * happened and whose turn it was; and a replacement handed over while the chain still waited for the
 * member to confirm it showed no movement at all.
 */
class MovementStandingParityTest extends RepositoryTestBase {
    private static final AtomicInteger CODES = new AtomicInteger();

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int inventoryId;
    private static ItemMovementService.Actor team;
    private static RouteHarness harness;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Standing Parity");
        account = accountRepo.create("standing-parity@test.com", "Stan", "Ding");
        member = stationMemberRepo.create(station.id(), account.id());
        inventoryId = inventoryRepo
                .create(station.id(), "Jacken", InventoryType.MIXED, false)
                .id();
        team = new ItemMovementService.Actor(member.id(), true);

        var guards = mock(MovementGuards.class);
        when(guards.visibleAmong(any(), any())).thenAnswer(call -> call.getArgument(1));
        when(guards.actorOf(any(), any())).thenReturn(team);
        var glyphs = new GlyphResolver(inventoryRepo, artRepo);
        var gear = new MemberGearService(inventoryService, itemMovementService, glyphs, memberIdentityFactory);
        harness = RouteHarness.serving(
                new MovementRoutes(
                        itemMovementService,
                        guards,
                        mock(StationMemberService.class),
                        mock(MemberNameResolver.class),
                        memberIdentityFactory,
                        mock(LossReportService.class),
                        inventoryService,
                        movementTargeting,
                        glyphs,
                        mock(MovementExportService.class),
                        mock(SelfCheckService.class)),
                new InventoryRoutes(
                        inventoryService,
                        mock(InventoryCheckService.class),
                        mock(InventoryExportService.class),
                        mock(InventoryContainerService.class),
                        memberIdentityFactory,
                        mock(StationMemberService.class),
                        mock(InventoryLossService.class),
                        mock(LossReportService.class),
                        mock(InventoryIntakeService.class),
                        mock(BorrowedGearService.class),
                        mock(SelfCheckService.class),
                        gear));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static int pieceWithMember() {
        int id = inventoryRepo
                .createItem(inventoryId, "P-" + CODES.incrementAndGet(), "Jacke", null, null, ItemOwner.STATION, null)
                .id();
        itemCustodyService.assignToMember(id, member.id(), "Stan Ding");
        return id;
    }

    private static int pieceOnTheShelf() {
        return inventoryRepo
                .createItem(inventoryId, "P-" + CODES.incrementAndGet(), "Jacke", null, null, ItemOwner.STATION, null)
                .id();
    }

    private static ItemMovement askForExchange(int itemId) {
        return itemMovementService.create(
                station.id(),
                MovementPurpose.EXCHANGE,
                member.id(),
                "Stan Ding",
                itemId,
                inventoryId,
                null,
                null,
                "zu klein",
                team,
                null);
    }

    private static ItemMovement walk(ItemMovement movement, Integer picked) {
        return itemMovementService.acknowledge(movement.id(), movement.currentStepId(), team, "", picked);
    }

    private static List<MovementFlowStep> stepsOf(ItemMovement movement) {
        return itemMovementService.stepsOf(movement);
    }

    /** The movement as the movement list answers it. */
    private static MovementResponse row(HttpClient client, int movementId) {
        var rows = read(
                client.get(
                        PREFIX + "/movements", harness.as(TestSessions.member(station.id(), StationPermission.USER))),
                MovementResponse[].class);
        return Arrays.stream(rows)
                .filter(candidate -> candidate.id() == movementId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the list has no movement " + movementId));
    }

    /** The piece as the member's page answers it, empty when it is not on their list. */
    private static Optional<MyInventoryItem> piece(HttpClient client, int itemId) {
        var pieces = read(
                client.get(
                        PREFIX + "/station-members/" + member.id() + "/inventory-items",
                        harness.as(TestSessions.member(station.id(), StationPermission.MEMBER_READ))),
                MyInventoryItem[].class);
        return Arrays.stream(pieces)
                .filter(candidate -> candidate.id() == itemId)
                .findFirst();
    }

    private static MovementStanding standingOf(MovementResponse row) {
        return new MovementStanding(
                row.id(),
                row.state(),
                row.reachedStepLabel(),
                row.currentStepActor(),
                row.ownerKind(),
                row.ownerName());
    }

    /** The piece is on the member's list and says exactly what the movement's own row says. */
    private static MovementStanding sameStory(HttpClient client, int itemId, int movementId) {
        var onThePage = piece(client, itemId).orElseThrow(() -> new AssertionError("the piece is on the member"));
        MovementStanding inTheList = standingOf(row(client, movementId));
        assertEquals(inTheList, onThePage.movement(), "the member's page and the movement list agree");
        return inTheList;
    }

    @Test
    void theGearAndTheListAgreeAtEveryStepOfAnExchange() {
        int old = pieceWithMember();
        int replacement = pieceOnTheShelf();

        harness.run((server, client) -> {
            ItemMovement movement = askForExchange(old);
            List<MovementFlowStep> steps = stepsOf(movement);
            assertEquals(5, steps.size(), "asked, taken back, made ready, handed over, confirmed");

            MovementStanding asked = sameStory(client, old, movement.id());
            assertEquals(steps.get(0).label(), asked.reachedStepLabel(), "the request is what has happened");
            assertNotEquals(
                    steps.get(1).label(),
                    asked.reachedStepLabel(),
                    "not the taking back it waits on, with the jacket still on the member");
            assertEquals(StepActor.STATION, asked.currentStepActor());
            assertEquals(MovementState.OPEN, asked.state());

            movement = walk(movement, null);
            assertTrue(piece(client, old).isEmpty(), "taken back means off the member's list");
            assertEquals(steps.get(1).label(), row(client, movement.id()).reachedStepLabel());

            movement = walk(movement, replacement);
            assertTrue(piece(client, replacement).isEmpty(), "ready on the shelf is not theirs yet");
            assertEquals(steps.get(2).label(), row(client, movement.id()).reachedStepLabel());

            movement = walk(movement, null);
            MovementStanding handedOver = sameStory(client, replacement, movement.id());
            assertEquals(steps.get(3).label(), handedOver.reachedStepLabel());
            assertEquals(
                    StepActor.MEMBER,
                    handedOver.currentStepActor(),
                    "the new piece is on them while the chain waits for them to say they have it");

            movement = walk(movement, null);
            assertEquals(MovementState.DONE, row(client, movement.id()).state());
            assertNull(piece(client, replacement).orElseThrow().movement(), "nothing runs on it any more");
        });
    }

    @Test
    void aRefusedExchangeLeavesThePieceWithNothingRunningOnIt() {
        int old = pieceWithMember();

        harness.run((server, client) -> {
            ItemMovement movement = askForExchange(old);
            sameStory(client, old, movement.id());

            itemMovementService.decline(movement.id(), team, "kein Ersatz");

            assertEquals(MovementState.DECLINED, row(client, movement.id()).state());
            var after = piece(client, old).orElseThrow(() -> new AssertionError("the jacket stays on the member"));
            assertEquals(ItemCustody.WITH_MEMBER, after.custody());
            assertNull(after.movement());
        });
    }

    @Test
    void aCalledOffExchangeLeavesThePieceWithNothingRunningOnIt() {
        int old = pieceWithMember();

        harness.run((server, client) -> {
            ItemMovement movement = askForExchange(old);

            itemMovementService.cancel(movement.id(), team, "passt doch");

            assertEquals(MovementState.CANCELLED, row(client, movement.id()).state());
            assertNull(piece(client, old).orElseThrow().movement());
        });
    }

    @Test
    void aCorrectionPutsBothBackOnTheSameStep() {
        int old = pieceWithMember();

        harness.run((server, client) -> {
            ItemMovement movement = walk(askForExchange(old), null);
            assertTrue(piece(client, old).isEmpty());

            itemMovementService.correct(
                    movement.id(),
                    new ItemMovementService.Correction(ItemCustody.WITH_MEMBER, null, false, null),
                    team,
                    "nie abgegeben");

            MovementStanding corrected = sameStory(client, old, movement.id());
            assertEquals(stepsOf(movement).get(0).label(), corrected.reachedStepLabel());
            assertEquals(StepActor.STATION, corrected.currentStepActor());
        });
    }
}

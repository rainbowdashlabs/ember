/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.equipment.service.EquipmentAvailabilityService;
import dev.chojo.ember.feature.events.service.EventBreakService;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.SelfCheckAnswer;
import dev.chojo.ember.feature.inventory.entity.SelfCheckAnswerInput;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRaisedKind;
import dev.chojo.ember.feature.inventory.entity.SelfCheckState;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SelfCheckServiceTest extends RepositoryTestBase {
    private static Station station;
    private static Station otherStation;
    private static Account memberAccount;
    private static Account guardianAccount;
    private static Account strangerAccount;
    private static Account elsewhereAccount;
    private static Account leaverAccount;
    private static StationMember member;
    private static StationMember guardian;
    private static StationMember stranger;
    private static StationMember elsewhere;
    private static StationMember leaver;
    private static Inventory inventory;
    private static InventoryItem owned;
    private static InventoryItem lost;
    private static InventoryItem borrowed;
    private static FederationRepository federationRepo;
    private static FederationService federationService;
    private static int gap;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("SelfCheckSvcStation");
        otherStation = stationRepo.create("SelfCheckSvcOtherStation");
        memberAccount = accountRepo.create("selfcheck-svc-member@test.com", "Self", "Member");
        guardianAccount = accountRepo.create("selfcheck-svc-guardian@test.com", "Self", "Guardian");
        strangerAccount = accountRepo.create("selfcheck-svc-stranger@test.com", "Self", "Stranger");
        elsewhereAccount = accountRepo.create("selfcheck-svc-elsewhere@test.com", "Self", "Elsewhere");
        leaverAccount = accountRepo.create("selfcheck-svc-leaver@test.com", "Self", "Leaver");
        member = stationMemberRepo.create(station.id(), memberAccount.id());
        guardian = stationMemberRepo.create(station.id(), guardianAccount.id());
        stranger = stationMemberRepo.create(station.id(), strangerAccount.id());
        elsewhere = stationMemberRepo.create(otherStation.id(), elsewhereAccount.id());
        leaver = stationMemberRepo.create(station.id(), leaverAccount.id());
        stationMemberRepo.addManager(guardian.id(), member.id());
        stationMemberRepo.setFormer(leaver.id(), true);

        inventory = inventoryRepo.create(station.id(), "SelfCheckSvcInv", InventoryType.INTERNAL, false);
        owned = inventoryRepo.createItem(inventory.id(), "SCS-001", "Helmet", null, null);
        lost = inventoryRepo.createItem(inventory.id(), "SCS-002", "Jacket", null, null);
        borrowed = borrowFromThePartner();
        for (InventoryItem item : List.of(owned, lost, borrowed)) {
            itemCustodyService.assignToMember(item.id(), member.id(), "");
        }
        itemCustodyService.markLost(lost.id(), "left it on the truck", member.id());

        inventoryRepo.createRequirement(inventory.id(), StationUserType.MEMBER, 0, null, 9);
        var required = inventoryCheckService.getRequiredItems(station.id(), member.id()).stream()
                .filter(r -> r.inventoryId() == inventory.id())
                .findFirst()
                .orElseThrow();
        gap = required.requiredQuantity() - required.assignedQuantity();
        assertTrue(gap > 1, "the fixture needs at least two empty places, found " + gap);
    }

    /**
     * A piece of the partner station's, brought in on a loan, which is the only way a borrowed row
     * comes about. It rests on the shelf the borrowing station keeps for such gear rather than in
     * the inventory the member's requirements are counted against.
     */
    private static InventoryItem borrowFromThePartner() {
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());
        var lending = newLendingService(
                new DomainEventBus(Set.of()),
                new EquipmentAvailabilityService(
                        equipmentAvailabilityRepo,
                        equipmentNeedRepo,
                        eventRepo,
                        new EventBreakService(eventBreakRepo)));
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                station.id(), otherStation.id(), federationService.encodePublicKey(keyPair), null, null);

        var partnerStock =
                inventoryRepo.create(otherStation.id(), "SelfCheckSvcPartnerStock", InventoryType.INTERNAL, false);
        var source = inventoryRepo.createItem(partnerStock.id(), "SCS-P-001", "Radio", null, null);
        var request = lending.createRequest(
                station.id(),
                otherStation.id(),
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                member.id(),
                null,
                null,
                "");
        var line = lending.addRequestItem(request.id(), partnerStock.id(), source.id(), null, 1, null);
        lending.assignItem(line.id(), source.id(), otherStation.id());
        lending.markLent(request.id(), otherStation.id());
        return borrowedGearService.borrowedAt(station.id()).getFirst().item();
    }

    @AfterAll
    static void cleanup() {
        for (var partner : federationService.findPartners(station.id())) federationRepo.deletePartner(partner.id());
        for (var partner : federationService.findPartners(otherStation.id()))
            federationRepo.deletePartner(partner.id());
        inventoryRepo.delete(inventory.id());
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        accountRepo.delete(memberAccount.id());
        accountRepo.delete(guardianAccount.id());
        accountRepo.delete(strangerAccount.id());
        accountRepo.delete(elsewhereAccount.id());
        accountRepo.delete(leaverAccount.id());
    }

    private static int handOut() {
        selfCheckService.closeAllFor(member.id());
        return selfCheckService
                .handOut(station.id(), List.of(member.id()), LocalDate.of(2026, 11, 1), guardian.id())
                .getFirst()
                .id();
    }

    private static SelfCheckAnswerInput aboutPiece(int itemId, SelfCheckAnswer answer) {
        return new SelfCheckAnswerInput(itemId, null, null, answer, "", null);
    }

    private static SelfCheckAnswerInput aboutPlace(int slot, SelfCheckAnswer answer, String typed) {
        return new SelfCheckAnswerInput(null, inventory.id(), slot, answer, "nothing was ever handed to me", typed);
    }

    @Test
    void handingOutCreatesOneTaskPerMemberAndPassesOverAnybodyWhoAlreadyHasOne() {
        selfCheckService.closeAllFor(member.id());
        var first = selfCheckService.handOut(
                station.id(), List.of(member.id(), member.id()), LocalDate.of(2026, 11, 1), guardian.id());
        assertEquals(1, first.size());
        assertEquals(LocalDate.of(2026, 11, 1), first.getFirst().dueOn());
        assertEquals(SelfCheckState.OPEN, first.getFirst().state());

        assertTrue(selfCheckService
                .handOut(station.id(), List.of(member.id()), null, guardian.id())
                .isEmpty());
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void handingOutNeedsSomebodyToAsk() {
        assertThrows(
                BadRequestResponse.class, () -> selfCheckService.handOut(station.id(), List.of(), null, guardian.id()));
        assertThrows(BadRequestResponse.class, () -> selfCheckService.handOut(station.id(), null, null, guardian.id()));
    }

    @Test
    void handingOutRefusesSomebodyOfAnotherStation() {
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.handOut(station.id(), List.of(elsewhere.id()), null, guardian.id()));
    }

    @Test
    void handingOutRefusesAFormerMember() {
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.handOut(station.id(), List.of(leaver.id()), null, guardian.id()));
    }

    @Test
    void aMemberSeesTheirOwnTaskAndAGuardianSeesTheirsToo() {
        int taskId = handOut();
        assertTrue(selfCheckService.outstandingFor(member.id(), false).stream().anyMatch(t -> t.id() == taskId));
        assertEquals(1, selfCheckService.countOutstandingFor(member.id(), false));

        assertTrue(selfCheckService.outstandingFor(guardian.id(), true).stream().anyMatch(t -> t.id() == taskId));
        assertEquals(1, selfCheckService.countOutstandingFor(guardian.id(), true));
        assertEquals(0, selfCheckService.countOutstandingFor(guardian.id(), false));
        assertEquals(0, selfCheckService.countOutstandingFor(stranger.id(), true));
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void readingATaskGivesTheMemberTheirOwnGearAndNothingElse() {
        int taskId = handOut();
        var view = selfCheckService.read(taskId, station.id(), member.id(), false);
        assertEquals(taskId, view.task().id());
        assertEquals("Self Member", view.memberName());
        assertTrue(view.assigned().stream().anyMatch(i -> i.id() == owned.id()));
        assertTrue(view.required().stream().anyMatch(r -> r.inventoryId() == inventory.id()));
        assertTrue(view.rows().isEmpty());
        assertTrue(view.raised().isEmpty());
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void aGuardianReadsTheTaskOfAMemberInTheirCareAndNobodyElseDoes() {
        int taskId = handOut();
        assertEquals(
                taskId,
                selfCheckService
                        .read(taskId, station.id(), guardian.id(), true)
                        .task()
                        .id());
        assertThrows(ForbiddenResponse.class, () -> selfCheckService.read(taskId, station.id(), guardian.id(), false));
        assertThrows(ForbiddenResponse.class, () -> selfCheckService.read(taskId, station.id(), stranger.id(), true));
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void aTaskOfAnotherStationIsSimplyNotThere() {
        int taskId = handOut();
        assertThrows(
                NotFoundResponse.class, () -> selfCheckService.read(taskId, otherStation.id(), member.id(), false));
        assertThrows(NotFoundResponse.class, () -> selfCheckService.read(-1, station.id(), member.id(), false));
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void everyAnswerAMemberMayGiveIsWritten() {
        int taskId = handOut();
        var rows = selfCheckService.answer(
                taskId,
                station.id(),
                member.id(),
                false,
                List.of(
                        aboutPiece(owned.id(), SelfCheckAnswer.HAVE_IT),
                        aboutPiece(lost.id(), SelfCheckAnswer.TURNED_UP),
                        aboutPiece(borrowed.id(), SelfCheckAnswer.DO_NOT_HAVE_IT),
                        aboutPlace(0, SelfCheckAnswer.NEVER_HAD, null),
                        aboutPlace(1, SelfCheckAnswer.HAVE_ONE, " J-42 ")));
        assertEquals(5, rows.size());
        assertTrue(rows.stream()
                .anyMatch(r -> r.answer() == SelfCheckAnswer.HAVE_ONE && "J-42".equals(r.typedInternalId())));
        assertTrue(rows.stream().allMatch(r -> r.answeredBy() != null && r.answeredBy() == member.id()));
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void aGuardianAnsweringIsTheOneWrittenDown() {
        int taskId = handOut();
        var rows = selfCheckService.answer(
                taskId, station.id(), guardian.id(), true, List.of(aboutPiece(owned.id(), SelfCheckAnswer.HAVE_IT)));
        assertEquals(guardian.id(), rows.getFirst().answeredBy());
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void aWrongRecordIsRaisedAndNothingIsPutRight() {
        int taskId = handOut();
        selfCheckService.answer(
                taskId,
                station.id(),
                member.id(),
                false,
                List.of(aboutPiece(owned.id(), SelfCheckAnswer.WRONG_RECORD)));
        var unchanged = inventoryRepo.findItemById(owned.id()).orElseThrow();
        assertEquals(member.id(), unchanged.assignedTo());
        assertEquals("SCS-001", unchanged.internalId());
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void aTypedNumberIsKeptAsTypedAndNothingIsAssignedFromIt() {
        int taskId = handOut();
        var free = inventoryRepo.createItem(inventory.id(), "SCS-FREE", "Spare", null, null);
        selfCheckService.answer(
                taskId, station.id(), member.id(), false, List.of(aboutPlace(0, SelfCheckAnswer.HAVE_ONE, "SCS-FREE")));
        assertNull(inventoryRepo.findItemById(free.id()).orElseThrow().assignedTo());
        inventoryRepo.deleteItem(free.id());
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void nothingIsWrittenWithoutAnAnswer() {
        int taskId = handOut();
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(taskId, station.id(), member.id(), false, List.of()));
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(taskId, station.id(), member.id(), false, null));
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId, station.id(), member.id(), false, Arrays.asList((SelfCheckAnswerInput) null)));
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(new SelfCheckAnswerInput(owned.id(), null, null, null, "", null))));
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void anAnswerHasToFitTheThingItIsAbout() {
        int taskId = handOut();
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(aboutPiece(owned.id(), SelfCheckAnswer.NEVER_HAD))));
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(aboutPlace(0, SelfCheckAnswer.HAVE_IT, null))));
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void aMemberMaySayNothingAboutGearThatIsNotOnTheirRecord() {
        int taskId = handOut();
        var somebodyElses = inventoryRepo.createItem(inventory.id(), "SCS-OTHER", "Boots", null, null);
        itemCustodyService.assignToMember(somebodyElses.id(), stranger.id(), "");
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(aboutPiece(somebodyElses.id(), SelfCheckAnswer.HAVE_IT))));
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId, station.id(), member.id(), false, List.of(aboutPiece(-1, SelfCheckAnswer.HAVE_IT))));
        itemCustodyService.takeBack(somebodyElses.id());
        inventoryRepo.deleteItem(somebodyElses.id());
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void whatCannotBeSaidAboutAPieceIsRefused() {
        int taskId = handOut();
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(aboutPiece(owned.id(), SelfCheckAnswer.DO_NOT_HAVE_IT))));
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(aboutPiece(owned.id(), SelfCheckAnswer.TURNED_UP))));
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void anEmptyPlaceHasToBeOneTheMemberActuallyHas() {
        int taskId = handOut();
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(new SelfCheckAnswerInput(null, null, 0, SelfCheckAnswer.NEVER_HAD, "", null))));
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(new SelfCheckAnswerInput(
                                null, inventory.id(), -1, SelfCheckAnswer.NEVER_HAD, "", null))));
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(new SelfCheckAnswerInput(null, -1, 0, SelfCheckAnswer.NEVER_HAD, "", null))));
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(aboutPlace(gap, SelfCheckAnswer.NEVER_HAD, null))));
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void onlyAPlaceSomethingIsHeldForTakesANumber() {
        int taskId = handOut();
        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(aboutPlace(0, SelfCheckAnswer.NEVER_HAD, "X-1"))));
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void aSubmittedTaskTakesNothingFurther() {
        int taskId = handOut();
        var submitted = selfCheckService.submit(taskId, station.id(), member.id(), false);
        assertEquals(SelfCheckState.SUBMITTED, submitted.state());
        assertEquals(member.id(), submitted.submittedBy());

        assertThrows(ConflictResponse.class, () -> selfCheckService.submit(taskId, station.id(), member.id(), false));
        assertThrows(
                ConflictResponse.class,
                () -> selfCheckService.answer(
                        taskId,
                        station.id(),
                        member.id(),
                        false,
                        List.of(aboutPiece(owned.id(), SelfCheckAnswer.HAVE_IT))));
        assertThrows(
                ConflictResponse.class,
                () -> selfCheckService.recordLoss(taskId, station.id(), member.id(), false, owned.id()));
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void whatTheMemberSetGoingIsRecordedAgainstTheTask() {
        int taskId = handOut();
        var spare = inventoryRepo.createItem(inventory.id(), "SCS-SPARE", "Spare Jacket", null, null);
        itemCustodyService.assignToMember(spare.id(), stranger.id(), "");
        var movement = exchangeService.create(
                station.id(),
                stranger.id(),
                "Self Stranger",
                spare.id(),
                inventory.id(),
                null,
                null,
                "too small",
                null);

        var loss = selfCheckService.recordLoss(taskId, station.id(), member.id(), false, owned.id());
        var exchange =
                selfCheckService.recordExchange(taskId, station.id(), guardian.id(), true, spare.id(), movement.id());
        assertEquals(SelfCheckRaisedKind.LOSS, loss.kind());
        assertEquals(SelfCheckRaisedKind.EXCHANGE, exchange.kind());
        assertEquals(guardian.id(), exchange.raisedBy());
        assertEquals(movement.id(), exchange.movementId());

        var view = selfCheckService.read(taskId, station.id(), member.id(), false);
        assertEquals(2, view.raised().size());
        selfCheckService.closeAllFor(member.id());
    }

    @Test
    void leavingTheStationEndsWhatWasStillOpen() {
        handOut();
        assertEquals(1, selfCheckService.closeAllFor(member.id()));
        assertEquals(0, selfCheckService.closeAllFor(member.id()));
        assertTrue(selfCheckService.outstandingFor(member.id(), false).isEmpty());
    }
}

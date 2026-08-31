/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCorrection;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.SelfCheck;
import dev.chojo.ember.feature.inventory.entity.SelfCheckAnswer;
import dev.chojo.ember.feature.inventory.entity.SelfCheckIdentifierFinding;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRaisedKind;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRecordRemoval;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRow;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRowState;
import dev.chojo.ember.feature.inventory.entity.SelfCheckSettlement;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Settling a submission, and everything that must not settle one.
 *
 * <p>The rows are written straight onto the repository rather than through the member's own service,
 * because what is under test is the reading and the settling, and an answer only a borrowed piece
 * may carry would otherwise need a whole loan built beside it.
 */
class SelfCheckReviewServiceTest extends RepositoryTestBase {
    private static Station station;
    private static Station otherStation;
    private static StationMember member;
    private static StationMember guardian;
    private static StationMember reviewer;
    private static StationMember walked;
    private static Inventory inventory;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("SelfCheckReviewStation");
        otherStation = stationRepo.create("SelfCheckReviewOther");
        Account memberAccount = accountRepo.create("review-member@test.com", "Review", "Member");
        Account guardianAccount = accountRepo.create("review-guardian@test.com", "Review", "Guardian");
        Account reviewerAccount = accountRepo.create("review-reviewer@test.com", "Review", "Reviewer");
        member = stationMemberRepo.create(station.id(), memberAccount.id());
        guardian = stationMemberRepo.create(station.id(), guardianAccount.id());
        reviewer = stationMemberRepo.create(station.id(), reviewerAccount.id());
        stationMemberRepo.addManager(guardian.id(), member.id());
        Account walkedAccount = accountRepo.create("review-walked@test.com", "Review", "Walked");
        walked = stationMemberRepo.create(station.id(), walkedAccount.id());

        inventory = inventoryRepo.create(station.id(), "SelfCheckReviewInv", InventoryType.INTERNAL, false);
        inventoryRepo.createRequirement(inventory.id(), StationUserType.MEMBER, 0, null, 6);
    }

    @AfterAll
    static void cleanup() {
        inventoryRepo.delete(inventory.id());
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
    }

    private static InventoryItem piece(String internalId, String name) {
        InventoryItem item = inventoryRepo.createItem(inventory.id(), internalId, name, null, null);
        itemCustodyService.assignToMember(item.id(), member.id(), "");
        return inventoryRepo.findItemById(item.id()).orElseThrow();
    }

    private static SelfCheck submitted() {
        return submitted(member.id());
    }

    private static SelfCheck submitted(int answeredBy) {
        SelfCheck task = selfCheckRepo.create(station.id(), member.id(), reviewer.id(), LocalDate.now());
        selfCheckRepo.submit(task.id(), answeredBy);
        return selfCheckRepo.findById(task.id()).orElseThrow();
    }

    private static SelfCheckRow answer(SelfCheck task, InventoryItem item, SelfCheckAnswer given) {
        return selfCheckRepo.answerForItem(task.id(), item.id(), item.inventoryId(), given, "", null, member.id());
    }

    @Test
    void takingTheLastAnswerWritesACheckCarryingBothNames() {
        InventoryItem helmet = piece("SCR-CONFIRM", "Helmet");
        SelfCheck task = submitted();
        SelfCheckRow row = answer(task, helmet, SelfCheckAnswer.HAVE_IT);

        var review = selfCheckReviewService.take(task.id(), row.id(), station.id(), reviewer.id());

        assertEquals(SelfCheckState.DONE, review.task().state());
        assertNotNull(review.task().checkId());
        var check = inventoryCheckRepo.findById(review.task().checkId()).orElseThrow();
        assertEquals(reviewer.id(), check.checkedBy());
        assertEquals(member.id(), check.reportedBy());
        var items = inventoryCheckRepo.findCheckItems(check.id());
        assertEquals(1, items.size());
        assertEquals(CheckResult.CONFIRMED, items.getFirst().result());
        assertEquals(SelfCheckRowState.TAKEN, review.rows().getFirst().row().state());
        assertEquals(reviewer.id(), review.rows().getFirst().row().reviewedBy());
    }

    @Test
    void anEmptyPlaceTheMemberNeverHadGoesDownAsNotHeld() {
        SelfCheck task = submitted();
        SelfCheckRow row = selfCheckRepo.answerForPlace(
                task.id(), inventory.id(), 0, SelfCheckAnswer.NEVER_HAD, "never got one", null, member.id());

        var review = selfCheckReviewService.take(task.id(), row.id(), station.id(), reviewer.id());

        var check = inventoryCheckRepo.findById(review.task().checkId()).orElseThrow();
        var items = inventoryCheckRepo.findCheckItems(check.id());
        assertEquals(CheckResult.NOT_IN_POSSESSION, items.getFirst().result());
        assertNull(items.getFirst().itemId());
    }

    @Test
    void aPieceThatTurnedUpIsMarkedFound() {
        InventoryItem jacket = piece("SCR-FOUND", "Jacket");
        itemCustodyService.markLost(jacket.id(), "left it somewhere", member.id());
        SelfCheck task = submitted();
        SelfCheckRow row = answer(task, jacket, SelfCheckAnswer.TURNED_UP);

        selfCheckReviewService.take(task.id(), row.id(), station.id(), reviewer.id());

        var after = inventoryRepo.findItemById(jacket.id()).orElseThrow();
        assertFalse(after.custody() == ItemCustody.LOST, "the piece should be back on the record");
    }

    @Test
    void aReviewerReadsWhatEachAnswerWouldDo() {
        InventoryItem boots = piece("SCR-READ", "Boots");
        SelfCheck task = submitted();
        answer(task, boots, SelfCheckAnswer.WRONG_RECORD);

        var review = selfCheckReviewService.read(task.id(), station.id(), reviewer.id());

        var row = review.rows().getFirst();
        assertEquals(SelfCheckSettlement.NEEDS_RECORD_PUT_RIGHT, row.settlement());
        assertEquals(SelfCheckRecordRemoval.BACK_TO_STORE, row.removal());
        assertEquals("Review Member", review.memberName());
        assertEquals("Review Reviewer", review.handedOutByName());
        assertTrue(review.mayApprove());
        assertFalse(review.freeStock().isEmpty());
    }

    @Test
    void anAnswerAskingForTheRecordToBePutRightCannotSimplyBeTaken() {
        InventoryItem gloves = piece("SCR-WRONG", "Gloves");
        SelfCheck task = submitted();
        SelfCheckRow row = answer(task, gloves, SelfCheckAnswer.WRONG_RECORD);

        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckReviewService.take(task.id(), row.id(), station.id(), reviewer.id()));
    }

    @Test
    void anEmptyPlaceTheMemberIsHoldingSomethingForCannotSimplyBeTaken() {
        SelfCheck task = submitted();
        SelfCheckRow row = selfCheckRepo.answerForPlace(
                task.id(), inventory.id(), 1, SelfCheckAnswer.HAVE_ONE, "", "SCR-TYPED", member.id());

        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckReviewService.take(task.id(), row.id(), station.id(), reviewer.id()));
    }

    @Test
    void puttingTheRecordRightSettlesTheAnswerAgainstThePieceTheMemberHolds() {
        InventoryItem written = piece("SCR-STALE", "Old jacket");
        InventoryItem actual = inventoryRepo.createItem(inventory.id(), "SCR-ACTUAL", "New jacket", null, null);
        SelfCheck task = submitted();
        SelfCheckRow row = answer(task, written, SelfCheckAnswer.WRONG_RECORD);

        var review = selfCheckReviewService.correctAndTake(
                task.id(),
                row.id(),
                station.id(),
                reviewer.id(),
                new ItemCorrection(inventory.id(), null, actual.id(), null, null, null, null));

        assertEquals(SelfCheckState.DONE, review.task().state());
        assertEquals(actual.id(), review.rows().getFirst().row().itemId());
        assertEquals(
                member.id(),
                inventoryRepo.findItemById(actual.id()).orElseThrow().assignedTo());
        assertNull(inventoryRepo.findItemById(written.id()).orElseThrow().assignedTo());
    }

    @Test
    void namingAPieceForAnEmptyPlaceAssignsItAndSettlesTheAnswer() {
        InventoryItem unwritten = inventoryRepo.createItem(inventory.id(), "SCR-UNWRITTEN", "Spare", null, null);
        SelfCheck task = submitted();
        SelfCheckRow row = selfCheckRepo.answerForPlace(
                task.id(), inventory.id(), 2, SelfCheckAnswer.HAVE_ONE, "", "SCR-UNWRITTEN", member.id());

        var review = selfCheckReviewService.correctAndTake(
                task.id(),
                row.id(),
                station.id(),
                reviewer.id(),
                new ItemCorrection(inventory.id(), null, unwritten.id(), null, null, null, null));

        assertEquals(unwritten.id(), review.rows().getFirst().row().itemId());
        assertEquals(
                member.id(),
                inventoryRepo.findItemById(unwritten.id()).orElseThrow().assignedTo());
    }

    @Test
    void anAnswerThatSettlesOnItsOwnIsNotCorrected() {
        InventoryItem helmet = piece("SCR-NOCORRECT", "Helmet");
        SelfCheck task = submitted();
        SelfCheckRow row = answer(task, helmet, SelfCheckAnswer.HAVE_IT);

        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckReviewService.correctAndTake(
                        task.id(),
                        row.id(),
                        station.id(),
                        reviewer.id(),
                        new ItemCorrection(inventory.id(), null, null, null, null, "SCR-X", null)));
    }

    @Test
    void aCorrectionHasToSayWhatTheMemberHolds() {
        InventoryItem coat = piece("SCR-NOTHING", "Coat");
        SelfCheck task = submitted();
        SelfCheckRow row = answer(task, coat, SelfCheckAnswer.WRONG_RECORD);

        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckReviewService.correctAndTake(task.id(), row.id(), station.id(), reviewer.id(), null));
    }

    @Test
    void arefusedAnswerGoesBackToTheMemberAndTakesNothingWithIt() {
        InventoryItem kept = piece("SCR-KEPT", "Kept");
        InventoryItem sent = piece("SCR-SENT", "Sent back");
        SelfCheck task = submitted();
        SelfCheckRow taken = answer(task, kept, SelfCheckAnswer.HAVE_IT);
        SelfCheckRow refused = answer(task, sent, SelfCheckAnswer.HAVE_IT);

        selfCheckReviewService.take(task.id(), taken.id(), station.id(), reviewer.id());
        var review = selfCheckReviewService.refuse(
                task.id(), refused.id(), station.id(), reviewer.id(), "  that is not this station's  ");

        assertEquals(SelfCheckState.OPEN, review.task().state());
        assertNull(review.task().submittedBy());
        assertEquals(1, review.rows().size());
        assertEquals(SelfCheckRowState.REFUSED, review.rows().getFirst().row().state());
        assertEquals(
                "that is not this station's", review.rows().getFirst().row().reviewerReason());
    }

    @Test
    void aRefusalHasToSayWhy() {
        InventoryItem cap = piece("SCR-NOREASON", "Cap");
        SelfCheck task = submitted();
        SelfCheckRow row = answer(task, cap, SelfCheckAnswer.HAVE_IT);

        assertThrows(
                BadRequestResponse.class,
                () -> selfCheckReviewService.refuse(task.id(), row.id(), station.id(), reviewer.id(), "   "));
    }

    @Test
    void anAnswerIsSettledOnce() {
        InventoryItem belt = piece("SCR-TWICE", "Belt");
        SelfCheck task = submitted();
        SelfCheckRow row = answer(task, belt, SelfCheckAnswer.HAVE_IT);

        selfCheckReviewService.take(task.id(), row.id(), station.id(), reviewer.id());

        assertThrows(
                ConflictResponse.class,
                () -> selfCheckReviewService.take(task.id(), row.id(), station.id(), reviewer.id()));
    }

    @Test
    void aTaskNobodyHandedInIsNotWaitingToBeRead() {
        InventoryItem scarf = piece("SCR-OPEN", "Scarf");
        SelfCheck task = selfCheckRepo.create(station.id(), member.id(), reviewer.id(), null);
        SelfCheckRow row = answer(task, scarf, SelfCheckAnswer.HAVE_IT);

        assertThrows(
                ConflictResponse.class,
                () -> selfCheckReviewService.take(task.id(), row.id(), station.id(), reviewer.id()));
    }

    @Test
    void aReviewerDoesNotSignOffTheirOwnGear() {
        SelfCheck ownTask = selfCheckRepo.create(station.id(), reviewer.id(), reviewer.id(), null);
        selfCheckRepo.submit(ownTask.id(), reviewer.id());
        InventoryItem own = inventoryRepo.createItem(inventory.id(), "SCR-OWN", "Own", null, null);
        itemCustodyService.assignToMember(own.id(), reviewer.id(), "");
        SelfCheckRow row = selfCheckRepo.answerForItem(
                ownTask.id(), own.id(), inventory.id(), SelfCheckAnswer.HAVE_IT, "", null, reviewer.id());

        assertThrows(
                ForbiddenResponse.class,
                () -> selfCheckReviewService.take(ownTask.id(), row.id(), station.id(), reviewer.id()));
        var review = selfCheckReviewService.read(ownTask.id(), station.id(), reviewer.id());
        assertFalse(review.mayApprove());
        assertEquals("This submission is about your own gear", review.approvalRefusal());
    }

    @Test
    void aGuardianDoesNotSignOffWhatTheyAnsweredForTheirWard() {
        InventoryItem ward = piece("SCR-WARD", "Ward helmet");
        SelfCheck task = submitted(guardian.id());
        SelfCheckRow row = selfCheckRepo.answerForItem(
                task.id(), ward.id(), inventory.id(), SelfCheckAnswer.HAVE_IT, "", null, guardian.id());

        assertThrows(
                ForbiddenResponse.class,
                () -> selfCheckReviewService.take(task.id(), row.id(), station.id(), guardian.id()));
        var review = selfCheckReviewService.read(task.id(), station.id(), guardian.id());
        assertFalse(review.mayApprove());
        assertEquals("You entered this submission yourself", review.approvalRefusal());
    }

    @Test
    void whoeverWroteAnAnswerDoesNotSettleThatAnswer() {
        InventoryItem shared = piece("SCR-WROTE", "Shared");
        SelfCheck task = submitted();
        SelfCheckRow row = selfCheckRepo.answerForItem(
                task.id(), shared.id(), inventory.id(), SelfCheckAnswer.HAVE_IT, "", null, guardian.id());

        assertThrows(
                ForbiddenResponse.class,
                () -> selfCheckReviewService.refuse(task.id(), row.id(), station.id(), guardian.id(), "no"));
    }

    @Test
    void aTaskOfAnotherStationAnswersAsAbsent() {
        SelfCheck task = submitted();
        assertThrows(
                NotFoundResponse.class, () -> selfCheckReviewService.read(task.id(), otherStation.id(), reviewer.id()));
    }

    @Test
    void anAnswerOfAnotherTaskAnswersAsAbsent() {
        SelfCheck task = submitted();
        SelfCheck other = submitted();
        InventoryItem elsewhere = piece("SCR-ELSEWHERE", "Elsewhere");
        SelfCheckRow row = answer(other, elsewhere, SelfCheckAnswer.HAVE_IT);

        assertThrows(
                NotFoundResponse.class,
                () -> selfCheckReviewService.take(task.id(), row.id(), station.id(), reviewer.id()));
    }

    @Test
    void aNumberNobodyTypedIsNoFinding() {
        InventoryItem plain = piece("SCR-PLAIN", "Plain");
        SelfCheck task = submitted();
        answer(task, plain, SelfCheckAnswer.HAVE_IT);

        var review = selfCheckReviewService.read(task.id(), station.id(), reviewer.id());
        assertEquals(
                SelfCheckIdentifierFinding.NOTHING_TYPED,
                review.rows().getFirst().identifier().finding());
    }

    @Test
    void aNumberIsMatchedWithoutRegardToCaseOrSpaces() {
        InventoryItem free = inventoryRepo.createItem(inventory.id(), "SCR-CASE", "Free piece", null, null);
        SelfCheck task = submitted();
        selfCheckRepo.answerForPlace(
                task.id(), inventory.id(), 3, SelfCheckAnswer.HAVE_ONE, "", "  scr-case ", member.id());

        var match = selfCheckReviewService
                .read(task.id(), station.id(), reviewer.id())
                .rows()
                .getFirst()
                .identifier();
        assertEquals(SelfCheckIdentifierFinding.FREE, match.finding());
        assertEquals(free.id(), match.pieces().getFirst().itemId());
        assertEquals("SelfCheckReviewInv", match.pieces().getFirst().inventoryName());
    }

    @Test
    void aNumberOnSomebodyElsesPieceIsAFindingRatherThanATransfer() {
        InventoryItem theirs = inventoryRepo.createItem(inventory.id(), "SCR-THEIRS", "Theirs", null, null);
        itemCustodyService.assignToMember(theirs.id(), guardian.id(), "");
        SelfCheck task = submitted();
        selfCheckRepo.answerForPlace(
                task.id(), inventory.id(), 4, SelfCheckAnswer.HAVE_ONE, "", "SCR-THEIRS", member.id());

        var match = selfCheckReviewService
                .read(task.id(), station.id(), reviewer.id())
                .rows()
                .getFirst()
                .identifier();
        assertEquals(SelfCheckIdentifierFinding.HELD, match.finding());
        assertEquals("Review Guardian", match.pieces().getFirst().heldByName());
    }

    @Test
    void aNumberNothingCarriesIsAFindingOfItsOwn() {
        SelfCheck task = submitted();
        selfCheckRepo.answerForPlace(
                task.id(), inventory.id(), 5, SelfCheckAnswer.HAVE_ONE, "", "SCR-NOWHERE", member.id());

        var match = selfCheckReviewService
                .read(task.id(), station.id(), reviewer.id())
                .rows()
                .getFirst()
                .identifier();
        assertEquals(SelfCheckIdentifierFinding.NO_MATCH, match.finding());
        assertTrue(match.pieces().isEmpty());
    }

    @Test
    void aNumberMoreThanOnePieceCarriesShowsThemAll() {
        inventoryRepo.createItem(inventory.id(), "SCR-DOUBLE", "First", null, null);
        inventoryRepo.createItem(inventory.id(), "SCR-DOUBLE", "Second", null, null);
        SelfCheck task = submitted();
        selfCheckRepo.answerForPlace(
                task.id(), inventory.id(), 0, SelfCheckAnswer.HAVE_ONE, "", "SCR-DOUBLE", member.id());

        var match = selfCheckReviewService
                .read(task.id(), station.id(), reviewer.id())
                .rows()
                .getFirst()
                .identifier();
        assertEquals(SelfCheckIdentifierFinding.SEVERAL, match.finding());
        assertEquals(2, match.pieces().size());
    }

    @Test
    void aContainersNumberIsAFindingBecauseTheyShareTheNumbering() {
        containerRepo.create(station.id(), null, "SCR-BOX", "The box", null, "", reviewer.id());
        SelfCheck task = submitted();
        selfCheckRepo.answerForPlace(
                task.id(), inventory.id(), 1, SelfCheckAnswer.HAVE_ONE, "", "scr-box", member.id());

        var match = selfCheckReviewService
                .read(task.id(), station.id(), reviewer.id())
                .rows()
                .getFirst()
                .identifier();
        assertEquals(SelfCheckIdentifierFinding.A_CONTAINER, match.finding());
        assertEquals(List.of("The box"), match.containers());
    }

    @Test
    void aRecordPutRightEndsAPieceNobodyKeeps() {
        InventoryItem orphan =
                inventoryRepo.createItem(inventory.id(), "SCR-ORPHAN", "Orphan", null, null, ItemOwner.CLUSTER, null);
        itemCustodyService.assignToMember(orphan.id(), member.id(), "");
        SelfCheck task = submitted();
        answer(task, inventoryRepo.findItemById(orphan.id()).orElseThrow(), SelfCheckAnswer.WRONG_RECORD);

        var row = selfCheckReviewService
                .read(task.id(), station.id(), reviewer.id())
                .rows()
                .getFirst();
        assertEquals(SelfCheckRecordRemoval.DELETED, row.removal());
    }

    @Test
    void anAnswerAboutAnEmptyPlaceTakesNoPieceOffTheRecord() {
        SelfCheck task = submitted();
        selfCheckRepo.answerForPlace(task.id(), inventory.id(), 2, SelfCheckAnswer.NEVER_HAD, "", null, member.id());

        var row = selfCheckReviewService
                .read(task.id(), station.id(), reviewer.id())
                .rows()
                .getFirst();
        assertEquals(SelfCheckRecordRemoval.NOTHING, row.removal());
    }

    @Test
    void anAnswerWhosePieceHasGoneIsShownAsHavingLostItsAnchor() {
        InventoryItem doomed = piece("SCR-DOOMED", "Doomed");
        SelfCheck task = submitted();
        SelfCheckRow row = answer(task, doomed, SelfCheckAnswer.HAVE_IT);
        itemCustodyService.takeBack(doomed.id());
        inventoryRepo.deleteItem(doomed.id());

        var read = selfCheckReviewService
                .read(task.id(), station.id(), reviewer.id())
                .rows()
                .getFirst();
        assertEquals(SelfCheckSettlement.ANCHOR_GONE, read.settlement());
        assertTrue(read.row().anchorGone());

        var review = selfCheckReviewService.take(task.id(), row.id(), station.id(), reviewer.id());
        var check = inventoryCheckRepo.findById(review.task().checkId()).orElseThrow();
        assertTrue(inventoryCheckRepo.findCheckItems(check.id()).isEmpty());
    }

    @Test
    void whatTheMemberSetGoingWithoutWaitingIsShownBeside() {
        InventoryItem raisedAbout = piece("SCR-RAISED", "Raised about");
        SelfCheck task = submitted();
        answer(task, raisedAbout, SelfCheckAnswer.HAVE_IT);
        selfCheckRepo.recordRaised(task.id(), SelfCheckRaisedKind.EXCHANGE, raisedAbout.id(), null, member.id());

        var review = selfCheckReviewService.read(task.id(), station.id(), reviewer.id());
        assertEquals(1, review.raised().size());
        assertEquals("Raised about", review.raised().getFirst().itemName());
        assertEquals("Review Member", review.raised().getFirst().raisedByName());
    }

    @Test
    void theStationSeesWhatItHasOutAndWhatItHasFinished() {
        SelfCheck open = selfCheckRepo.create(station.id(), member.id(), reviewer.id(), null);
        selfCheckRepo.finish(open.id(), SelfCheckState.DONE, null);
        SelfCheck waiting = submitted();

        var outstanding = selfCheckReviewService.forStation(station.id(), false);
        assertTrue(outstanding.stream().anyMatch(t -> t.id() == waiting.id()));
        assertFalse(outstanding.stream().anyMatch(t -> t.id() == open.id()));

        var everything = selfCheckReviewService.forStation(station.id(), true);
        assertTrue(everything.stream().anyMatch(t -> t.id() == open.id()));
    }

    @Test
    void aCheckersOwnWalkClosesWhatTheMemberWasAsked() {
        SelfCheck task = selfCheckRepo.create(station.id(), walked.id(), reviewer.id(), null);

        var begun = inventoryCheckService.startCheck(station.id(), walked.id(), reviewer.id());
        var loaded = inventoryCheckService.startCheck(station.id(), walked.id(), reviewer.id());
        inventoryCheckService.cancelCheck(walked.id(), reviewer.id());

        assertEquals(
                SelfCheckState.OVERTAKEN,
                selfCheckRepo.findById(task.id()).orElseThrow().state());
        assertEquals(1, begun.overtookSelfChecks().size());
        assertTrue(loaded.overtookSelfChecks().isEmpty(), "the load path must not report an overtaking twice");
    }
}

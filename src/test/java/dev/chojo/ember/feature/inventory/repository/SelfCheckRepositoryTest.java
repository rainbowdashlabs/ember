/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.SelfCheckAnswer;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRaisedKind;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRowState;
import dev.chojo.ember.feature.inventory.entity.SelfCheckState;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.*;

class SelfCheckRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account memberAccount;
    private static Account checkerAccount;
    private static StationMember member;
    private static StationMember checker;
    private static Inventory inventory;
    private static InventoryItem item;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("SelfCheckRepoStation");
        memberAccount = accountRepo.create("selfcheck-repo-member@test.com", "Self", "Member");
        checkerAccount = accountRepo.create("selfcheck-repo-checker@test.com", "Self", "Checker");
        member = stationMemberRepo.create(station.id(), memberAccount.id());
        checker = stationMemberRepo.create(station.id(), checkerAccount.id());
        inventory = inventoryRepo.create(station.id(), "SelfCheckRepoInv", InventoryType.INTERNAL, false);
        item = inventoryRepo.createItem(inventory.id(), "SCR-001", "Helmet", null, null);
        itemCustodyService.assignToMember(item.id(), member.id(), "");
    }

    @AfterAll
    static void cleanup() {
        inventoryRepo.delete(inventory.id());
        stationRepo.delete(station.id());
        accountRepo.delete(memberAccount.id());
        accountRepo.delete(checkerAccount.id());
    }

    private static int newTask() {
        return selfCheckRepo
                .create(station.id(), member.id(), checker.id(), LocalDate.of(2026, 10, 1))
                .id();
    }

    @Test
    void createStartsOpenAndCarriesTheDueDate() {
        var task = selfCheckRepo.create(station.id(), member.id(), checker.id(), LocalDate.of(2026, 12, 24));
        assertEquals(SelfCheckState.OPEN, task.state());
        assertTrue(task.open());
        assertEquals(LocalDate.of(2026, 12, 24), task.dueOn());
        assertEquals(member.id(), task.memberId());
        assertEquals(checker.id(), task.handedOutBy());
        assertNull(task.submittedAt());
        assertNull(task.closedAt());
        assertNull(task.checkId());
        assertNotNull(task.handedOutAt());
        selfCheckRepo.overtake(task.id());
    }

    @Test
    void createTakesNoDueDate() {
        var task = selfCheckRepo.create(station.id(), member.id(), checker.id(), null);
        assertNull(task.dueOn());
        selfCheckRepo.overtake(task.id());
    }

    @Test
    void findByIdReadsBackWhatWasWritten() {
        int taskId = newTask();
        var found = selfCheckRepo.findById(taskId).orElseThrow();
        assertEquals(taskId, found.id());
        assertEquals(station.id(), found.stationId());
        selfCheckRepo.overtake(taskId);
    }

    @Test
    void findByIdIsEmptyForSomethingThatIsNotThere() {
        assertTrue(selfCheckRepo.findById(-1).isEmpty());
    }

    @Test
    void unfinishedTasksAreFoundAndCountedForTheMembersNamed() {
        int taskId = newTask();
        assertTrue(selfCheckRepo.findUnfinishedForMembers(List.of(member.id())).stream()
                .anyMatch(t -> t.id() == taskId));
        assertTrue(selfCheckRepo.countUnfinishedForMembers(List.of(member.id())) > 0);
        selfCheckRepo.overtake(taskId);
    }

    @Test
    void nobodyNamedMeansNothingAsked() {
        assertTrue(selfCheckRepo.findUnfinishedForMembers(List.of()).isEmpty());
        assertTrue(selfCheckRepo.findUnfinishedForMembers(null).isEmpty());
        assertEquals(0, selfCheckRepo.countUnfinishedForMembers(List.of()));
        assertEquals(0, selfCheckRepo.countUnfinishedForMembers(null));
    }

    @Test
    void submitClosesTheAnsweringOnlyOnce() {
        int taskId = newTask();
        assertTrue(selfCheckRepo.submit(taskId, member.id()));
        assertFalse(selfCheckRepo.submit(taskId, member.id()));
        var task = selfCheckRepo.findById(taskId).orElseThrow();
        assertEquals(SelfCheckState.SUBMITTED, task.state());
        assertEquals(member.id(), task.submittedBy());
        assertNotNull(task.submittedAt());
        assertFalse(task.open());
        selfCheckRepo.overtake(taskId);
    }

    @Test
    void reopenSendsASubmittedTaskBackAndOnlyASubmittedOne() {
        int taskId = newTask();
        assertFalse(selfCheckRepo.reopen(taskId));
        selfCheckRepo.submit(taskId, member.id());
        assertTrue(selfCheckRepo.reopen(taskId));
        var task = selfCheckRepo.findById(taskId).orElseThrow();
        assertEquals(SelfCheckState.OPEN, task.state());
        assertNull(task.submittedAt());
        assertNull(task.submittedBy());
        selfCheckRepo.overtake(taskId);
    }

    @Test
    void anAnswerAboutAPieceIsWrittenOnceAndThenRewritten() {
        int taskId = newTask();
        var first = selfCheckRepo.answerForItem(
                taskId, item.id(), inventory.id(), SelfCheckAnswer.HAVE_IT, "still fits", null, member.id());
        assertEquals(SelfCheckRowState.OUTSTANDING, first.state());
        assertEquals(item.id(), first.itemId());
        assertNull(first.slot());
        assertFalse(first.anchorGone());

        var second = selfCheckRepo.answerForItem(
                taskId, item.id(), inventory.id(), SelfCheckAnswer.WRONG_RECORD, "different one", null, member.id());
        assertEquals(first.id(), second.id());
        assertEquals(SelfCheckAnswer.WRONG_RECORD, second.answer());
        assertEquals("different one", second.note());
        assertEquals(1, selfCheckRepo.findRows(taskId).size());
        selfCheckRepo.overtake(taskId);
    }

    @Test
    void anAnswerAboutAnEmptyPlaceHangsOnTheInventoryAndTheSlot() {
        int taskId = newTask();
        var zero = selfCheckRepo.answerForPlace(
                taskId, inventory.id(), 0, SelfCheckAnswer.NEVER_HAD, "", null, member.id());
        var one = selfCheckRepo.answerForPlace(
                taskId, inventory.id(), 1, SelfCheckAnswer.HAVE_ONE, "found one", "X-9", member.id());
        assertNotEquals(zero.id(), one.id());
        assertEquals(0, zero.slot());
        assertEquals("X-9", one.typedInternalId());
        assertNull(one.itemId());
        assertEquals(2, selfCheckRepo.findRows(taskId).size());

        var again = selfCheckRepo.answerForPlace(
                taskId, inventory.id(), 1, SelfCheckAnswer.NEVER_HAD, "", null, member.id());
        assertEquals(one.id(), again.id());
        assertNull(again.typedInternalId());
        selfCheckRepo.overtake(taskId);
    }

    @Test
    void findRowReadsOneAnswerBack() {
        int taskId = newTask();
        var row = selfCheckRepo.answerForPlace(
                taskId, inventory.id(), 0, SelfCheckAnswer.NEVER_HAD, "", null, member.id());
        assertEquals(row.id(), selfCheckRepo.findRow(row.id()).orElseThrow().id());
        assertTrue(selfCheckRepo.findRow(-1).isEmpty());
        selfCheckRepo.overtake(taskId);
    }

    @Test
    void takingAnAnswerSucceedsOnceAndOnlyOnce() {
        int taskId = newTask();
        var row = selfCheckRepo.answerForPlace(
                taskId, inventory.id(), 0, SelfCheckAnswer.NEVER_HAD, "", null, member.id());
        assertTrue(selfCheckRepo.hasOutstandingRows(taskId));
        assertTrue(selfCheckRepo.take(row.id(), checker.id()));
        assertFalse(selfCheckRepo.take(row.id(), checker.id()));
        assertFalse(selfCheckRepo.refuse(row.id(), "too late", checker.id()));

        var settled = selfCheckRepo.findRow(row.id()).orElseThrow();
        assertEquals(SelfCheckRowState.TAKEN, settled.state());
        assertEquals(checker.id(), settled.reviewedBy());
        assertNotNull(settled.reviewedAt());
        assertFalse(selfCheckRepo.hasOutstandingRows(taskId));
        selfCheckRepo.overtake(taskId);
    }

    @Test
    void refusingAnAnswerKeepsTheReason() {
        int taskId = newTask();
        var row = selfCheckRepo.answerForPlace(
                taskId, inventory.id(), 0, SelfCheckAnswer.HAVE_ONE, "", "someone else's", member.id());
        assertTrue(selfCheckRepo.refuse(row.id(), "that piece is somebody else's", checker.id()));
        var settled = selfCheckRepo.findRow(row.id()).orElseThrow();
        assertEquals(SelfCheckRowState.REFUSED, settled.state());
        assertEquals("that piece is somebody else's", settled.reviewerReason());
        selfCheckRepo.overtake(taskId);
    }

    @Test
    void aTaskIsFinishedOnlyOnceNothingIsOutstanding() {
        int taskId = newTask();
        var row = selfCheckRepo.answerForPlace(
                taskId, inventory.id(), 0, SelfCheckAnswer.NEVER_HAD, "", null, member.id());
        selfCheckRepo.submit(taskId, member.id());

        assertFalse(selfCheckRepo.finish(taskId, SelfCheckState.DONE, null));
        selfCheckRepo.take(row.id(), checker.id());
        assertTrue(selfCheckRepo.finish(taskId, SelfCheckState.DONE, null));
        assertFalse(selfCheckRepo.finish(taskId, SelfCheckState.DONE, null));

        var task = selfCheckRepo.findById(taskId).orElseThrow();
        assertEquals(SelfCheckState.DONE, task.state());
        assertNotNull(task.closedAt());
    }

    @Test
    void finishingCarriesTheCheckItWrote() {
        int taskId = newTask();
        var check = inventoryCheckRepo.createCheck(station.id(), member.id(), checker.id(), member.id());
        assertEquals(member.id(), check.reportedBy());
        assertTrue(selfCheckRepo.finish(taskId, SelfCheckState.DONE, check.id()));
        assertEquals(check.id(), selfCheckRepo.findById(taskId).orElseThrow().checkId());
    }

    @Test
    void overtakingEndsATaskWhateverItHolds() {
        int taskId = newTask();
        selfCheckRepo.answerForPlace(taskId, inventory.id(), 0, SelfCheckAnswer.NEVER_HAD, "", null, member.id());
        assertTrue(selfCheckRepo.overtake(taskId));
        assertFalse(selfCheckRepo.overtake(taskId));
        assertEquals(
                SelfCheckState.OVERTAKEN,
                selfCheckRepo.findById(taskId).orElseThrow().state());
    }

    @Test
    void everyTaskOfAMemberCanBeEndedAtOnce() {
        newTask();
        newTask();
        assertTrue(selfCheckRepo.overtakeAllFor(member.id()) >= 2);
        assertEquals(0, selfCheckRepo.countUnfinishedForMembers(List.of(member.id())));
        assertEquals(0, selfCheckRepo.overtakeAllFor(member.id()));
    }

    @Test
    void whatWasTakenDoesNotComeBackASecondTime() {
        int taskId = newTask();
        var taken = selfCheckRepo.answerForPlace(
                taskId, inventory.id(), 0, SelfCheckAnswer.NEVER_HAD, "", null, member.id());
        var refused = selfCheckRepo.answerForPlace(
                taskId, inventory.id(), 1, SelfCheckAnswer.NEVER_HAD, "", null, member.id());
        selfCheckRepo.take(taken.id(), checker.id());
        selfCheckRepo.refuse(refused.id(), "cannot be settled", checker.id());

        assertEquals(1, selfCheckRepo.deleteSettledRows(taskId));
        var left = selfCheckRepo.findRows(taskId);
        assertEquals(1, left.size());
        assertEquals(SelfCheckRowState.REFUSED, left.getFirst().state());
        selfCheckRepo.overtake(taskId);
    }

    @Test
    void whatTheMemberSetGoingIsRecordedBesideTheAnswers() {
        int taskId = newTask();
        var loss = selfCheckRepo.recordRaised(taskId, SelfCheckRaisedKind.LOSS, item.id(), null, member.id());
        var exchange = selfCheckRepo.recordRaised(taskId, SelfCheckRaisedKind.EXCHANGE, item.id(), null, member.id());
        assertEquals(SelfCheckRaisedKind.LOSS, loss.kind());
        assertEquals(item.id(), loss.itemId());
        assertNull(loss.movementId());
        assertEquals(member.id(), exchange.raisedBy());
        assertNotNull(exchange.raisedAt());

        var raised = selfCheckRepo.findRaised(taskId);
        assertEquals(2, raised.size());
        assertEquals(loss.id(), raised.getFirst().id());
        selfCheckRepo.overtake(taskId);
    }

    /**
     * Every read of a task's answers filters on the task alone. The two indexes the table carries
     * besides this one are partial and cannot serve such a read, so without a plain one every read
     * walks the whole table.
     */
    @Test
    void aTasksAnswersAreReachedByAnIndexOnTheTask() {
        assertTrue(indexDefinitions("inventory_self_check_item").stream().anyMatch(def -> def.endsWith("(task_id)")));
    }

    private static List<String> indexDefinitions(String table) {
        return query("SELECT indexdef FROM pg_indexes WHERE schemaname = :schema AND tablename = :table;")
                .single(call().bind("schema", schemaName).bind("table", table))
                .map(row -> row.getString("indexdef"))
                .all();
    }
}

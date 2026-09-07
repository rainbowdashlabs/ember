/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.inventory.entity.SelfCheck;
import dev.chojo.ember.feature.inventory.entity.SelfCheckAnswer;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRaised;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRaisedKind;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRow;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRowState;
import dev.chojo.ember.feature.inventory.entity.SelfCheckState;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The self-check task, its answers, and the losses and exchanges raised beside them.
 *
 * <p>There are no transactions here, so every statement that moves a task or a row along says in
 * its {@code WHERE} clause which state it is moving from, and the caller reads the row count to
 * find out whether it was the one that moved it. Two reviewers settling the last row at the same
 * moment therefore write one check between them rather than two.
 */
@Singleton
public class SelfCheckRepository {

    /**
     * Hands a task to one member.
     *
     * @param stationId   the station handing it out
     * @param memberId    the member whose gear it is about
     * @param handedOutBy the checker handing it out
     * @param dueOn       the day the answer is wanted by, or {@code null}
     * @return the created task
     */
    public SelfCheck create(int stationId, int memberId, int handedOutBy, LocalDate dueOn) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_self_check(station_id, member_id, handed_out_by, due_on)
                VALUES (:station_id, :member_id, :handed_out_by, :due_on)
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("handed_out_by", handedOutBy)
                        .bind("due_on", dueOn),
                SelfCheck.map(),
                SelfCheck.COLUMNS);
    }

    /**
     * One task by id.
     */
    public Optional<SelfCheck> findById(int id) {
        return SqlSupport.findById("inventory_self_check", SelfCheck.COLUMNS, id, SelfCheck.map());
    }

    /**
     * Every task belonging to one of the given members that still asks for something, newest first.
     *
     * <p>The empty list is answered without asking the database, because a member with no guardian
     * duties reaches this with one id and a guardian of nobody reaches it with none.
     */
    public List<SelfCheck> findUnfinishedForMembers(Collection<Integer> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return List.of();
        return query("""
                SELECT %s
                FROM inventory_self_check
                WHERE member_id = ANY(:member_ids)
                  AND state IN ('OPEN', 'SUBMITTED')
                ORDER BY due_on ASC NULLS LAST, id ASC;""", SelfCheck.COLUMNS)
                .single(call().bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER))
                .map(SelfCheck.map())
                .all();
    }

    /**
     * Every task of one station, newest first, for the checker chasing the ones nobody answered.
     *
     * @param stationId    the station
     * @param includeEnded whether tasks that ask for nothing any more are wanted too
     * @return the tasks
     */
    public List<SelfCheck> findForStation(int stationId, boolean includeEnded) {
        return query("""
                SELECT %s
                FROM inventory_self_check
                WHERE station_id = :station_id
                  AND (:include_ended OR state IN ('OPEN', 'SUBMITTED'))
                ORDER BY id DESC;""", SelfCheck.COLUMNS)
                .single(call().bind("station_id", stationId).bind("include_ended", includeEnded))
                .map(SelfCheck.map())
                .all();
    }

    /**
     * How many tasks belonging to one of the given members still ask for something.
     */
    public int countUnfinishedForMembers(Collection<Integer> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) return 0;
        return SqlSupport.count("""
                SELECT count(*) AS count
                FROM inventory_self_check
                WHERE member_id = ANY(:member_ids)
                  AND state IN ('OPEN', 'SUBMITTED');""", call().bind("member_ids", List.copyOf(memberIds), PostgreSqlTypes.INTEGER));
    }

    /**
     * Hands the task in, if it is still open.
     *
     * @param taskId      the task
     * @param submittedBy who entered the submission
     * @return {@code true} where this call is the one that closed the answering
     */
    public boolean submit(int taskId, int submittedBy) {
        return query("""
                UPDATE inventory_self_check
                SET state = 'SUBMITTED', submitted_at = now(), submitted_by = :submitted_by
                WHERE id = :id AND state = 'OPEN';""")
                .single(call().bind("id", taskId).bind("submitted_by", submittedBy))
                .update()
                .changed();
    }

    /**
     * Ends the task, if nothing about it is outstanding any more.
     *
     * <p>The condition is the whole point: the last row being settled is what finishes a task, and
     * two reviewers settling their last row at the same moment would otherwise each believe they
     * were last and write a check apiece.
     *
     * @param taskId  the task
     * @param state   how it ended
     * @param checkId the check it wrote, or {@code null} where it wrote none
     * @return {@code true} where this call is the one that ended it
     */
    public boolean finish(int taskId, SelfCheckState state, Integer checkId) {
        return query("""
                UPDATE inventory_self_check
                SET state = :state, closed_at = now(), check_id = :check_id
                WHERE id = :id
                  AND state IN ('OPEN', 'SUBMITTED')
                  AND NOT EXISTS (
                      SELECT 1 FROM inventory_self_check_item
                      WHERE task_id = :id AND state = 'OUTSTANDING'
                  );""")
                .single(call().bind("id", taskId).bind("state", state).bind("check_id", checkId))
                .update()
                .changed();
    }

    /**
     * Writes the check a finished task produced onto the task.
     *
     * <p>Kept apart from finishing it so the claim comes first: whoever wins the conditional finish is
     * the one that writes the check, and nobody else reaches this.
     *
     * @param taskId  the task
     * @param checkId the check it wrote
     */
    public void attachCheck(int taskId, int checkId) {
        query("""
                UPDATE inventory_self_check SET check_id = :check_id WHERE id = :id;""").single(call().bind("id", taskId).bind("check_id", checkId)).update();
    }

    /**
     * Ends the task whatever it still holds, which is what a checker walking the member does to it.
     *
     * @param taskId the task
     * @return {@code true} where this call is the one that ended it
     */
    public boolean overtake(int taskId) {
        return query("""
                UPDATE inventory_self_check
                SET state = 'OVERTAKEN', closed_at = now()
                WHERE id = :id AND state IN ('OPEN', 'SUBMITTED');""").single(call().bind("id", taskId)).update().changed();
    }

    /**
     * Ends every task a member still holds, which is what leaving the station does to them.
     *
     * @param memberId the member
     * @return how many tasks were ended
     */
    public int overtakeAllFor(int memberId) {
        return query("""
                UPDATE inventory_self_check
                SET state = 'OVERTAKEN', closed_at = now()
                WHERE member_id = :member_id AND state IN ('OPEN', 'SUBMITTED');""").single(call().bind("member_id", memberId)).update().rows();
    }

    /**
     * Sends a task back to the member holding only what was refused.
     *
     * @param taskId the task
     * @return {@code true} where this call is the one that reopened it
     */
    public boolean reopen(int taskId) {
        return query("""
                UPDATE inventory_self_check
                SET state = 'OPEN', submitted_at = NULL, submitted_by = NULL
                WHERE id = :id AND state = 'SUBMITTED';""").single(call().bind("id", taskId)).update().changed();
    }

    /**
     * Writes what the member said about one piece, replacing whatever they said about it before.
     *
     * <p>Answering the same thing twice is the ordinary case rather than a mistake: a member puts
     * the boots on and comes back, and the screen saves as they go. An answer that came back with a
     * reason and has been given again is outstanding once more, and the reason goes with the answer
     * it was about.
     */
    public SelfCheckRow answerForItem(
            int taskId,
            int itemId,
            int inventoryId,
            SelfCheckAnswer answer,
            String note,
            String typedInternalId,
            Integer sizeId,
            int answeredBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_self_check_item(task_id, item_id, inventory_id, answer, note,
                                                      typed_internal_id, size_id, answered_by)
                VALUES (:task_id, :item_id, :inventory_id, :answer, :note, :typed_internal_id, :size_id,
                        :answered_by)
                ON CONFLICT (task_id, item_id) WHERE item_id IS NOT NULL
                DO UPDATE SET answer = excluded.answer,
                              note = excluded.note,
                              typed_internal_id = excluded.typed_internal_id,
                              size_id = excluded.size_id,
                              answered_by = excluded.answered_by,
                              answered_at = now(),
                              state = 'OUTSTANDING',
                              reviewer_reason = '',
                              reviewed_by = NULL,
                              reviewed_at = NULL
                RETURNING %s;""",
                call().bind("task_id", taskId)
                        .bind("item_id", itemId)
                        .bind("inventory_id", inventoryId)
                        .bind("answer", answer)
                        .bind("note", note)
                        .bind("typed_internal_id", typedInternalId)
                        .bind("size_id", sizeId)
                        .bind("answered_by", answeredBy),
                SelfCheckRow.map(),
                SelfCheckRow.COLUMNS);
    }

    /**
     * Writes what the member said about one empty place, replacing whatever they said about it
     * before.
     */
    public SelfCheckRow answerForPlace(
            int taskId,
            int inventoryId,
            int slot,
            SelfCheckAnswer answer,
            String note,
            String typedInternalId,
            Integer sizeId,
            int answeredBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_self_check_item(task_id, inventory_id, slot, answer, note,
                                                      typed_internal_id, size_id, answered_by)
                VALUES (:task_id, :inventory_id, :slot, :answer, :note, :typed_internal_id, :size_id,
                        :answered_by)
                ON CONFLICT (task_id, inventory_id, slot) WHERE slot IS NOT NULL
                DO UPDATE SET answer = excluded.answer,
                              note = excluded.note,
                              typed_internal_id = excluded.typed_internal_id,
                              size_id = excluded.size_id,
                              answered_by = excluded.answered_by,
                              answered_at = now(),
                              state = 'OUTSTANDING',
                              reviewer_reason = '',
                              reviewed_by = NULL,
                              reviewed_at = NULL
                RETURNING %s;""",
                call().bind("task_id", taskId)
                        .bind("inventory_id", inventoryId)
                        .bind("slot", slot)
                        .bind("answer", answer)
                        .bind("note", note)
                        .bind("typed_internal_id", typedInternalId)
                        .bind("size_id", sizeId)
                        .bind("answered_by", answeredBy),
                SelfCheckRow.map(),
                SelfCheckRow.COLUMNS);
    }

    /**
     * Every answer on a task, oldest first.
     */
    public List<SelfCheckRow> findRows(int taskId) {
        return query("""
                SELECT %s FROM inventory_self_check_item WHERE task_id = :task_id ORDER BY id ASC;""", SelfCheckRow.COLUMNS)
                .single(call().bind("task_id", taskId))
                .map(SelfCheckRow.map())
                .all();
    }

    /**
     * One answer by id.
     */
    public Optional<SelfCheckRow> findRow(int rowId) {
        return SqlSupport.findById("inventory_self_check_item", SelfCheckRow.COLUMNS, rowId, SelfCheckRow.map());
    }

    /**
     * Takes one answer, if nobody has settled it yet.
     *
     * @param rowId      the answer
     * @param reviewedBy who is settling it
     * @return {@code true} where this call is the one that settled it
     */
    public boolean take(int rowId, int reviewedBy) {
        return settle(rowId, SelfCheckRowState.TAKEN, "", reviewedBy);
    }

    /**
     * Sends one answer back with a reason, if nobody has settled it yet.
     *
     * @param rowId      the answer
     * @param reason     why it cannot be settled
     * @param reviewedBy who is settling it
     * @return {@code true} where this call is the one that settled it
     */
    public boolean refuse(int rowId, String reason, int reviewedBy) {
        return settle(rowId, SelfCheckRowState.REFUSED, reason, reviewedBy);
    }

    private boolean settle(int rowId, SelfCheckRowState state, String reason, int reviewedBy) {
        return query("""
                UPDATE inventory_self_check_item
                SET state = :state, reviewer_reason = :reason, reviewed_by = :reviewed_by, reviewed_at = now()
                WHERE id = :id AND state = 'OUTSTANDING';""")
                .single(call().bind("id", rowId)
                        .bind("state", state)
                        .bind("reason", reason)
                        .bind("reviewed_by", reviewedBy))
                .update()
                .changed();
    }

    /**
     * Points one answer at the piece a correction produced, so a true statement about a stale record
     * settles against the piece the member is actually holding.
     *
     * @param rowId       the answer
     * @param itemId      the piece it now names
     * @param inventoryId the inventory that piece sits in
     * @return {@code true} where the row was still outstanding and took the new piece
     */
    public boolean repointRow(int rowId, int itemId, int inventoryId) {
        return query("""
                UPDATE inventory_self_check_item
                SET item_id = :item_id, inventory_id = :inventory_id
                WHERE id = :id AND state = 'OUTSTANDING';""")
                .single(call().bind("id", rowId).bind("item_id", itemId).bind("inventory_id", inventoryId))
                .update()
                .changed();
    }

    /**
     * Whether anything on the task came back with a reason, which is what sends it to the member
     * again instead of writing a check.
     */
    public boolean hasRefusedRows(int taskId) {
        return SqlSupport.exists("""
                SELECT 1 FROM inventory_self_check_item WHERE task_id = :task_id AND state = 'REFUSED';""", call().bind("task_id", taskId));
    }

    /**
     * Whether anything on the task is still waiting for a reviewer.
     */
    public boolean hasOutstandingRows(int taskId) {
        return SqlSupport.exists("""
                SELECT 1 FROM inventory_self_check_item WHERE task_id = :task_id AND state = 'OUTSTANDING';""", call().bind("task_id", taskId));
    }

    /**
     * Clears the answers a returned task no longer holds, so what was taken does not come back a
     * second time.
     *
     * @param taskId the task
     * @return how many answers were cleared
     */
    public int deleteSettledRows(int taskId) {
        return query("""
                DELETE FROM inventory_self_check_item WHERE task_id = :task_id AND state = 'TAKEN';""").single(call().bind("task_id", taskId)).delete().rows();
    }

    /**
     * Records that the member set a loss or an exchange going while answering, which has already
     * happened by the time this is written.
     */
    public SelfCheckRaised recordRaised(
            int taskId, SelfCheckRaisedKind kind, Integer itemId, Integer movementId, int raisedBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_self_check_raised(task_id, kind, state, item_id, movement_id, raised_by)
                VALUES (:task_id, :kind, 'RAISED', :item_id, :movement_id, :raised_by)
                RETURNING %s;""",
                call().bind("task_id", taskId)
                        .bind("kind", kind)
                        .bind("item_id", itemId)
                        .bind("movement_id", movementId)
                        .bind("raised_by", raisedBy),
                SelfCheckRaised.map(),
                SelfCheckRaised.COLUMNS);
    }

    /**
     * Writes down a report that is not to go out yet, because the answer it hangs on says the record
     * it names is wrong.
     *
     * <p>The words and the wanted size are kept here rather than where they will end up, because
     * until the report goes out there is no piece carrying a note and no movement carrying a reason.
     *
     * @param rowId     the answer it waits on
     * @param newSizeId the size an exchange asks for, or {@code null}
     * @param words     the note on a loss, the reason on an exchange
     */
    public SelfCheckRaised recordWaiting(
            int taskId,
            SelfCheckRaisedKind kind,
            int itemId,
            int rowId,
            Integer newSizeId,
            String words,
            int raisedBy) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO inventory_self_check_raised(task_id, kind, state, item_id, waits_for_row_id,
                                                        new_size_id, words, raised_by)
                VALUES (:task_id, :kind, 'WAITING', :item_id, :row_id, :new_size_id, :words, :raised_by)
                RETURNING %s;""",
                call().bind("task_id", taskId)
                        .bind("kind", kind)
                        .bind("item_id", itemId)
                        .bind("row_id", rowId)
                        .bind("new_size_id", newSizeId)
                        .bind("words", words)
                        .bind("raised_by", raisedBy),
                SelfCheckRaised.map(),
                SelfCheckRaised.COLUMNS);
    }

    /**
     * The reports still waiting on one answer, oldest first.
     */
    public List<SelfCheckRaised> findWaitingFor(int rowId) {
        return query("""
                SELECT %s
                FROM inventory_self_check_raised
                WHERE waits_for_row_id = :row_id AND state = 'WAITING'
                ORDER BY id ASC;""", SelfCheckRaised.COLUMNS)
                .single(call().bind("row_id", rowId))
                .map(SelfCheckRaised.map())
                .all();
    }

    /**
     * Marks one waiting report as gone out, naming what it produced.
     *
     * <p>Conditional on it still waiting, like everything else that moves a self-check along: two
     * reviewers correcting the same answer at the same moment would otherwise raise the report twice.
     *
     * @param raisedId   the report
     * @param itemId     the piece the correction produced, which is what the report is really about
     * @param movementId the movement an exchange started, or {@code null} for a loss
     * @return {@code true} where this call is the one that sent it out
     */
    public boolean markRaised(int raisedId, int itemId, Integer movementId) {
        return query("""
                UPDATE inventory_self_check_raised
                SET state = 'RAISED', item_id = :item_id, movement_id = :movement_id
                WHERE id = :id AND state = 'WAITING';""")
                .single(call().bind("id", raisedId).bind("item_id", itemId).bind("movement_id", movementId))
                .update()
                .changed();
    }

    /**
     * Writes the movement an exchange produced onto the report that asked for it.
     *
     * <p>Kept apart from the claim above so the claim comes first, the way the check on a finished
     * task does: whoever wins the claim is the one that raises the exchange, and the movement it
     * produced can only be named once it exists.
     */
    public void attachMovement(int raisedId, int movementId) {
        query("""
                UPDATE inventory_self_check_raised SET movement_id = :movement_id WHERE id = :id;""")
                .single(call().bind("id", raisedId).bind("movement_id", movementId))
                .update();
    }

    /**
     * Drops every report still waiting on one answer, because that answer has come to nothing.
     *
     * @param rowId the answer
     * @return how many reports will never go out
     */
    public int dropWaitingFor(int rowId) {
        return query("""
                UPDATE inventory_self_check_raised
                SET state = 'DROPPED'
                WHERE waits_for_row_id = :row_id AND state = 'WAITING';""").single(call().bind("row_id", rowId)).update().rows();
    }

    /**
     * Everything the member set going during one task, oldest first.
     */
    public List<SelfCheckRaised> findRaised(int taskId) {
        return query("""
                SELECT %s FROM inventory_self_check_raised WHERE task_id = :task_id ORDER BY id ASC;""", SelfCheckRaised.COLUMNS)
                .single(call().bind("task_id", taskId))
                .map(SelfCheckRaised.map())
                .all();
    }
}

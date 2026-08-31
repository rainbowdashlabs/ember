/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.CheckItemRequest;
import dev.chojo.ember.feature.inventory.entity.CheckResult;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryCheck;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemCorrection;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.RequiredInventoryItem;
import dev.chojo.ember.feature.inventory.entity.SelfCheck;
import dev.chojo.ember.feature.inventory.entity.SelfCheckAnswer;
import dev.chojo.ember.feature.inventory.entity.SelfCheckIdentifierMatch;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRaised;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRecordRemoval;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRow;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRowState;
import dev.chojo.ember.feature.inventory.entity.SelfCheckSettlement;
import dev.chojo.ember.feature.inventory.entity.SelfCheckState;
import dev.chojo.ember.feature.inventory.repository.InventoryCheckRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryContainerRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.SelfCheckRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reading a member's submission and settling it, line by line.
 *
 * <p>A review answers one question, and it is not whether the member told the truth: it is which
 * record the statement lands on. A member saying a jacket is gone may be entirely right while the
 * jacket written against their name is the wrong one, and the answer to that is to put the record
 * right and carry on rather than to send the row back. Refusing is for a row that cannot be settled
 * at all.
 *
 * <p>Nothing here runs in a transaction, because nothing in this project does. The row state carries
 * the concurrency instead: taking or refusing is a conditional update on the row still being
 * outstanding, and finishing the task is conditional on none of them being. Two reviewers settling
 * the last row at the same moment therefore write one check between them.
 */
@Singleton
public class SelfCheckReviewService {
    private static final Logger log = LoggerFactory.getLogger(SelfCheckReviewService.class);

    private final SelfCheckRepository repository;
    private final InventoryCheckService checkService;
    private final InventoryCheckRepository checkRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryContainerRepository containerRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final ItemCustodyService custodyService;
    private final NotificationService notificationService;

    @Inject
    public SelfCheckReviewService(
            SelfCheckRepository repository,
            InventoryCheckService checkService,
            InventoryCheckRepository checkRepository,
            InventoryRepository inventoryRepository,
            InventoryContainerRepository containerRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            ItemCustodyService custodyService,
            NotificationService notificationService) {
        this.repository = repository;
        this.checkService = checkService;
        this.checkRepository = checkRepository;
        this.inventoryRepository = inventoryRepository;
        this.containerRepository = containerRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.custodyService = custodyService;
        this.notificationService = notificationService;
    }

    /**
     * The tasks a station has out, so whoever handed them out can chase the ones nobody answered.
     *
     * @param stationId    the station
     * @param includeEnded whether tasks that ask for nothing any more are wanted too
     * @return the tasks, newest first
     */
    public List<SelfCheck> forStation(int stationId, boolean includeEnded) {
        return repository.findForStation(stationId, includeEnded);
    }

    /**
     * One submission as a reviewer reads it: every answer, what taking it would do, and what the
     * number the member typed matched.
     *
     * @param taskId     the task
     * @param stationId  the reviewer's station
     * @param reviewerId the reviewer
     * @return the submission
     */
    public SelfCheckReview read(int taskId, int stationId, int reviewerId) {
        SelfCheck task = require(taskId, stationId);
        return view(task, reviewerId);
    }

    /**
     * Takes one answer, writing whatever follows from it through the service that already owns that
     * effect.
     *
     * @param taskId     the task
     * @param rowId      the answer
     * @param stationId  the reviewer's station
     * @param reviewerId the reviewer
     * @return the submission as it now stands
     * @throws BadRequestResponse when the answer cannot be settled without the record being put
     *                            right first
     * @throws ConflictResponse   when somebody else settled it in the meantime
     */
    public SelfCheckReview take(int taskId, int rowId, int stationId, int reviewerId) {
        SelfCheck task = require(taskId, stationId);
        SelfCheckRow row = requireOutstanding(task, rowId);
        requireArmsLength(task, row, reviewerId);
        SelfCheckSettlement settlement = settlementOf(row);
        if (settlement == SelfCheckSettlement.NEEDS_RECORD_PUT_RIGHT
                || settlement == SelfCheckSettlement.NEEDS_A_PIECE_NAMED) {
            throw new BadRequestResponse("This answer needs the record putting right before it can be taken");
        }
        if (!repository.take(rowId, reviewerId)) {
            throw new ConflictResponse("Somebody has already settled this answer");
        }
        apply(row, settlement, reviewerId);
        return settled(task, reviewerId);
    }

    /**
     * Puts the record right and takes the answer in one act, which is the ordinary way a true
     * statement against a stale record settles.
     *
     * @param taskId     the task
     * @param rowId      the answer
     * @param stationId  the reviewer's station
     * @param reviewerId the reviewer
     * @param correction what the member actually holds
     * @return the submission as it now stands
     */
    public SelfCheckReview correctAndTake(
            int taskId, int rowId, int stationId, int reviewerId, ItemCorrection correction) {
        SelfCheck task = require(taskId, stationId);
        SelfCheckRow row = requireOutstanding(task, rowId);
        requireArmsLength(task, row, reviewerId);
        SelfCheckSettlement settlement = settlementOf(row);
        if (settlement != SelfCheckSettlement.NEEDS_RECORD_PUT_RIGHT
                && settlement != SelfCheckSettlement.NEEDS_A_PIECE_NAMED) {
            throw new BadRequestResponse("This answer does not ask for the record to be put right");
        }
        InventoryItem replacement = checkService.correct(task.memberId(), withOldPieceOf(row, correction));
        repository.repointRow(rowId, replacement.id(), replacement.inventoryId());
        if (!repository.take(rowId, reviewerId)) {
            throw new ConflictResponse("Somebody has already settled this answer");
        }
        log.info(
                "Self-check {} row {} corrected onto piece {} by member {}",
                taskId,
                rowId,
                replacement.id(),
                reviewerId);
        return settled(task, reviewerId);
    }

    /**
     * Sends one answer back to the member with a reason.
     *
     * <p>This is for an answer that cannot be settled at all, not for one a reviewer doubts. Where
     * the record is merely stale, putting it right and taking the row is the answer instead: sending
     * it back would ask the member to solve a problem only the station can see.
     *
     * @param taskId     the task
     * @param rowId      the answer
     * @param stationId  the reviewer's station
     * @param reviewerId the reviewer
     * @param reason     why it cannot be settled
     * @return the submission as it now stands
     */
    public SelfCheckReview refuse(int taskId, int rowId, int stationId, int reviewerId, String reason) {
        SelfCheck task = require(taskId, stationId);
        SelfCheckRow row = requireOutstanding(task, rowId);
        requireArmsLength(task, row, reviewerId);
        String written = reason == null ? "" : reason.strip();
        if (written.isEmpty()) {
            throw new BadRequestResponse("Say why the answer cannot be settled");
        }
        if (!repository.refuse(rowId, written, reviewerId)) {
            throw new ConflictResponse("Somebody has already settled this answer");
        }
        tellTheMember(task, row, written);
        return settled(task, reviewerId);
    }

    /**
     * Writes whatever follows from one taken answer, through the service that already owns it.
     *
     * <p>A piece a partner owns is never marked missing here. The loss on borrowed gear belongs on
     * the lending request it came in on, which is why the walk skips it too, and the answer a member
     * may give about such a piece says only that they have not got it.
     */
    private void apply(SelfCheckRow row, SelfCheckSettlement settlement, int reviewerId) {
        if (settlement != SelfCheckSettlement.MARKS_FOUND || row.itemId() == null) return;
        custodyService.markFound(row.itemId());
        log.info("Self-check row {} brought piece {} back, settled by member {}", row.id(), row.itemId(), reviewerId);
    }

    /**
     * Reads what the task has become now that one more answer is settled, and moves it on where it
     * has nothing outstanding left.
     */
    private SelfCheckReview settled(SelfCheck task, int reviewerId) {
        if (!repository.hasOutstandingRows(task.id())) {
            if (repository.hasRefusedRows(task.id())) sendBack(task);
            else complete(task, reviewerId);
        }
        return view(repository.findById(task.id()).orElseThrow(), reviewerId);
    }

    /**
     * Returns the task to the member holding only what came back, so what was taken is not asked a
     * second time.
     */
    private void sendBack(SelfCheck task) {
        if (!repository.reopen(task.id())) return;
        int cleared = repository.deleteSettledRows(task.id());
        log.info("Self-check {} went back to the member with {} settled answer(s) cleared", task.id(), cleared);
    }

    /**
     * Writes the check a wholly taken task produces, carrying the two people behind it.
     *
     * <p>The task is claimed before the check is written rather than after: the claim is conditional
     * on nothing being outstanding, so exactly one caller reaches the writing and two reviewers
     * settling their last row at the same moment do not produce a check apiece.
     */
    private void complete(SelfCheck task, int reviewerId) {
        if (!repository.finish(task.id(), SelfCheckState.DONE, null)) return;
        List<SelfCheckRow> rows = repository.findRows(task.id());
        InventoryCheck check =
                checkRepository.createCheck(task.stationId(), task.memberId(), reviewerId, task.submittedBy());
        for (CheckItemRequest result : resultsOf(rows)) {
            checkRepository.createCheckItem(
                    check.id(), result.itemId(), result.inventoryId(), result.result(), result.note());
        }
        repository.attachCheck(task.id(), check.id());
        log.info(
                "Self-check {} wrote check {} on member {}, reported by {} and approved by {}",
                task.id(),
                check.id(),
                task.memberId(),
                task.submittedBy(),
                reviewerId);
    }

    /**
     * The lines the check carries, one per answer that was taken.
     *
     * <p>An answer whose piece has gone since leaves no line: there is nothing left to record a
     * result against, and the row itself keeps saying what the member said.
     */
    private static List<CheckItemRequest> resultsOf(List<SelfCheckRow> rows) {
        List<CheckItemRequest> results = new ArrayList<>();
        for (SelfCheckRow row : rows) {
            if (row.state() != SelfCheckRowState.TAKEN) continue;
            if (row.anchorGone()) continue;
            results.add(new CheckItemRequest(row.itemId(), row.inventoryId(), resultOf(row), row.note()));
        }
        return results;
    }

    private static CheckResult resultOf(SelfCheckRow row) {
        return switch (settlementOf(row)) {
            case RECORDS_NOT_HELD, CONFIRMS_GAP -> CheckResult.NOT_IN_POSSESSION;
            default -> CheckResult.CONFIRMED;
        };
    }

    /**
     * What taking one answer would do, read off the answer and the piece it lands on.
     */
    private static SelfCheckSettlement settlementOf(SelfCheckRow row) {
        if (row.anchorGone()) return SelfCheckSettlement.ANCHOR_GONE;
        boolean repointed = row.slot() != null && row.itemId() != null;
        boolean settled = row.state() == SelfCheckRowState.TAKEN;
        return switch (row.answer()) {
            case HAVE_IT -> SelfCheckSettlement.CONFIRMS_PIECE;
            case DO_NOT_HAVE_IT -> SelfCheckSettlement.RECORDS_NOT_HELD;
            case TURNED_UP -> SelfCheckSettlement.MARKS_FOUND;
            case NEVER_HAD -> SelfCheckSettlement.CONFIRMS_GAP;
            case WRONG_RECORD ->
                settled ? SelfCheckSettlement.CONFIRMS_PIECE : SelfCheckSettlement.NEEDS_RECORD_PUT_RIGHT;
            case HAVE_ONE -> repointed ? SelfCheckSettlement.CONFIRMS_PIECE : SelfCheckSettlement.NEEDS_A_PIECE_NAMED;
        };
    }

    /**
     * The correction as it is actually performed: the piece coming off the record is the one the
     * answer named, never one the caller may choose, so a reviewer cannot correct away a piece the
     * member said nothing about.
     */
    private static ItemCorrection withOldPieceOf(SelfCheckRow row, ItemCorrection correction) {
        if (correction == null) throw new BadRequestResponse("Say what the member is actually holding");
        return new ItemCorrection(
                correction.inventoryId(),
                row.answer() == SelfCheckAnswer.WRONG_RECORD ? row.itemId() : null,
                correction.pickedItemId(),
                correction.sizeId(),
                correction.ownerKind(),
                correction.internalId(),
                correction.metadata());
    }

    /**
     * What putting the record right would do with the piece that comes off it, which is three
     * different things and one of them ends the piece.
     */
    private SelfCheckRecordRemoval removalOf(SelfCheckRow row) {
        if (row.itemId() == null || row.answer() != SelfCheckAnswer.WRONG_RECORD) {
            return SelfCheckRecordRemoval.NOTHING;
        }
        return inventoryRepository
                .findItemById(row.itemId())
                .map(SelfCheckReviewService::removalOf)
                .orElse(SelfCheckRecordRemoval.NOTHING);
    }

    private static SelfCheckRecordRemoval removalOf(InventoryItem item) {
        if (item.ownerKind() != ItemOwner.CLUSTER) return SelfCheckRecordRemoval.BACK_TO_STORE;
        return item.ownerClusterId() == null
                ? SelfCheckRecordRemoval.DELETED
                : SelfCheckRecordRemoval.RETURNED_TO_OWNER;
    }

    /**
     * Everything the number a member typed matched, gathered and not decided.
     *
     * <p>It is compared without regard to case and without the spaces around it, because a member
     * reading a label is not a scanner, and every match travels because nothing makes the number
     * unique and the containers share the numbering with the gear.
     */
    private SelfCheckIdentifierMatch identifierOf(SelfCheck task, SelfCheckRow row) {
        String typed =
                row.typedInternalId() == null ? "" : row.typedInternalId().strip();
        if (typed.isEmpty()) return SelfCheckIdentifierMatch.nothingTyped();
        List<SelfCheckIdentifierMatch.Piece> pieces =
                inventoryRepository.findAllByInternalId(task.stationId(), typed).stream()
                        .map(this::piece)
                        .toList();
        List<String> containers = containerRepository.findAllByInternalId(task.stationId(), typed).stream()
                .map(container -> container.name())
                .toList();
        return SelfCheckIdentifierMatch.of(typed, pieces, containers);
    }

    private SelfCheckIdentifierMatch.Piece piece(InventoryItem item) {
        return new SelfCheckIdentifierMatch.Piece(
                item.id(),
                item.name(),
                item.internalId(),
                inventoryNameOf(item.inventoryId()),
                item.assignedTo(),
                item.assignedTo() == null ? "" : nameOf(item.assignedTo()));
    }

    /**
     * The whole submission as a reviewer reads it, gathered once so every row is answered from the
     * same reading of the records.
     */
    private SelfCheckReview view(SelfCheck task, int reviewerId) {
        List<SelfCheckRow> rows = repository.findRows(task.id());
        List<SelfCheckReviewRow> reviewed = new ArrayList<>(rows.size());
        for (SelfCheckRow row : rows) reviewed.add(reviewRow(task, row));
        var gear = checkService.readGear(task.stationId(), task.memberId());
        Map<Integer, List<InventoryItem>> free = new HashMap<>();
        for (RequiredInventoryItem required : gear.required()) {
            free.put(required.inventoryId(), inventoryRepository.findUnassignedItems(required.inventoryId()));
        }
        String refusal = approvalRefusal(task, reviewerId);
        return new SelfCheckReview(
                task,
                gear.memberName(),
                nameOf(task.submittedBy()),
                nameOf(task.handedOutBy()),
                reviewed,
                raisedOf(task),
                gear.required(),
                gear.assigned(),
                free,
                refusal == null,
                refusal == null ? "" : refusal);
    }

    private SelfCheckReviewRow reviewRow(SelfCheck task, SelfCheckRow row) {
        InventoryItem item = row.itemId() == null
                ? null
                : inventoryRepository.findItemById(row.itemId()).orElse(null);
        return new SelfCheckReviewRow(
                row,
                nameOf(row.answeredBy()),
                nameOf(row.reviewedBy()),
                item,
                inventoryNameOf(row.inventoryId()),
                item != null && item.borrowed(),
                item != null && item.custody() == ItemCustody.LOST,
                settlementOf(row),
                removalOf(row),
                identifierOf(task, row));
    }

    private List<SelfCheckRaisedView> raisedOf(SelfCheck task) {
        List<SelfCheckRaisedView> raised = new ArrayList<>();
        for (SelfCheckRaised entry : repository.findRaised(task.id())) {
            String itemName = entry.itemId() == null
                    ? ""
                    : inventoryRepository
                            .findItemById(entry.itemId())
                            .map(InventoryItem::name)
                            .orElse("");
            raised.add(new SelfCheckRaisedView(entry, itemName, nameOf(entry.raisedBy())));
        }
        return raised;
    }

    /**
     * Why this reviewer may not sign this submission off, or {@code null} where they may.
     *
     * <p>The two names on a check are the point of it, so they may not be the same person: a checker
     * cannot hand themselves a task and approve it, and a guardian who answered for a member cannot
     * approve what they wrote.
     */
    private static String approvalRefusal(SelfCheck task, int reviewerId) {
        if (task.memberId() == reviewerId) return "This submission is about your own gear";
        if (task.submittedBy() != null && task.submittedBy() == reviewerId) {
            return "You entered this submission yourself";
        }
        return null;
    }

    private void requireArmsLength(SelfCheck task, SelfCheckRow row, int reviewerId) {
        String refusal = approvalRefusal(task, reviewerId);
        if (refusal != null) throw new ForbiddenResponse(refusal);
        if (row.answeredBy() != null && row.answeredBy() == reviewerId) {
            throw new ForbiddenResponse("You entered this answer yourself");
        }
    }

    private SelfCheck require(int taskId, int stationId) {
        return repository
                .findById(taskId)
                .filter(task -> task.stationId() == stationId)
                .orElseThrow(NotFoundResponse::new);
    }

    private SelfCheckRow requireOutstanding(SelfCheck task, int rowId) {
        SelfCheckRow row = repository
                .findRow(rowId)
                .filter(candidate -> candidate.taskId() == task.id())
                .orElseThrow(NotFoundResponse::new);
        if (task.state() != SelfCheckState.SUBMITTED) {
            throw new ConflictResponse("This task is not waiting to be read");
        }
        if (row.state() != SelfCheckRowState.OUTSTANDING) {
            throw new ConflictResponse("Somebody has already settled this answer");
        }
        return row;
    }

    private void tellTheMember(SelfCheck task, SelfCheckRow row, String reason) {
        String itemName = row.itemId() == null
                ? inventoryNameOf(row.inventoryId())
                : inventoryRepository
                        .findItemById(row.itemId())
                        .map(InventoryItem::name)
                        .orElse(inventoryNameOf(row.inventoryId()));
        var data = NotificationData.of(
                new NotificationParams.SelfCheckRowRefused(nameOf(task.memberId()), itemName, reason),
                new NotificationData.NotificationLink("inventory-self-check", Map.of("id", task.id())));
        notificationService.notifyIfAbsent(task.memberId(), NotificationType.SELF_CHECK_ROW_REFUSED, data);
        if (row.answeredBy() != null && row.answeredBy() != task.memberId()) {
            notificationService.notifyIfAbsent(row.answeredBy(), NotificationType.SELF_CHECK_ROW_REFUSED, data);
        }
    }

    private String inventoryNameOf(int inventoryId) {
        return inventoryRepository.findById(inventoryId).map(Inventory::name).orElse("");
    }

    private String nameOf(Integer memberId) {
        if (memberId == null) return "";
        return Optional.of(memberId)
                .flatMap(stationMemberRepository::findById)
                .flatMap(member ->
                        member.accountId() == null ? Optional.empty() : accountRepository.findById(member.accountId()))
                .map(Account::fullName)
                .map(String::strip)
                .orElse("");
    }

    /**
     * One answer as a reviewer reads it.
     *
     * @param row            the answer as the member left it
     * @param answeredByName who entered it
     * @param reviewedByName who settled it, empty while it is outstanding
     * @param item           the piece it is about, or {@code null} on an empty place or a piece that
     *                       has gone since
     * @param inventoryName  the kind of gear it is about
     * @param borrowed       whether the piece belongs to a partner, which is why no loss follows
     * @param recordedLost   whether the station had already written the piece off
     * @param settlement     what taking it would do
     * @param removal        what putting the record right would do with the piece coming off it
     * @param identifier     everything the number the member typed matched
     */
    public record SelfCheckReviewRow(
            SelfCheckRow row,
            String answeredByName,
            String reviewedByName,
            InventoryItem item,
            String inventoryName,
            boolean borrowed,
            boolean recordedLost,
            SelfCheckSettlement settlement,
            SelfCheckRecordRemoval removal,
            SelfCheckIdentifierMatch identifier) {}

    /**
     * A loss or an exchange the member set going, which waited for nobody and is shown so the
     * reviewer knows it happened.
     *
     * @param raised       the record of it
     * @param itemName     the piece it was about, empty where that piece has gone
     * @param raisedByName who raised it
     */
    public record SelfCheckRaisedView(SelfCheckRaised raised, String itemName, String raisedByName) {}

    /**
     * A whole submission as a reviewer reads it.
     *
     * @param task            the task itself
     * @param memberName      whose gear it is about
     * @param submittedByName who entered the submission, which is the member or one of their guardians
     * @param handedOutByName who asked for it
     * @param rows            every answer, with what it would do
     * @param raised          what the member set going without waiting
     * @param required        what the member's role asks of them
     * @param assigned        what they are holding towards it
     * @param freeStock       the free pieces per inventory, which is what a correction may pick from
     * @param mayApprove      whether this reviewer may settle anything here at all
     * @param approvalRefusal why not, empty where they may
     */
    public record SelfCheckReview(
            SelfCheck task,
            String memberName,
            String submittedByName,
            String handedOutByName,
            List<SelfCheckReviewRow> rows,
            List<SelfCheckRaisedView> raised,
            List<RequiredInventoryItem> required,
            List<InventoryItem> assigned,
            Map<Integer, List<InventoryItem>> freeStock,
            boolean mayApprove,
            String approvalRefusal) {}
}

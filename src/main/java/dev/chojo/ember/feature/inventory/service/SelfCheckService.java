/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.RequiredInventoryItem;
import dev.chojo.ember.feature.inventory.entity.SelfCheck;
import dev.chojo.ember.feature.inventory.entity.SelfCheckAnswer;
import dev.chojo.ember.feature.inventory.entity.SelfCheckAnswerInput;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRaised;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRaisedKind;
import dev.chojo.ember.feature.inventory.entity.SelfCheckRow;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A member answering for their own gear, and a guardian answering for a member in their care.
 *
 * <p>What this service will not do is as much of its shape as what it will. It never assigns a
 * piece, never creates one, never corrects a record and never signs anything off: those settle
 * something, and settling belongs to whoever holds the check permission. What a member types as the
 * number on a piece is stored as typed and matched against nothing here, so a member naming a
 * colleague's jacket produces a question on a reviewer's screen rather than a transfer.
 *
 * <p>Reading a task returns the member's own gear and no free stock, which is the one place the
 * shape a checker's walk returns is deliberately narrowed.
 */
@Singleton
public class SelfCheckService {
    private static final Logger log = LoggerFactory.getLogger(SelfCheckService.class);

    private final SelfCheckRepository repository;
    private final InventoryCheckService checkService;
    private final InventoryRepository inventoryRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;

    @Inject
    public SelfCheckService(
            SelfCheckRepository repository,
            InventoryCheckService checkService,
            InventoryRepository inventoryRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            NotificationService notificationService) {
        this.repository = repository;
        this.checkService = checkService;
        this.inventoryRepository = inventoryRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.notificationService = notificationService;
    }

    /**
     * Hands one task to each of several members, with one due date between them.
     *
     * <p>A member already holding a task they have not finished is passed over rather than asked
     * twice: handing the whole group a task is one act, and one member who was asked last week is no
     * reason for the other eleven to go unasked. The answer says who was actually asked.
     *
     * @param stationId   the station handing them out
     * @param memberIds   the members to ask
     * @param dueOn       the day the answers are wanted by, or {@code null}
     * @param handedOutBy the checker handing them out
     * @return the tasks that were created
     * @throws BadRequestResponse when no member was named, or one of them is not of this station or
     *                            has left it
     */
    public List<SelfCheck> handOut(int stationId, List<Integer> memberIds, LocalDate dueOn, int handedOutBy) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new BadRequestResponse("Name at least one member to ask");
        }
        List<SelfCheck> handed = new ArrayList<>();
        for (int memberId : new LinkedHashSet<>(memberIds)) {
            var member = stationMemberRepository
                    .findById(memberId)
                    .filter(m -> m.stationId() == stationId)
                    .orElseThrow(() -> new BadRequestResponse("This member is not of this station"));
            if (member.former()) {
                throw new BadRequestResponse("A former member cannot be asked to check their gear");
            }
            if (repository.countUnfinishedForMembers(List.of(memberId)) > 0) continue;
            SelfCheck task = repository.create(stationId, memberId, handedOutBy, dueOn);
            handed.add(task);
            announce(task, handedOutBy);
        }
        log.info("Handed out {} self-checks at station {} by member {}", handed.size(), stationId, handedOutBy);
        return handed;
    }

    /**
     * Tells the member their gear is being asked about, and every guardian who answers for them.
     *
     * <p>A child with no address of their own is reached through their guardian, so a task that only
     * told the child would sit unanswered.
     */
    private void announce(SelfCheck task, int handedOutBy) {
        var params = new NotificationParams.SelfCheckAssigned(
                nameOf(task.memberId()),
                nameOf(handedOutBy),
                task.dueOn() == null ? "" : task.dueOn().toString());
        var data = NotificationData.of(
                params, new NotificationData.NotificationLink("inventory-self-check", Map.of("id", task.id())));
        notificationService.notifyIfAbsent(task.memberId(), NotificationType.SELF_CHECK_ASSIGNED, data);
        for (var manager : stationMemberRepository.findManagers(task.memberId())) {
            notificationService.notifyIfAbsent(manager.id(), NotificationType.SELF_CHECK_ASSIGNED, data);
        }
    }

    /**
     * Tells whoever holds the check permission that a submission is waiting to be read.
     */
    private void announceSubmission(SelfCheck task, int submittedBy) {
        var params = new NotificationParams.SelfCheckSubmitted(nameOf(task.memberId()), nameOf(submittedBy));
        var data = NotificationData.of(
                params, new NotificationData.NotificationLink("inventory-self-check-review", Map.of("id", task.id())));
        notificationService.notifyMembersWithRole(
                task.stationId(),
                StationPermission.INVENTORY_CHECK.name(),
                NotificationType.SELF_CHECK_SUBMITTED,
                data,
                submittedBy);
    }

    /**
     * The name a notification puts to a member, which is the only place this service reads accounts.
     */
    private String nameOf(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .flatMap(m -> m.accountId() == null ? Optional.empty() : accountRepository.findById(m.accountId()))
                .map(account -> account.fullName().strip())
                .orElse("");
    }

    /**
     * The tasks somebody is answerable for: their own, plus one for each member in their care.
     *
     * @param memberId the member reading their list
     * @param guardian whether they hold the guardian permission
     * @return the tasks that still ask for something
     */
    public List<SelfCheck> outstandingFor(int memberId, boolean guardian) {
        return repository.findUnfinishedForMembers(reach(memberId, guardian));
    }

    /**
     * How many tasks somebody is answerable for, for the badge that counts them.
     */
    public int countOutstandingFor(int memberId, boolean guardian) {
        return repository.countUnfinishedForMembers(reach(memberId, guardian));
    }

    /**
     * Reads a task, as the person answering it may see it.
     *
     * @param taskId    the task
     * @param stationId the caller's station
     * @param memberId  the caller
     * @param guardian  whether the caller holds the guardian permission
     * @return the task, the member's own gear, the answers so far, and what was already set going
     */
    public SelfCheckView read(int taskId, int stationId, int memberId, boolean guardian) {
        SelfCheck task = require(taskId, stationId, memberId, guardian);
        var gear = checkService.readGear(stationId, task.memberId());
        return new SelfCheckView(
                task,
                gear.memberName(),
                gear.required(),
                gear.assigned(),
                repository.findRows(taskId),
                repository.findRaised(taskId));
    }

    /**
     * Writes what the member said, as often as they like while the task is open.
     *
     * @param taskId    the task
     * @param stationId the caller's station
     * @param memberId  the caller
     * @param guardian  whether the caller holds the guardian permission
     * @param answers   what they said
     * @return the answers as they now stand
     * @throws ConflictResponse   when the task no longer takes answers
     * @throws BadRequestResponse when an answer is about gear that is not the member's, or says
     *                            something that cannot be said about that piece
     */
    public List<SelfCheckRow> answer(
            int taskId, int stationId, int memberId, boolean guardian, List<SelfCheckAnswerInput> answers) {
        SelfCheck task = require(taskId, stationId, memberId, guardian);
        requireOpen(task);
        if (answers == null || answers.isEmpty()) {
            throw new BadRequestResponse("Say something before saving");
        }
        var required = checkService.getRequiredItems(stationId, task.memberId());
        for (SelfCheckAnswerInput input : answers) {
            write(task, input, required, memberId);
        }
        return repository.findRows(taskId);
    }

    /**
     * Hands the task in.
     *
     * @return the task as it now stands
     * @throws ConflictResponse when somebody has already handed it in
     */
    public SelfCheck submit(int taskId, int stationId, int memberId, boolean guardian) {
        SelfCheck task = require(taskId, stationId, memberId, guardian);
        requireOpen(task);
        if (!repository.submit(taskId, memberId)) {
            throw new ConflictResponse("This task has already been handed in");
        }
        log.info("Self-check {} submitted by member {}", taskId, memberId);
        announceSubmission(task, memberId);
        return repository.findById(taskId).orElseThrow();
    }

    /**
     * Records that the member said a piece cannot be found while answering a task.
     *
     * <p>The loss itself went through the screen that already accepts it from them and took effect
     * the moment it was given. This only writes down that it happened here, so the reviewer reading
     * the submission can see it.
     */
    public SelfCheckRaised recordLoss(int taskId, int stationId, int memberId, boolean guardian, int itemId) {
        SelfCheck task = require(taskId, stationId, memberId, guardian);
        requireOpen(task);
        return repository.recordRaised(taskId, SelfCheckRaisedKind.LOSS, itemId, null, memberId);
    }

    /**
     * Records that the member asked for a different size while answering a task.
     */
    public SelfCheckRaised recordExchange(
            int taskId, int stationId, int memberId, boolean guardian, Integer itemId, int movementId) {
        SelfCheck task = require(taskId, stationId, memberId, guardian);
        requireOpen(task);
        return repository.recordRaised(taskId, SelfCheckRaisedKind.EXCHANGE, itemId, movementId, memberId);
    }

    /**
     * Ends every task a member still holds, which is what leaving the station does to them.
     *
     * @param memberId the member leaving
     * @return how many tasks were ended
     */
    public int closeAllFor(int memberId) {
        int closed = repository.overtakeAllFor(memberId);
        if (closed > 0) log.info("Closed {} open self-checks of member {}", closed, memberId);
        return closed;
    }

    /**
     * Loads a task and refuses anybody who is not answerable for it.
     *
     * <p>The reach is the one a guardian already has everywhere else: their own things, widened by
     * the members in their care, and nothing else. A task of another station answers as absent
     * rather than as refused, so no caller learns that it exists.
     */
    private SelfCheck require(int taskId, int stationId, int memberId, boolean guardian) {
        SelfCheck task = repository
                .findById(taskId)
                .filter(t -> t.stationId() == stationId)
                .orElseThrow(NotFoundResponse::new);
        if (!reach(memberId, guardian).contains(task.memberId())) {
            throw new ForbiddenResponse("This check belongs to somebody you do not answer for");
        }
        return task;
    }

    private static void requireOpen(SelfCheck task) {
        if (!task.open()) {
            throw new ConflictResponse("This check no longer takes answers");
        }
    }

    /**
     * Whose tasks somebody may touch.
     */
    private Set<Integer> reach(int memberId, boolean guardian) {
        Set<Integer> reach = new LinkedHashSet<>();
        reach.add(memberId);
        if (guardian) {
            for (var managed : stationMemberRepository.findManaged(memberId)) reach.add(managed.id());
        }
        return reach;
    }

    /**
     * Writes one answer, once it is certain the member may say that about that thing.
     */
    private void write(
            SelfCheck task, SelfCheckAnswerInput input, List<RequiredInventoryItem> required, int enteredBy) {
        if (input == null || input.answer() == null) {
            throw new BadRequestResponse("Every answer has to say something");
        }
        String note = input.note() == null ? "" : input.note().strip();
        if (input.itemId() != null) {
            writeAboutPiece(task, input, note, enteredBy);
            return;
        }
        writeAboutPlace(task, input, required, note, enteredBy);
    }

    private void writeAboutPiece(SelfCheck task, SelfCheckAnswerInput input, String note, int enteredBy) {
        if (!input.answer().aboutAPiece()) {
            throw new BadRequestResponse("That answer is about an empty place, not about a piece");
        }
        InventoryItem item = inventoryRepository
                .findItemById(input.itemId())
                .orElseThrow(() -> new BadRequestResponse("This piece does not exist"));
        if (item.assignedTo() == null || item.assignedTo() != task.memberId()) {
            throw new BadRequestResponse("This piece is not on this member's record");
        }
        if (input.answer() == SelfCheckAnswer.DO_NOT_HAVE_IT && !item.borrowed()) {
            throw new BadRequestResponse("Say a piece the station owns is missing where losses are reported");
        }
        if (input.answer() == SelfCheckAnswer.TURNED_UP && item.custody() != ItemCustody.LOST) {
            throw new BadRequestResponse("This piece is not recorded as missing, so it cannot have turned up");
        }
        repository.answerForItem(task.id(), item.id(), item.inventoryId(), input.answer(), note, null, enteredBy);
    }

    private void writeAboutPlace(
            SelfCheck task,
            SelfCheckAnswerInput input,
            List<RequiredInventoryItem> required,
            String note,
            int enteredBy) {
        if (input.answer().aboutAPiece()) {
            throw new BadRequestResponse("That answer is about a piece, and no piece was named");
        }
        if (input.inventoryId() == null || input.slot() == null || input.slot() < 0) {
            throw new BadRequestResponse("An answer about an empty place has to say which one");
        }
        RequiredInventoryItem gap = required.stream()
                .filter(r -> r.inventoryId() == input.inventoryId())
                .findFirst()
                .orElseThrow(() -> new BadRequestResponse("Nothing of this kind is asked of this member"));
        if (input.slot() >= gap.requiredQuantity() - gap.assignedQuantity()) {
            throw new BadRequestResponse("This member has no such empty place");
        }
        String typed = typedIdentifier(input);
        repository.answerForPlace(task.id(), gap.inventoryId(), input.slot(), input.answer(), note, typed, enteredBy);
    }

    /**
     * The number a member read off a piece nobody wrote down, kept as they typed it apart from the
     * spaces around it. It is never looked up here: what it matched is a finding for whoever reviews
     * the submission, and answering it back to the member would tell them about gear that is not
     * theirs.
     */
    private static String typedIdentifier(SelfCheckAnswerInput input) {
        String typed =
                input.typedInternalId() == null ? "" : input.typedInternalId().strip();
        if (typed.isEmpty()) return null;
        if (input.answer() != SelfCheckAnswer.HAVE_ONE) {
            throw new BadRequestResponse("Only a place you are holding something for takes a number");
        }
        return typed;
    }

    /**
     * A task as the person answering it sees it: their own gear, what they have said so far, and
     * what they already set going. No free stock and no resolution of anything they typed.
     *
     * @param task       the task itself
     * @param memberName whose gear it is about
     * @param required   what that member's role asks of them
     * @param assigned   what they are holding towards it
     * @param rows       what has been said so far
     * @param raised     the losses and exchanges already set going during this task
     */
    public record SelfCheckView(
            SelfCheck task,
            String memberName,
            List<RequiredInventoryItem> required,
            List<InventoryItem> assigned,
            List<SelfCheckRow> rows,
            List<SelfCheckRaised> raised) {}
}

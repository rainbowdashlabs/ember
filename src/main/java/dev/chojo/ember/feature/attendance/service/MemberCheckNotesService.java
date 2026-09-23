/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.lostandfound.repository.LostAndFoundRepository;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldScopes;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What is worth saying about a member while they are standing in front of you.
 *
 * <p>An attendance check is the one moment in the week when the person and whoever keeps the
 * equipment are in the same room, so it is where a swap waiting on a handover or an uncollected find
 * can actually be dealt with rather than remembered. These notes carry no decision of their own:
 * they say what is outstanding, and two of them can be acted on where the reader is allowed to.
 *
 * <p>Everything here is read for a whole sheet at once. The walk steps through every name on it, and
 * a read per name is fifty round trips on a tablet in a hall.
 *
 * <p>What a reader may not see is left out here rather than sent and hidden. Taking an attendance
 * says nothing about being allowed to know what somebody swapped or lost, so each note is answered
 * only to a reader holding what that note's own feature asks of anybody else.
 */
@Singleton
public class MemberCheckNotesService {
    private static final Logger log = LoggerFactory.getLogger(MemberCheckNotesService.class);

    /**
     * How far back a birthday is still worth mentioning. Six days means the one appointment a week
     * a station meets always falls within reach of the birthday before it.
     */
    private static final int BIRTHDAY_WINDOW_DAYS = 6;

    private final ItemMovementService movementService;
    private final InventoryService inventoryService;
    private final LostAndFoundRepository lostAndFoundRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final StationRepository stationRepository;

    @Inject
    public MemberCheckNotesService(
            ItemMovementService movementService,
            InventoryService inventoryService,
            LostAndFoundRepository lostAndFoundRepository,
            ProfileFieldRepository profileFieldRepository,
            StationRepository stationRepository) {
        this.movementService = movementService;
        this.inventoryService = inventoryService;
        this.lostAndFoundRepository = lostAndFoundRepository;
        this.profileFieldRepository = profileFieldRepository;
        this.stationRepository = stationRepository;
    }

    /**
     * The notes for every member of a station who has any, as far as this reader may see them.
     *
     * @param stationId   the station the sheet belongs to
     * @param permissions the reader's permissions, already expanded
     * @return notes by member id; a member with nothing outstanding is absent rather than empty
     */
    public Map<Integer, MemberNotes> findForStation(int stationId, Set<StationPermission> permissions) {
        var swaps = permissions.contains(StationPermission.INVENTORY_READ)
                ? openSwaps(stationId)
                : Map.<Integer, List<SwapNote>>of();
        var found = permissions.contains(StationPermission.LOST_AND_FOUND_MANAGE)
                ? claimedFinds(stationId)
                : Map.<Integer, List<FoundNote>>of();
        var birthdays = birthdays(stationId, ProfileFieldScopes.readableBy(permissions));

        var members = new HashSet<Integer>();
        members.addAll(swaps.keySet());
        members.addAll(found.keySet());
        members.addAll(birthdays.keySet());

        var notes = new HashMap<Integer, MemberNotes>();
        for (int memberId : members) {
            notes.put(
                    memberId,
                    new MemberNotes(
                            memberId,
                            swaps.getOrDefault(memberId, List.of()),
                            found.getOrDefault(memberId, List.of()),
                            birthdays.get(memberId)));
        }
        return notes;
    }

    /**
     * Swaps standing at the member, by member.
     *
     * <p>Only a swap the member has the piece of, or one whose next step hands them a piece, is
     * anything to say to somebody standing in front of you. A swap whose piece is on the shelf or in
     * the post is between the station and the owner until it comes back, and saying it here reads as
     * work for a member who has nothing to do with it.
     *
     * <p>Read for the station in one go rather than a member at a time, because working out where a
     * swap stands means reading where both its pieces are.
     */
    private Map<Integer, List<SwapNote>> openSwaps(int stationId) {
        var byMember = new HashMap<Integer, List<SwapNote>>();
        for (var movement : movementService.findAtMemberByStation(stationId)) {
            if (!movementService.involvesMemberNext(movement)) continue;
            var inventory = movement.inventoryId() == null
                    ? Optional.<Inventory>empty()
                    : inventoryService.findById(movement.inventoryId());
            var standing = movementService.stepsOf(movement).stream()
                    .filter(step -> movement.currentStepId() != null && step.id() == movement.currentStepId())
                    .findFirst();
            var subject = subjectOf(movement, standing.orElse(null));
            byMember.computeIfAbsent(movement.memberId(), key -> new ArrayList<>())
                    .add(new SwapNote(
                            movement.id(),
                            movement.purpose(),
                            movement.currentStepId(),
                            standing.map(MovementFlowStep::label).orElse(""),
                            standing.map(MovementFlowStep::actor).orElse(null),
                            movementService.handsOverNext(movement),
                            movement.incomingItemId(),
                            inventory.map(Inventory::name).orElse(""),
                            subject.map(InventoryItem::name).orElse(""),
                            subject.flatMap(this::sizeOf).orElse(null)));
        }
        return byMember;
    }

    /**
     * The piece the step is about, which is the one it names rather than the one the movement began
     * with: a step bringing a replacement is about the replacement.
     */
    private Optional<InventoryItem> subjectOf(ItemMovement movement, MovementFlowStep step) {
        Integer itemId = step != null && step.subject() == StepSubject.INCOMING
                ? movement.incomingItemId()
                : movement.outgoingItemId();
        return itemId == null ? Optional.empty() : inventoryService.findItemById(itemId);
    }

    /** The size written on a piece, where its inventory keeps sizes at all. */
    private Optional<String> sizeOf(InventoryItem item) {
        return item.sizeId() == null
                ? Optional.empty()
                : inventoryService.findSizeById(item.sizeId()).map(InventorySize::label);
    }

    /**
     * Found items somebody has claimed and not yet collected, by member.
     *
     * <p>Handing one over is what removes it, so a claimed row that is still there is exactly one
     * still to be collected.
     */
    private Map<Integer, List<FoundNote>> claimedFinds(int stationId) {
        var byMember = new HashMap<Integer, List<FoundNote>>();
        for (var item : lostAndFoundRepository.findByStation(stationId)) {
            if (item.claimedBy() == null) continue;
            byMember.computeIfAbsent(item.claimedBy(), key -> new ArrayList<>())
                    .add(new FoundNote(item.id(), item.description()));
        }
        return byMember;
    }

    /**
     * Birthdays that fell within the window, by member, as the number of days ago.
     *
     * <p>Nothing is answered where the station declares no birth date field, or where the field it
     * declares is out of the reader's scope: a field kept from somebody is kept from them here too.
     *
     * <p>The anniversary is what matters rather than the date, and the year it falls in is decided by
     * the date itself: a birthday on the 30th of December is six days ago on the 5th of January, and
     * comparing within one year would make it three hundred and sixty.
     */
    private Map<Integer, Integer> birthdays(int stationId, Set<ProfileFieldScope> readableScopes) {
        var field = profileFieldRepository.findReadableBy(stationId, readableScopes).stream()
                .filter(candidate -> candidate.fieldType() == ProfileFieldType.BIRTH_DATE)
                .findFirst()
                .orElse(null);
        if (field == null) return Map.of();

        LocalDate today = LocalDate.now(
                StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null)));
        var byMember = new HashMap<Integer, Integer>();
        for (var value : profileFieldRepository.findValuesOfField(field.id())) {
            var daysAgo = daysSinceBirthday(value.value(), today);
            if (daysAgo != null) byMember.put(value.memberId(), daysAgo);
        }
        return byMember;
    }

    /**
     * How many days ago this year's birthday fell, or null where it is outside the window or the
     * answer is not a date at all.
     *
     * <p>A profile answer is whatever somebody typed, so an unreadable one is no birthday rather than
     * a failure: a malformed date must not stop the rest of the sheet being answered.
     */
    static Integer daysSinceBirthday(String stored, LocalDate today) {
        if (stored == null || stored.isBlank()) return null;
        MonthDay born;
        try {
            born = MonthDay.from(LocalDate.parse(stored.strip().replace("\"", "")));
        } catch (Exception e) {
            return null;
        }
        LocalDate thisYear = bestEffortDate(born, today.getYear());
        LocalDate lastYear = bestEffortDate(born, today.getYear() - 1);
        LocalDate mostRecent = thisYear.isAfter(today) ? lastYear : thisYear;
        long daysAgo = ChronoUnit.DAYS.between(mostRecent, today);
        return daysAgo >= 0 && daysAgo <= BIRTHDAY_WINDOW_DAYS ? (int) daysAgo : null;
    }

    /**
     * The birthday in a given year, moved to the 28th where that year has no 29th of February. A
     * leap-day birthday is otherwise unrepresentable in three years out of four.
     */
    private static LocalDate bestEffortDate(MonthDay born, int year) {
        return born.getDayOfMonth() == 29 && born.getMonthValue() == 2 && !Year.isLeap(year)
                ? LocalDate.of(year, 2, 28)
                : LocalDate.of(year, born.getMonthValue(), born.getDayOfMonth());
    }

    /**
     * @param memberId    the member the notes are about
     * @param swaps       swaps of theirs that have not finished
     * @param foundItems  found items they claimed and have not collected
     * @param birthdayDaysAgo how many days ago their birthday fell, zero for today, null for none
     */
    public record MemberNotes(
            int memberId, List<SwapNote> swaps, List<FoundNote> foundItems, Integer birthdayDaysAgo) {}

    /**
     * One movement of this member's, as somebody standing in front of them needs it.
     *
     * <p>It carries the step rather than a status, because a step is what there is: the words the chain
     * gives it, whose turn it is, and the id to acknowledge. There is no next step to name in advance
     * either, since the chain decides that when this one is acknowledged.
     *
     * @param movementId    the movement
     * @param purpose       what it is for, which is what says whether a piece is coming or going
     * @param stepId        the step it stands on, which is what an acknowledgement names
     * @param stepLabel     the words that step carries
     * @param stepActor     whose turn it is
     * @param handOverNext  whether acknowledging it puts a piece into the member's hands
     * @param replacementItemId the piece set aside for the member, where one is already named. Carried
     *     because the movement knows it: asking whoever runs the check to pick it out again, from a
     *     sheet of names, would be asking them a question the movement has already answered
     * @param inventoryName what it is out of, for saying which movement this is
     * @param itemName      the piece this step is about, which is the one arriving where the step
     *     brings one and the one they are holding otherwise
     * @param itemSize      the size written on that piece, or null where its inventory keeps no sizes
     */
    public record SwapNote(
            int movementId,
            MovementPurpose purpose,
            Integer stepId,
            String stepLabel,
            StepActor stepActor,
            boolean handOverNext,
            Integer replacementItemId,
            String inventoryName,
            String itemName,
            String itemSize) {}

    /**
     * @param itemId      the found item
     * @param description what it is
     */
    public record FoundNote(int itemId, String description) {}
}

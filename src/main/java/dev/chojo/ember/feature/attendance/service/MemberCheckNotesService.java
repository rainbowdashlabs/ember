/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
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
     * How far back a birthday is still worth mentioning. Six days means the one evening a week a
     * station meets always falls within reach of the birthday before it.
     */
    private static final int BIRTHDAY_WINDOW_DAYS = 6;

    private final ExchangeService exchangeService;
    private final InventoryService inventoryService;
    private final LostAndFoundRepository lostAndFoundRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final StationRepository stationRepository;

    @Inject
    public MemberCheckNotesService(
            ExchangeService exchangeService,
            InventoryService inventoryService,
            LostAndFoundRepository lostAndFoundRepository,
            ProfileFieldRepository profileFieldRepository,
            StationRepository stationRepository) {
        this.exchangeService = exchangeService;
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
     * Swaps that have not finished, by member.
     *
     * <p>Read for the station in one go rather than a member at a time, because working out where a
     * swap stands means reading where both its pieces are.
     */
    private Map<Integer, List<SwapNote>> openSwaps(int stationId) {
        var byMember = new HashMap<Integer, List<SwapNote>>();
        for (var request : exchangeService.findByStation(stationId)) {
            if (!request.status().walkable() || request.status() == ExchangeStatus.DONE) continue;
            var inventory = inventoryService.findById(request.inventoryId());
            var next = nextStatus(
                    request.status(), inventory.map(Inventory::inventoryType).orElse(InventoryType.EXTERNAL));
            byMember.computeIfAbsent(request.memberId(), key -> new ArrayList<>())
                    .add(new SwapNote(
                            request.id(),
                            request.status(),
                            next,
                            next == ExchangeStatus.DONE,
                            request.exchangedItemId(),
                            inventory.map(Inventory::name).orElse("")));
        }
        return byMember;
    }

    /**
     * The one step a swap takes next, or null where it is at its end.
     *
     * <p>Worked out here rather than in the browser so the order of the steps is written down once.
     * An inventory of the station's own skips the two postal steps, because nothing is posted to
     * fetch a piece that is already in the building.
     */
    static ExchangeStatus nextStatus(ExchangeStatus current, InventoryType inventoryType) {
        List<ExchangeStatus> flow = inventoryType == InventoryType.INTERNAL
                ? List.of(ExchangeStatus.ANNOUNCED, ExchangeStatus.RECEIVED, ExchangeStatus.DONE)
                : List.of(
                        ExchangeStatus.ANNOUNCED,
                        ExchangeStatus.RECEIVED,
                        ExchangeStatus.SHIPPED,
                        ExchangeStatus.ARRIVED,
                        ExchangeStatus.DONE);
        int index = flow.indexOf(current);
        return index < 0 || index >= flow.size() - 1 ? null : flow.get(index + 1);
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
        var field = profileFieldRepository.findAllByStationAndType(stationId, ProfileFieldType.BIRTH_DATE).stream()
                .filter(candidate -> candidate.scope() != null && readableScopes.contains(candidate.scope()))
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
     * @param exchangeId    the swap
     * @param status        where it stands, which is what says who is being waited on
     * @param nextStatus    the one step it takes next, null where it is at its end
     * @param handOverNext  whether that step is putting the piece into the member's hands
     * @param replacementItemId the piece set aside for the member, which the step that hands it over
     *     has to be told about. Carried here because the swap already knows it: asking whoever runs
     *     the check to pick it out again, from a sheet of names, would be asking them to answer a
     *     question the swap has already answered
     * @param inventoryName what the swap is out of, for saying which swap this is
     */
    public record SwapNote(
            int exchangeId,
            ExchangeStatus status,
            ExchangeStatus nextStatus,
            boolean handOverNext,
            Integer replacementItemId,
            String inventoryName) {}

    /**
     * @param itemId      the found item
     * @param description what it is
     */
    public record FoundNote(int itemId, String description) {}
}

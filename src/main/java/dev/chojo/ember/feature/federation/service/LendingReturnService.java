/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ItemCustodyService;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What happens when lent gear comes back, and when it does not come back in time.
 *
 * <p>The return check answers for every piece of the loan: in order, damaged, or missing. The loan is
 * then returned as a whole; a missing piece is marked lost with a note naming the loan, and the result
 * goes into the request's messages, where the borrowing station reads it. Damage is only written down
 * there for now, as pieces have no defect state of their own yet.
 *
 * <p>Gear past its last day can be reminded of by hand, and is reminded of once on its own the day
 * after: both write into the messages, which notifies the borrowing station.
 */
@Singleton
public class LendingReturnService implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(LendingReturnService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** How often the reminders that go out on their own are looked for. */
    private static final Duration REMINDER_SWEEP = Duration.ofHours(6);

    /** The longest comment a checked piece keeps. */
    static final int MAX_COMMENT = 500;

    private final LendingService lendingService;
    private final LendingRepository repository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final StationRepository stationRepository;
    private final StationService stationService;
    private final ItemCustodyService custodyService;

    @Inject
    public LendingReturnService(
            LendingService lendingService,
            LendingRepository repository,
            InventoryRepository inventoryRepository,
            InventoryService inventoryService,
            StationRepository stationRepository,
            StationService stationService,
            ItemCustodyService custodyService) {
        this.lendingService = lendingService;
        this.repository = repository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.stationRepository = stationRepository;
        this.stationService = stationService;
        this.custodyService = custodyService;
    }

    /** The pieces a loan holds, as the return check lists them. */
    public List<LoanPiece> pieces(LendingRequest request) {
        var pieces = new ArrayList<LoanPiece>();
        for (var line : repository.findItemsByRequest(request.id())) {
            for (int itemId : repository.findAssignedItems(line.id())) {
                inventoryRepository
                        .findItemById(itemId)
                        .ifPresent(item -> pieces.add(new LoanPiece(item.id(), item.name(), item.internalId())));
            }
        }
        return pieces;
    }

    /**
     * Takes lent gear back after checking every piece.
     *
     * @param request the loan, which this station lends
     * @throws RefusalResponse when the gear is not out, the check does not answer for exactly the
     *                         pieces of the loan, or a missing piece cannot be marked lost, which is
     *                         asked before anything is taken back
     */
    public void checkReturn(
            LendingRequest request, int stationId, int memberId, String memberName, List<PieceCheck> checks) {
        if (request.status() != LendingStatus.LENT) throw FederationRefusal.LENDING_NOT_OUT.raise();
        var pieces = pieces(request);
        Set<Integer> held = pieces.stream().map(LoanPiece::itemId).collect(Collectors.toSet());
        Set<Integer> answered = new HashSet<>();
        for (var check : checks) {
            if (check.state() == null || !answered.add(check.itemId())) {
                throw FederationRefusal.LENDING_RETURN_PIECES_MISMATCH.raise();
            }
        }
        if (!answered.equals(held)) throw FederationRefusal.LENDING_RETURN_PIECES_MISMATCH.raise();

        for (var check : checks) {
            if (check.state() == ReturnState.MISSING) {
                custodyService.requireFreeToActOn(check.itemId(), ItemCustodyService.Allowance.lending(request.id()));
            }
        }
        if (!lendingService.markReturnedFromLent(request.id(), stationId)) {
            throw FederationRefusal.LENDING_NOT_OUT.raise();
        }
        String partner = lendingService.stationName(request.requestingStationUid(), stationId);
        for (var check : checks) {
            if (check.state() != ReturnState.MISSING) continue;
            String note = "Nicht zurückgekommen aus der Ausleihe an %s (Anfrage #%d)%s"
                    .formatted(partner, request.id(), suffix(check.comment()));
            inventoryService.markLost(check.itemId(), note, memberId);
        }
        lendingService.sendMessage(request.id(), stationId, memberId, memberName, summary(pieces, checks));
    }

    /**
     * Reminds the borrowing station of gear past its last day.
     *
     * @throws RefusalResponse when the gear is not out or not overdue yet
     */
    public void remind(LendingRequest request, int stationId, int memberId, String memberName, LocalDate today) {
        if (request.status() != LendingStatus.LENT) throw FederationRefusal.LENDING_NOT_OUT.raise();
        if (!isOverdue(request, today)) throw FederationRefusal.LENDING_NOT_OVERDUE.raise();
        lendingService.sendMessage(request.id(), stationId, memberId, memberName, reminder(request));
        repository.markReminded(request.id());
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "lending-reminders",
                Schedule.fixedDelay(Duration.ofMinutes(5), REMINDER_SWEEP),
                () -> sweep(LocalDate.now())));
    }

    /**
     * Sends the reminders that go out on their own: every loan of a station here past its last day
     * that was not reminded of yet. Never lets an exception escape, because the scheduler would stop
     * running a task that threw.
     */
    void sweep(LocalDate today) {
        List<LendingRequest> due;
        try {
            due = repository.findOverdueUnreminded(today);
        } catch (RuntimeException e) {
            log.error("Lending reminders could not be read", e);
            return;
        }
        for (var request : due) {
            try {
                var lender = stationRepository
                        .findHereByUid(request.owningStationUid())
                        .orElse(null);
                if (lender == null || !stationService.isModuleEnabled(lender.id(), StationModule.INVENTORY_LENDING)) {
                    continue;
                }
                lendingService.sendStationMessage(request.id(), lender.id(), reminder(request));
                repository.markReminded(request.id());
            } catch (RuntimeException e) {
                log.error("Lending reminder for request {} could not be sent", request.id(), e);
            }
        }
    }

    private static String reminder(LendingRequest request) {
        return "Erinnerung: Die Ausrüstung dieser Ausleihe war bis zum %s vereinbart. Bitte gebt sie zurück oder schreibt, wann sie kommt."
                .formatted(DAY.format(request.lastDay()));
    }

    /** Whether a loan's gear is out past its last day, the first day where it names none. */
    public static boolean isOverdue(LendingRequest request, LocalDate today) {
        return request.status() == LendingStatus.LENT && request.lastDay().isBefore(today);
    }

    static String summary(List<LoanPiece> pieces, List<PieceCheck> checks) {
        var names = pieces.stream().collect(Collectors.toMap(LoanPiece::itemId, LoanPiece::label));
        long ok =
                checks.stream().filter(check -> check.state() == ReturnState.OK).count();
        var text = new StringBuilder("Rückgabe geprüft: %d in Ordnung".formatted(ok));
        for (var check : checks) {
            if (check.state() == ReturnState.OK) continue;
            text.append("\n- ")
                    .append(names.get(check.itemId()))
                    .append(check.state() == ReturnState.DAMAGED ? ": beschädigt" : ": fehlt")
                    .append(suffix(check.comment()));
        }
        return text.toString();
    }

    private static String suffix(@Nullable String comment) {
        if (comment == null || comment.isBlank()) return "";
        String text = comment.strip();
        return " (" + (text.length() > MAX_COMMENT ? text.substring(0, MAX_COMMENT) : text) + ")";
    }

    /** How a piece came back. */
    public enum ReturnState {
        OK,
        DAMAGED,
        MISSING
    }

    /**
     * The answer for one piece of a loan.
     *
     * @param comment what was seen, or {@code null}
     */
    public record PieceCheck(
            int itemId,
            @Nullable ReturnState state,
            @Nullable String comment) {}

    /**
     * One piece a loan holds.
     *
     * @param internalId the piece's identifier at the lending station, or {@code null}
     */
    public record LoanPiece(
            int itemId, String name, @Nullable String internalId) {
        String label() {
            return internalId == null || internalId.isBlank() ? name : name + " (" + internalId + ")";
        }
    }
}

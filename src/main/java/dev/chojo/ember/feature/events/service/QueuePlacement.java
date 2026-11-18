/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.events.entity.QueueEntry;
import dev.chojo.ember.feature.events.entity.QueueOrder;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueList;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueOwner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts sign-ups on waiting lists in the order the list keeps.
 *
 * <p>Every sign-up joining a list carries the fairness score it has at that moment, whatever the
 * list's order, so a list changed to fairness later still has a score for each entry. A list kept by
 * time puts a new sign-up at the end. A list kept by fairness puts it before the first entry with a
 * lower score, so equal scores keep the older sign-up first, and entries a manager moved by hand keep
 * their order among each other; a newcomer with a higher score can still land ahead of them. The score
 * is taken once and stored, so the order does not shift while decisions elsewhere change people's
 * history; the member sees their place, never the score.
 *
 * <p>The score reads the member's history at the station over the last {@value #MONTHS} months within
 * the category of what they wait for. A partner's member has no history here and scores as a newcomer.
 * Callers hold the lock of the appointment or event group.
 */
@Singleton
public class QueuePlacement {
    /** How many months of history the score reads, the same window the registration statistics open with. */
    static final int MONTHS = 12;

    private final RegistrationQueueRepository queue;
    private final EventRegistrationRepository registrations;

    /**
     * Creates the placement.
     *
     * @param queue         the waiting lists
     * @param registrations the sign-up history the score reads
     */
    @Inject
    public QueuePlacement(RegistrationQueueRepository queue, EventRegistrationRepository registrations) {
        this.queue = queue;
        this.registrations = registrations;
    }

    /**
     * The fairness score a member has at a station within a category.
     *
     * @param stationId  the station
     * @param categoryId the category, or null for every appointment
     * @param memberId   the member, or null for a partner's member
     * @return the score
     */
    public double scoreOf(int stationId, @Nullable Integer categoryId, @Nullable Integer memberId) {
        if (memberId == null) return FairnessScore.NEWCOMER.score();
        return FairnessScore.of(registrations.findStatsOfMember(stationId, categoryId, memberId, MONTHS))
                .score();
    }

    /**
     * Puts a sign-up on a waiting list. A sign-up already on it keeps its place.
     *
     * @param list  the list
     * @param order the order the list keeps
     * @param owner the sign-up
     * @param score its fairness score
     * @return its entry
     */
    public QueueEntry place(QueueList list, QueueOrder order, QueueOwner owner, double score) {
        var entries = queue.entries(list);
        var existing = entries.stream().filter(owner::owns).findFirst();
        if (existing.isPresent()) return existing.get();
        queue.insertAt(list, owner, positionFor(entries, order, score), score);
        return queue.entryOf(list, owner).orElseThrow();
    }

    /**
     * Moves a sign-up to another place in line on a waiting list. Moving by hand wins over the order the
     * list keeps; whoever comes later is placed by that order around it.
     *
     * @param list    the list
     * @param owner   the sign-up, which waits on it
     * @param rank    the place in line it takes, 1 moving up next; beyond the end it goes last
     * @param missing the refusal where the sign-up does not wait on the list
     */
    public void moveTo(QueueList list, QueueOwner owner, int rank, Refusal missing) {
        var entries = new ArrayList<>(queue.entries(list));
        var moving = entries.stream().filter(owner::owns).findFirst().orElseThrow(missing::raise);
        entries.remove(moving);
        entries.add(Math.clamp(rank - 1L, 0, entries.size()), moving);
        queue.reorder(list, entries.stream().map(QueueEntry::id).toList());
    }

    /** The position key a new entry with this score takes among the entries of its list. */
    private static int positionFor(List<QueueEntry> entries, QueueOrder order, double score) {
        if (order == QueueOrder.FAIRNESS) {
            for (var entry : entries) {
                if (entry.score() != null && entry.score() < score) return entry.position();
            }
        }
        return entries.isEmpty() ? 1 : entries.getLast().position() + 1;
    }
}

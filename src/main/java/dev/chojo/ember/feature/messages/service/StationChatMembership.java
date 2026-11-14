/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

import dev.chojo.ember.feature.messages.entity.Conversation;
import dev.chojo.ember.feature.messages.entity.Membership;
import dev.chojo.ember.feature.messages.entity.MembershipChange;
import dev.chojo.ember.feature.messages.entity.MessageEvent;
import dev.chojo.ember.feature.messages.entity.StationScope;
import dev.chojo.ember.feature.messages.entity.SystemKind;
import dev.chojo.ember.feature.messages.repository.ConversationRepository;
import dev.chojo.ember.feature.messages.repository.MessageRepository;
import dev.chojo.ember.feature.messages.repository.StationChatRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Keeps the members of station chats in line with their audience.
 *
 * <p>Membership is kept as rows, because a member's read marker and muting live there, and the rows
 * are brought in line with the audience whenever it may have changed: after a change to the
 * station's people, debounced per station, and once a day for every station, so a change that
 * forgot to say so costs a day at most. Somebody taken in reads the whole history; somebody taken out
 * loses the chat with it. Neither writes a line into the chat.
 *
 * <p>A closed chat takes nobody new in, but still lets go of whoever left its audience, so somebody
 * who left the station cannot read it any more. A chat whose group or tag was deleted is closed here
 * and keeps the members it had, as long as they are still at the station. A muted chat stays muted
 * for somebody taken out and back in.
 */
@Singleton
public class StationChatMembership implements TaskSource {
    /**
     * How long a station's reconciliation waits after a change. Changes that come together are
     * reconciled once, and the change has long been committed by the time it runs.
     */
    static final Duration DEBOUNCE = Duration.ofSeconds(3);

    /** How often every station is reconciled, whether anything said it changed or not. */
    static final Duration SWEEP_INTERVAL = Duration.ofDays(1);

    private static final Logger log = LoggerFactory.getLogger(StationChatMembership.class);

    private final StationChatRepository chats;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final TaskScheduler scheduler;
    private final MessageSignals signals;
    private final Set<Integer> pending = ConcurrentHashMap.newKeySet();

    @Inject
    public StationChatMembership(
            StationChatRepository chats,
            ConversationRepository conversations,
            MessageRepository messages,
            TaskScheduler scheduler,
            MessageSignals signals) {
        this.signals = signals;
        this.chats = chats;
        this.conversations = conversations;
        this.messages = messages;
        this.scheduler = scheduler;
    }

    /**
     * Reconciles a station's chats a moment from now, once for any number of calls until it runs.
     * A call that comes while a reconciliation runs plans another.
     *
     * @param stationId the station
     */
    public void soon(int stationId) {
        if (!pending.add(stationId)) return;
        scheduler.later("station-chat-membership", DEBOUNCE, () -> {
            pending.remove(stationId);
            reconcile(stationId);
        });
    }

    /**
     * Reconciles the chats of every station an account is a current member of, a moment from now.
     *
     * @param accountId the account
     */
    public void soonForAccount(int accountId) {
        chats.stationsOf(accountId).forEach(this::soon);
    }

    /**
     * Brings every chat of a station in line with its audience now.
     *
     * @param stationId the station
     * @return what changed, one entry per chat that changed
     */
    public List<MembershipChange> reconcile(int stationId) {
        List<MembershipChange> changes = new ArrayList<>();
        for (Conversation chat : chats.ofStation(stationId)) {
            try {
                reconcileChat(chat.id()).filter(MembershipChange::any).ifPresent(changes::add);
            } catch (RuntimeException e) {
                log.warn("Reconciling station chat {} of station {} failed", chat.id(), stationId, e);
            }
        }
        if (!changes.isEmpty()) {
            log.info("Station chats of station {} reconciled: {} chat(s) changed", stationId, changes.size());
        }
        return changes;
    }

    /** Reconciles the chats of every station that has any. */
    void sweep() {
        for (int stationId : chats.stationsWithChats()) {
            try {
                reconcile(stationId);
            } catch (RuntimeException e) {
                log.warn("Reconciling the chats of station {} failed", stationId, e);
            }
        }
    }

    /**
     * Brings one chat in line with its audience now, as after it was opened or its audience changed.
     *
     * @param conversationId the chat
     * @return what changed, empty where the chat is gone
     */
    public Optional<MembershipChange> reconcileChat(int conversationId) {
        Optional<MembershipChange> change = Transactions.call(() -> reconcileLocked(conversationId));
        change.filter(MembershipChange::any).ifPresent(changed -> Transactions.afterCommit(() -> tell(changed)));
        return change;
    }

    /**
     * Tells the tabs of everybody in a changed chat, and of everybody taken out of it, so the chat
     * appears, disappears or shows its new members without a reload.
     */
    private void tell(MembershipChange change) {
        Conversation chat = change.conversation();
        signals.publishTo(chat.id(), MessageEvent.conversation(chat.uid()), change.removed());
    }

    private Optional<MembershipChange> reconcileLocked(int conversationId) {
        return chats.lock(conversationId).map(chat -> {
            StationScope scope = Objects.requireNonNull(chat.station(), "a station chat has its scope");
            boolean closed = scope.orphaned() && closeOrphaned(chat);
            Set<Integer> wanted = scope.orphaned() ? chats.stationPeople(scope.stationId()) : chats.audience(chat.id());
            Set<Integer> present = conversations.activeMembers(chat.id()).stream()
                    .map(Membership::accountId)
                    .collect(Collectors.toSet());
            Set<Integer> added = new HashSet<>();
            if (!scope.closed() && !scope.orphaned()) {
                added.addAll(wanted);
                added.removeAll(present);
            }
            Set<Integer> removed = new HashSet<>(present);
            removed.removeAll(wanted);
            added.forEach(accountId -> conversations.follow(chat.id(), accountId));
            removed.forEach(accountId -> conversations.leave(chat.id(), accountId));
            return new MembershipChange(chat, Set.copyOf(added), Set.copyOf(removed), closed);
        });
    }

    private boolean closeOrphaned(Conversation chat) {
        if (!chats.close(chat.id(), null)) return false;
        messages.writeSystem(chat.id(), null, SystemKind.CLOSED, null, "");
        conversations.touch(chat.id());
        log.info("Station chat {} closed: its group or tag is gone", chat.id());
        return true;
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "station-chat-membership-sweep",
                Schedule.fixedDelay(Duration.ofMinutes(10), SWEEP_INTERVAL),
                this::sweep));
    }
}

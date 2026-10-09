/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.entity.DueReminder;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reminds people of the signatures a document still waits for.
 *
 * <p>A field that is still open {@link #INTERVAL} after it was asked for is reminded of, and again every
 * {@link #INTERVAL} after that, {@link #MAX_REMINDERS} times at most. A week leaves room for a weekend and a
 * week's practice evening in between, and three reminders cover a month: whoever has not signed by then is
 * better asked in person, and the field is still listed among their open tasks. A field settled in the
 * meantime, or one whose request was withdrawn, superseded or whose document is gone, is never reminded of.
 *
 * <p>The reminders reach the same people and the same way as the request did ({@link SignatureNotices}), one
 * mail per address and document. Each reminder is counted before it is sent and only once, so a run that
 * overlaps another or fails halfway never reminds twice; a reminder whose sending failed is not repeated
 * before the next interval. The sweep looks in every hour, so a reminder is at most an hour late.
 */
@Singleton
public class SignatureReminders implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(SignatureReminders.class);
    private static final Duration START_DELAY = Duration.ofMinutes(20);
    private static final Duration SWEEP_INTERVAL = Duration.ofHours(1);

    /** How long a field waits before its first reminder, and between two. */
    static final Duration INTERVAL = Duration.ofDays(7);

    /** How many reminders a field gets at most. */
    static final int MAX_REMINDERS = 3;

    /** How many fields one run reminds of at most. */
    static final int MAX_PER_RUN = 200;

    private final SignatureRequestRepository requests;
    private final SignatureNotices notices;
    private final Clock clock;

    @Inject
    public SignatureReminders(SignatureRequestRepository requests, SignatureNotices notices) {
        this(requests, notices, Clock.systemUTC());
    }

    SignatureReminders(SignatureRequestRepository requests, SignatureNotices notices, Clock clock) {
        this.requests = requests;
        this.notices = notices;
        this.clock = clock;
    }

    /**
     * Body of the run, reachable by tests so they need not wait. A failure is logged and swallowed: what was
     * due and not counted stays due for the next run.
     */
    void sweep() {
        try {
            int reminded = sweep(clock.instant());
            if (reminded > 0) log.info("Reminded of {} signature fields", reminded);
        } catch (Exception e) {
            log.warn("Reminding of open signature fields failed", e);
        }
    }

    /**
     * Reminds of every field that is due, grouped by request.
     *
     * @param now the time the intervals are measured against
     * @return how many fields were reminded of
     */
    int sweep(Instant now) {
        Map<Integer, List<RequestedSignature>> byRequest = new LinkedHashMap<>();
        for (DueReminder due : requests.dueForReminder(now, INTERVAL, MAX_REMINDERS, MAX_PER_RUN)) {
            var field = due.pending().field();
            if (requests.markReminded(field.id(), due.remindersSent(), now)) {
                byRequest
                        .computeIfAbsent(field.requestId(), id -> new ArrayList<>())
                        .add(field);
            }
        }
        int reminded = 0;
        for (var entry : byRequest.entrySet()) {
            var request = requests.findById(entry.getKey());
            if (request.isEmpty()) continue;
            notices.reminded(request.get(), entry.getValue());
            reminded += entry.getValue().size();
        }
        return reminded;
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "signature-reminders", Schedule.fixedDelay(START_DELAY, SWEEP_INTERVAL), this::sweep));
    }
}

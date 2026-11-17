/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.push.entity.PushSubscription;
import dev.chojo.ember.feature.push.repository.PushOutboxRepository;
import dev.chojo.ember.feature.push.repository.PushSubscriptionRepository;
import dev.chojo.ember.feature.system.service.InstanceSettingsService;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Sends the queued pushes, a few seconds after they were queued.
 *
 * <p>Sending never happens in the request that caused it: a slow push service would slow the save,
 * and a restart in between would lose the push. A push the service asks to wait for is tried again
 * with a growing pause; one it refuses outright is dropped; a browser it no longer knows is
 * forgotten. Each browser takes at most {@link #PER_HOUR} pushes an hour, and what the limit holds
 * back goes out as one summary once the hour allows it again. While the instance has push switched
 * off, whatever is queued is dropped, so switching it on again sends nothing stale.
 */
@Singleton
public class PushSender implements TaskSource {
    /** How many pushes one browser takes in an hour. */
    static final int PER_HOUR = 20;

    private static final Logger log = getLogger(PushSender.class);
    private static final Duration SWEEP = Duration.ofSeconds(5);
    /**
     * Longer than a whole batch can take, each push spending at most its name lookup, the connect
     * timeout and the request timeout, so no other node takes a row this one still holds.
     */
    private static final Duration LEASE = Duration.ofMinutes(10);

    private static final Duration MAX_PAUSE = Duration.ofHours(1);
    private static final int BATCH = 20;
    private static final int MAX_ATTEMPTS = 6;
    private static final int MAX_FAILURES = 10;

    private final PushOutboxRepository outbox;
    private final PushSubscriptionRepository subscriptions;
    private final PushMessages messages;
    private final VapidKeys keys;
    private final PushTransport transport;
    private final InstanceSettingsService instance;
    private final Api api;
    private Instant lastPrune = Instant.EPOCH;

    @Inject
    public PushSender(
            PushOutboxRepository outbox,
            PushSubscriptionRepository subscriptions,
            PushMessages messages,
            VapidKeys keys,
            PushTransport transport,
            InstanceSettingsService instance,
            Api api) {
        this.outbox = outbox;
        this.subscriptions = subscriptions;
        this.messages = messages;
        this.keys = keys;
        this.transport = transport;
        this.instance = instance;
        this.api = api;
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask("push-sender", Schedule.fixedDelay(SWEEP, SWEEP), () -> sweep(Instant.now())));
    }

    /**
     * One look at the queue: sends what is due, browser by browser, and once an hour forgets what
     * no longer counts. Never lets an exception escape, because the scheduler would stop running a
     * task that threw.
     *
     * @param now the moment of this sweep
     */
    void sweep(Instant now) {
        try {
            if (Duration.between(lastPrune, now).compareTo(Duration.ofHours(1)) >= 0) {
                outbox.prune();
                lastPrune = now;
            }
            if (!instance.pushEnabled()) {
                outbox.dropWaiting();
                return;
            }
            var due = outbox.claimDue(BATCH, LEASE);
            if (due.isEmpty()) return;
            Map<Integer, List<PushOutboxRepository.Due>> byBrowser = new LinkedHashMap<>();
            for (var push : due)
                byBrowser
                        .computeIfAbsent(push.subscriptionId(), id -> new ArrayList<>())
                        .add(push);
            Map<Integer, PushSubscription> browsers = subscriptions.findByIds(List.copyOf(byBrowser.keySet())).stream()
                    .collect(Collectors.toMap(PushSubscription::id, Function.identity()));
            byBrowser.forEach((id, pushes) -> {
                var browser = browsers.get(id);
                if (browser != null) sendTo(browser, pushes, now);
            });
        } catch (RuntimeException e) {
            log.error("Sending push messages failed", e);
        }
    }

    /**
     * Sends one browser's due pushes within what the hour still allows it. Held-back pushes, those
     * of this sweep and any others that are due, go out first as one summary, which counts once;
     * then the rest one by one; whatever is beyond the limit waits until the hour allows it again.
     */
    private void sendTo(PushSubscription browser, List<PushOutboxRepository.Due> pushes, Instant now) {
        int budget = PER_HOUR - outbox.sentWithinHour(browser.id());
        var held = pushes.stream().filter(PushOutboxRepository.Due::deferred).toList();
        var fresh = pushes.stream().filter(push -> !push.deferred()).toList();
        var waiting = new ArrayList<PushOutboxRepository.Due>();
        if (!held.isEmpty()) {
            if (budget > 0) {
                var rows = new ArrayList<Long>(
                        held.stream().map(PushOutboxRepository.Due::id).toList());
                rows.addAll(outbox.claimHeldBack(browser.id(), LEASE));
                sendHeldBack(browser, held.getFirst(), rows, now);
                budget--;
            } else {
                waiting.addAll(held);
            }
        }
        for (var push : fresh) {
            if (budget > 0) {
                sendOne(browser, push, now);
                budget--;
            } else {
                waiting.add(push);
            }
        }
        if (waiting.isEmpty()) return;
        Instant opens = outbox.sendableAgainAt(browser.id(), PER_HOUR).orElse(now.plus(Duration.ofMinutes(5)));
        for (var push : waiting) outbox.defer(push.id(), opens);
    }

    private void sendHeldBack(PushSubscription browser, PushOutboxRepository.Due first, List<Long> rows, Instant now) {
        if (rows.size() > 1) {
            deliver(browser, first, rows, messages.summary(rows.size(), browser.accountId()), now);
            return;
        }
        sendOne(browser, first, now);
    }

    private void sendOne(PushSubscription browser, PushOutboxRepository.Due push, Instant now) {
        var message = messages.render(push, browser.accountId());
        if (message.isEmpty()) {
            outbox.drop(push.id());
            return;
        }
        deliver(browser, push, List.of(push.id()), message.get(), now);
    }

    /**
     * Encrypts, signs and posts one push, and acts on the answer. A browser whose keys cannot be
     * used is forgotten; a push this side cannot build is only dropped, since the browser is not at
     * fault for it.
     */
    private void deliver(
            PushSubscription browser,
            PushOutboxRepository.Due push,
            List<Long> rows,
            PushMessage message,
            Instant now) {
        if (!keysUsable(browser)) {
            log.info("Push subscription {} has keys that cannot be used, so it is forgotten", browser.id());
            subscriptions.delete(browser.id());
            return;
        }
        byte[] body;
        Map<String, String> headers;
        try {
            body = PushPayloadCipher.encrypt(message.payload(), browser.p256dh(), browser.auth());
            headers = headers(browser, message, now);
        } catch (IllegalArgumentException e) {
            log.warn("A push to subscription {} could not be built and is dropped: {}", browser.id(), e.getMessage());
            rows.forEach(outbox::drop);
            return;
        }
        PushTransport.Answer answer;
        try {
            answer = transport.post(URI.create(browser.endpoint()), headers, body);
        } catch (IOException e) {
            answer = new PushTransport.Answer(503, Duration.ZERO);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (answer.accepted()) {
            outbox.markSummarySent(push.id(), rows);
            subscriptions.recordSuccess(browser.id());
        } else if (answer.gone()) {
            subscriptions.delete(browser.id());
        } else if (answer.worthRetrying() && push.attempts() + 1 < MAX_ATTEMPTS) {
            Instant again = now.plus(pause(push.attempts(), answer.retryAfter()));
            for (long row : rows) outbox.retryLater(row, again);
        } else {
            rows.forEach(outbox::drop);
            if (subscriptions.recordFailure(browser.id()) >= MAX_FAILURES) {
                log.info(
                        "Push subscription {} was refused {} times in a row and is forgotten",
                        browser.id(),
                        MAX_FAILURES);
                subscriptions.delete(browser.id());
            }
        }
    }

    private static boolean keysUsable(PushSubscription browser) {
        if (browser.auth().length != 16) return false;
        try {
            EcP256.publicKeyOf(browser.p256dh());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The pause before another try: what the service asked for, or a minute doubled per try, at most an hour. */
    static Duration pause(int attempts, Duration asked) {
        Duration doubling = Duration.ofMinutes(1L << Math.min(attempts, 6));
        Duration pause = asked.compareTo(doubling) > 0 ? asked : doubling;
        return pause.compareTo(MAX_PAUSE) > 0 ? MAX_PAUSE : pause;
    }

    private Map<String, String> headers(PushSubscription subscription, PushMessage message, Instant now) {
        var opened = keys.keys();
        var headers = new LinkedHashMap<String, String>();
        headers.put("Content-Type", "application/octet-stream");
        headers.put("Content-Encoding", "aes128gcm");
        headers.put("TTL", String.valueOf(message.ttl().toSeconds()));
        headers.put("Urgency", "normal");
        headers.put("Topic", message.topic());
        headers.put(
                "Authorization",
                VapidSigner.authorization(
                        URI.create(subscription.endpoint()), opened.privateKey(), opened.publicKey(), subject(), now));
        return headers;
    }

    /**
     * How the push service reaches the operator: the installation's address where it is public
     * HTTPS, otherwise a mail address on its host, since the standard takes nothing else.
     */
    private String subject() {
        String base = api.baseUrl();
        if (base.startsWith("https://")) return base;
        return "mailto:push@" + URI.create(base).getHost();
    }
}

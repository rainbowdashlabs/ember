/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import dev.chojo.ember.api.refusal.PushRefusal;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import dev.chojo.ember.feature.push.entity.PushSubscription;
import dev.chojo.ember.feature.push.repository.PushOutboxRepository;
import dev.chojo.ember.feature.push.repository.PushSubscriptionRepository;
import dev.chojo.ember.feature.system.service.InstanceSettingsService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * The browsers an account receives push messages on, as the account's own settings manage them.
 *
 * <p>A browser is registered for the account and the sign-in it is used in, and only ever by that
 * sign-in: nobody can register a browser for somebody else, and the subscription ends with the
 * sign-in. Its address must be a public HTTPS push service, and its keys must be usable, both
 * checked before anything is stored. An account keeps at most {@link #MAX_DEVICES} browsers, so one
 * account cannot turn every notification into a flood of requests to some host it names.
 */
@Singleton
public class PushService {
    /** The longest name a browser is listed under. */
    static final int MAX_LABEL = 100;
    /** The longest push address taken; real ones are a few hundred characters. */
    static final int MAX_ENDPOINT = 2048;
    /** How many browsers one account keeps; registering another forgets the one used least recently. */
    static final int MAX_DEVICES = 20;

    private static final int AUTH_BYTES = 16;

    private final PushSubscriptionRepository subscriptions;
    private final PushOutboxRepository outbox;
    private final VapidKeys keys;
    private final RemoteUrlValidator urls;
    private final InstanceSettingsService instance;

    @Inject
    public PushService(
            PushSubscriptionRepository subscriptions,
            PushOutboxRepository outbox,
            VapidKeys keys,
            RemoteUrlValidator urls,
            InstanceSettingsService instance) {
        this.subscriptions = subscriptions;
        this.outbox = outbox;
        this.keys = keys;
        this.urls = urls;
        this.instance = instance;
    }

    /**
     * What a browser needs to subscribe.
     *
     * @param publicKey the installation's public key, base64url, which the browser subscribes with
     * @param enabled   whether this instance sends push messages at all
     */
    public record PushKey(String publicKey, boolean enabled) {}

    /**
     * A browser as its owner sees it.
     *
     * @param id            the device's id
     * @param label         the name it is listed under
     * @param createdAt     when it was registered
     * @param lastSuccessAt when a push last reached it
     * @param current       whether it is the browser asking
     */
    public record PushDevice(
            int id,
            String label,
            Instant createdAt,
            @Nullable Instant lastSuccessAt,
            boolean current) {}

    /** @return the key to subscribe with and whether push is on */
    public PushKey key() {
        return new PushKey(
                Base64.getUrlEncoder().withoutPadding().encodeToString(keys.publicKey()), instance.pushEnabled());
    }

    /**
     * Registers the asking browser, or refreshes it.
     *
     * @param endpoint the push service's address for the browser
     * @param p256dh   the browser's public key, base64url
     * @param auth     the browser's secret, base64url
     * @param label    the name to list it under
     * @return the device as listed
     */
    public PushDevice register(
            int accountId,
            int sessionId,
            @Nullable String endpoint,
            @Nullable String p256dh,
            @Nullable String auth,
            @Nullable String label) {
        if (!instance.pushEnabled()) throw PushRefusal.PUSH_TURNED_OFF.raise();
        if (endpoint == null || endpoint.isBlank() || p256dh == null || auth == null) {
            throw PushRefusal.SUBSCRIPTION_INCOMPLETE.raise();
        }
        if (endpoint.length() > MAX_ENDPOINT || !urls.isAllowed(endpoint.trim())) {
            throw PushRefusal.ENDPOINT_NOT_ALLOWED.raise();
        }
        byte[] publicKey = decoded(p256dh);
        byte[] secret = decoded(auth);
        try {
            EcP256.publicKeyOf(publicKey);
        } catch (IllegalArgumentException e) {
            throw PushRefusal.KEYS_NOT_USABLE.raise();
        }
        if (secret.length != AUTH_BYTES) throw PushRefusal.KEYS_NOT_USABLE.raise();
        var stored = subscriptions.register(accountId, sessionId, endpoint.trim(), publicKey, secret, labelOf(label));
        subscriptions.keepNewest(accountId, MAX_DEVICES);
        return device(stored, sessionId);
    }

    /** @return the account's devices, the asking one marked */
    public List<PushDevice> devices(int accountId, int sessionId) {
        return subscriptions.findByAccount(accountId).stream()
                .map(s -> device(s, sessionId))
                .toList();
    }

    /** Forgets one of the account's devices. */
    public void forget(int accountId, int id) {
        if (!subscriptions.deleteOwn(id, accountId)) throw PushRefusal.DEVICE_NOT_HERE.raise();
    }

    /** Queues a test push to one of the account's devices. */
    public void test(int accountId, int id) {
        if (!instance.pushEnabled()) throw PushRefusal.PUSH_TURNED_OFF.raise();
        var subscription = subscriptions.findOwn(id, accountId).orElseThrow(PushRefusal.DEVICE_NOT_HERE::raise);
        outbox.enqueueTest(subscription.id());
    }

    private static PushDevice device(PushSubscription subscription, int sessionId) {
        return new PushDevice(
                subscription.id(),
                subscription.label(),
                subscription.createdAt(),
                subscription.lastSuccessAt(),
                subscription.sessionId() == sessionId);
    }

    private static byte[] decoded(String value) {
        try {
            return Base64.getUrlDecoder()
                    .decode(value.trim().replace('+', '-').replace('/', '_').replace("=", ""));
        } catch (IllegalArgumentException e) {
            throw PushRefusal.KEYS_NOT_USABLE.raise();
        }
    }

    private static String labelOf(@Nullable String label) {
        if (label == null) return "";
        String trimmed = label.strip();
        return trimmed.length() > MAX_LABEL ? trimmed.substring(0, MAX_LABEL) : trimmed;
    }
}

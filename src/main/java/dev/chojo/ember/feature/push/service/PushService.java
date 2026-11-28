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
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * <p>A renewal is the page registering a browser again on its own, after the sign-in it was bound to ended or
     * the browser moved to a new push address. It is taken only for a browser this account allowed and has not
     * removed by hand since, or for a new address whose previous one was such a browser, which then replaces it.
     * Anything else needs the account to allow push explicitly: a browser somebody else allowed, on a computer
     * the next person signs in to, does not start pushing to them unasked.
     *
     * @param endpoint the push service's address for the browser
     * @param p256dh   the browser's public key, base64url
     * @param auth     the browser's secret, base64url
     * @param label    the name to list it under
     * @param renewal  whether the page renews the browser on its own rather than the account allowing it
     * @param previous the address the browser was registered under before it moved, for a renewal, or null
     * @return the device as listed, empty for a renewal this account did not allow
     */
    public Optional<PushDevice> register(
            int accountId,
            int sessionId,
            @Nullable String endpoint,
            @Nullable String p256dh,
            @Nullable String auth,
            @Nullable String label,
            boolean renewal,
            @Nullable String previous) {
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
        String address = endpoint.trim();
        return Transactions.call(() -> {
            if (renewal && !mayRenew(accountId, address, previous)) return Optional.empty();
            subscriptions.allow(accountId, address);
            var stored = subscriptions.register(accountId, sessionId, address, publicKey, secret, labelOf(label));
            subscriptions.keepNewest(accountId, MAX_DEVICES);
            return Optional.of(device(stored, sessionId));
        });
    }

    /**
     * Whether the page may register this browser again on its own: the account allowed it and did not remove it
     * since, or it moved from an address the account allowed, which it then takes the place of.
     */
    private boolean mayRenew(int accountId, String address, @Nullable String previous) {
        var own = subscriptions.allowed(accountId, address);
        if (own.isPresent()) return own.get();
        if (previous == null || previous.isBlank() || previous.trim().equals(address)) return false;
        String old = previous.trim();
        if (!subscriptions.allowed(accountId, old).orElse(false)) return false;
        subscriptions.forgetAddress(accountId, old);
        return true;
    }

    /** @return the account's devices, the asking one marked */
    public List<PushDevice> devices(int accountId, int sessionId) {
        return subscriptions.findByAccount(accountId).stream()
                .map(s -> device(s, sessionId))
                .toList();
    }

    /**
     * Which of the account's sign-ins receive pushes, so the list of sign-ins can show it.
     *
     * @return the device of each sign-in that has one, by sign-in id
     */
    public Map<Integer, Integer> deviceBySession(int accountId) {
        var bySession = new HashMap<Integer, Integer>();
        for (var subscription : subscriptions.findByAccount(accountId)) {
            bySession.putIfAbsent(subscription.sessionId(), subscription.id());
        }
        return bySession;
    }

    /**
     * Queues a new message as a push to the devices of the given accounts, those that want messages
     * pushed and are still signed in there. Nothing while the instance has push switched off.
     *
     * @param messageId the message
     * @param accountIds who should get it
     */
    public void queueMessage(int messageId, List<Integer> accountIds) {
        if (accountIds.isEmpty() || !instance.pushEnabled()) return;
        outbox.enqueueMessage(messageId, accountIds);
    }

    /** Forgets one of the account's devices, which the page then does not register again on its own. */
    public void forget(int accountId, int id) {
        if (!subscriptions.declineOwn(id, accountId)) throw PushRefusal.DEVICE_NOT_HERE.raise();
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

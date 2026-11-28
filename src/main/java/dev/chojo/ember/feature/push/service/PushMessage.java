/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.util.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;

/**
 * One push as the browser shows it.
 *
 * @param title    the notification's heading
 * @param body     its text
 * @param url      where a tap leads, on this installation
 * @param topicKey what the push is about; a later push with the same key replaces it on the device
 *                 and in the push service's queue
 * @param ttl      how long the push service keeps it for a browser that is offline
 */
public record PushMessage(String title, String body, String url, String topicKey, Duration ttl) {
    /** How long a push waits for an offline browser when nothing else is set. */
    public static final Duration DEFAULT_TTL = Duration.ofDays(1);

    private static final int TOPIC_CHARS = 32;
    /** A heading longer than a lock screen shows is cut, so the body keeps the room. */
    private static final int MAX_TITLE = 200;

    /**
     * The JSON the service worker reads, its body cut short enough that the whole fits one push.
     *
     * @return the bytes to encrypt, at most {@link PushPayloadCipher#MAX_PLAINTEXT_BYTES}
     * @throws IllegalArgumentException when even an empty body does not fit, which only an absurd
     *     address could cause
     */
    public byte[] payload() {
        String heading = NotificationText.truncateSnippet(title, MAX_TITLE);
        String text = body;
        byte[] json = json(heading, text);
        while (json.length > PushPayloadCipher.MAX_PLAINTEXT_BYTES && !text.isEmpty()) {
            int keep = text.length() - Math.max(json.length - PushPayloadCipher.MAX_PLAINTEXT_BYTES, 16);
            text = keep > 0 ? NotificationText.truncateSnippet(text, keep) : "";
            json = json(heading, text);
        }
        if (json.length > PushPayloadCipher.MAX_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException("A push to " + url.length() + " characters of address does not fit");
        }
        return json;
    }

    /**
     * The {@code Topic} header: a hash of the topic key in the 32 URL-safe characters push services
     * take, so a key of any length and alphabet can collapse pushes.
     */
    public String topic() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(topicKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, TOPIC_CHARS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK offers no SHA-256", e);
        }
    }

    private byte[] json(String heading, String text) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("title", heading);
        fields.put("body", text);
        fields.put("url", url);
        fields.put("tag", topic());
        return Json.MAPPER.writeValueAsString(fields).getBytes(StandardCharsets.UTF_8);
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.util.Sha256;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * How a message is recognised as one that has already been dealt with.
 *
 * <p>A message with no identifier of its own is not unusual in the wild, so one is made from the fields
 * of its envelope. Falling back to the hash of the attachments would be wrong here, because a mail that
 * arrives with nothing attached has no hash and would then look handled the moment a second empty one
 * came in, which would quietly swallow whatever came after.
 *
 * <p>The server's own numbering is deliberately not used for this. It is the cursor for what to fetch
 * and not the authority for what was done, because those numbers are reset by the server whenever it
 * feels like it.
 */
public final class MessageIdentity {
    private static final String DERIVED_PREFIX = "derived:";

    /**
     * A message's identity and whether it had to be invented.
     *
     * @param id      the identifier to record against the mailbox
     * @param derived whether it was made from the envelope rather than carried by the message
     */
    public record Identity(String id, boolean derived) {}

    private MessageIdentity() {}

    /**
     * The message's own identifier, or one made from its envelope.
     *
     * @param messageId  what the message called itself, or null
     * @param sender     who it came from
     * @param subject    what it was called
     * @param receivedAt when it arrived
     * @return the identity to record
     */
    public static Identity of(
            @Nullable String messageId, @Nullable String sender, @Nullable String subject, Instant receivedAt) {
        if (messageId != null && !messageId.isBlank()) {
            return new Identity(messageId.trim(), false);
        }
        String envelope = String.join(
                "\0",
                sender == null ? "" : sender,
                subject == null ? "" : subject,
                receivedAt == null ? "" : String.valueOf(receivedAt.getEpochSecond()));
        return new Identity(DERIVED_PREFIX + Sha256.hex(envelope), true);
    }

    /**
     * The hash of an attachment's bytes, which is the other duplicate key.
     *
     * <p>These same bytes already in this station are a duplicate whatever message carried them, which is
     * what catches a forward: a forwarded mail gets a new identifier, so a key needing both to match would
     * let it through.
     *
     * @param data the file as it arrived
     * @return the hash, in hex
     */
    public static String hashOf(byte[] data) {
        return Sha256.hex(data);
    }
}

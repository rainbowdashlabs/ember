/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import java.util.Locale;
import java.util.Set;

/**
 * Whether a refusal blames the relay rather than the message or the address.
 *
 * <p>A provider reports a soft bounce for anything it expects to pass later, and a receiving server
 * that has our sending address on a block list produces exactly that: a temporary-looking refusal
 * that will never pass, however often it is tried.
 *
 * <p>Deliberately hard to satisfy. The text has to name a block <em>and</em> name the sending side
 * as the thing blocked, so "mailbox full" and "message rejected as spam" are left alone. Shutting a
 * provider out of a whole domain is worth doing only when the receiving side has said plainly that
 * it is the sender it refuses.
 */
public final class RelayBlockDetector {

    private RelayBlockDetector() {}

    /** Words for the act of refusing a sender outright. */
    private static final Set<String> BLOCK_WORDS =
            Set.of("blacklist", "blocklist", "block list", "denylist", "deny list", "blocked", "banned", "rbl");

    /** Words that put the refusal on our side of the exchange rather than the reader's. */
    private static final Set<String> SENDER_WORDS =
            Set.of("sender ip", "sending ip", "sender address", "sender", "relay", "your ip", "client host", "helo");

    /**
     * Whether this reason says the relay itself is refused.
     *
     * @param detail what the receiving side gave as the reason, or null
     */
    public static boolean blamesTheRelay(String detail) {
        if (detail == null || detail.isBlank()) return false;
        String text = detail.toLowerCase(Locale.ROOT);
        return BLOCK_WORDS.stream().anyMatch(text::contains)
                && SENDER_WORDS.stream().anyMatch(text::contains);
    }
}

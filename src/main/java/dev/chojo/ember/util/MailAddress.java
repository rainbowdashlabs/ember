/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;

/**
 * The check an address gets before a relay is asked to carry a message to it.
 *
 * <p>Deliberately shallow: whether an address exists is something only the receiving side knows,
 * and a stricter pattern would refuse addresses that are perfectly valid. This catches the typo
 * and the empty field, and leaves the rest to the provider.
 */
public final class MailAddress {

    private MailAddress() {}

    /**
     * Returns the address with surrounding space removed.
     *
     * @param value   the address as it was typed
     * @param refusal what the caller refuses with when the value is no address, naming the value
     * @return the address, trimmed
     * @throws RefusalResponse when the value is plainly not an address
     */
    public static String require(String value, Refusal refusal) {
        String trimmed = value == null ? "" : value.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0 || at == trimmed.length() - 1 || trimmed.contains(" ")) {
            throw refusal.raise(trimmed);
        }
        return trimmed;
    }
}

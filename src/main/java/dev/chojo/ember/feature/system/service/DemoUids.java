/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Deterministic UUID helpers for demo-mode entities. Every restart wipes and re-seeds the
 * demo database, and station-scoped storage paths embed entity UUIDs ({@code station/<uid>/...},
 * {@code account/<uid>/...}); without stable UUIDs the local backend would accumulate one
 * orphaned demo dataset per restart. UUID v3 (name-based) gives the same value for the same
 * input string, so every seed run writes to the same on-disk paths and {@code store(...)} just
 * overwrites the previous bytes in place.
 */
public final class DemoUids {

    private static final String NAMESPACE = "ember-demo:";

    private DemoUids() {}

    public static UUID station(String key) {
        return UUID.nameUUIDFromBytes((NAMESPACE + "station:" + key).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID account(String email) {
        return UUID.nameUUIDFromBytes((NAMESPACE + "account:" + email).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID member(String email, int stationId) {
        return UUID.nameUUIDFromBytes(
                (NAMESPACE + "member:" + email + "@" + stationId).getBytes(StandardCharsets.UTF_8));
    }
}

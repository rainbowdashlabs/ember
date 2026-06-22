/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import java.util.UUID;

/**
 * Scope of a stored object. One of {@link Instance}, {@link Station}, or {@link Account};
 * every {@link StorageCategory} fixes the kind of scope it expects.
 *
 * <p>The scope contributes the first path segment of the backend key
 * ({@code inst/}, {@code station/<uid>/}, {@code account/<uid>/}). See concept §4.2.
 */
public sealed interface StorageScope {

    /** Returns the path prefix this scope contributes (without trailing slash). */
    String prefix();

    /** Returns the discriminator. */
    Kind kind();

    /** Instance-wide objects (logo fragments, app logos, documents, discovery key, tile cache, demo avatars). */
    record Instance() implements StorageScope {
        @Override
        public String prefix() {
            return "inst";
        }

        @Override
        public Kind kind() {
            return Kind.INSTANCE;
        }
    }

    /** Station-scoped objects (page files, KB files, board attachments, station images). */
    record Station(int stationId, UUID stationUid) implements StorageScope {
        @Override
        public String prefix() {
            return "station/" + stationUid;
        }

        @Override
        public Kind kind() {
            return Kind.STATION;
        }
    }

    /** Account-scoped objects (user avatars — follow the user across stations). */
    record Account(UUID accountUid) implements StorageScope {
        @Override
        public String prefix() {
            return "account/" + accountUid;
        }

        @Override
        public Kind kind() {
            return Kind.ACCOUNT;
        }
    }

    /** Type-level discriminator for compatibility checks against {@link StorageCategory#scopeKind()}. */
    enum Kind {
        INSTANCE,
        STATION,
        ACCOUNT
    }
}

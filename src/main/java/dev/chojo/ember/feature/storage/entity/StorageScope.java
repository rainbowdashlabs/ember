/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import java.util.UUID;

/**
 * Scope of a stored object. One of {@link Instance}, {@link Station}, {@link Association} or
 * {@link Account}; every {@link StorageCategory} fixes the kind of scope it expects.
 *
 * <p>The scope contributes the first path segment of the backend key
 * ({@code inst/}, {@code station/<uid>/}, {@code station/<home uid>/association/}, {@code account/<uid>/}).
 */
public sealed interface StorageScope {

    /**
     * Returns the path prefix this scope contributes (without trailing slash).
     */
    String prefix();

    /**
     * Returns the discriminator.
     */
    Kind kind();

    /**
     * Type-level discriminator for compatibility checks against {@link StorageCategory#scopeKind()}.
     */
    enum Kind {
        INSTANCE,
        STATION,
        ASSOCIATION,
        ACCOUNT
    }

    /**
     * Instance-wide objects (logo fragments, app logos, documents, discovery key, tile cache, demo avatars).
     */
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

    /**
     * Station-scoped objects (page files, KB files, board attachments, station images).
     */
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

    /**
     * An association's own objects (its fonts), kept in the store of the station shell it owns.
     *
     * <p>An association keeps its files where it keeps everything else: in its home station. Its
     * objects therefore lie under that station's prefix, on whatever backend the station stands on,
     * travel with the station when its bytes move, and count against the room the association was given
     * for its home station.
     *
     * @param homeStationId  the association's home station
     * @param homeStationUid that station's uid
     */
    record Association(int homeStationId, UUID homeStationUid) implements StorageScope {
        @Override
        public String prefix() {
            return "station/" + homeStationUid + "/association";
        }

        @Override
        public Kind kind() {
            return Kind.ASSOCIATION;
        }

        /** @return the home station as a scope, whose backend and room the association shares */
        public Station home() {
            return new Station(homeStationId, homeStationUid);
        }
    }

    /**
     * Account-scoped objects (user avatars - follow the user across stations).
     */
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
}

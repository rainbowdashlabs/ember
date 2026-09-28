/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.repository;

import jakarta.inject.Singleton;

import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * What the database server itself says about itself, as opposed to anything stored in it.
 */
@Singleton
public class DatabaseServerRepository {

    /**
     * The major version of the PostgreSQL server, such as 18.
     */
    public int majorVersion() {
        return query("SELECT current_setting('server_version_num')::int / 10000 AS major;")
                .single()
                .map(row -> row.getInt("major"))
                .first()
                .orElseThrow();
    }
}

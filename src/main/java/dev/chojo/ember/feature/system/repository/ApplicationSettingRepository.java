/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.repository;

import jakarta.inject.Singleton;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class ApplicationSettingRepository {

    public Optional<String> get(String key) {
        return query("SELECT value FROM application_setting WHERE key = :key;")
                .single(call().bind("key", key))
                .map(row -> row.getString("value"))
                .first();
    }

    public void set(String key, String value) {
        query("""
                INSERT
                INTO
                    application_setting
                    (key, value)
                VALUES
                    (:key, :value)
                ON CONFLICT (key)
                    DO UPDATE
                    SET
                        value = :value;""").single(call().bind("key", key).bind("value", value)).insert();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return get(key).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    public void setBoolean(String key, boolean value) {
        set(key, String.valueOf(value));
    }
}

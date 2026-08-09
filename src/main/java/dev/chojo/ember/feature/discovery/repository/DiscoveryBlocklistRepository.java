/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.repository;

import dev.chojo.ember.feature.discovery.entity.BlocklistKind;
import dev.chojo.ember.feature.discovery.entity.DiscoveryBlocklistEntry;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.util.sql.SqlSupport.exists;

@Singleton
public class DiscoveryBlocklistRepository {
    private static final String DISCOVERY_BLOCKLIST_COLUMNS = "value, kind, note, created_at";

    public List<DiscoveryBlocklistEntry> findAll() {
        return query("SELECT %s FROM discovery_blocklist ORDER BY created_at DESC;", DISCOVERY_BLOCKLIST_COLUMNS)
                .single(call())
                .map(DiscoveryBlocklistEntry.map())
                .all();
    }

    public boolean contains(BlocklistKind kind, String value) {
        return exists(
                "SELECT 1 FROM discovery_blocklist WHERE kind = :kind AND value = :value;",
                call().bind("kind", kind.name()).bind("value", value));
    }

    public void add(BlocklistKind kind, String value, String note) {
        query("""
                INSERT INTO discovery_blocklist (value, kind, note)
                VALUES (:value, :kind, :note)
                ON CONFLICT (value) DO UPDATE
                SET kind = excluded.kind, note = excluded.note;""")
                .single(call().bind("value", value).bind("kind", kind.name()).bind("note", note))
                .insert();
    }

    public boolean remove(String value) {
        return query("DELETE FROM discovery_blocklist WHERE value = :value;")
                .single(call().bind("value", value))
                .delete()
                .changed();
    }
}

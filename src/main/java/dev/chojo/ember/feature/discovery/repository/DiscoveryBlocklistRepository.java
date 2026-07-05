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

@Singleton
public class DiscoveryBlocklistRepository {

    public List<DiscoveryBlocklistEntry> findAll() {
        return query("SELECT * FROM discovery_blocklist ORDER BY created_at DESC;")
                .single(call())
                .map(DiscoveryBlocklistEntry.map())
                .all();
    }

    public boolean contains(BlocklistKind kind, String value) {
        return query("SELECT 1 FROM discovery_blocklist WHERE kind = :kind AND value = :value;")
                .single(call().bind("kind", kind.name()).bind("value", value))
                .map(_ -> true)
                .first()
                .orElse(false);
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

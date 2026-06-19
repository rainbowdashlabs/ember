/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record BackupCode(int id, int factorId, String codeHash, Instant usedAt, String usedViaIp) {

    public static RowMapping<BackupCode> map() {
        return row -> new BackupCode(
                row.getInt("id"),
                row.getInt("factor_id"),
                row.getString("code_hash"),
                row.get("used_at", INSTANT_TIMESTAMP),
                row.getString("used_via_ip"));
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record TotpFactor(
        int factorId, byte[] secretEncrypted, short secretKid, short digits, short periodSeconds, String algorithm) {

    public static RowMapping<TotpFactor> map() {
        return row -> new TotpFactor(
                row.getInt("factor_id"),
                row.getBytes("secret_encrypted"),
                row.getShort("secret_kid"),
                row.getShort("digits"),
                row.getShort("period_seconds"),
                row.getString("algorithm"));
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A column whose value identifies a person for GDPR purposes.
 *
 * @param type   what kind of identifier the column holds
 * @param column the column name (or {@code table.column} for joined references in file stores)
 * @param filter optional SQL fragment to scope the rows (e.g. {@code version = 1})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentityColumn(IdentityType type, String column, String filter) {
    public IdentityColumn(IdentityType type, String column) {
        this(type, column, null);
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A column whose value identifies a person for GDPR purposes.
 *
 * <p>A row can name several people, and some of its columns may be one person's data only, such as the
 * device a guardian confirmed a signature with on behalf of a child. Those columns are withheld from the
 * export of a person the row is found for through this column, and reach only the export of somebody it
 * is found for through another identity column that does not withhold them.
 *
 * @param type            what kind of identifier the column holds
 * @param column          the column name (or {@code table.column} for joined references in file stores)
 * @param filter          optional SQL fragment to scope the rows (e.g. {@code version = 1})
 * @param withheldColumns columns of the row that the person found through this column does not get, or
 *                        null for none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentityColumn(
        IdentityType type,
        String column,
        @Nullable String filter,
        @Nullable List<String> withheldColumns) {
    public IdentityColumn(IdentityType type, String column) {
        this(type, column, null, null);
    }

    /**
     * @param column a column of the row
     * @return whether the person found through this identity column does not get it
     */
    public boolean withholds(String column) {
        return Objects.requireNonNullElse(withheldColumns, List.<String>of()).contains(column);
    }
}

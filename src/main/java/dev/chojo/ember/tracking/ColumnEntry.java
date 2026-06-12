/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single column in a tracked table.
 *
 * @param name        PostgreSQL column name
 * @param type        PostgreSQL data type (e.g. {@code int4}, {@code text}, {@code timestamptz})
 * @param nullable    whether the column allows NULL values
 * @param verified    whether a reviewer has confirmed the export/import/GDPR coverage for this column
 * @param description {@code COMMENT ON COLUMN} text mirrored from the live schema. Intentionally excluded
 *                    from {@link HashComputer} so editing a comment does not flip verification flags.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ColumnEntry(String name, String type, boolean nullable, boolean verified, String description) {

    /** Backwards-compatible constructor for callers that don't supply a description. */
    public ColumnEntry(String name, String type, boolean nullable, boolean verified) {
        this(name, type, nullable, verified, null);
    }
}

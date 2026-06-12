/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

/**
 * A join-flattened field emitted alongside a table's own columns by the
 * generic export engine. Follows one of the table's foreign keys to a
 * referenced row and copies a single column into the output.
 *
 * <p>Example: on {@code station_member} a lookup {@code via=account_id, pick=email, emitAs=account_email}
 * produces a {@code account_email} field in the exported row by joining
 * {@code account} on {@code station_member.account_id = account.id} and reading
 * {@code account.email}.
 *
 * @param via     local FK column to follow (must match a {@link ForeignKey#column()} entry on the table)
 * @param pick    column on the referenced table to read
 * @param emitAs  field name to emit in the exported row
 */
public record Lookup(String via, String pick, String emitAs) {}

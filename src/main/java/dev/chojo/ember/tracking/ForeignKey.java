/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

/**
 * Foreign-key metadata used by the generic export/import engine to derive
 * scope paths and ID-remap targets.
 *
 * @param column    the column on the owning table
 * @param refTable  the referenced table
 * @param refColumn the referenced column
 * @param onDelete  the ON DELETE rule (CASCADE, SET NULL, NO ACTION, ...)
 */
public record ForeignKey(String column, String refTable, String refColumn, String onDelete) {}

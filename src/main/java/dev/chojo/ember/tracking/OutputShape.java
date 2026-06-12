/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

/**
 * How a TRACKED table's rows are projected into the export wire.
 *
 * <ul>
 *   <li>{@link #ROWS} — default; emit a {@code List<Map<column,value>>}.</li>
 *   <li>{@link #SINGLE} — emit the first row as a single object (or omit if no row exists). Used for
 *       tables with one row per station such as {@code station}.</li>
 *   <li>{@link #FLAT} — emit a flat list of values from {@link TableEntry#flatField()}. Used for
 *       enum-style child tables like {@code station_disabled_module} whose payload is a single column.</li>
 * </ul>
 */
public enum OutputShape {
    ROWS,
    SINGLE,
    FLAT
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The column widths of the field table, shared so the header and the rows cannot drift apart.
 *
 * <p>The sixth column carries either one switch or the choice of who may change an answer, and a
 * choice needs room a switch does not. Both spellings are written out rather than assembled, because
 * Tailwind reads these files as text and never sees a class that was built at runtime.
 */
export const FIELD_GRID
    = 'grid grid-cols-[2rem_1fr_6rem_3rem_2.5rem_2.5rem_2.5rem_2.5rem_2.5rem_5rem]'

export const FIELD_GRID_WRITABILITY
    = 'grid grid-cols-[2rem_1fr_6rem_3rem_2.5rem_7.5rem_2.5rem_2.5rem_2.5rem_5rem]'

export function fieldGrid(writability: boolean): string {
    return writability ? FIELD_GRID_WRITABILITY : FIELD_GRID
}

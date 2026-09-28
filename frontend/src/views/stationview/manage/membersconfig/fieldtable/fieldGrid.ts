/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The column widths of the question table, shared so the header and the rows cannot drift apart.
 *
 * <p>The fifth column carries either one switch or the choice of who may change an answer, and a
 * choice needs room a switch does not. Both spellings are written out rather than assembled, because
 * Tailwind reads these files as text and never sees a class that was built at runtime.
 */
export const FIELD_GRID
    = 'grid grid-cols-[1fr_6rem_3rem_2.5rem_2.5rem_2.5rem_2.5rem_2.5rem_5rem]'

export const FIELD_GRID_WRITABILITY
    = 'grid grid-cols-[1fr_6rem_3rem_2.5rem_11rem_2.5rem_2.5rem_2.5rem_5rem]'

/** The narrowest the name column may become before the names stop being readable. */
export const NAME_MIN_REM = 10

/** The horizontal padding of a row, one `px-1` on either side. */
const ROW_PADDING_REM = 0.5

const REM_COLUMN = /(\d+(?:\.\d+)?)rem/g

export function fieldGrid(writability: boolean): string {
    return writability ? FIELD_GRID_WRITABILITY : FIELD_GRID
}

/**
 * The width in rem below which the table no longer fits and the questions are shown as tiles.
 *
 * <p>Read from the grid classes themselves: every column of a fixed width, plus a readable name and
 * the padding of the row. A column added to the grid therefore moves the threshold with it.
 */
export function fieldGridMinWidthRem(writability: boolean): number {
    const fixedColumns = [...fieldGrid(writability).matchAll(REM_COLUMN)]
        .reduce((sum, match) => sum + Number(match[1]), 0)
    return fixedColumns + NAME_MIN_REM + ROW_PADDING_REM
}

/** Whether a table as wide as `widthRem` has room for every column of the grid. */
export function fitsFieldGrid(widthRem: number, writability: boolean): boolean {
    return widthRem >= fieldGridMinWidthRem(writability)
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getItem, setItem} from '@/api/storage'

/** Which columns of one table a reader turned on or off, by column key. Unnamed columns keep their default. */
export type ColumnChoices = Record<string, boolean>

/**
 * The one stored value holding every table's choices, by table.
 *
 * <p>One value rather than one per table, so the disclosure names it once and a table added later
 * is covered by the consent already given instead of asking for a new one.
 */
const STORAGE_KEY = 'table_columns'

function isChoices(value: unknown): value is ColumnChoices {
    return !!value && typeof value === 'object' && !Array.isArray(value)
        && Object.values(value).every(shown => typeof shown === 'boolean')
}

function readAll(): Record<string, ColumnChoices> {
    try {
        const parsed: unknown = JSON.parse(getItem(STORAGE_KEY) ?? '{}')
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
        return Object.fromEntries(Object.entries(parsed).filter(([, choices]) => isChoices(choices)))
    } catch {
        return {}
    }
}

/**
 * The columns a reader chose for one table, as this browser remembers them.
 *
 * <p>Kept in the browser rather than on the account: it is a way of looking at a list, not
 * something anybody else should see change. Anything unreadable is treated as no choice at all,
 * because a table falling back to its defaults is harmless and a table that will not draw is not.
 *
 * @param table names the table, including whatever it belongs to, such as the station
 */
export function loadColumnChoices(table: string): ColumnChoices {
    return readAll()[table] ?? {}
}

/**
 * Remembers the columns a reader chose for one table, where the reader allowed storing such
 * comforts. Without that the choice holds until the page is left.
 */
export function saveColumnChoices(table: string, choices: ColumnChoices) {
    try {
        setItem(STORAGE_KEY, JSON.stringify({...readAll(), [table]: choices}))
    } catch {
        return
    }
}

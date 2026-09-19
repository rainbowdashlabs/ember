/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** Which columns of one table a reader turned on or off, by column key. Unnamed columns keep their default. */
export type ColumnChoices = Record<string, boolean>

const PREFIX = 'tableColumns:'

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
    if (typeof window === 'undefined') return {}
    try {
        const parsed: unknown = JSON.parse(window.localStorage.getItem(PREFIX + table) ?? '{}')
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
        return Object.fromEntries(Object.entries(parsed).filter(([, shown]) => typeof shown === 'boolean'))
    } catch {
        return {}
    }
}

/** Remembers the columns a reader chose for one table. A browser refusing to store them loses nothing else. */
export function saveColumnChoices(table: string, choices: ColumnChoices) {
    if (typeof window === 'undefined') return
    try {
        window.localStorage.setItem(PREFIX + table, JSON.stringify(choices))
    } catch {
        return
    }
}

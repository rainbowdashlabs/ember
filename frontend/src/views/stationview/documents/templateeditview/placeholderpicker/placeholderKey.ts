/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** A key as a template writes it: the key of a value, and for a date the format after a bar. */
export interface PlaceholderKey {
    base: string
    format: string | null
}

/** A placeholder as the picker hands it on: the key to write, a date with its format, and what the chip is called. */
export interface PlaceholderChoice {
    key: string
    label: string
}

/** What stands between the key of a value and its format, as in `member.birthDate|long`. */
export const FORMAT_SEPARATOR = '|'

/**
 * The two keys of the first letter templates that named a date in one format each. Templates saved with
 * them keep working on the server, and the editor names them like the date in that format.
 */
const EARLIER_KEYS: ReadonlyMap<string, PlaceholderKey> = new Map([
    ['member.joinDate.monthYear', {base: 'member.joinDate', format: 'monthYear'}],
    ['today.long', {base: 'today', format: 'long'}],
])

/**
 * @param written a key as a template writes it
 * @returns the key read into the key of its value and its format
 */
export function parsePlaceholderKey(written: string): PlaceholderKey {
    const earlier = EARLIER_KEYS.get(written)
    if (earlier) return earlier
    const bar = written.indexOf(FORMAT_SEPARATOR)
    if (bar < 0) return {base: written, format: null}
    return {base: written.slice(0, bar), format: written.slice(bar + 1)}
}

/**
 * @param base   the key of a value
 * @param format its format, or null for none
 * @returns the key as a template writes it
 */
export function writtenPlaceholderKey(base: string, format: string | null): string {
    return format === null ? base : `${base}${FORMAT_SEPARATOR}${format}`
}

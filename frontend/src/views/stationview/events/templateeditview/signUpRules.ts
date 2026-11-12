/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** How long before the start a newly set deadline closes the registration, until somebody changes it. */
export const DEFAULT_DEADLINE_OFFSET_HOURS = 24

/**
 * A count typed into a number field, as a whole number of zero or more, or undefined for a field
 * left empty.
 *
 * <p>The server refuses a negative count, and a number field hands over an empty string when it is
 * cleared, so neither is passed on as it came.
 *
 * @param value what the field holds
 */
export function nonNegative(value: unknown): number | undefined {
    if (typeof value !== 'number' || Number.isNaN(value)) return undefined
    return Math.max(0, Math.trunc(value))
}

/**
 * A deadline offset in hours, split into the whole days and the hours left over that the editor shows.
 *
 * @param offsetHours hours before the start
 */
export function offsetParts(offsetHours: number): {days: number; hours: number} {
    return {days: Math.floor(offsetHours / 24), hours: offsetHours % 24}
}

/**
 * Days and hours before the start, as the hours the server stores.
 *
 * @param days  whole days before the start
 * @param hours hours on top of the days
 */
export function offsetHoursOf(days: unknown, hours: unknown): number {
    return (nonNegative(days) ?? 0) * 24 + (nonNegative(hours) ?? 0)
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
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

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** The smallest size in pixels selected words can be set in. */
export const SMALLEST_TEXT_SIZE = 6

/** The largest size in pixels selected words can be set in. */
export const LARGEST_TEXT_SIZE = 96

/** The size in pixels of rendered text without one of its own, the body size of `.markdown-content`. */
export const NORMAL_TEXT_SIZE = 15

/**
 * A size in pixels as typed or as stored, when it is one words can be set in.
 *
 * @param input what was typed, or the number of pixels a stored style sets: a whole number, surrounding
 *              blanks ignored
 * @returns the size in whole pixels, or null for anything else, a size out of bounds among it
 */
export function pixelSize(input: string | number | null | undefined): number | null {
    const text = String(input ?? '').trim()
    if (!/^\d{1,3}$/.test(text)) return null
    const size = Number(text)
    return size >= SMALLEST_TEXT_SIZE && size <= LARGEST_TEXT_SIZE ? size : null
}

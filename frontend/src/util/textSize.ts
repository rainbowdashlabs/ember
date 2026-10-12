/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** The smallest size in points selected words can be set in. */
export const SMALLEST_TEXT_SIZE = 5

/** The largest size in points selected words can be set in. */
export const LARGEST_TEXT_SIZE = 72

/** The size in points of rendered text without one of its own, the body size of `.markdown-content`. */
export const NORMAL_TEXT_SIZE = 11

/** How many points one CSS pixel is: a pixel is 1/96 inch, a point 1/72. */
const POINTS_PER_PIXEL = 0.75

/**
 * A size in points as typed or as stored, when it is one words can be set in.
 *
 * @param input what was typed, or the number of points a stored style sets: a whole number, surrounding
 *              blanks ignored
 * @returns the size in whole points, or null for anything else, a size out of bounds among it
 */
export function pointSize(input: string | number | null | undefined): number | null {
    const text = String(input ?? '').trim()
    if (!/^\d{1,3}$/.test(text)) return null
    const size = Number(text)
    return size >= SMALLEST_TEXT_SIZE && size <= LARGEST_TEXT_SIZE ? size : null
}

/**
 * The size in whole points a size in pixels comes closest to, for words stored in pixels before sizes
 * were written in points, and for a size measured on screen.
 *
 * @param pixels a size in CSS pixels
 * @returns the size in whole points, or null where it is out of bounds
 */
export function pointsOfPixels(pixels: number): number | null {
    return Number.isFinite(pixels) ? pointSize(Math.round(pixels * POINTS_PER_PIXEL)) : null
}

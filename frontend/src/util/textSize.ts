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

const SIZED_SPAN =/<span data-size="(\d+)">/g

/**
 * A size in pixels as typed or as stored, when it is one words can be set in.
 *
 * @param input what was typed or read from `data-size`: a whole number, surrounding blanks ignored
 * @returns the size in whole pixels, or null for anything else, a size out of bounds among it
 */
export function pixelSize(input: string | number | null | undefined): number | null {
    const text = String(input ?? '').trim()
    if (!/^\d{1,3}$/.test(text)) return null
    const size = Number(text)
    return size >= SMALLEST_TEXT_SIZE && size <= LARGEST_TEXT_SIZE ? size : null
}

/**
 * Gives every sized span of sanitised HTML the font size it names.
 *
 * <p>The words are stored as `<span data-size="14">`, and a stylesheet cannot read a number out of an
 * attribute in every browser yet. So the renderer adds the style itself, after sanitising, and only to
 * a span written exactly the way the editor stores it with a size within bounds: the style carries
 * nothing but that number, and nothing an author writes reaches it.
 */
export function sizeTextSpans(html: string): string {
    return html.replace(SIZED_SPAN, (span, written: string) => {
        const size = pixelSize(written)
        return size ? `<span data-size="${size}" style="font-size: ${size}px">` : span
    })
}

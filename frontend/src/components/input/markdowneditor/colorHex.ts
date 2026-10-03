/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const HEX = /^#?([0-9a-f]{3}|[0-9a-f]{6})$/i
const RGB = /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*[,)]/i

/**
 * A colour typed as a hex code, as the six lower-case digits it is stored with.
 *
 * @param input what was typed: `#rgb` or `#rrggbb`, the hash optional, surrounding blanks ignored
 * @returns the colour as `#rrggbb`, or null where the input is no hex code
 */
export function hexColor(input: string): string | null {
    const digits = HEX.exec(input.trim())?.[1]?.toLowerCase()
    if (!digits) return null
    return `#${digits.length === 3 ? [...digits].map(digit => digit + digit).join('') : digits}`
}

/**
 * A colour as the browser or the editor hands it over, as `#rrggbb`.
 *
 * <p>A style read back from the page comes as `rgb(r, g, b)` whatever it was written as, and the
 * picker of the browser and the stored markdown both want the hex code.
 *
 * @param color a hex code or an `rgb()` colour
 * @returns the colour as `#rrggbb`, or null for anything else, a named colour among them
 */
export function asHex(color: string | null | undefined): string | null {
    if (!color) return null
    const hex = hexColor(color)
    if (hex) return hex
    const channels = RGB.exec(color.trim())?.slice(1, 4)
    if (!channels) return null
    return `#${channels.map(channel => Math.min(255, Number(channel)).toString(16).padStart(2, '0')).join('')}`
}

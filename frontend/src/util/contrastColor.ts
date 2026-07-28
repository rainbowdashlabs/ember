/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
function linearize(channel: number): number {
    // Per WCAG: channel must be normalized to 0..1 first, then gamma-corrected.
    const c = channel / 255
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
}

export function relativeLuminance(r: number, g: number, b: number): number {
    return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
}

/**
 * Parses a CSS color string in the form `rgb(...)` / `rgba(...)` (as returned by
 * {@link getComputedStyle}) into 0..255 channels. Returns `null` if it doesn't look like one.
 */
export function parseRgbString(css: string): [number, number, number] | null {
    const match = css.match(/[\d.]+/g)
    if (!match) return null
    const [r, g, b] = match
    if (r === undefined || g === undefined || b === undefined) return null
    return [Number(r), Number(g), Number(b)]
}

/**
 * Parses a CSS hex color (`#RGB`, `#RRGGBB`, with or without leading `#`) into 0..255 channels.
 * Returns `null` if the input doesn't look like a hex color.
 */
export function parseHexColor(hex: string): [number, number, number] | null {
    if (!hex) return null
    const clean = hex.trim().replace(/^#/, '')
    const expanded = clean.length === 3 ? clean.replace(/./g, channel => channel + channel) : clean
    if (!/^[\da-f]{6}$/i.test(expanded)) return null
    const value = parseInt(expanded, 16)
    return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff]
}

/**
 * Returns either `#1a1a1a` (near-black) or `#ffffff` depending on which gives better contrast
 * against the given 0..255 sRGB background. Threshold chosen to match the visual feel of
 * {@link BaseBadge}; do not tweak in isolation.
 */
export function contrastingTextColor(r: number, g: number, b: number): string {
    const lum = relativeLuminance(r, g, b)
    return lum > 0.4 ? '#1a1a1a' : '#ffffff'
}

/**
 * Convenience wrapper: takes a hex color and returns the contrasting text color, or `null` if
 * the input cannot be parsed.
 */
export function contrastingTextColorForHex(hex: string): string | null {
    const rgb = parseHexColor(hex)
    if (!rgb) return null
    return contrastingTextColor(rgb[0], rgb[1], rgb[2])
}

/**
 * Convenience wrapper: takes a CSS rgb(...) string (e.g. from {@link getComputedStyle}) and
 * returns the contrasting text color, or `null` if it cannot be parsed.
 */
export function contrastingTextColorForRgbString(css: string): string | null {
    const rgb = parseRgbString(css)
    if (!rgb) return null
    return contrastingTextColor(rgb[0], rgb[1], rgb[2])
}

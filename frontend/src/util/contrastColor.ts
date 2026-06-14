/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
export function relativeLuminance(r: number, g: number, b: number): number {
    return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
}

/**
 * Parses a CSS color string in the form `rgb(...)` / `rgba(...)` (as returned by
 * {@link getComputedStyle}) into 0..255 channels. Returns `null` if it doesn't look like one.
 */
export function parseRgbString(css: string): [number, number, number] | null {
    const match = css.match(/[\d.]+/g)
    if (!match || match.length < 3) return null
    const [r, g, b] = match.map(Number)
    return [r, g, b]
}

/**
 * Parses a CSS hex color (`#RGB`, `#RRGGBB`, with or without leading `#`) into 0..255 channels.
 * Returns `null` if the input doesn't look like a hex color.
 */
export function parseHexColor(hex: string): [number, number, number] | null {
    if (!hex) return null
    const clean = hex.trim().replace(/^#/, '')
    if (clean.length === 3) {
        const r = parseInt(clean[0] + clean[0], 16)
        const g = parseInt(clean[1] + clean[1], 16)
        const b = parseInt(clean[2] + clean[2], 16)
        if ([r, g, b].some(isNaN)) return null
        return [r, g, b]
    }
    if (clean.length === 6) {
        const r = parseInt(clean.slice(0, 2), 16)
        const g = parseInt(clean.slice(2, 4), 16)
        const b = parseInt(clean.slice(4, 6), 16)
        if ([r, g, b].some(isNaN)) return null
        return [r, g, b]
    }
    return null
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

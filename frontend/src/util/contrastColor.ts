/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** An opaque colour in 0..255 sRGB channels. */
export type Rgb = [number, number, number]

/** A colour in 0..255 sRGB channels with an alpha between 0 and 1. */
export type Rgba = [number, number, number, number]

function linearize(channel: number): number {
    const c = channel / 255
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
}

export function relativeLuminance(r: number, g: number, b: number): number {
    return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
}

function gammaEncode(channel: number): number {
    const encoded = channel <= 0.0031308
        ? 12.92 * channel
        : 1.055 * Math.pow(Math.max(channel, 0), 1 / 2.4) - 0.055
    return Math.round(Math.min(1, Math.max(0, encoded)) * 255)
}

/**
 * Converts an Oklab triple into 0..255 sRGB channels, clamped to the gamut.
 *
 * <p>A browser answers with Oklab whenever a colour has been through a mix, which is what an
 * opacity modifier on a utility class compiles to. Reading such an answer as though it were
 * `rgb(...)` mistakes a lightness of 0.82 for a red channel of 0.82 and calls the brightest
 * colour in the palette near black.
 */
function oklabToRgb(lightness: number, a: number, b: number): Rgb {
    const l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3
    const m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3
    const s = (lightness - 0.0894841775 * a - 1.2914855480 * b) ** 3
    return [
        gammaEncode(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
        gammaEncode(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
        gammaEncode(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
    ]
}

function componentValue(token: string, percentBase: number): number {
    if (token === 'none') return 0
    if (token.endsWith('%')) return (parseFloat(token) / 100) * percentBase
    return parseFloat(token)
}

function alphaValue(token: string | undefined): number {
    if (token === undefined || token === 'none') return 1
    const parsed = componentValue(token, 1)
    return Number.isNaN(parsed) ? 1 : Math.min(1, Math.max(0, parsed))
}

interface ColorFunction {
    name: string
    parts: string[]
    slashAlpha: string | undefined
}

function splitColorFunction(css: string): ColorFunction | null {
    const match = css.match(/^([a-z]+)\(([^)]*)\)$/i)
    if (!match) return null
    const [body, alpha] = match[2]!.split('/')
    return {
        name: match[1]!.toLowerCase(),
        parts: body!.trim().split(/[\s,]+/).filter(Boolean),
        slashAlpha: alpha?.trim(),
    }
}

/**
 * Parses a CSS hex color (`#RGB`, `#RRGGBB`, with or without leading `#`) into 0..255 channels.
 * Returns `null` if the input doesn't look like a hex color.
 */
export function parseHexColor(hex: string): Rgb | null {
    if (!hex) return null
    const clean = hex.trim().replace(/^#/, '')
    const expanded = clean.length === 3 ? clean.replace(/./g, channel => channel + channel) : clean
    if (!/^[\da-f]{6}$/i.test(expanded)) return null
    const value = parseInt(expanded, 16)
    return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff]
}

/**
 * Parses any colour {@link getComputedStyle} hands back into sRGB channels and an alpha.
 *
 * <p>Covers the forms a browser serialises in practice: `rgb()` and `rgba()`, the `oklab()` a mix
 * resolves to, the `oklch()` the utility palette is written in, a hex literal, and `transparent`.
 * Anything else returns `null`, which reads as "nothing is painted here".
 */
export function parseCssColor(css: string): Rgba | null {
    if (!css) return null
    const value = css.trim()
    if (value === 'transparent') return [0, 0, 0, 0]

    const hex = parseHexColor(value)
    if (hex) return [hex[0], hex[1], hex[2], 1]

    const fn = splitColorFunction(value)
    if (!fn) return null
    const [first, second, third, fourth] = fn.parts
    if (first === undefined || second === undefined || third === undefined) return null

    if (fn.name === 'rgb' || fn.name === 'rgba') {
        return [
            componentValue(first, 255),
            componentValue(second, 255),
            componentValue(third, 255),
            alphaValue(fn.slashAlpha ?? fourth),
        ]
    }
    if (fn.name === 'oklab') {
        const rgb = oklabToRgb(
            componentValue(first, 1),
            componentValue(second, 0.4),
            componentValue(third, 0.4),
        )
        return [rgb[0], rgb[1], rgb[2], alphaValue(fn.slashAlpha)]
    }
    if (fn.name === 'oklch') {
        const chroma = componentValue(second, 0.4)
        const hue = (componentValue(third, 360) * Math.PI) / 180
        const rgb = oklabToRgb(
            componentValue(first, 1),
            chroma * Math.cos(hue),
            chroma * Math.sin(hue),
        )
        return [rgb[0], rgb[1], rgb[2], alphaValue(fn.slashAlpha)]
    }
    return null
}

/** Lays a translucent colour over an opaque one, the way the browser paints the two. */
export function compositeOver(color: Rgba, backdrop: Rgb): Rgb {
    const alpha = color[3]
    return [
        Math.round(color[0] * alpha + backdrop[0] * (1 - alpha)),
        Math.round(color[1] * alpha + backdrop[1] * (1 - alpha)),
        Math.round(color[2] * alpha + backdrop[2] * (1 - alpha)),
    ]
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

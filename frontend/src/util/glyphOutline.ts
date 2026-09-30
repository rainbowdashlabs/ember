/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    compositeOver,
    contrastingTextColor,
    contrastRatio,
    parseCssColor,
    parseHexColor,
    type Rgb,
} from '@/util/contrastColor'
import {browserShallowRef} from '@/util/browserState'

/**
 * The surface a glyph is drawn on, named by whoever draws it.
 *
 * <p>Named rather than measured from the page. Three surfaces carry gear in this product and the
 * caller knows which one it is painting on, so the answer costs three reads of the root element per
 * theme instead of an ancestor walk per glyph in a list of two hundred. It also answers correctly for
 * a row that only changes colour while the pointer is over it, which a measurement taken once on
 * mount cannot.
 */
export type GlyphSurface = 'page' | 'accent' | 'highlight'

/** WCAG 1.4.11: a graphic needs this much against what is behind it to stay distinguishable. */
const GRAPHIC_CONTRAST_MIN = 3

/** What a picker row tints its background by when it is hovered or carries the keyboard highlight. */
const HIGHLIGHT_TINT = 0.1

const PAPER: Rgb = [255, 255, 255]

/** What was measured of the page and what was worked out from it, kept until the theme repaints. */
interface Measured {
    surfaces: Map<GlyphSurface, Rgb> | null
    outlines: Map<string, string | null>
}

const measured = browserShallowRef<Measured>({surfaces: null, outlines: new Map()})

/**
 * Whether there is a page to measure. On the server there is none: every surface is taken for
 * paper, and nothing is kept, since the browser measures for itself.
 */
function pageToMeasure(): boolean {
    return typeof document !== 'undefined'
}

function readToken(styles: CSSStyleDeclaration, token: string, fallback: Rgb): Rgb {
    const parsed = parseCssColor(styles.getPropertyValue(token).trim())
    if (!parsed) return fallback
    return compositeOver(parsed, PAPER)
}

function measureSurfaces(): Map<GlyphSurface, Rgb> {
    const styles = getComputedStyle(document.documentElement)
    const page = readToken(styles, '--bg', PAPER)
    const accent = readToken(styles, '--bg-accent', page)
    const primary = readToken(styles, '--color-primary', page)
    const highlight = compositeOver([primary[0], primary[1], primary[2], HIGHLIGHT_TINT], page)
    return new Map<GlyphSurface, Rgb>([
        ['page', page],
        ['accent', accent],
        ['highlight', highlight],
    ])
}

/**
 * Forgets what was measured, which is what a theme repaint makes necessary.
 *
 * <p>Called by the theme itself rather than watched, so a module with no component around it still
 * answers for the theme the page is actually wearing.
 */
export function forgetGlyphSurfaces(): void {
    measured.value = {surfaces: null, outlines: new Map()}
}

/**
 * The colour behind a glyph on one of the three surfaces.
 *
 * @param surface the surface the caller is painting on
 */
export function surfaceColor(surface: GlyphSurface): Rgb {
    if (!pageToMeasure()) return PAPER
    measured.value.surfaces ??= measureSurfaces()
    return measured.value.surfaces.get(surface) ?? PAPER
}

/**
 * The stroke a coloured glyph needs to stay visible where it sits, or null when it needs none.
 *
 * <p>A colour chosen against a white page can vanish against a dark one, a dark hue can vanish on
 * the accent surface a selected row uses, and a station's own palette moves both out from under a
 * choice made before it. Under three to one the glyph is stroked in the neutral that contrasts with
 * the surface, near black on a light one and white on a dark one. At or above it nothing is drawn, so
 * a well chosen colour is never boxed in for no reason.
 *
 * <p>Answers are kept per colour and per surface, because every row of one list shares a background.
 *
 * @param color   the colour the glyph is drawn in, or null for the muted neutral
 * @param surface the surface it sits on
 */
export function outlineFor(color: string | null | undefined, surface: GlyphSurface = 'page'): string | null {
    if (!color) return null
    if (!pageToMeasure()) return outlineAgainst(color, PAPER)
    const outlines = measured.value.outlines
    const key = `${color}|${surface}`
    const known = outlines.get(key)
    if (known !== undefined) return known

    const answer = outlineAgainst(color, surfaceColor(surface))
    outlines.set(key, answer)
    return answer
}

function outlineAgainst(color: string, behind: Rgb): string | null {
    const painted = parseHexColor(color)
    if (!painted) return null
    return contrastRatio(painted, behind) < GRAPHIC_CONTRAST_MIN
        ? contrastingTextColor(behind[0], behind[1], behind[2])
        : null
}

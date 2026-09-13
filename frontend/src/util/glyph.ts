/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The picture a row of gear is drawn with, resolved and ready to render.
 *
 * @property icon  the FontAwesome prefix and name, always present
 * @property color the colour it is drawn in, or null for the muted neutral
 */
export interface Glyph {
    icon: [string, string]
    color: string | null
}

/**
 * What is known about a row of gear when the picture is resolved.
 *
 * <p>Every field is optional, because the three payload shapes that feed this know different
 * amounts: a kind knows its own picture, a piece knows its kind's and its inventory's, and a
 * flattened row already carries the answer and resolves to itself.
 *
 * @property icon            a picture already resolved, or the row's own
 * @property color           the colour already resolved, or the row's own
 * @property artIcon         the picture of the piece's kind
 * @property artColor        the colour of the piece's kind
 * @property inventoryIcon   the picture of the inventory the piece is out of
 * @property inventoryColor  the colour of that inventory
 * @property homogeneous     whether that inventory is a stock rather than a collection, which decides
 *                           the shape when nothing else answers
 */
export interface GlyphSource {
    icon?: string | null
    color?: string | null
    artIcon?: string | null
    artColor?: string | null
    inventoryIcon?: string | null
    inventoryColor?: string | null
    homogeneous?: boolean
}

/** The shape a stock falls back to: one thing in many copies. */
const STOCK_FALLBACK = 'cube'

/** The shape a collection falls back to: a drawer of different things. */
const COLLECTION_FALLBACK = 'box'

function firstNamed(...candidates: Array<string | null | undefined>): string | null {
    for (const candidate of candidates) {
        if (candidate && candidate.trim()) return candidate.trim()
    }
    return null
}

/**
 * The one answer to "what is this drawn with", so no two screens disagree.
 *
 * <p>The kind answers first, then the inventory, then a plain shape. The two halves are resolved
 * together rather than one field at a time: a kind that set a colour and no picture is drawn in its
 * colour on the inventory's shape, which is what somebody choosing only a colour meant.
 *
 * @param source what is known about the row
 */
export function glyphFor(source: GlyphSource): Glyph {
    const icon = firstNamed(source.icon, source.artIcon, source.inventoryIcon)
        ?? (source.homogeneous === false ? COLLECTION_FALLBACK : STOCK_FALLBACK)
    const color = firstNamed(source.color, source.artColor, source.inventoryColor)
    return {icon: ['fas', icon], color}
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** The lower-cased texts a piece is searched by. */
export interface ItemSearchText {
    id: string
    name: string
    size: string
    inventory: string
    location: string
}

/** How many pieces a search offers at most. */
export const ITEM_SEARCH_LIMIT = 25

function escapeRegex(s: string): string {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * How well one word of the query matches a piece, or -1 where it does not match at all.
 *
 * <p>The internal number decides first, because it is what is printed on the piece and what a
 * reader types off it; the name follows, then the size, and where the piece sits last.
 */
function scoreToken(text: ItemSearchText, token: string): number {
    if (text.id === token) return 200
    if (text.id.startsWith(token)) return 150
    if (text.name.startsWith(token)) return 100
    if (text.size === token) return 90
    if (new RegExp(`\\b${escapeRegex(token)}`).test(text.name)) return 85
    if (text.id.includes(token)) return 70
    if (text.name.includes(token)) return 60
    if (text.size.includes(token)) return 50
    if (text.inventory.includes(token)) return 30
    if (text.location.includes(token)) return 20
    return -1
}

/** The sum over every word of the query, or -1 where any word matches nothing. */
export function scoreItemText(text: ItemSearchText, tokens: string[]): number {
    let total = 0
    for (const token of tokens) {
        const score = scoreToken(text, token)
        if (score < 0) return -1
        total += score
    }
    return total
}

/** The words of a query, lower-cased, with the blanks between them dropped. */
export function queryTokens(query: string): string[] {
    return query.toLowerCase().split(/\s+/).filter(Boolean)
}

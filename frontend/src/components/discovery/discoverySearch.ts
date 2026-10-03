/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {DiscoveryEntry} from '@/api/generated/schema'

/** How long typing has to pause, in milliseconds, before the search takes effect. */
export const SEARCH_DELAY_MS = 200

/** A station together with the text the search reads in it, lowercased. */
export interface SearchableEntry {
    entry: DiscoveryEntry
    text: string
}

/**
 * The stations prepared for searching: the text of each is put together once, so a search runs over
 * ready text rather than building it again for every term typed.
 */
export function searchIndex(entries: DiscoveryEntry[]): SearchableEntry[] {
    return entries.map(entry => ({entry, text: searchableText(entry)}))
}

/**
 * The stations whose name, description, place, association or instance contain the search term,
 * ignoring case. Stations of this instance and of other instances are searched alike; a blank term
 * keeps every station.
 */
export function searchDiscovery(index: SearchableEntry[], term: string): DiscoveryEntry[] {
    const needle = term.trim().toLowerCase()
    const found = needle ? index.filter(({text}) => text.includes(needle)) : index
    return found.map(({entry}) => entry)
}

function searchableText(entry: DiscoveryEntry): string {
    return [entry.name, entry.description, entry.city, entry.country, entry.clusterName, entry.instanceHost]
        .filter(Boolean)
        .join('\n')
        .toLowerCase()
}

/** The search term a link hands the page, as `?q=`; the page's structured data advertises that form. */
export function initialSearchTerm(query: unknown): string {
    const value = Array.isArray(query) ? query[0] : query
    return typeof value === 'string' ? value : ''
}

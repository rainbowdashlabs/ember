/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {DiscoveryEntry} from '@/api/generated/schema'

/**
 * The stations whose name, description, place, association or instance contain the search term,
 * ignoring case. Stations of this instance and of other instances are searched alike; a blank term
 * keeps every station.
 */
export function searchDiscovery(entries: DiscoveryEntry[], term: string): DiscoveryEntry[] {
    const needle = term.trim().toLowerCase()
    if (!needle) return entries
    return entries.filter(entry => searchableText(entry).includes(needle))
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

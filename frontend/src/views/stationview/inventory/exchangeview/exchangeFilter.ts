/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ExchangeStatus, type ExchangeRequestEntry, type ExchangeStatusName} from '@/api/exchanges'
import {byDate, byValue, type SortComparator} from '@/composables/useSortable'

/** Status filter standing for every exchange that is still on its way. */
export const OPEN_STATUSES = 'open'

/** Status filter standing for every exchange, the finished ones included. */
export const ALL_STATUSES = ''

/**
 * The statuses in the order an exchange passes through them. An inventory of the station's own
 * skips the two postal steps, so this one order holds for both flows.
 */
export const statusChain: ExchangeStatusName[] = [
    ExchangeStatus.ANNOUNCED,
    ExchangeStatus.RECEIVED,
    ExchangeStatus.SHIPPED,
    ExchangeStatus.ARRIVED,
    ExchangeStatus.DONE,
]

export interface ExchangeFilter {
    /** Part of a member name, matched without regard to case. */
    search: string
    /** Id of an inventory as text, empty for every inventory. */
    inventoryId: string
    /** A single status, {@link OPEN_STATUSES} or {@link ALL_STATUSES}. */
    status: string
}

/**
 * What the page starts with: every inventory, every name, and only the exchanges still running.
 * A finished exchange is a record rather than a task, and whoever opens the list is looking at
 * the tasks.
 */
export const defaultExchangeFilter: ExchangeFilter = {
    search: '',
    inventoryId: '',
    status: OPEN_STATUSES,
}

/** The member name a row shows, which is the one a search has to match. */
export function memberNameOf(request: ExchangeRequestEntry): string {
    return request.memberIdentity?.name ?? request.memberName ?? ''
}

/** Position of a status in the chain, which is how far the exchange has come. */
export function statusRank(status: ExchangeStatusName): number {
    const index = statusChain.indexOf(status)
    return index < 0 ? statusChain.length : index
}

function matchesStatus(request: ExchangeRequestEntry, status: string): boolean {
    if (status === ALL_STATUSES) return true
    if (status === OPEN_STATUSES) return request.status !== ExchangeStatus.DONE
    return request.status === status
}

/**
 * The rows left once all three filters have had their say. They narrow together: a row survives
 * only where it matches the name, the inventory and the status at once.
 */
export function filterExchanges(requests: ExchangeRequestEntry[], filter: ExchangeFilter): ExchangeRequestEntry[] {
    const needle = filter.search.trim().toLowerCase()
    return requests.filter(request => {
        const nameMatches = needle === '' || memberNameOf(request).toLowerCase().includes(needle)
        const inventoryMatches = filter.inventoryId === '' || String(request.inventoryId) === filter.inventoryId
        return nameMatches && inventoryMatches && matchesStatus(request, filter.status)
    })
}

/** Name of an inventory as the filter offers it. */
export interface InventoryChoice {
    id: number
    name: string
}

/**
 * The inventories the loaded exchanges actually mention, sorted by name. Offering every inventory
 * of the station would fill the list with entries that match nothing.
 */
export function inventoryChoices(requests: ExchangeRequestEntry[]): InventoryChoice[] {
    const names = new Map<number, string>()
    for (const request of requests) names.set(request.inventoryId, request.inventoryName)
    return [...names]
        .map(([id, name]) => ({id, name}))
        .sort((a, b) => a.name.localeCompare(b.name, 'de'))
}

export type ExchangeSortKey = 'member' | 'inventory' | 'status' | 'date'

/**
 * How each sortable column compares two rows. The status is ordered along the chain rather than
 * by its label, so a row that has come further stands further down whatever its word starts with.
 */
export const exchangeComparators: Record<ExchangeSortKey, SortComparator<ExchangeRequestEntry>> = {
    member: byValue(memberNameOf),
    inventory: byValue(request => request.inventoryName),
    status: byValue(request => statusRank(request.status)),
    date: byDate(request => request.createdAt),
}

/**
 * Which way round a column reads when it is picked rather than toggled. The date answers "what is
 * new" and therefore starts at the newest, everything else starts at the top of the alphabet or
 * the beginning of the chain.
 */
export function naturalDirection(key: ExchangeSortKey): 'asc' | 'desc' {
    return key === 'date' ? 'desc' : 'asc'
}

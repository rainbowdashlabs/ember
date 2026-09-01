/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ExchangeStatus, type ExchangeRequestEntry, type ExchangeStatusName} from '@/api/exchanges'
import {byDate, byValue, type SortComparator} from '@/composables/useSortable'

/**
 * The statuses in the order an exchange passes through them. An inventory of the station's own
 * skips the two postal steps, so this one order holds for both flows. A further status is a
 * further entry here, and one that ends an exchange is also named in {@link finishedStatuses}.
 */
export const statusChain: ExchangeStatusName[] = [
    ExchangeStatus.ANNOUNCED,
    ExchangeStatus.RECEIVED,
    ExchangeStatus.SHIPPED,
    ExchangeStatus.ARRIVED,
    ExchangeStatus.DONE,
]

/** The statuses at which an exchange is over, whatever came of it. */
const finishedStatuses: ExchangeStatusName[] = [ExchangeStatus.DONE]

/** The statuses an exchange can still be sitting in, which are the ones that are still tasks. */
export const openStatuses: ExchangeStatusName[] = statusChain.filter(name => !finishedStatuses.includes(name))

export interface ExchangeFilter {
    /** Part of a member name, matched without regard to case. */
    search: string
    /** Ids of the inventories that were ticked, as text. */
    inventoryIds: string[]
    /** The statuses that were ticked. */
    statuses: string[]
}

/**
 * What the page starts with: every inventory, every name, and the statuses an exchange can still
 * be sitting in. A finished exchange is a record rather than a task, and whoever opens the list is
 * looking at the tasks. The statuses stand ticked rather than hidden in a mode of their own, so
 * whoever wants the finished ones back only has to tick them.
 */
export const defaultExchangeFilter: ExchangeFilter = {
    search: '',
    inventoryIds: [],
    statuses: [...openStatuses],
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

/**
 * Whether a value is among the ones that were ticked. Nothing ticked is no restriction at all,
 * because a filter that empties the list when its last tick is taken away reads as a fault.
 */
function amongTicked(ticked: string[], value: string): boolean {
    return ticked.length === 0 || ticked.includes(value)
}

/**
 * The rows left once all three filters have had their say. Within a filter the ticks stand beside
 * one another, so three ticked statuses show the rows of all three; between the filters they
 * narrow together, and a row survives only where it answers the name, the inventory and the
 * status at once.
 */
export function filterExchanges(requests: ExchangeRequestEntry[], filter: ExchangeFilter): ExchangeRequestEntry[] {
    const needle = filter.search.trim().toLowerCase()
    return requests.filter(request => {
        const nameMatches = needle === '' || memberNameOf(request).toLowerCase().includes(needle)
        return nameMatches
            && amongTicked(filter.inventoryIds, String(request.inventoryId))
            && amongTicked(filter.statuses, request.status)
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

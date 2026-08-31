/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/**
 * A collection as the list screen reads it.
 */
export interface InventoryCollection {
    id: number
    name: string
    note: string
    lineCount: number
}

/**
 * One line read against the stock. A line naming a piece carries an itemId, a line asking for a
 * count of one kind carries an artId, and a line counting out of a whole inventory carries an
 * inventoryId. Exactly one of the three.
 */
export interface ResolvedCollectionLine {
    lineId: number
    itemId: number | null
    artId: number | null
    inventoryId: number | null
    label: string
    requested: number
    available: number
    clusterOwned: number
    filled: boolean
    missing: number
}

/**
 * A collection with one answer per line, over the window it was read for.
 */
export interface ResolvedCollection {
    collection: {id: number; stationId: number; name: string; note: string}
    dateFrom: string | null
    dateTo: string | null
    lines: ResolvedCollectionLine[]
    complete: boolean
    holdsClusterOwned: boolean
}

/**
 * The window a collection is read over. Leaving it out asks what is here today.
 */
export interface CollectionWindow {
    from?: string | null
    to?: string | null
}

export async function list(): Promise<InventoryCollection[]> {
    const res = await client.get<InventoryCollection[]>('/inventory-collections')
    return res.data
}

export async function get(id: number, window?: CollectionWindow): Promise<ResolvedCollection> {
    const res = await client.get<ResolvedCollection>(`/inventory-collections/${id}`, {
        params: {from: window?.from || undefined, to: window?.to || undefined},
    })
    return res.data
}

export async function create(name: string, note: string): Promise<{id: number}> {
    const res = await client.post<{id: number}>('/inventory-collections', {name, note})
    return res.data
}

export async function update(id: number, name: string, note: string): Promise<void> {
    await client.put(`/inventory-collections/${id}`, {name, note})
}

export async function remove(id: number): Promise<void> {
    await client.delete(`/inventory-collections/${id}`)
}

export async function addItemLine(id: number, itemId: number): Promise<void> {
    await client.post(`/inventory-collections/${id}/lines`, {itemId, quantity: 1})
}

export async function addArtLine(id: number, artId: number, quantity: number): Promise<void> {
    await client.post(`/inventory-collections/${id}/lines`, {artId, quantity})
}

export async function addInventoryLine(id: number, inventoryId: number, quantity: number): Promise<void> {
    await client.post(`/inventory-collections/${id}/lines`, {inventoryId, quantity})
}

export async function updateLineQuantity(id: number, lineId: number, quantity: number): Promise<void> {
    await client.put(`/inventory-collections/${id}/lines/${lineId}`, {quantity})
}

export async function reorderLines(id: number, lineIds: number[]): Promise<void> {
    await client.put(`/inventory-collections/${id}/line-order`, {lineIds})
}

export async function removeLine(id: number, lineId: number): Promise<void> {
    await client.delete(`/inventory-collections/${id}/lines/${lineId}`)
}

/**
 * The collections that would lose a line if this piece went.
 */
export async function holdingItem(itemId: number): Promise<string[]> {
    const res = await client.get<string[]>(`/inventory-items/${itemId}/collections`)
    return res.data
}

/**
 * The collections that would lose a line if this inventory went.
 */
export async function touchingInventory(inventoryId: number): Promise<string[]> {
    const res = await client.get<string[]>(`/inventories/${inventoryId}/collections`)
    return res.data
}

/**
 * The collections that would lose a line if this kind of thing went.
 */
export async function askingForArt(inventoryId: number, artId: number): Promise<string[]> {
    const res = await client.get<string[]>(`/inventories/${inventoryId}/arts/${artId}/collections`)
    return res.data
}

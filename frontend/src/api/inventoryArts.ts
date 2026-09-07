/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createScopedCrudResource} from './crud'
import type {InventoryItem} from './inventory'

/**
 * A kind of thing inside one inventory, sitting between the inventory and the piece.
 *
 * Kinds exist only in inventories that hold a drawer of different things. An inventory of one
 * thing in many copies is structured by its sizes instead.
 */
export interface InventoryArt {
    id: number
    inventoryId: number
    name: string
    note: string
    position: number
    /** The name trimmed and lowered, maintained by the backend. Two stations that write the same word mean the same kind. */
    mergeKey: string
}

export interface ArtRequest {
    name: string
    note: string
    position: number
}

/** How many pieces of one kind an inventory holds, and how many of those are free. */
export interface ArtStock {
    artId: number
    name: string
    pieces: number
    free: number
}

/** One name written on the pieces of an inventory, with a count. What the tidying screen reads. */
export interface ItemNameCount {
    name: string
    pieces: number
    unassigned: number
}

/** How many pieces a tidying action changed. */
export interface TidyResult {
    changed: number
}

const arts = createScopedCrudResource<InventoryArt, ArtRequest, ArtRequest>(
    (inventoryId: number) => `/inventories/${inventoryId}/arts`,
)

export const listArts = arts.list
export const createArt = arts.create
export const updateArt = arts.update
export const deleteArt = arts.remove

/**
 * The kind a form ended up wanting, written down only now that the form is being saved.
 *
 * This is the deferred half of the picker. A kind typed into the picker is kept as a word until
 * here, so an abandoned form leaves nothing behind and a mistyped word is never written down as
 * firmly as a right one. A word that already names a kind picks that kind rather than making a
 * second one, matched the way the backend matches: trimmed and without regard to case.
 */
export async function ensureArt(
    inventoryId: number,
    existing: InventoryArt[],
    name: string,
): Promise<number | null> {
    const wanted = name.trim()
    if (!wanted) return null
    const already = existing.find(art => art.name.trim().toLowerCase() === wanted.toLowerCase())
    if (already) return already.id
    const created = await createArt(inventoryId, {name: wanted, note: '', position: 0})
    return created.id
}

/** How many pieces of each kind the inventory holds, and how many are free. */
export async function artStock(inventoryId: number): Promise<ArtStock[]> {
    const res = await client.get<ArtStock[]>(`/inventories/${inventoryId}/art-stock`)
    return res.data
}

/** The distinct names written on the pieces, commonest first. */
export async function itemNames(inventoryId: number): Promise<ItemNameCount[]> {
    const res = await client.get<ItemNameCount[]>(`/inventories/${inventoryId}/item-names`)
    return res.data
}

/** The pieces of one kind. */
export async function artItems(inventoryId: number, artId: number): Promise<InventoryItem[]> {
    const res = await client.get<InventoryItem[]>(`/inventories/${inventoryId}/art-items/${artId}`)
    return res.data
}

/**
 * Puts pieces under a kind and leaves their names exactly as they are.
 *
 * Pass `null` as the kind to take it away again.
 */
export async function assignArt(
    inventoryId: number,
    artId: number | null,
    itemIds: number[],
): Promise<TidyResult> {
    const res = await client.put<TidyResult>(`/inventories/${inventoryId}/item-arts`, {artId, itemIds})
    return res.data
}

/**
 * Puts pieces under a kind and rewrites their names to it.
 *
 * The rewrite is the destructive half of tidying up, and the point of it: the name is what every
 * list and both exports read, so a kind on its own corrects nothing anybody can see.
 */
export async function mergeIntoArt(
    inventoryId: number,
    artId: number,
    itemIds: number[],
): Promise<TidyResult> {
    const res = await client.post<TidyResult>(`/inventories/${inventoryId}/art-merges`, {artId, itemIds})
    return res.data
}

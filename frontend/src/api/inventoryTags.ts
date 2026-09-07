/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'

/**
 * A word a station puts on its things.
 *
 * Unlike an inventory it says nothing about where a thing is kept, and unlike a kind it says
 * nothing about what the thing is. It spans inventories, which is what lets the radios, the
 * charging station and the antenna belong together without being alike.
 */
export interface InventoryTag {
    id: number
    name: string
    color?: string | null
    position: number
}

/** A word with the number of things wearing it, which is what a list of words is read for. */
export interface CountedInventoryTag extends InventoryTag {
    itemCount: number
}

export interface TagRequest {
    name: string
    color?: string | null
    position: number
}

/** A word an association recommends, and whether the station already uses it. */
export interface RecommendedTag {
    name: string
    color?: string | null
    adopted: boolean
}

/** One thing found by a word, wherever it is kept and whoever keeps it. */
export interface TaggedItem {
    itemId: number
    internalId?: string | null
    name: string
    inventoryName: string
    stationUid: string
    stationName: string
    tagName: string
    available: boolean
}

const tags = createCrudResource<
    CountedInventoryTag,
    TagRequest,
    TagRequest,
    CountedInventoryTag,
    CountedInventoryTag,
    CountedInventoryTag,
    number
>('/inventory-tags')

export const listTags = tags.list
export const createTag = tags.create
export const updateTag = tags.update
export const deleteTag = tags.remove

/** The words the association above this station recommends to it. */
export async function recommendedTags(): Promise<RecommendedTag[]> {
    const res = await client.get<RecommendedTag[]>('/inventory-tags/recommended')
    return res.data
}

/** The words every thing in one inventory wears, keyed by the thing. */
export interface ItemTags {
    itemId: number
    tags: InventoryTag[]
}

/** The words every thing in one inventory wears, in one request rather than one per row. */
export async function inventoryItemTags(inventoryId: number): Promise<ItemTags[]> {
    const res = await client.get<ItemTags[]>(`/inventories/${inventoryId}/item-tags`)
    return res.data
}

/** The words one thing wears. */
export async function itemTags(itemId: number): Promise<InventoryTag[]> {
    const res = await client.get<InventoryTag[]>(`/inventory-items/${itemId}/tags`)
    return res.data
}

/**
 * Says which words a thing wears.
 *
 * The call speaks in words rather than identifiers, which is what makes a word picked from the
 * list and a word made up on the spot the same thing, and what lets the picker keep a new word as
 * a draft until the form is actually saved.
 */
export async function setItemTags(itemId: number, names: string[]): Promise<InventoryTag[]> {
    const res = await client.put<InventoryTag[]>(`/inventory-items/${itemId}/tags`, {names})
    return res.data
}

/** The things wearing a word in this station's own stock. */
export async function itemsByTag(tag: string): Promise<TaggedItem[]> {
    const res = await client.get<TaggedItem[]>('/inventory-tags/items', {params: {tag}})
    return res.data
}

/** The things wearing a word here and at every partner that lends to this station. */
export async function federatedItemsByTag(tag: string): Promise<TaggedItem[]> {
    const res = await client.get<TaggedItem[]>('/federated/inventory-tags/items', {params: {tag}})
    return res.data
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {
    InventoryTag,
    InventoryTagRequest,
    ItemTagsResponse,
    RecommendedTag,
    TaggedItemSummary,
    TagResponse,
} from './generated/schema'

const tags = createCrudResource<
    TagResponse,
    InventoryTagRequest,
    InventoryTagRequest,
    TagResponse,
    TagResponse,
    TagResponse,
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

/** The words every thing in one inventory wears, in one request rather than one per row. */
export async function inventoryItemTags(inventoryId: number): Promise<ItemTagsResponse[]> {
    const res = await client.get<ItemTagsResponse[]>(`/inventories/${inventoryId}/item-tags`)
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
export async function itemsByTag(tag: string): Promise<TaggedItemSummary[]> {
    const res = await client.get<TaggedItemSummary[]>('/inventory-tags/items', {params: {tag}})
    return res.data
}

/** The things wearing a word here and at every partner that lends to this station. */
export async function federatedItemsByTag(tag: string): Promise<TaggedItemSummary[]> {
    const res = await client.get<TaggedItemSummary[]>('/federated/inventory-tags/items', {params: {tag}})
    return res.data
}

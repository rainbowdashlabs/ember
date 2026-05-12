/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    AssignRequest,
    Inventory,
    InventoryDetail,
    InventoryItem,
    InventoryItemHistory,
    InventoryRequest,
    InventoryRequirement,
    InventorySize,
    ItemRequest,
    RequirementRequest,
    SizeRequest,
} from './types'

export interface MyInventoryItem {
    id: number
    inventoryId: number
    name?: string
    internalId?: string
    inventoryName: string
    sizeName?: string | null
    lostAt?: string | null
}

export interface MyRequirement {
    inventoryId: number
    inventoryName: string
    requiredQuantity: number
}

export async function myItems(): Promise<MyInventoryItem[]> {
    const res = await client.get<MyInventoryItem[]>('/my-inventory-items')
    return res.data
}

export async function myRequirements(): Promise<MyRequirement[]> {
    const res = await client.get<MyRequirement[]>('/my-inventory-requirements')
    return res.data
}

// -- Inventories --

export async function listInventories(): Promise<Inventory[]> {
    const res = await client.get<Inventory[]>('/inventories')
    return res.data
}

export async function getInventory(id: number): Promise<InventoryDetail> {
    const res = await client.get<InventoryDetail>(`/inventories/${id}`)
    return res.data
}

export async function createInventory(data: InventoryRequest): Promise<Inventory> {
    const res = await client.post<Inventory>('/inventories', data)
    return res.data
}

export async function updateInventory(id: number, data: InventoryRequest): Promise<Inventory> {
    const res = await client.put<Inventory>(`/inventories/${id}`, data)
    return res.data
}

export async function deleteInventory(id: number): Promise<void> {
    await client.delete(`/inventories/${id}`)
}

// -- Sizes --

export async function listSizes(inventoryId: number): Promise<InventorySize[]> {
    const res = await client.get<InventorySize[]>(`/inventories/${inventoryId}/sizes`)
    return res.data
}

export async function createSize(inventoryId: number, data: SizeRequest): Promise<InventorySize[]> {
    const res = await client.post<InventorySize[]>(`/inventories/${inventoryId}/sizes`, data)
    return res.data
}

export async function updateSize(inventoryId: number, sizeId: number, data: SizeRequest): Promise<InventorySize[]> {
    const res = await client.put<InventorySize[]>(`/inventories/${inventoryId}/sizes/${sizeId}`, data)
    return res.data
}

export async function deleteSize(inventoryId: number, sizeId: number): Promise<InventorySize[]> {
    const res = await client.delete<InventorySize[]>(`/inventories/${inventoryId}/sizes/${sizeId}`)
    return res.data
}

// -- Items --

export async function listItems(inventoryId: number): Promise<InventoryItem[]> {
    const res = await client.get<InventoryItem[]>(`/inventories/${inventoryId}/items`)
    return res.data
}

export async function getItem(id: number): Promise<InventoryItem> {
    const res = await client.get<InventoryItem>(`/inventory-items/${id}`)
    return res.data
}

export async function createItem(inventoryId: number, data: ItemRequest): Promise<InventoryItem> {
    const res = await client.post<InventoryItem>(`/inventories/${inventoryId}/items`, data)
    return res.data
}

export async function updateItem(id: number, data: ItemRequest): Promise<InventoryItem> {
    const res = await client.put<InventoryItem>(`/inventory-items/${id}`, data)
    return res.data
}

export async function deleteItem(id: number): Promise<void> {
    await client.delete(`/inventory-items/${id}`)
}

export async function assignItem(id: number, data: AssignRequest): Promise<InventoryItem> {
    const res = await client.put<InventoryItem>(`/inventory-items/${id}/assign`, data)
    return res.data
}

export async function getItemHistory(id: number): Promise<InventoryItemHistory[]> {
    const res = await client.get<InventoryItemHistory[]>(`/inventory-items/${id}/history`)
    return res.data
}

export async function markLost(id: number): Promise<InventoryItem> {
    const res = await client.put<InventoryItem>(`/inventory-items/${id}/lost`)
    return res.data
}

export async function markFound(id: number): Promise<InventoryItem> {
    const res = await client.delete<InventoryItem>(`/inventory-items/${id}/lost`)
    return res.data
}

// -- Requirements --

export async function listAllRequirements(): Promise<InventoryRequirement[]> {
    const res = await client.get<InventoryRequirement[]>('/inventory-requirements')
    return res.data
}

export async function listRequirements(inventoryId: number): Promise<InventoryRequirement[]> {
    const res = await client.get<InventoryRequirement[]>(`/inventories/${inventoryId}/requirements`)
    return res.data
}

export async function createRequirement(data: RequirementRequest): Promise<InventoryRequirement> {
    const res = await client.post<InventoryRequirement>('/inventory-requirements', data)
    return res.data
}

export async function updateRequirement(id: number, data: { quantity: number }): Promise<void> {
    await client.put(`/inventory-requirements/${id}`, data)
}

export async function updateRequirementPosition(id: number, position: number): Promise<void> {
    await client.patch(`/inventory-requirements/${id}/position`, {position})
}

export async function deleteRequirement(id: number): Promise<void> {
    await client.delete(`/inventory-requirements/${id}`)
}

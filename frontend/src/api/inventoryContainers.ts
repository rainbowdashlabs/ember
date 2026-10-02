/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {
    CompleteContainerCheckRequest,
    ContainerContents,
    ContainerDetail,
    ContainerPathResponse,
    ContainerRequest,
    InventoryCheck,
    InventoryContainer,
    InventoryContainerHistory,
    InventoryContainerKind,
    InventoryItem,
    ItemCheckHistoryEntry,
    ItemLastCheck,
    ItemLocationResponse,
    KindRequest,
} from './generated/schema'

const kinds = createCrudResource<InventoryContainerKind, KindRequest>('/inventory-container-kinds')

const containers = createCrudResource<
    InventoryContainer,
    ContainerRequest,
    ContainerRequest,
    ContainerDetail
>('/inventory-containers')

export const listKinds = kinds.list
export const createKind = kinds.create
export const updateKind = kinds.update
export const deleteKind = kinds.remove

export const listContainers = containers.list
export const getContainer = containers.get
export const createContainer = containers.create
export const updateContainer = containers.update
export const deleteContainer = containers.remove

export async function listRoots(): Promise<InventoryContainer[]> {
    const res = await client.get<InventoryContainer[]>('/inventory-containers/roots')
    return res.data
}

export async function getContainerContents(id: number, recursive = false): Promise<ContainerContents> {
    const res = await client.get<ContainerContents>(`/inventory-containers/${id}/contents`, {
        params: {recursive},
    })
    return res.data
}

export async function getContainerPath(id: number): Promise<ContainerPathResponse> {
    const res = await client.get<ContainerPathResponse>(`/inventory-containers/${id}/path`)
    return res.data
}

export async function getContainerHistory(id: number): Promise<InventoryContainerHistory[]> {
    const res = await client.get<InventoryContainerHistory[]>(`/inventory-containers/${id}/history`)
    return res.data
}

export async function resolveContainerByScan(internalId: string): Promise<InventoryContainer | null> {
    try {
        const res = await client.get<InventoryContainer>('/inventory-containers/by-scan', {
            params: {internalId},
        })
        return res.data
    } catch {
        return null
    }
}

export async function getItemLocation(itemId: number): Promise<ItemLocationResponse> {
    const res = await client.get<ItemLocationResponse>(`/inventory-items/${itemId}/location`)
    return res.data
}

export async function setItemContainer(itemId: number, containerId: number | null): Promise<void> {
    await client.put(`/inventory-items/${itemId}/container`, {containerId})
}

export async function listExpectedItemsInContainer(
    containerId: number,
    deep: boolean,
): Promise<InventoryItem[]> {
    const res = await client.get<InventoryItem[]>(
        `/inventory-checks/container/${containerId}/expected`,
        {params: {deep}},
    )
    return res.data
}

export async function listLastCheckResults(
    containerId: number,
    deep: boolean,
): Promise<ItemLastCheck[]> {
    const res = await client.get<ItemLastCheck[]>(
        `/inventory-checks/container/${containerId}/last-results`,
        {params: {deep}},
    )
    return res.data
}

export async function listItemCheckHistory(itemId: number): Promise<ItemCheckHistoryEntry[]> {
    const res = await client.get<ItemCheckHistoryEntry[]>(`/inventory-checks/item/${itemId}/history`)
    return res.data
}

export async function completeContainerCheck(
    containerId: number,
    body: CompleteContainerCheckRequest,
): Promise<InventoryCheck> {
    const res = await client.post<InventoryCheck>(`/inventory-checks/container/${containerId}/complete`, body)
    return res.data
}

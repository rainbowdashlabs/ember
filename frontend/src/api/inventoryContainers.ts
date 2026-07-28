/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {InventoryItem} from './inventory'

export interface InventoryContainerKind {
    id: number
    stationId: string
    key: string
    label: string
    icon: string
    sortOrder: number
    enabled: boolean
}

export interface KindRequest {
    key: string
    label: string
    icon?: string
    sortOrder: number
    enabled: boolean
}

export interface InventoryContainer {
    id: number
    stationId: string
    parentId?: number | null
    internalId?: string | null
    name: string
    kindId?: number | null
    description: string
    createdAt: string
    createdBy?: number | null
}

export interface ContainerRequest {
    parentId?: number | null
    internalId?: string | null
    name: string
    kindId?: number | null
    description?: string
}

export interface ContainerDetail {
    container: InventoryContainer
    pathSegments: string[]
    pathIds: number[]
    pathDisplay: string
}

export interface ContainerContents {
    children: InventoryContainer[]
    items: InventoryItem[]
}

export interface ContainerPathResponse {
    segments: string[]
    ids: number[]
    display: string
}

export const ContainerEventKind = {
    CREATED: 'CREATED',
    RENAMED: 'RENAMED',
    MOVED: 'MOVED',
    DELETED: 'DELETED',
} as const
export type ContainerEventKindName = (typeof ContainerEventKind)[keyof typeof ContainerEventKind]

/** Details of a {@link ContainerEventKind.CREATED} entry. `parentId` is absent when created as a root. */
export interface ContainerCreatedDetails {
    name: string
    parentId?: number
}

/** Details of a {@link ContainerEventKind.RENAMED} entry. */
export interface ContainerRenamedDetails {
    from: string
    to: string
}

/** Details of a {@link ContainerEventKind.MOVED} entry. Either end is `null` when it was or became a root. */
export interface ContainerMovedDetails {
    from: number | null
    to: number | null
}

/** Details of a {@link ContainerEventKind.DELETED} entry, which repeats the id the container had. */
export interface ContainerDeletedDetails {
    id: number
    name: string
}

/**
 * Payload of a history entry. The variant is selected by the entry's `eventKind` rather than by a
 * discriminator inside the payload, so narrow on `eventKind` before reading it.
 */
export type ContainerHistoryDetails =
    | ContainerCreatedDetails
    | ContainerRenamedDetails
    | ContainerMovedDetails
    | ContainerDeletedDetails

export interface InventoryContainerHistory {
    id: number
    containerId?: number | null
    stationId: string
    eventKind: ContainerEventKindName
    eventTs: string
    actorId?: number | null
    details?: ContainerHistoryDetails | null
}

export interface ItemLocationResponse {
    itemId: number
    containerId?: number | null
    pathSegments: string[]
    pathIds: number[]
    pathDisplay: string
}

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

export interface ContainerCheckItemResult {
    itemId: number | null
    inventoryId: number | null
    result: string
    note?: string
}

export interface CompleteContainerCheckRequest {
    deep: boolean
    items: ContainerCheckItemResult[]
}

export async function listExpectedItemsInContainer(
    containerId: number,
    deep: boolean,
): Promise<import('./types').InventoryItem[]> {
    const res = await client.get<import('./types').InventoryItem[]>(
        `/inventory-checks/container/${containerId}/expected`,
        {params: {deep}},
    )
    return res.data
}

export interface ItemLastCheck {
    itemId: number
    result: string
    checkedAt: string
    checkerName: string
}

export interface ItemCheckHistoryEntry {
    checkId: number
    result: string
    checkedAt: string
    checkerName: string
    containerName: string | null
    scope: 'CONTAINER' | 'MEMBER'
    note: string
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
): Promise<unknown> {
    const res = await client.post(`/inventory-checks/container/${containerId}/complete`, body)
    return res.data
}

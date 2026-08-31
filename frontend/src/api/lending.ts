/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'

// -- Types --

export const LendingStatus = {
    REQUESTED: 'REQUESTED',
    APPROVED: 'APPROVED',
    DECLINED: 'DECLINED',
    LENT: 'LENT',
    RETURNED: 'RETURNED',
    CLOSED: 'CLOSED',
} as const

export type LendingStatusName = (typeof LendingStatus)[keyof typeof LendingStatus]

export interface LendingRequest {
    id: number
    requestingStationId: string
    owningStationId: string
    status: LendingStatusName
    requestedDateFrom: string
    requestedDateTo: string | null
    createdBy: number
    createdAt: string
    updatedAt: string
}

export interface LendingRequestResponse {
    request: LendingRequest
    requestingStationName: string
    owningStationName: string
    isOwner: boolean
    itemSummary: string
    overdue: boolean
}

export interface LendingRequestItem {
    id: number
    requestId: number
    inventoryId: number | null
    itemId: number | null
    quantity: number
    assignedItemId: number | null
}

export interface EnrichedItem {
    item: LendingRequestItem
    inventoryName: string
}

export interface LendingRequestDetail {
    request: LendingRequestResponse
    items: EnrichedItem[]
}

export interface LendingMessage {
    id: number
    requestId: number
    senderStationId: string
    senderMemberId: number | null
    message: string
    isSystem: boolean
    createdAt: string
}

export interface AvailableItemDetail {
    itemId: number
    inventoryId: number
    inventoryName: string
    internalId: string
    itemName: string
    sizeName: string | null
    requestItemId: number
    preselected: boolean
}

export interface EnrichedMessage {
    message: LendingMessage
    senderName: string | null
    senderStationName: string
}

export interface InventoryBlock {
    id: number
    stationId: string
    inventoryId: number | null
    itemId: number | null
    blockFrom: string
    blockTo: string
    reason: string
    inventoryName: string | null
    itemName: string | null
    itemInternalId: string | null
}

export interface CreateLendingRequestPayload {
    owningStationId: string
    dateFrom: string
    dateTo: string | null
    items: { inventoryId?: number | null; itemId?: number | null; quantity: number }[]
}

export interface CreateBlockPayload {
    inventoryId?: number | null
    itemId?: number | null
    blockFrom: string
    blockTo: string
    reason?: string
}

export interface AvailableInventoryEntry {
    inventoryId: number
    inventoryName: string
    stationId: string
    stationName: string
    availableCount: number
    distanceKm: number | null
}

/**
 * Why a browse answer came back empty. It names the situation and never the gear: which
 * inventories a partner holds back is that partner's business.
 */
export const LendingEmptyReason = {
    NOTHING_SHARED: 'NOTHING_SHARED',
    NOTHING_FREE: 'NOTHING_FREE',
} as const

export type LendingEmptyReasonName = (typeof LendingEmptyReason)[keyof typeof LendingEmptyReason]

export interface AvailableInventoryResult {
    entries: AvailableInventoryEntry[]
    emptyReason: LendingEmptyReasonName | null
}

// -- What this station offers --

export const ShareGrant = {
    GRANT: 'GRANT',
    WITHHOLD: 'WITHHOLD',
} as const

export type ShareGrantName = (typeof ShareGrant)[keyof typeof ShareGrant]

export const ShareScope = {
    ALL_PARTNERS: 'ALL_PARTNERS',
    SPECIFIC: 'SPECIFIC',
} as const

export type ShareScopeName = (typeof ShareScope)[keyof typeof ShareScope]

export interface ShareSetting {
    shared: boolean
    grant: ShareGrantName | null
    scope: ShareScopeName | null
    partnerIds: number[]
}

export interface InventoryShare {
    id: number
    stationId: number
    inventoryId: number | null
    itemId: number | null
    shareScope: ShareScopeName
    shareGrant: ShareGrantName
}

export interface SharePartner {
    partnerId: number
    stationName: string
}

export interface ShareDetail {
    share: InventoryShare
    inventoryName: string | null
    itemName: string | null
    itemInternalId: string | null
    partners: SharePartner[]
}

export interface SetSharePayload {
    grant: ShareGrantName
    scope: ShareScopeName
    partnerIds: number[]
}

// -- Lent-out items by inventory --

export interface LentOutItem {
    requestItemId: number
    requestId: number
    itemId: number | null
    quantity: number
    assignedItemId: number | null
    status: string
    dateFrom: string
    dateTo: string | null
    requestingStationName: string
}

export async function getLentOutByInventory(inventoryId: number): Promise<LentOutItem[]> {
    const res = await client.get<LentOutItem[]>(`/lending/inventory/${inventoryId}/lent-out`)
    return res.data
}

// -- Available inventory --

export async function listAvailable(options?: { q?: string; from?: string; to?: string }): Promise<AvailableInventoryResult> {
    const params: Record<string, string> = {}
    if (options?.q) params.q = options.q
    if (options?.from) params.from = options.from
    if (options?.to) params.to = options.to
    const res = await client.get<AvailableInventoryResult>('/federated/lending/available', {params})
    return res.data
}

// -- Sharing --

export async function listShares(): Promise<ShareDetail[]> {
    const res = await client.get<ShareDetail[]>('/lending/shares')
    return res.data
}

export async function getInventoryShare(inventoryId: number): Promise<ShareSetting> {
    const res = await client.get<ShareSetting>(`/lending/shares/inventory/${inventoryId}`)
    return res.data
}

export async function setInventoryShare(inventoryId: number, payload: SetSharePayload): Promise<ShareSetting> {
    const res = await client.put<ShareSetting>(`/lending/shares/inventory/${inventoryId}`, payload)
    return res.data
}

export async function removeInventoryShare(inventoryId: number): Promise<void> {
    await client.delete(`/lending/shares/inventory/${inventoryId}`)
}

export async function getItemShare(itemId: number): Promise<ShareSetting> {
    const res = await client.get<ShareSetting>(`/lending/shares/item/${itemId}`)
    return res.data
}

export async function setItemShare(itemId: number, payload: SetSharePayload): Promise<ShareSetting> {
    const res = await client.put<ShareSetting>(`/lending/shares/item/${itemId}`, payload)
    return res.data
}

export async function removeItemShare(itemId: number): Promise<void> {
    await client.delete(`/lending/shares/item/${itemId}`)
}

// -- Requests --

const requests = createCrudResource<
    LendingRequestResponse,
    CreateLendingRequestPayload,
    CreateLendingRequestPayload,
    LendingRequestDetail
>('/lending/requests')

const blocks = createCrudResource<InventoryBlock, CreateBlockPayload>('/lending/blocks')

export const listRequests = requests.list
export const createRequest = requests.create
export const getRequest = requests.get

export async function approveRequest(id: number): Promise<LendingRequestResponse> {
    const res = await client.post<LendingRequestResponse>(`/lending/requests/${id}/approve`)
    return res.data
}

export async function declineRequest(id: number, reason?: string): Promise<LendingRequestResponse> {
    const res = await client.post<LendingRequestResponse>(`/lending/requests/${id}/decline`, {reason: reason || ''})
    return res.data
}

export async function markLent(id: number): Promise<LendingRequestResponse> {
    const res = await client.post<LendingRequestResponse>(`/lending/requests/${id}/lent`)
    return res.data
}

export async function markReturned(id: number): Promise<LendingRequestResponse> {
    const res = await client.post<LendingRequestResponse>(`/lending/requests/${id}/returned`)
    return res.data
}

export async function closeRequest(id: number): Promise<LendingRequestResponse> {
    const res = await client.post<LendingRequestResponse>(`/lending/requests/${id}/close`)
    return res.data
}

// -- Item assignment --

export async function getAvailableItems(requestId: number): Promise<AvailableItemDetail[]> {
    const res = await client.get<AvailableItemDetail[]>(`/lending/requests/${requestId}/available-items`)
    return res.data
}

export async function assignItems(requestId: number, items: { requestItemId: number; itemId: number }[]): Promise<void> {
    await client.post(`/lending/requests/${requestId}/assign-items`, {items})
}

// -- Messages --

export async function getMessages(requestId: number): Promise<EnrichedMessage[]> {
    const res = await client.get<EnrichedMessage[]>(`/lending/requests/${requestId}/messages`)
    return res.data
}

export async function sendMessage(requestId: number, message: string): Promise<LendingMessage> {
    const res = await client.post<LendingMessage>(`/lending/requests/${requestId}/messages`, {message})
    return res.data
}

// -- Blocks --

export const listBlocks = blocks.list
export const createBlock = blocks.create
export const deleteBlock = blocks.remove

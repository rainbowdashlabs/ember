/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

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
}

// -- Available inventory --

export async function listAvailable(options?: { q?: string; from?: string; to?: string }): Promise<AvailableInventoryEntry[]> {
    const params: Record<string, string> = {}
    if (options?.q) params.q = options.q
    if (options?.from) params.from = options.from
    if (options?.to) params.to = options.to
    const res = await client.get<AvailableInventoryEntry[]>('/lending/available', {params})
    return res.data
}

// -- Requests --

export async function listRequests(): Promise<LendingRequestResponse[]> {
    const res = await client.get<LendingRequestResponse[]>('/lending/requests')
    return res.data
}

export async function createRequest(data: CreateLendingRequestPayload): Promise<LendingRequestResponse> {
    const res = await client.post<LendingRequestResponse>('/lending/requests', data)
    return res.data
}

export async function getRequest(id: number): Promise<LendingRequestDetail> {
    const res = await client.get<LendingRequestDetail>(`/lending/requests/${id}`)
    return res.data
}

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

export async function listBlocks(): Promise<InventoryBlock[]> {
    const res = await client.get<InventoryBlock[]>('/lending/blocks')
    return res.data
}

export async function createBlock(data: CreateBlockPayload): Promise<InventoryBlock> {
    const res = await client.post<InventoryBlock>('/lending/blocks', data)
    return res.data
}

export async function deleteBlock(id: number): Promise<void> {
    await client.delete(`/lending/blocks/${id}`)
}

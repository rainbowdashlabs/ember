/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {
    AvailableInventoryResult,
    AvailableItemDetail,
    components,
    CreateBlockRequest,
    CreateLendingRequest,
    EnrichedMessage,
    InventoryBlock,
    ItemAssignment,
    LendingMessage,
    LendingRequestDetail,
    LendingRequestResponse,
    LentOutItem,
    SetShareRequest,
    ShareDetail,
    ShareSetting,
} from './generated/schema'

type Schemas = components['schemas']

export type LendingEmptyReasonName = Schemas['EmptyReason']

/**
 * Why a browse answer came back empty. It names the situation and never the gear: which
 * inventories a partner holds back is that partner's business.
 */
export const LendingEmptyReason = {
    NOTHING_SHARED: 'NOTHING_SHARED',
    NOTHING_FREE: 'NOTHING_FREE',
} as const satisfies Record<LendingEmptyReasonName, LendingEmptyReasonName>

/** Which of the three levels a sharing row speaks at. The narrowest one that exists decides. */
export type ShareTarget = 'inventory' | 'art' | 'item'

export async function getLentOutByInventory(inventoryId: number): Promise<LentOutItem[]> {
    const res = await client.get<LentOutItem[]>(`/lending/inventory/${inventoryId}/lent-out`)
    return res.data
}

export async function listAvailable(options?: { q?: string; from?: string; to?: string }): Promise<AvailableInventoryResult> {
    const params: Record<string, string> = {}
    if (options?.q) params.q = options.q
    if (options?.from) params.from = options.from
    if (options?.to) params.to = options.to
    const res = await client.get<AvailableInventoryResult>('/federated/lending/available', {params})
    return res.data
}

/**
 * What one row of the station's whole offer says, in the shape a single screen reads.
 *
 * <p>A row existing at all is what "shared" means: it says somebody has decided about this thing,
 * and the grant then says which way. Reading the overview and reading one thing therefore give the
 * same answer instead of two that could drift.
 */
export function settingOf(detail: ShareDetail): ShareSetting {
    return {
        shared: true,
        grant: detail.share.shareGrant,
        scope: detail.share.shareScope,
        partnerIds: detail.partners.map(partner => partner.partnerId),
    }
}

export async function listShares(): Promise<ShareDetail[]> {
    const res = await client.get<ShareDetail[]>('/lending/shares')
    return res.data
}

/**
 * What is currently said about one inventory, one kind or one piece. The three levels answer the
 * same shape, so the level travels as an argument rather than splitting into three near-identical
 * calls that every caller would then have to choose between.
 */
export async function getShare(target: ShareTarget, id: number): Promise<ShareSetting> {
    const res = await client.get<ShareSetting>(`/lending/shares/${target}/${id}`)
    return res.data
}

export async function setShare(target: ShareTarget, id: number, payload: SetShareRequest): Promise<ShareSetting> {
    const res = await client.put<ShareSetting>(`/lending/shares/${target}/${id}`, payload)
    return res.data
}

export async function removeShare(target: ShareTarget, id: number): Promise<void> {
    await client.delete(`/lending/shares/${target}/${id}`)
}

const requests = createCrudResource<
    LendingRequestResponse,
    CreateLendingRequest,
    CreateLendingRequest,
    LendingRequestDetail
>('/lending/requests')

const blocks = createCrudResource<InventoryBlock, CreateBlockRequest>('/lending/blocks')

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

export async function getAvailableItems(requestId: number): Promise<AvailableItemDetail[]> {
    const res = await client.get<AvailableItemDetail[]>(`/lending/requests/${requestId}/available-items`)
    return res.data
}

export async function assignItems(requestId: number, items: ItemAssignment[]): Promise<void> {
    await client.post(`/lending/requests/${requestId}/assign-items`, {items})
}

export async function getMessages(requestId: number): Promise<EnrichedMessage[]> {
    const res = await client.get<EnrichedMessage[]>(`/lending/requests/${requestId}/messages`)
    return res.data
}

export async function sendMessage(requestId: number, message: string): Promise<LendingMessage> {
    const res = await client.post<LendingMessage>(`/lending/requests/${requestId}/messages`, {message})
    return res.data
}

export const listBlocks = blocks.list
export const createBlock = blocks.create
export const deleteBlock = blocks.remove

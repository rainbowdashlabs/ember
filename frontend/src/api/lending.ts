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
    CatalogProduct,
    components,
    CreateBlockRequest,
    CreateLendingRequest,
    EnrichedMessage,
    InventoryBlock,
    ItemAssignment,
    LendingCalendar,
    LendingMessage,
    LendingRequestDetail,
    LendingRequestResponse,
    LentOutItem,
    ProductDetail,
    ProductList,
    RemoteProductDays,
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

/** What every lending partner offers, one product per inventory; best match first where something was searched. */
export async function listProducts(options?: { q?: string; from?: string; to?: string }): Promise<ProductList> {
    const params: Record<string, string> = {}
    if (options?.q) params.q = options.q
    if (options?.from) params.from = options.from
    if (options?.to) params.to = options.to
    const res = await client.get<ProductList>('/federated/lending/products', {params})
    return res.data
}

/** One product of a lending partner with its kinds, single pieces and where the gear is kept. */
export async function getProduct(
    stationUid: string, inventoryId: number, options?: { from?: string; to?: string },
): Promise<ProductDetail> {
    const params: Record<string, string> = {}
    if (options?.from) params.from = options.from
    if (options?.to) params.to = options.to
    const res = await client.get<ProductDetail>(`/federated/lending/products/${stationUid}/${inventoryId}`, {params})
    return res.data
}

/** What is free of a partner's product on each day of a month, as `yyyy-MM`; of one piece where given. */
export async function getProductDays(
    stationUid: string, inventoryId: number, month: string, itemId?: number,
): Promise<RemoteProductDays> {
    const params: Record<string, string> = {month}
    if (itemId != null) params.itemId = String(itemId)
    const res = await client.get<RemoteProductDays>(`/federated/lending/products/${stationUid}/${inventoryId}/days`, {params})
    return res.data
}

/** The station's own offer, product by product; read through one partnership where one is given. */
export async function getCatalog(partnerId?: number): Promise<CatalogProduct[]> {
    const params: Record<string, string> = {}
    if (partnerId != null) params.partnerId = String(partnerId)
    const res = await client.get<CatalogProduct[]>('/lending/catalog', {params})
    return res.data
}

/** The station's products, the requests for its gear and its block periods from one day to another. */
export async function getCalendar(from: string, to: string): Promise<LendingCalendar> {
    const res = await client.get<LendingCalendar>('/lending/calendar', {params: {from, to}})
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
 * What one row of the station's whole offer shows, in the shape a single screen reads.
 *
 * <p>A row existing at all is what "shared" means: it shows somebody has decided about this thing,
 * and the grant then shows which way. Reading the overview and reading one thing therefore give the
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
 * What is currently decided about one inventory, one kind or one piece. The three levels answer the
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

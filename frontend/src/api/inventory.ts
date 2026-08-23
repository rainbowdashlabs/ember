/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {MemberIdentity} from './types'

export const InventoryTypes = {
    INTERNAL: 'INTERNAL',
    EXTERNAL: 'EXTERNAL',
    MIXED: 'MIXED',
} as const

export type InventoryTypeName = (typeof InventoryTypes)[keyof typeof InventoryTypes]

/**
 * Who owns an item: the station running its inventory, or the one body above that station.
 * Members never own tracked items.
 */
export const ItemOwner = {
    STATION: 'STATION',
    CLUSTER: 'CLUSTER',
} as const

export type ItemOwnerName = (typeof ItemOwner)[keyof typeof ItemOwner]

/**
 * Who has an item right now, which is a different question from who owns it. A station can hold
 * gear it does not own, and an owner can be holding gear nobody at the station has seen for a year.
 */
export const ItemCustody = {
    WITH_OWNER: 'WITH_OWNER',
    AT_STATION: 'AT_STATION',
    WITH_MEMBER: 'WITH_MEMBER',
    WITH_PARTNER: 'WITH_PARTNER',
    IN_TRANSIT: 'IN_TRANSIT',
    LOST: 'LOST',
} as const

export type ItemCustodyName = (typeof ItemCustody)[keyof typeof ItemCustody]

/** Whether an item in this custody is free to hand to somebody. */
export function isAvailable(custody?: ItemCustodyName | null): boolean {
    return custody === ItemCustody.WITH_OWNER || custody === ItemCustody.AT_STATION
}

export interface Inventory {
    id: number
    stationId: string
    name?: string
    inventoryType?: InventoryTypeName
    hasSizes: boolean
}

export interface InventoryRequest {
    name?: string
    inventoryType?: InventoryTypeName
    hasSizes: boolean
}

export interface InventoryDetail {
    id: number
    stationId: string
    name?: string
    inventoryType?: InventoryTypeName
    hasSizes: boolean
    sizes?: InventorySize[]
}

export interface InventorySize {
    id: number
    inventoryId: number
    label?: string
    position: number
    note?: string
}

export interface SizeRequest {
    label?: string
    position: number
    note?: string
}

export interface InventoryItem {
    id: number
    inventoryId: number
    internalId?: string
    name?: string
    sizeId?: number | null
    metadata?: string | ItemMetadata | null
    assignedTo?: number | null
    lostAt?: string | null
    /** What was written when it was reported missing, cleared when it turns up again. */
    lostNote?: string | null
    /** The member who wrote that note, which is the guardian when one wrote it for somebody. */
    lostNoteBy?: number | null
    ownerKind?: ItemOwnerName | null
    ownerClusterId?: number | null
    custody?: ItemCustodyName | null
    custodyStationId?: number | null
    custodyMovementId?: number | null
    containerId?: number | null
}

export interface ItemMetadata {
    fields: Record<string, {kind: string; value: unknown}>
}

export interface ItemRequest {
    internalId?: string
    name?: string
    sizeId?: number
    metadata?: ItemMetadata
    ownerKind?: ItemOwnerName
    ownerClusterId?: number | null
}

export interface AssignRequest {
    memberId?: number | null
    memberName?: string
}

/** What was written when gear was reported missing. */
export interface LostRequest {
    note?: string
}

/** What the station has decided about its gear beyond any one inventory. */
export interface InventorySettings {
    /** Whether a member reporting their own gear missing has to write a note about it. */
    lossNoteRequired: boolean
}

export interface InventoryRequirement {
    id: number
    inventoryId: number
    userType: string
    groupId: number
    quantity: number
    position: number
}

export interface RequirementRequest {
    inventoryId: number
    userType?: string
    groupId?: number
    quantity?: number
}

export interface InventoryItemHistory {
    id: number
    itemId: number
    memberId?: number | null
    memberName?: string
    givenOut?: string
    returned?: string | null
    memberIdentity?: MemberIdentity | null
}

export interface MyInventoryItem {
    id: number
    inventoryId: number
    name?: string
    internalId?: string
    inventoryName: string
    sizeId?: number | null
    sizeName?: string | null
    lostAt?: string | null
    custody?: ItemCustodyName | null
    /** The open movement this item is on, when it is on its way to or from the member. */
    movementId?: number | null
    /** The step that movement is standing on, in the words the flow gives it. */
    movementStep?: string | null
    /** Whether it is the item coming to the member rather than the one leaving them. */
    movementIncoming?: boolean
    /** Who owns it, which a member is entitled to know about what they are looking after. */
    ownerKind?: ItemOwnerName | null
    ownerClusterId?: number | null
    /** What was written when it was reported missing. */
    lostNote?: string | null
    /** Who wrote that note, which is the guardian when one reported it for the member. */
    lostNoteBy?: MemberIdentity | null
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

export async function memberItems(memberId: number): Promise<MyInventoryItem[]> {
    const res = await client.get<MyInventoryItem[]>(`/station-members/${memberId}/inventory-items`)
    return res.data
}

interface RequirementQuantityRequest {
    quantity: number
}

const inventories = createCrudResource<
    Inventory,
    InventoryRequest,
    InventoryRequest,
    InventoryDetail
>('/inventories')

const items = createCrudResource<InventoryItem, ItemRequest>('/inventory-items')

const requirements = createCrudResource<
    InventoryRequirement,
    RequirementRequest,
    RequirementQuantityRequest,
    InventoryRequirement,
    InventoryRequirement,
    void
>('/inventory-requirements')

// -- Inventories --

export const listInventories = inventories.list
export const getInventory = inventories.get
export const createInventory = inventories.create
export const updateInventory = inventories.update
export const deleteInventory = inventories.remove

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

export async function listAllItems(): Promise<InventoryItem[]> {
    const res = await client.get<InventoryItem[]>('/inventories/all-items')
    return res.data
}

export async function listAllSizes(): Promise<InventorySize[]> {
    const res = await client.get<InventorySize[]>('/inventories/all-sizes')
    return res.data
}

// -- Items --

export async function listItems(inventoryId: number): Promise<InventoryItem[]> {
    const res = await client.get<InventoryItem[]>(`/inventories/${inventoryId}/items`)
    return res.data
}

export interface InventorySummary {
    id: number
    stationId: string
    name?: string
    inventoryType?: string
    hasSizes: boolean
    itemCount: number
    lostCount: number
    procurementCount: number
    lentOutCount: number
}

export async function listSummaries(): Promise<InventorySummary[]> {
    const res = await client.get<InventorySummary[]>('/inventories/summary')
    return res.data
}

export const getItem = items.get

export async function findByInternalId(internalId: string): Promise<InventoryItem | null> {
    try {
        const res = await client.get<InventoryItem>('/inventory-items/by-internal-id', { params: { internalId } })
        return res.data
    } catch {
        return null
    }
}

export async function createItem(inventoryId: number, data: ItemRequest): Promise<InventoryItem> {
    const res = await client.post<InventoryItem>(`/inventories/${inventoryId}/items`, data)
    return res.data
}

export const updateItem = items.update
export const deleteItem = items.remove

export async function assignItem(id: number, data: AssignRequest): Promise<InventoryItem> {
    const res = await client.put<InventoryItem>(`/inventory-items/${id}/assign`, data)
    return res.data
}

export async function getItemHistory(id: number): Promise<InventoryItemHistory[]> {
    const res = await client.get<InventoryItemHistory[]>(`/inventory-items/${id}/history`)
    return res.data
}

/**
 * Reports a piece of gear missing.
 *
 * <p>Whoever looks after the station's gear may report any of it. Everybody else may report what is
 * assigned to them, and a guardian may report it for the person they act for.
 */
export async function markLost(id: number, request: LostRequest = {}): Promise<InventoryItem> {
    const res = await client.put<InventoryItem>(`/inventory-items/${id}/lost`, request)
    return res.data
}

export async function markFound(id: number): Promise<InventoryItem> {
    const res = await client.delete<InventoryItem>(`/inventory-items/${id}/lost`)
    return res.data
}

/** What the body that owns a piece of gear asks for before it will consider replacing it. */
export interface LossReportTerms {
    /** Whether there is an owner here to report to at all. */
    reportable: boolean
    requires?: LossReportRequirementName | null
}

export const LossReportRequirement = {
    NOTHING: 'NOTHING',
    NOTE: 'NOTE',
    DOCUMENT: 'DOCUMENT',
} as const

export type LossReportRequirementName = (typeof LossReportRequirement)[keyof typeof LossReportRequirement]

export async function lossReportTerms(itemId: number): Promise<LossReportTerms> {
    const res = await client.get<LossReportTerms>(`/inventory-items/${itemId}/loss-report`)
    return res.data
}

/**
 * Reports a missing item to the body that owns it, asking for a replacement.
 *
 * <p>Multipart because the owner may demand a document: a report short of what it asks for is refused
 * outright, and writing it first and attaching afterwards would leave half a request standing.
 */
export async function reportLoss(itemId: number, note: string, document?: File | null): Promise<void> {
    const form = new FormData()
    form.append('note', note)
    if (document) form.append('document', document)
    await client.post(`/inventory-items/${itemId}/loss-report`, form)
}

export async function getSettings(): Promise<InventorySettings> {
    const res = await client.get<InventorySettings>('/inventory-settings')
    return res.data
}

export async function updateSettings(settings: InventorySettings): Promise<InventorySettings> {
    const res = await client.put<InventorySettings>('/inventory-settings', settings)
    return res.data
}

// -- Requirements --

export const listAllRequirements = requirements.list
export const createRequirement = requirements.create
export const updateRequirement = requirements.update
export const deleteRequirement = requirements.remove

export async function listRequirements(inventoryId: number): Promise<InventoryRequirement[]> {
    const res = await client.get<InventoryRequirement[]>(`/inventories/${inventoryId}/requirements`)
    return res.data
}

export async function updateRequirementPosition(id: number, position: number): Promise<void> {
    await client.patch(`/inventory-requirements/${id}/position`, {position})
}

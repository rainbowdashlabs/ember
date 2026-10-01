/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, type NoContent} from './crud'
import {uploadFile} from './upload'
import type {
    AssignRequest,
    BorrowedItemResponse,
    components,
    HistoryResponse,
    Inventory,
    InventoryDetail,
    InventoryIntakeRow,
    InventoryItem,
    InventoryRequest,
    InventoryRequirement,
    InventorySettings,
    InventorySize,
    InventorySummary,
    ItemRequest,
    LossReportTerms,
    LostRequest,
    MemberRequirements,
    MyInventoryItem,
    MyRequirement,
    OwnerAboveResponse,
    RequirementRequest,
    RequirementResponse,
    SizeRequest,
    SwitchRefusal,
    UpdateRequirementRequest,
} from './generated/schema'

type Schemas = components['schemas']

export type InventoryTypeName = Schemas['InventoryType']

export const InventoryTypes = {
    INTERNAL: 'INTERNAL',
    EXTERNAL: 'EXTERNAL',
    MIXED: 'MIXED',
} as const satisfies Record<InventoryTypeName, InventoryTypeName>

/**
 * Whether gear filed here can be offered to a partner station at all.
 *
 * <p>An external inventory holds nothing but the gear of the body above the station. The station
 * does not own any of it and therefore cannot lend it, so an offer written on such an inventory
 * could never be filled and the controls for one do not belong on its screens.
 *
 * <p>A mixed inventory is the other case and stays open: the station's own pieces stand in it
 * beside the body's, and the pieces that are not the station's are dropped where the offer is read
 * rather than by hiding the decision.
 */
export function isLendableInventory(inventoryType: InventoryTypeName | null | undefined): boolean {
    return inventoryType !== InventoryTypes.EXTERNAL
}

export type ItemOwnerName = Schemas['ItemOwner']

/**
 * Who owns an item: the station running its inventory, the one body above that station, or a
 * federation partner the station has borrowed it from. Members never own tracked items.
 */
export const ItemOwner = {
    STATION: 'STATION',
    CLUSTER: 'CLUSTER',
    PARTNER_STATION: 'PARTNER_STATION',
} as const satisfies Record<ItemOwnerName, ItemOwnerName>

export type ItemCustodyName = Schemas['ItemCustody']

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
} as const satisfies Record<ItemCustodyName, ItemCustodyName>

/** Whether an item in this custody is free to hand to somebody. */
export function isAvailable(custody?: ItemCustodyName | null): boolean {
    return custody === ItemCustody.WITH_OWNER || custody === ItemCustody.AT_STATION
}

/**
 * The two kinds of inventory, named on both sides.
 *
 * <p>The wire carries a single boolean, which reads as one kind and the absence of it. Screens
 * speak in names instead, so the reader can tell which of the two they have in front of them.
 */
export const InventoryKinds = {
    /** One thing in many copies: the shelf full of blousons. */
    STOCK: 'STOCK',
    /** Different things that belong together: twelve radios, a charging station and an antenna. */
    COLLECTION: 'COLLECTION',
} as const

export type InventoryKindName = (typeof InventoryKinds)[keyof typeof InventoryKinds]

/** Which kind the boolean on the wire stands for. */
export function inventoryKindOf(homogeneous: boolean): InventoryKindName {
    return homogeneous ? InventoryKinds.STOCK : InventoryKinds.COLLECTION
}

/** Whether a kind is the one thing in many copies, which is what the wire calls homogeneous. */
export function isStock(kind: InventoryKindName): boolean {
    return kind === InventoryKinds.STOCK
}

export type SwitchBlockerKindName = Schemas['SwitchBlockerKind']

/** What sort of thing stands in the way of an inventory changing what it holds. */
export const SwitchBlockerKinds = {
    REQUIREMENT: 'REQUIREMENT',
    PROCUREMENT: 'PROCUREMENT',
    EXCHANGE: 'EXCHANGE',
    SIZE: 'SIZE',
    ART: 'ART',
} as const satisfies Record<SwitchBlockerKindName, SwitchBlockerKindName>

/** The name the backend puts on that refusal, which is how it is told from any other bad request. */
export const SWITCH_REFUSED = 'InventorySwitchRefusedException'

/**
 * Reads a refused change of kind out of a failed request, or nothing when it was some other failure.
 */
export function switchRefusal(e: unknown): SwitchRefusal | undefined {
    const data = (e as {response?: {data?: Partial<SwitchRefusal>}})?.response?.data
    if (data?.error !== SWITCH_REFUSED) return undefined
    return {error: data.error, message: data.message ?? '', blockers: data.blockers ?? []}
}

/**
 * The little a dialogue about one piece needs in order to name it.
 *
 * <p>Both the loss and the exchange are raised from more than one screen now, and each screen holds
 * its pieces in the shape its own endpoint returns. What the dialogue reads is the same three words
 * either way.
 */
export interface NamedPiece {
    inventoryName: string
    name?: string
    sizeId?: number | null
    sizeName?: string | null
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

export async function memberRequirements(memberId: number): Promise<MemberRequirements> {
    const res = await client.get<MemberRequirements>(`/station-members/${memberId}/inventory-requirements`)
    return res.data
}

/** Takes a fresh piece into an inventory and hands it straight to the member. */
export async function handOutNewItem(memberId: number, inventoryId: number, sizeId?: number | null): Promise<InventoryItem> {
    const res = await client.post<InventoryItem>(`/station-members/${memberId}/inventory-items`, {inventoryId, sizeId})
    return res.data
}

const inventories = createCrudResource<
    Inventory,
    InventoryRequest,
    InventoryRequest,
    InventoryDetail
>('/inventories')

const items = createCrudResource<InventoryItem, ItemRequest>('/inventory-items')

const requirements = createCrudResource<
    RequirementResponse,
    RequirementRequest,
    UpdateRequirementRequest,
    RequirementResponse,
    InventoryRequirement,
    NoContent
>('/inventory-requirements')

export const listInventories = inventories.list
export const getInventory = inventories.get
export const createInventory = inventories.create
export const updateInventory = inventories.update
export const deleteInventory = inventories.remove

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

export async function listItems(inventoryId: number): Promise<InventoryItem[]> {
    const res = await client.get<InventoryItem[]>(`/inventories/${inventoryId}/items`)
    return res.data
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

/**
 * Writes down several pieces at once and hands each one to the member on its line.
 *
 * <p>A line that names no piece at all is passed over by the server, so a table opened with a row
 * per member needs no tidying up before it is saved.
 */
export async function takeStock(inventoryId: number, rows: InventoryIntakeRow[]): Promise<InventoryItem[]> {
    const res = await client.post<InventoryItem[]>(`/inventories/${inventoryId}/items/batch`, {rows})
    return res.data
}

export async function createItem(inventoryId: number, data: ItemRequest): Promise<InventoryItem> {
    const res = await client.post<InventoryItem>(`/inventories/${inventoryId}/items`, data)
    return res.data
}

export const updateItem = items.update
export const deleteItem = items.remove

/**
 * Moves a piece into another inventory of the same station, keeping the piece it has always been.
 *
 * Its identifier, its history, who has it and where it has been all stay with it. Only the size
 * cannot come along as it stands: the size list belongs to the inventory being left, so the piece
 * keeps a size of the same name in the new list and arrives without one where there is none.
 */
export async function moveItem(id: number, inventoryId: number): Promise<InventoryItem> {
    const res = await client.put<InventoryItem>(`/inventory-items/${id}/inventory`, {inventoryId})
    return res.data
}

export async function assignItem(id: number, data: AssignRequest): Promise<InventoryItem> {
    const res = await client.put<InventoryItem>(`/inventory-items/${id}/assign`, data)
    return res.data
}

export async function getItemHistory(id: number): Promise<HistoryResponse[]> {
    const res = await client.get<HistoryResponse[]>(`/inventory-items/${id}/history`)
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

export type LossReportRequirementName = Schemas['LossReportRequirement']

export const LossReportRequirement = {
    NOTHING: 'NOTHING',
    NOTE: 'NOTE',
    DOCUMENT: 'DOCUMENT',
} as const satisfies Record<LossReportRequirementName, LossReportRequirementName>

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
    await uploadFile(`/inventory-items/${itemId}/loss-report`, {note, document})
}

/** Everything the station has borrowed, by partner and then by name. */
export async function listBorrowed(): Promise<BorrowedItemResponse[]> {
    const res = await client.get<BorrowedItemResponse[]>('/inventory-borrowed')
    return res.data
}

/**
 * The body above this station that keeps its gear in Ember, or null when there is none. What a station
 * may ask for follows from it: with nobody above, there is nobody to ask.
 */
export async function ownerAbove(): Promise<string | null> {
    const res = await client.get<OwnerAboveResponse>('/inventory-owner-above')
    return res.data.name
}

export async function getSettings(): Promise<InventorySettings> {
    const res = await client.get<InventorySettings>('/inventory-settings')
    return res.data
}

export async function updateSettings(settings: InventorySettings): Promise<InventorySettings> {
    const res = await client.put<InventorySettings>('/inventory-settings', settings)
    return res.data
}

export const listAllRequirements = requirements.list
export const createRequirement = requirements.create
export const updateRequirement = requirements.update
export const deleteRequirement = requirements.remove

export async function updateRequirementPosition(id: number, position: number): Promise<void> {
    await client.patch(`/inventory-requirements/${id}/position`, {position})
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {MemberIdentity} from './types'
import {uploadFile} from './upload'

export const InventoryTypes = {
    INTERNAL: 'INTERNAL',
    EXTERNAL: 'EXTERNAL',
    MIXED: 'MIXED',
} as const

export type InventoryTypeName = (typeof InventoryTypes)[keyof typeof InventoryTypes]

/**
 * Who owns an item: the station running its inventory, the one body above that station, or a
 * federation partner the station has borrowed it from. Members never own tracked items.
 */
export const ItemOwner = {
    STATION: 'STATION',
    CLUSTER: 'CLUSTER',
    PARTNER_STATION: 'PARTNER_STATION',
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

export interface Inventory {
    id: number
    stationId: string
    name?: string
    inventoryType?: InventoryTypeName
    hasSizes: boolean
    /**
     * Whether the inventory is a stock rather than a collection. Requirements, orders, exchanges and
     * sizes are only offered for a stock.
     */
    homogeneous: boolean
    /**
     * Whether this is the station's one shelf for gear belonging to somebody else. It appears on the
     * first handover, it can be renamed, and it refuses to go while anything is still on it.
     */
    borrowed: boolean
}

export interface InventoryRequest {
    name?: string
    inventoryType?: InventoryTypeName
    hasSizes: boolean
    /** Left out it means "as it was", which on creation is one thing in many copies. */
    homogeneous?: boolean
}

export interface InventoryDetail {
    id: number
    stationId: string
    name?: string
    inventoryType?: InventoryTypeName
    hasSizes: boolean
    homogeneous: boolean
    sizes?: InventorySize[]
}

/** What sort of thing stands in the way of an inventory changing what it holds. */
export const SwitchBlockerKinds = {
    REQUIREMENT: 'REQUIREMENT',
    PROCUREMENT: 'PROCUREMENT',
    EXCHANGE: 'EXCHANGE',
    SIZE: 'SIZE',
} as const

export type SwitchBlockerKindName = (typeof SwitchBlockerKinds)[keyof typeof SwitchBlockerKinds]

/**
 * One live thing standing in the way, named well enough to go and deal with.
 *
 * @property id the thing's own identifier, so the refusal can link straight to it
 * @property label what it is called on the screen it lives on
 */
export interface SwitchBlocker {
    kind: SwitchBlockerKindName
    id: number
    label: string
}

/** The body of the refusal the backend sends when a change of kind is turned down. */
export interface SwitchRefusal {
    error: string
    message?: string
    blockers: SwitchBlocker[]
}

/** The name the backend puts on that refusal, which is how it is told from any other bad request. */
export const SWITCH_REFUSED = 'InventorySwitchRefusedException'

/**
 * Reads a refused change of kind out of a failed request, or nothing when it was some other failure.
 */
export function switchRefusal(e: unknown): SwitchRefusal | undefined {
    const data = (e as {response?: {data?: SwitchRefusal}})?.response?.data
    if (data?.error !== SWITCH_REFUSED) return undefined
    return {error: data.error, message: data.message, blockers: data.blockers ?? []}
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
    /**
     * The kind of thing this piece is, or null when nobody has said. Null is the ordinary state
     * rather than a gap: most pieces are written down by a path with nobody present to name a kind.
     */
    artId?: number | null
    metadata?: string | ItemMetadata | null
    assignedTo?: number | null
    lostAt?: string | null
    /** What was written when it was reported missing, cleared when it turns up again. */
    lostNote?: string | null
    /** The member who wrote that note, which is the guardian when one wrote it for somebody. */
    lostNoteBy?: number | null
    ownerKind?: ItemOwnerName | null
    /** The owning association's stable identity. An internal id never leaves the backend. */
    ownerClusterId?: string | null
    /** The partner station that owns it, set only for gear borrowed from one. */
    ownerStationId?: number | null
    /** The line of the lending request a borrowed piece came in on, and nothing else. */
    loanRequestItemId?: number | null
    custody?: ItemCustodyName | null
    custodyStationId?: number | null
    /** The partner holding the piece while it is out on loan. */
    custodyPartnerStationId?: number | null
    custodyMovementId?: number | null
    containerId?: number | null
}

export interface ItemMetadata {
    fields: Record<string, {kind: string; value: unknown}>
}

export interface ItemRequest {
    internalId?: string
    /** What the piece is called. The kind sits beside this and never replaces it. */
    name?: string
    sizeId?: number
    /** The kind of thing it is, or null for none. */
    artId?: number | null
    metadata?: ItemMetadata
    ownerKind?: ItemOwnerName
    /** The owning association's stable identity, which is what the backend takes back. */
    ownerClusterId?: string | null
}

export interface AssignRequest {
    memberId?: number | null
    memberName?: string
}

/** What was written when gear was reported missing. */
export interface LostRequest {
    note?: string
    /**
     * The self-check this was reported during, where it was reported during one.
     *
     * <p>The piece counts as missing from the moment it is said, task or no task. Naming the task
     * only records that it happened while the member was answering.
     */
    selfCheckId?: number | null
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
    /** The group of stations it counts at, absent when it counts at every station reading it. */
    stationGroupId?: number | null
    quantity: number
    position: number
    /**
     * What the requirement points at, sent along because an association's inventory is not among the
     * station's own and the screen would have nothing to look the name up in.
     */
    inventoryName?: string
    /**
     * The association that wrote it, absent on one the station wrote itself. A station reads what the
     * association asks of its people and changes none of it, so the name is both the badge and the reason
     * the controls are gone.
     */
    clusterName?: string | null
}

export interface RequirementRequest {
    inventoryId: number
    userType?: string
    groupId?: number
    /**
     * The group of stations it counts at, absent for every station reading it. Only an association
     * writing its own requirement may name one.
     */
    stationGroupId?: number
    quantity?: number
}

export interface InventoryItemHistory {
    id: number
    itemId: number
    memberId?: number | null
    memberName?: string
    givenOut?: string
    returned?: string | null
    /** Whether a check ended the spell by putting the record right rather than by a hand-back. */
    corrected?: boolean
    memberIdentity?: MemberIdentity | null
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

export interface MyInventoryItem {
    id: number
    inventoryId: number
    name?: string
    internalId?: string
    inventoryName: string
    /**
     * Whether the inventory holds one thing in many copies, which is what makes the piece
     * exchangeable. Among a drawer of different things there is nothing to swap it for.
     */
    inventoryHomogeneous: boolean
    sizeId?: number | null
    sizeName?: string | null
    lostAt?: string | null
    custody?: ItemCustodyName | null
    /** The open movement running on this item, which the member is still holding. */
    movementId?: number | null
    /** The step that movement is standing on, in the words the flow gives it. */
    movementStep?: string | null
    /** Who owns it, which a member is entitled to know about what they are looking after. */
    ownerKind?: ItemOwnerName | null
    ownerClusterId?: string | null
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

/**
 * One inventory a member is required to hold something from, with what they hold towards it.
 */
export interface RequiredInventoryItem {
    inventoryId: number
    inventoryName: string
    inventoryType: string
    hasSizes: boolean
    /**
     * Whether the inventory holds one thing in many copies, which is what makes a piece of it
     * exchangeable. Among a drawer of different things there is nothing to swap one for.
     */
    homogeneous: boolean
    sizes: InventorySize[]
    requiredQuantity: number
    /** What the member has towards it, counting pieces away in an exchange. */
    assignedQuantity: number
    /** How many of those are away in an exchange rather than in their hands. */
    inExchangeQuantity: number
}

/**
 * What a member is expected to hold, and which pieces of those inventories are in nobody's hands.
 *
 * The same requirements the stock-taking works from, read without taking the member's record for a
 * check, so their own page can show what is still missing and hand a piece over on the spot.
 */
export interface MemberRequirements {
    required: RequiredInventoryItem[]
    unassigned: Record<number, InventoryItem[]>
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
    /** Whether it is a stock rather than a collection. */
    homogeneous: boolean
    /** Whether it is the station's one shelf for gear belonging to somebody else. */
    borrowed: boolean
    itemCount: number
    lostCount: number
    procurementCount: number
    lentOutCount: number
    /** How many kinds are defined in it, which only a collection has any use for. */
    artCount: number
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
 * One line of a stock-taking: a piece the station already owns, and who holds it.
 *
 * <p>A line that names no piece at all is passed over by the server, so a table opened with a row
 * per member needs no tidying up before it is saved.
 */
export interface IntakeRow {
    memberId?: number | null
    internalId?: string | null
    sizeId?: number | null
    ownerKind?: ItemOwnerName
    metadata?: ItemMetadata
    /** Written down even with nothing on it, for gear there is nothing to record about. */
    askedFor?: boolean
}

/** Writes down several pieces at once and hands each one to the member on its line. */
export async function takeStock(inventoryId: number, rows: IntakeRow[]): Promise<InventoryItem[]> {
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
    await uploadFile(`/inventory-items/${itemId}/loss-report`, {note, document})
}

/**
 * The body above this station that keeps its gear in Ember, or null when there is none. What a station
 * may ask for follows from it: with nobody above, there is nobody to ask.
 */
/**
 * One piece the station is holding that belongs to a partner station.
 *
 * The row is a copy taken when the gear changed hands and is not kept in step with the owner's
 * afterwards, so what it shows is the thing as it was handed over.
 */
export interface BorrowedItem {
    item: InventoryItem
    /** The partner the gear belongs to. */
    ownerStationName: string
    /** The lending request it came in on, which is where anything about the loan is said. */
    loanRequestId: number
    /** The day the loan was asked to run to, or null when none was named. */
    dueOn?: string | null
}

/** Everything the station has borrowed, by partner and then by name. */
export async function listBorrowed(): Promise<BorrowedItem[]> {
    const res = await client.get<BorrowedItem[]>('/inventory-borrowed')
    return res.data
}

export async function ownerAbove(): Promise<string | null> {
    const res = await client.get<{name: string | null}>('/inventory-owner-above')
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

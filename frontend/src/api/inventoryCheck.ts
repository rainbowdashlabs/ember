/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {InventoryItem, InventorySize, ItemMetadata, ItemOwnerName, RequiredInventoryItem} from './inventory'
import type {MemberIdentity} from './types'

export interface MemberCheckSummary {
    memberId: number
    firstName?: string
    lastName?: string
    lastCheckedAt?: string | null
    checkerFirstName?: string | null
    checkerLastName?: string | null
    locked: boolean
    lockedBy?: number | null
    lockerFirstName?: string | null
    lockerLastName?: string | null
    userType?: string
    identity?: MemberIdentity | null
}

export interface CheckDetail {
    check: InventoryCheck
    checkerFirstName?: string
    checkerLastName?: string
    /**
     * Whoever said what the check records, where that is somebody other than the person who signed
     * it off. Empty on a check somebody walked themselves.
     */
    reporterFirstName?: string
    reporterLastName?: string
    items: EnrichedCheckItem[]
}

export interface EnrichedCheckItem {
    id: number
    itemId?: number | null
    itemName?: string | null
    internalId?: string | null
    inventoryName: string
    sizeName?: string | null
    result: CheckResult
    note: string
}

export interface InventoryCheckItem {
    id: number
    checkId: number
    itemId: number
    result: CheckResult
    note: string
}

export interface MemberCheckState {
    memberName: string
    memberIdentity?: MemberIdentity | null
    required: RequiredInventoryItem[]
    assigned: InventoryItem[]
    lastCheck?: InventoryCheck | null
    unassigned: Record<number, InventoryItem[]>
}

export interface InventoryCheck {
    id: number
    stationId: string
    memberId: number
    checkedBy: number
    checkedAt: string
}

export type CheckResult = 'CONFIRMED' | 'NOT_IN_POSSESSION' | 'LOST'

export interface CheckItemResult {
    itemId?: number | null
    inventoryId?: number | null
    result: CheckResult
    note?: string
}

export interface CompleteCheckRequest {
    items: CheckItemResult[]
}

export interface NextMemberResponse {
    memberId: number | null
}

export async function getCheckOverview(): Promise<MemberCheckSummary[]> {
    const res = await client.get<MemberCheckSummary[]>('/inventory-checks')
    return res.data
}

export async function startCheck(memberId: number): Promise<MemberCheckState> {
    const res = await client.post<MemberCheckState>(`/inventory-checks/${memberId}/start`)
    return res.data
}

export async function completeCheck(memberId: number, data: CompleteCheckRequest): Promise<InventoryCheck> {
    const res = await client.post<InventoryCheck>(`/inventory-checks/${memberId}/complete`, data)
    return res.data
}

export async function assignItem(memberId: number, newItemId: number, oldItemId?: number): Promise<MemberCheckState> {
    const res = await client.put<MemberCheckState>(`/inventory-checks/${memberId}/assign`, {newItemId, oldItemId})
    return res.data
}

export async function unassignItem(memberId: number, itemId: number): Promise<MemberCheckState> {
    const res = await client.put<MemberCheckState>(`/inventory-checks/${memberId}/unassign`, {itemId})
    return res.data
}

export async function createAndAssign(memberId: number, inventoryId: number, sizeId?: number | null, oldItemId?: number): Promise<MemberCheckState> {
    const res = await client.post<MemberCheckState>(`/inventory-checks/${memberId}/create-assign`, {
        inventoryId,
        sizeId,
        oldItemId
    })
    return res.data
}

/**
 * What a check found the member holding, where that is not what the record says.
 *
 * <p>Naming a piece from the free stock takes that one; naming none makes a new piece from the size,
 * number and fields given here. The owner only has to be named in an inventory that holds both.
 */
export interface CorrectItemRequest {
    inventoryId: number
    oldItemId?: number | null
    pickedItemId?: number | null
    sizeId?: number | null
    ownerKind?: ItemOwnerName | null
    internalId?: string | null
    metadata?: ItemMetadata | null
}

/** Puts the record right about which piece a member holds, without moving anything. */
export async function correctItem(memberId: number, data: CorrectItemRequest): Promise<MemberCheckState> {
    const res = await client.post<MemberCheckState>(`/inventory-checks/${memberId}/correct`, data)
    return res.data
}

export async function getLastCheck(memberId: number): Promise<CheckDetail> {
    const res = await client.get<CheckDetail>(`/inventory-checks/${memberId}/last`)
    return res.data
}

export async function cancelCheck(memberId: number): Promise<void> {
    await client.post(`/inventory-checks/${memberId}/cancel`)
}

export async function getNextMember(currentMemberId?: number, teamOnly?: boolean): Promise<number | null> {
    const params: Record<string, unknown> = {}
    if (currentMemberId) params.currentMemberId = currentMemberId
    if (teamOnly !== undefined) params.teamOnly = teamOnly
    const res = await client.get<NextMemberResponse>('/inventory-checks/next', {params})
    return res.data.memberId
}

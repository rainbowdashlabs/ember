/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    CompleteCheckRequest,
    CorrectItemRequest,
    EnrichedCheckDetail,
    EnrichedCheckSummary,
    InventoryCheck,
    MemberCheckState,
    NextMemberResponse,
} from './generated/schema'

export async function getCheckOverview(): Promise<EnrichedCheckSummary[]> {
    const res = await client.get<EnrichedCheckSummary[]>('/inventory-checks')
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
 * Puts the record right about which piece a member holds, without moving anything.
 *
 * <p>Naming a piece from the free stock takes that one; naming none makes a new piece from the size,
 * number and fields given here. The owner only has to be named in an inventory that holds both.
 */
export async function correctItem(memberId: number, data: CorrectItemRequest): Promise<MemberCheckState> {
    const res = await client.post<MemberCheckState>(`/inventory-checks/${memberId}/correct`, data)
    return res.data
}

export async function getLastCheck(memberId: number): Promise<EnrichedCheckDetail> {
    const res = await client.get<EnrichedCheckDetail>(`/inventory-checks/${memberId}/last`)
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

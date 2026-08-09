/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource } from './crud'
import type { MemberIdentity } from './types'

export interface ProcurementEntry {
    id: number
    inventoryId: number
    inventoryName: string
    memberId: number
    memberName: string
    sizeId?: number | null
    sizeLabel: string
    notes: string
    requestedAt: string
    fulfilledAt?: string | null
    memberIdentity?: MemberIdentity | null
}

export interface CreateProcurementRequest {
    inventoryId: number
    memberId: number
    sizeId?: number | null
    notes?: string
}

const procurement = createCrudResource<ProcurementEntry, CreateProcurementRequest>('/procurement')

export const listProcurement = procurement.list
export const createProcurement = procurement.create
export const deleteProcurement = procurement.remove

export async function listOpen(): Promise<ProcurementEntry[]> {
    const res = await client.get<ProcurementEntry[]>('/procurement/open')
    return res.data
}

export async function fulfill(id: number): Promise<void> {
    await client.put(`/procurement/${id}/fulfill`)
}

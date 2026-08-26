/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource } from './crud'
import type { ItemOwnerName } from './inventory'
import type { MovementPurposeName } from './movements'
import type { MemberIdentity } from './types'

export const ExchangeStatus = {
    ANNOUNCED: 'ANNOUNCED',
    RECEIVED: 'RECEIVED',
    SHIPPED: 'SHIPPED',
    ARRIVED: 'ARRIVED',
    DONE: 'DONE',
} as const

export type ExchangeStatusName = (typeof ExchangeStatus)[keyof typeof ExchangeStatus]

export interface ExchangeRequestEntry {
    id: number
    memberId: number
    memberName: string
    itemId?: number | null
    inventoryId: number
    inventoryName: string
    oldSizeId?: number | null
    oldSizeLabel?: string | null
    newSizeId?: number | null
    newSizeLabel?: string | null
    inventoryType: string
    /** Who owns the piece itself, which is what a mixed inventory cannot say for a row. */
    ownerKind?: ItemOwnerName | null
    /** Whether this is an issue, a return or an exchange. */
    purpose?: MovementPurposeName | null
    status: ExchangeStatusName
    reason: string
    createdAt: string
    updatedAt: string
    createdByName?: string | null
    memberIdentity?: MemberIdentity | null
}

/** How a step of a movement came to be acknowledged. */
export const AckKind = {
    CONFIRMED: 'CONFIRMED',
    ASSERTED: 'ASSERTED',
    FORCED: 'FORCED',
} as const

export type AckKindName = (typeof AckKind)[keyof typeof AckKind]

export interface ExchangeLogEntry {
    id: number
    stepLabel: string
    ackKind: AckKindName
    changedBy: number
    changedByName: string
    changedAt: string
    note: string
}

export interface CreateExchangeRequest {
    memberId?: number | null
    itemId?: number | null
    inventoryId: number
    oldSizeId?: number | null
    newSizeId?: number | null
    reason: string
}

export interface UpdateStatusRequest {
    status: string
    note?: string
    exchangedItemId?: number | null
}

const exchanges = createCrudResource<ExchangeRequestEntry, CreateExchangeRequest>('/exchanges')

export const listExchanges = exchanges.list
export const getExchange = exchanges.get
export const createExchange = exchanges.create
export const deleteExchange = exchanges.remove

export async function getLogs(id: number): Promise<ExchangeLogEntry[]> {
    const res = await client.get<ExchangeLogEntry[]>(`/exchanges/${id}/logs`)
    return res.data
}

export async function updateStatus(id: number, data: UpdateStatusRequest): Promise<ExchangeRequestEntry> {
    const res = await client.put<ExchangeRequestEntry>(`/exchanges/${id}/status`, data)
    return res.data
}

export async function exportPdf(exchangeIds: number[], extraFieldIds: number[]): Promise<Blob> {
    const res = await client.post('/exchanges/export', { exchangeIds, extraFieldIds }, { responseType: 'blob' })
    return res.data as Blob
}

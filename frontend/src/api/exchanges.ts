/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource } from './crud'
import type { CreateExchangeRequest, ExchangeLogEntry, ExchangeRequestEntry, UpdateStatusRequest } from './types'

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

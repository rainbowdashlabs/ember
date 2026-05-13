/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { CreateExchangeRequest, ExchangeLogEntry, ExchangeRequestEntry, UpdateStatusRequest } from './types'

export async function listExchanges(): Promise<ExchangeRequestEntry[]> {
    const res = await client.get<ExchangeRequestEntry[]>('/exchanges')
    return res.data
}

export async function getExchange(id: number): Promise<ExchangeRequestEntry> {
    const res = await client.get<ExchangeRequestEntry>(`/exchanges/${id}`)
    return res.data
}

export async function getLogs(id: number): Promise<ExchangeLogEntry[]> {
    const res = await client.get<ExchangeLogEntry[]>(`/exchanges/${id}/logs`)
    return res.data
}

export async function createExchange(data: CreateExchangeRequest): Promise<ExchangeRequestEntry> {
    const res = await client.post<ExchangeRequestEntry>('/exchanges', data)
    return res.data
}

export async function updateStatus(id: number, data: UpdateStatusRequest): Promise<ExchangeRequestEntry> {
    const res = await client.put<ExchangeRequestEntry>(`/exchanges/${id}/status`, data)
    return res.data
}

export async function deleteExchange(id: number): Promise<void> {
    await client.delete(`/exchanges/${id}`)
}

export async function exportPdf(exchangeIds: number[], extraFieldIds: number[]): Promise<Blob> {
    const res = await client.post('/exchanges/export', { exchangeIds, extraFieldIds }, { responseType: 'blob' })
    return res.data as Blob
}

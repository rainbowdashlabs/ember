/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { CreateProcurementRequest, ProcurementEntry } from './types'

export async function listProcurement(): Promise<ProcurementEntry[]> {
    const res = await client.get<ProcurementEntry[]>('/procurement')
    return res.data
}

export async function listOpen(): Promise<ProcurementEntry[]> {
    const res = await client.get<ProcurementEntry[]>('/procurement/open')
    return res.data
}

export async function createProcurement(data: CreateProcurementRequest): Promise<ProcurementEntry> {
    const res = await client.post<ProcurementEntry>('/procurement', data)
    return res.data
}

export async function fulfill(id: number): Promise<void> {
    await client.put(`/procurement/${id}/fulfill`)
}

export async function deleteProcurement(id: number): Promise<void> {
    await client.delete(`/procurement/${id}`)
}

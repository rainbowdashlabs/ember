/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource } from './crud'
import type { CreateProcurementRequest, ProcurementResponse } from './generated/schema'

const procurement = createCrudResource<ProcurementResponse, CreateProcurementRequest>('/procurement')

export const listProcurement = procurement.list
export const createProcurement = procurement.create
export const deleteProcurement = procurement.remove

export async function listOpen(): Promise<ProcurementResponse[]> {
    const res = await client.get<ProcurementResponse[]>('/procurement/open')
    return res.data
}

export async function fulfill(id: number): Promise<void> {
    await client.put(`/procurement/${id}/fulfill`)
}

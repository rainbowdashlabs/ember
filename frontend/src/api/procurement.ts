/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource } from './crud'
import type { CreateProcurementRequest, IssueFromStockResponse, ProcurementResponse } from './generated/schema'

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

/** Settles an order with a piece already in storage, handing it out through a movement. */
export async function issueFromStock(id: number, itemId: number): Promise<IssueFromStockResponse> {
    const res = await client.post<IssueFromStockResponse>(`/procurement/${id}/issue-from-stock`, {itemId})
    return res.data
}

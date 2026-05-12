/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface SavedFilter {
    id: number
    accountId: number
    tableType: string
    name: string
    filterData: string
    position: number
}

export interface CreateFilterRequest {
    tableType: string
    name: string
    filterData: string
}

export async function listFilters(tableType: string): Promise<SavedFilter[]> {
    const res = await client.get('/saved-filters', {params: {tableType}})
    return res.data
}

export async function createFilter(data: CreateFilterRequest): Promise<SavedFilter> {
    const res = await client.post('/saved-filters', data)
    return res.data
}

export async function deleteFilter(id: number): Promise<void> {
    await client.delete(`/saved-filters/${id}`)
}

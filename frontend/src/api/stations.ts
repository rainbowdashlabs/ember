/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {Station, StationDetail, StationRequest} from './types'

export async function listStations(): Promise<Station[]> {
    const res = await client.get<Station[]>('/stations')
    return res.data
}

export async function getStation(id: number): Promise<StationDetail> {
    const res = await client.get<StationDetail>(`/stations/${id}`)
    return res.data
}

export async function createStation(data: StationRequest): Promise<StationDetail> {
    const res = await client.post<StationDetail>('/stations', data)
    return res.data
}

export async function updateStation(id: number, data: StationRequest): Promise<StationDetail> {
    const res = await client.put<StationDetail>(`/stations/${id}`, data)
    return res.data
}

export async function deleteStation(id: number): Promise<void> {
    await client.delete(`/stations/${id}`)
}

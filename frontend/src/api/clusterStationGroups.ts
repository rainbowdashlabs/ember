/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {StationGroupResponse, StationGroupStationResponse} from './generated/schema'

/** The ways the association files its stations. */
export async function listGroups(): Promise<StationGroupResponse[]> {
    const res = await client.get<StationGroupResponse[]>('/cluster/station-groups')
    return res.data
}

export async function createGroup(name: string): Promise<StationGroupResponse> {
    const res = await client.post<StationGroupResponse>('/cluster/station-groups', {name})
    return res.data
}

export async function renameGroup(groupId: number, name: string): Promise<void> {
    await client.put(`/cluster/station-groups/${groupId}`, {name})
}

export async function deleteGroup(groupId: number): Promise<void> {
    await client.delete(`/cluster/station-groups/${groupId}`)
}

/** The stations in one filing, in the currency the association's station API speaks. */
export async function listStations(groupId: number): Promise<StationGroupStationResponse[]> {
    const res = await client.get<StationGroupStationResponse[]>(`/cluster/station-groups/${groupId}/stations`)
    return res.data
}

export async function setStations(groupId: number, stationUids: string[]): Promise<void> {
    await client.put(`/cluster/station-groups/${groupId}/stations`, {stationUids})
}

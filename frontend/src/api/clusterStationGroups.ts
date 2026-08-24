/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** One way an association files its stations. */
export interface StationGroup {
    id: number
    name: string
}

/** A station in a filing, in the currency the association's station API speaks. */
export interface StationGroupStation {
    stationUid: string
    name: string
}

export async function listGroups(): Promise<StationGroup[]> {
    const res = await client.get<StationGroup[]>('/cluster/station-groups')
    return res.data
}

export async function createGroup(name: string): Promise<StationGroup> {
    const res = await client.post<StationGroup>('/cluster/station-groups', {name})
    return res.data
}

export async function renameGroup(groupId: number, name: string): Promise<void> {
    await client.put(`/cluster/station-groups/${groupId}`, {name})
}

export async function deleteGroup(groupId: number): Promise<void> {
    await client.delete(`/cluster/station-groups/${groupId}`)
}

export async function listStations(groupId: number): Promise<StationGroupStation[]> {
    const res = await client.get<StationGroupStation[]>(`/cluster/station-groups/${groupId}/stations`)
    return res.data
}

export async function setStations(groupId: number, stationUids: string[]): Promise<void> {
    await client.put(`/cluster/station-groups/${groupId}/stations`, {stationUids})
}

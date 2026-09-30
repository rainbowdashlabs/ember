/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
export interface Station {
    id: number
    name?: string
}

export interface StationRequest {
    name?: string
    managerEmail?: string
}

export interface StationDetail {
    id: number
    name?: string
    manager?: ManagerDetail | null
}

export interface ManagerDetail {
    email?: string
    firstName?: string
    lastName?: string
    accountReady: boolean
}

const stations = createCrudResource<
    Station,
    StationRequest,
    StationRequest,
    StationDetail,
    StationDetail,
    StationDetail,
    string
>('/stations')

export const listStations = stations.list
export const getStation = stations.get
export const createStation = stations.create
export const updateStation = stations.update
export const deleteStation = stations.remove

/** The station an administrator just founded as the instance's first. */
export interface FoundedStation {
    stationUid: string
    name: string
}

/** Whether the instance still waits for its first station; asked by administrators only. */
export async function isFirstStationNeeded(): Promise<boolean> {
    const res = await client.get<{needed: boolean}>('/admin/first-station')
    return res.data.needed
}

/** Founds the instance's first station, with the asking administrator as its manager. */
export async function foundFirstStation(name: string): Promise<FoundedStation> {
    const res = await client.post<FoundedStation>('/admin/first-station', {name})
    return res.data
}

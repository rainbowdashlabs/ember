/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {
    AdminStationSummary,
    FirstStationStatus,
    FoundedStation,
    ManagerReminderAnswer,
    StationDetail,
    StationRequest,
} from './generated/schema'

const stations = createCrudResource<StationDetail, StationRequest, StationRequest, StationDetail, StationDetail, StationDetail, string>(
    '/stations',
)

export const listStations = stations.list
export const getStation = stations.get
export const createStation = stations.create
export const updateStation = stations.update
export const deleteStation = stations.remove

/** Every station with its state, manager, member count and last reminder, for the instance administration. */
export async function listAdminStations(): Promise<AdminStationSummary[]> {
    const res = await client.get<AdminStationSummary[]>('/admin/stations')
    return res.data
}

/** One station as the instance administration lists it. */
export async function getAdminStation(uid: string): Promise<AdminStationSummary> {
    const res = await client.get<AdminStationSummary>(`/admin/stations/${uid}`)
    return res.data
}

/**
 * Reminds the manager of a station that waits, and answers the station as it stands afterwards with
 * whether a mail went out.
 */
export async function remindManager(uid: string): Promise<ManagerReminderAnswer> {
    const res = await client.post<ManagerReminderAnswer>(`/admin/stations/${uid}/remind-manager`)
    return res.data
}

/** Whether the instance still waits for its first station; asked by administrators only. */
export async function isFirstStationNeeded(): Promise<boolean> {
    const res = await client.get<FirstStationStatus>('/admin/first-station')
    return res.data.needed
}

/** Founds the instance's first station, with the asking administrator as its manager. */
export async function foundFirstStation(name: string): Promise<FoundedStation> {
    const res = await client.post<FoundedStation>('/admin/first-station', {name})
    return res.data
}

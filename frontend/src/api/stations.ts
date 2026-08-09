/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
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

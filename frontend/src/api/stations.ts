/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {createCrudResource} from './crud'
import type {Station, StationDetail, StationRequest} from './types'

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

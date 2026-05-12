/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {MessageResponse, StationManageInfo, UpdateStationNameRequest} from './types'

export async function getStationInfo(): Promise<StationManageInfo> {
    const res = await client.get<StationManageInfo>('/station/manage')
    return res.data
}

export async function updateStationName(data: UpdateStationNameRequest): Promise<StationManageInfo> {
    const res = await client.put<StationManageInfo>('/station/manage', data)
    return res.data
}

export async function uploadLogo(file: File): Promise<MessageResponse> {
    const formData = new FormData()
    formData.append('logo', file)
    const res = await client.post<MessageResponse>('/station/manage/logo', formData, {
        headers: {'Content-Type': 'multipart/form-data'},
    })
    return res.data
}

export async function deleteLogo(): Promise<MessageResponse> {
    const res = await client.delete<MessageResponse>('/station/manage/logo')
    return res.data
}

export function getLogoUrl(): string {
    return '/api/v1/station/manage/logo'
}

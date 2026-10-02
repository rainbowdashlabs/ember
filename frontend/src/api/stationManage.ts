/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {uploadFile} from './upload'
import type {
    DeleteRequestResponse,
    LocationUpdate,
    LocationView,
    MailTestResponse,
    MessageResponse,
    ModulesResponse,
    StationImportProgress,
    StationInfo,
    StationModule,
    UpdateStationRequest,
} from './generated/schema'

export async function getStationInfo(): Promise<StationInfo> {
    const res = await client.get<StationInfo>('/station/manage')
    return res.data
}

/**
 * Saves the station's settings. A setting left out is left alone, except for the custom colours, the
 * discovery description and the public address, which a missing value clears.
 */
export async function updateStationName(data: UpdateStationRequest): Promise<StationInfo> {
    const res = await client.put<StationInfo>('/station/manage', data)
    return res.data
}

export async function uploadLogo(file: File): Promise<MessageResponse> {
    return uploadFile<MessageResponse>('/station/manage/logo', {logo: file})
}

export async function deleteLogo(): Promise<MessageResponse> {
    const res = await client.delete<MessageResponse>('/station/manage/logo')
    return res.data
}

export function getLogoUrl(): string {
    return '/api/v1/station/manage/logo'
}

export async function testMailConfig(): Promise<MailTestResponse> {
    const res = await client.post<MailTestResponse>('/station/manage/mail/test')
    return res.data
}

export async function clearMailConfig(): Promise<void> {
    await client.delete('/station/manage/mail')
}

export async function sendTestMail(): Promise<void> {
    await client.post('/station/manage/mail/test-mail')
}

export async function getDisabledModules(): Promise<ModulesResponse> {
    const res = await client.get<ModulesResponse>('/station/manage/modules')
    return res.data
}

export async function setDisabledModules(disabledModules: StationModule[]): Promise<ModulesResponse> {
    const res = await client.put<ModulesResponse>('/station/manage/modules', {disabledModules})
    return res.data
}

/**
 * Asks for the station to be deleted. An instance with no way of sending has nobody to ask, so the
 * confirmation counts as given and the answer says the station is already gone.
 */
export async function requestStationDeletion(): Promise<DeleteRequestResponse> {
    const res = await client.post<DeleteRequestResponse>('/station/manage/request-delete')
    return res.data
}

export async function importStation(token: string): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/station/manage/import', {token})
    return res.data
}

export async function getImportProgress(): Promise<StationImportProgress> {
    const res = await client.get<StationImportProgress>('/station/manage/import/progress')
    return res.data
}

export async function transferOwnership(newOwnerMemberId: number): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/station/manage/transfer-ownership', {newOwnerMemberId})
    return res.data
}

export async function getStationLocation(): Promise<LocationView> {
    const res = await client.get<LocationView>('/station/location')
    return res.data
}

/** Saves the station's address and position; a part left out is cleared. */
export async function updateStationLocation(data: LocationUpdate): Promise<LocationView> {
    const res = await client.put<LocationView>('/station/location', data)
    return res.data
}

export async function clearStationLocation(): Promise<void> {
    await client.delete('/station/location')
}

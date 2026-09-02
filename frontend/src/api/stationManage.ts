/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {uploadFile} from './upload'
import type {MessageResponse} from './types'

export interface StationManageInfo {
    id: string
    name?: string
    timezone?: string
    locale?: string
    hasLogo: boolean
    ownerMemberId?: number | null
    isOwner: boolean
    defaultTheme?: string
    allowUserTheme?: boolean
    customThemeColors?: string | null
    defaultFeel?: string
    allowUserFeel?: boolean
    publicKbMode?: string
    discoveryVisibility?: string
    discoveryDescription?: string | null
    discoveryShowKb?: boolean
    publicCalendarEnabled?: boolean
    publicPagesEnabled?: boolean
    publicWaitlistEnabled?: boolean
    publicBlogEnabled?: boolean
    publicSlug?: string | null
    /** What the cluster above the station has taken out of its hands, and who to name for it. */
    themeLocked?: boolean
    colorsLocked?: boolean
    feelLocked?: boolean
    logoLocked?: boolean
    clusterName?: string | null
}

export interface UpdateStationNameRequest {
    name?: string
    timezone?: string
    locale?: string
    defaultTheme?: string
    allowUserTheme?: boolean
    customThemeColors?: string | null
    defaultFeel?: string
    allowUserFeel?: boolean
    publicKbMode?: string
    discoveryVisibility?: string
    discoveryDescription?: string | null
    discoveryShowKb?: boolean
    publicCalendarEnabled?: boolean
    publicPagesEnabled?: boolean
    publicWaitlistEnabled?: boolean
    publicBlogEnabled?: boolean
    publicSlug?: string | null
}

export async function getStationInfo(): Promise<StationManageInfo> {
    const res = await client.get<StationManageInfo>('/station/manage')
    return res.data
}

export async function updateStationName(data: UpdateStationNameRequest): Promise<StationManageInfo> {
    const res = await client.put<StationManageInfo>('/station/manage', data)
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

// -- Mail config --

export interface MailTestResponse {
    success: boolean
    error?: string | null
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

// -- Modules --

export interface ModulesResponse {
    disabledModules: string[]
    /** Modules the station's cluster has switched off, which the station cannot turn back on. */
    clusterDeniedModules?: string[]
    /** The cluster doing the denying, so the screen can say who. */
    clusterName?: string | null
}

export async function getDisabledModules(): Promise<ModulesResponse> {
    const res = await client.get<ModulesResponse>('/station/manage/modules')
    return res.data
}

export async function setDisabledModules(disabledModules: string[]): Promise<ModulesResponse> {
    const res = await client.put<ModulesResponse>('/station/manage/modules', {disabledModules})
    return res.data
}

// -- Station deletion --

export interface DeleteRequestResponse {
    message: string
    /**
     * Whether the station is already gone. An instance with no way of sending has nobody to ask,
     * so the confirmation counts as given and the deletion happens on the spot.
     */
    deleted: boolean
}

export async function requestStationDeletion(): Promise<DeleteRequestResponse> {
    const res = await client.post<DeleteRequestResponse>('/station/manage/request-delete')
    return res.data
}

export async function importStation(token: string): Promise<{message: string}> {
    const res = await client.post<{message: string}>('/station/manage/import', {token})
    return res.data
}

export interface StationImportProgress {
    stationId: string
    stationName: string
    status: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
    phases: string[]
    completedPhases: number
    currentPhase: string | null
    subTotal: number
    subCompleted: number
    error: string | null
}

export async function getImportProgress(): Promise<StationImportProgress> {
    const res = await client.get<StationImportProgress>('/station/manage/import/progress')
    return res.data
}

export async function transferOwnership(newOwnerMemberId: number): Promise<{message: string}> {
    const res = await client.post<{message: string}>('/station/manage/transfer-ownership', {newOwnerMemberId})
    return res.data
}

// -- Geolocation --

export interface StationLocation {
    addressLine: string | null
    postalCode: string | null
    city: string | null
    country: string | null
    latitude: number | null
    longitude: number | null
}

export async function getStationLocation(): Promise<StationLocation> {
    const res = await client.get<StationLocation>('/station/location')
    return res.data
}

export async function updateStationLocation(data: StationLocation): Promise<StationLocation> {
    const res = await client.put<StationLocation>('/station/location', data)
    return res.data
}

export async function clearStationLocation(): Promise<void> {
    await client.delete('/station/location')
}

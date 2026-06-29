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

// -- Mail config --

export interface MailConfig {
    provider: string
    smtpHost: string
    smtpPort: number
    smtpSsl: boolean
    smtpUser: string
    senderAddress: string
    senderName: string
    hasApiKey: boolean
    providerName: string
    providerUrl: string
    dailyLimit: number
    monthlyLimit: number
    sentToday: number
    sentThisMonth: number
}

export interface MailConfigRequest {
    provider: string
    smtpHost?: string
    smtpPort?: number
    smtpSsl?: boolean
    smtpUser?: string
    smtpPassword?: string
    senderAddress?: string
    senderName?: string
    apiKey?: string
    providerName?: string
    providerUrl?: string
    dailyLimit?: number
    monthlyLimit?: number
}

export interface MailTestResponse {
    success: boolean
    error?: string | null
}

export async function getMailConfig(): Promise<MailConfig> {
    const res = await client.get<MailConfig>('/station/manage/mail')
    return res.data
}

export async function updateMailConfig(data: MailConfigRequest): Promise<MailConfig> {
    const res = await client.put<MailConfig>('/station/manage/mail', data)
    return res.data
}

export async function testMailConfig(): Promise<MailTestResponse> {
    const res = await client.post<MailTestResponse>('/station/manage/mail/test')
    return res.data
}

export async function clearMailConfig(): Promise<void> {
    await client.delete('/station/manage/mail')
}

// -- Modules --

export interface ModulesResponse {
    disabledModules: string[]
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

export async function requestStationDeletion(): Promise<{message: string}> {
    const res = await client.post<{message: string}>('/station/manage/request-delete')
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

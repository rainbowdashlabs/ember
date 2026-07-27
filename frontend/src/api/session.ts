/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {uploadFile} from './upload'
import type {ActiveSession, ConsentChangesResponse, ConsentStatusResponse, DocumentResponse, LegalVersionsResponse, MessageResponse, RecordConsentRequest, SessionInfo, StationMembership} from './types'

export interface CrossStationSummary {
    stationId: string
    stationName: string
    notifications: number
    requirements: number
}

export interface CrossStationNotification {
    stationId: string
    stationName: string
    id: number
    type: string
    localeKey: string
    params: Record<string, string>
    link?: { route: string; routeParams: Record<string, unknown> }
    createdAt: string
}

export interface CrossStationDashboard {
    stations: CrossStationSummary[]
    recentNotifications: CrossStationNotification[]
}

export async function getSessionInfo(): Promise<SessionInfo> {
    const res = await client.get<SessionInfo>('/session')
    return res.data
}

export async function getStations(): Promise<StationMembership[]> {
    const res = await client.get<StationMembership[]>('/session/stations')
    return res.data
}

export async function getActiveSessions(): Promise<ActiveSession[]> {
    const res = await client.get<ActiveSession[]>('/session/active')
    return res.data
}

export async function invalidateSession(id: number): Promise<void> {
    await client.delete(`/session/active/${id}`)
}

export async function invalidateAllSessions(): Promise<MessageResponse> {
    const res = await client.post<MessageResponse>('/session/invalidate-all')
    return res.data
}

export async function uploadAvatar(file: File): Promise<void> {
    await uploadFile('/session/avatar', {avatar: file})
}

export async function deleteAvatar(): Promise<void> {
    await client.delete('/session/avatar')
}

export async function deleteAccount(): Promise<void> {
    await client.delete('/session/account')
}

export async function getConsentText(lang?: string): Promise<DocumentResponse> {
    const res = await client.get<DocumentResponse>('/public/consent', {params: lang ? {lang} : undefined})
    return res.data
}

export async function getPrivacyPolicy(lang?: string): Promise<DocumentResponse> {
    const res = await client.get<DocumentResponse>('/public/privacy-policy', {params: lang ? {lang} : undefined})
    return res.data
}

export async function getTermsOfService(lang?: string): Promise<DocumentResponse> {
    const res = await client.get<DocumentResponse>('/public/tos', {params: lang ? {lang} : undefined})
    return res.data
}

export async function getImprint(lang?: string): Promise<DocumentResponse> {
    const res = await client.get<DocumentResponse>('/public/imprint', {params: lang ? {lang} : undefined})
    return res.data
}

export async function getLegalVersions(): Promise<LegalVersionsResponse> {
    const res = await client.get<LegalVersionsResponse>('/public/legal-versions')
    return res.data
}

export async function recordConsent(request: RecordConsentRequest): Promise<void> {
    await client.post('/session/consent', request)
}

export async function getConsentStatus(): Promise<ConsentStatusResponse> {
    const res = await client.get<ConsentStatusResponse>('/session/consent')
    return res.data
}

export async function getConsentChanges(): Promise<ConsentChangesResponse> {
    const res = await client.get<ConsentChangesResponse>('/session/consent/changes')
    return res.data
}

export async function gdprExport(): Promise<Blob> {
    const res = await client.get('/session/gdpr-export', {responseType: 'blob'})
    return res.data as Blob
}

export async function getCrossStationDashboard(): Promise<CrossStationDashboard> {
    const res = await client.get<CrossStationDashboard>('/session/cross-station-dashboard')
    return res.data
}

export async function gdprExportManagedMember(memberId: number): Promise<Blob> {
    const res = await client.get(`/managed-members/${memberId}/gdpr-export`, {responseType: 'blob'})
    return res.data as Blob
}

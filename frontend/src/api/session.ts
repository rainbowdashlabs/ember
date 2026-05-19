/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {ActiveSession, MessageResponse, SessionInfo, StationMembership} from './types'

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
    const formData = new FormData()
    formData.append('avatar', file)
    await client.post('/session/avatar', formData, {
        headers: {'Content-Type': 'multipart/form-data'},
    })
}

export async function deleteAvatar(): Promise<void> {
    await client.delete('/session/avatar')
}

export function avatarUrl(accountId: number): string {
    return `${client.defaults.baseURL}/accounts/${accountId}/avatar`
}

export async function deleteAccount(): Promise<void> {
    await client.delete('/session/account')
}

export async function gdprExport(): Promise<Blob> {
    const res = await client.get('/session/gdpr-export', {responseType: 'blob'})
    return res.data as Blob
}

export async function gdprExportManagedMember(memberId: number): Promise<Blob> {
    const res = await client.get(`/managed-members/${memberId}/gdpr-export`, {responseType: 'blob'})
    return res.data as Blob
}

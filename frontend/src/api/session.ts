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

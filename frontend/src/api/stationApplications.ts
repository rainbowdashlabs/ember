/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface StationApplication {
    id: number
    firstName: string
    lastName: string
    email: string
    stationName: string
    introduction: string
    status: string
    denyReason?: string | null
    createdAt: string
    resolvedAt?: string | null
}

export async function verify(token: string): Promise<void> {
    await client.post('/station-applications/verify', {token})
}

export async function submit(data: {
    firstName: string;
    lastName: string;
    email: string;
    stationName: string;
    introduction: string
}): Promise<StationApplication> {
    const res = await client.post<StationApplication>('/station-applications', data)
    return res.data
}

export async function listAll(): Promise<StationApplication[]> {
    const res = await client.get<StationApplication[]>('/admin/station-applications')
    return res.data
}

export async function accept(id: number): Promise<StationApplication> {
    const res = await client.post<StationApplication>(`/admin/station-applications/${id}/accept`)
    return res.data
}

export async function deny(id: number, reason: string): Promise<StationApplication> {
    const res = await client.post<StationApplication>(`/admin/station-applications/${id}/deny`, {reason})
    return res.data
}

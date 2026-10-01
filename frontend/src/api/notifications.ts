/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {CountResponse, NotificationResponse} from './generated/schema'

export async function listAll(): Promise<NotificationResponse[]> {
    const res = await client.get<NotificationResponse[]>('/notifications')
    return res.data
}

export async function listUnacknowledged(): Promise<NotificationResponse[]> {
    const res = await client.get<NotificationResponse[]>('/notifications/unacknowledged')
    return res.data
}

export async function getCount(): Promise<number> {
    const res = await client.get<CountResponse>('/notifications/count')
    return res.data.count
}

export async function acknowledge(id: number): Promise<void> {
    await client.post(`/notifications/${id}/acknowledge`)
}

export async function acknowledgeAll(): Promise<void> {
    await client.post('/notifications/acknowledge-all')
}

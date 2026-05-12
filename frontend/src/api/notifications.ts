/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { NotificationEntry } from './types'

export async function listAll(): Promise<NotificationEntry[]> {
    const res = await client.get<NotificationEntry[]>('/notifications')
    return res.data
}

export async function listUnacknowledged(): Promise<NotificationEntry[]> {
    const res = await client.get<NotificationEntry[]>('/notifications/unacknowledged')
    return res.data
}

export async function getCount(): Promise<number> {
    const res = await client.get<{ count: number }>('/notifications/count')
    return res.data.count
}

export async function acknowledge(id: number): Promise<void> {
    await client.post(`/notifications/${id}/acknowledge`)
}

export async function acknowledgeAll(): Promise<void> {
    await client.post('/notifications/acknowledge-all')
}

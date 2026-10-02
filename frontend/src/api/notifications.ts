/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {CountResponse, NotificationResponse} from './generated/schema'

/**
 * One inbox of notifications: a station member's or an association member's. The two are separate
 * feeds behind separate addresses, and the inbox screen asks either the same questions.
 */
export interface NotificationInboxApi {
    listUnread(): Promise<NotificationResponse[]>
    count(): Promise<number>
    acknowledge(id: number): Promise<void>
    acknowledgeAll(): Promise<void>
}

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

/** The inbox of the caller's membership at the current station. */
export const stationInbox: NotificationInboxApi = {
    listUnread: listUnacknowledged,
    count: getCount,
    acknowledge,
    acknowledgeAll,
}

/** The inbox of the caller's membership in the current association. */
export const clusterInbox: NotificationInboxApi = {
    async listUnread() {
        const res = await client.get<NotificationResponse[]>('/cluster/notifications/unacknowledged')
        return res.data
    },
    async count() {
        const res = await client.get<CountResponse>('/cluster/notifications/count')
        return res.data.count
    },
    async acknowledge(id: number) {
        await client.post(`/cluster/notifications/${id}/acknowledge`)
    },
    async acknowledgeAll() {
        await client.post('/cluster/notifications/acknowledge-all')
    },
}

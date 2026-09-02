/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
export type NotificationType = 'NEW_NEWS' | 'NEWS_COMMENT' | 'COMMENT_MENTION' | 'EVENT_REGISTRATION_STATUS'
    | 'EXCHANGE_STATUS_CHANGE' | 'EXCHANGE_NEW_REQUEST' | 'MOVEMENT_DECLINED' | 'MOVEMENT_CANCELLED'
    | 'NEW_EVENT' | 'NEW_EVENTS_BATCH'
    | 'MEMBER_ADDED_TO_GROUP' | 'PROFILE_FIELD_CHANGED' | 'PROCUREMENT_REQUESTED' | 'PROCUREMENT_FULFILLED'
    | 'NEW_FORM' | 'LOST_AND_FOUND_NEW' | 'LOST_AND_FOUND_CLAIMED' | 'WAITLIST_NEW_ENTRY'
    | 'LENDING_NEW_REQUEST' | 'LENDING_STATUS_CHANGE' | 'LENDING_NEW_MESSAGE' | 'BOARD_TICKET_UPDATE'
    | 'REGISTRATION_DEADLINE_EXPIRED' | 'EVENT_CANCELLED' | 'EVENT_REMINDER' | 'REGISTRATION_CLOSING'
    | 'PROCEDURE_ASSIGNED' | 'PROCEDURE_RESOLVED' | 'PROCEDURE_REOPENED' | 'PROCEDURE_ITEM_CHECKED'
    | 'SELF_CHECK_ASSIGNED' | 'SELF_CHECK_SUBMITTED' | 'SELF_CHECK_ROW_REFUSED'
    | 'WAITLIST_PUBLIC_REGISTRATION' | 'WAITLIST_INVITATION_ANSWERED' | 'STORAGE_WARNING'
    | 'CLUSTER_APPLICATION_SUBMITTED' | 'CLUSTER_APPLICATION_APPROVED' | 'CLUSTER_APPLICATION_DENIED'
    | 'CLUSTER_APPLICATION_WITHDRAWN' | 'CLUSTER_STATION_RELEASED'
    | 'CLUSTER_MODULE_DENIED' | 'CLUSTER_QUOTA_CHANGED' | 'CLUSTER_MEMBER_ROLE_CHANGED' | 'CLUSTER_FIELD_VALUE_CHANGED'
    | 'CLUSTER_ITEM_ISSUED' | 'CLUSTER_ITEM_LOST'

export interface NotificationLink {
    route: string
    routeParams?: Record<string, string | number>
    /** Names a place inside the page the route opens, such as the comment a notification is about. */
    query?: Record<string, string | number>
}

export interface NotificationEntry {
    id: number
    type: NotificationType
    localeKey: string
    params: Record<string, string>
    link?: NotificationLink | null
    createdAt: string
    acknowledgedAt?: string | null
}

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

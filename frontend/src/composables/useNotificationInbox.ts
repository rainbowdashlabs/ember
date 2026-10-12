/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import type {NotificationInboxApi} from '@/api/notifications'
import type {NotificationResponse} from '@/api/generated/schema'

/**
 * What is waiting in one inbox, and marking it read, the same for a station member and an
 * association member: the api says whose inbox is meant.
 *
 * <p>Opening a row marks it read, and so does the acknowledge button inside it, so one press of that
 * button reaches both. The list is what says whether anything is left to mark, so a notification is
 * taken off it before the server is told rather than after, and the second call finds nothing to do.
 *
 * <p>Read notifications are listed only once asked for. While they are shown, whatever is marked
 * read moves over to them, so the two lists never disagree with the server.
 *
 * @param api      the inbox to read
 * @param onChange called after something was marked read, for the counters that show the unread
 */
export function useNotificationInbox(api: NotificationInboxApi, onChange: () => void = () => {}) {
    const notifications = ref<NotificationResponse[]>([])
    const loading = ref(true)
    const earlier = ref<NotificationResponse[] | null>(null)

    /** Reads what is waiting, keeping an empty list where the inbox cannot be read. */
    async function load() {
        loading.value = true
        notifications.value = await api.listUnread().catch(() => [])
        loading.value = false
    }

    /** Reads the notifications already read, among the most recent ones. */
    async function loadEarlier() {
        const recent = await api.listRecent().catch(() => [])
        earlier.value = recent.filter(entry => entry.acknowledgedAt)
    }

    function moveToEarlier(read: NotificationResponse[]) {
        if (!earlier.value || read.length === 0) return
        const acknowledgedAt = new Date().toISOString()
        earlier.value = [...read.map(entry => ({...entry, acknowledgedAt})), ...earlier.value]
            .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    }

    /** Marks one notification read and takes it off the list. */
    async function acknowledge(id: number) {
        const entry = notifications.value.find(candidate => candidate.id === id)
        if (!entry) return
        notifications.value = notifications.value.filter(candidate => candidate.id !== id)
        moveToEarlier([entry])
        await api.acknowledge(id)
        onChange()
    }

    /** Marks everything read and empties the list. */
    async function acknowledgeAll() {
        await api.acknowledgeAll()
        moveToEarlier(notifications.value)
        notifications.value = []
        onChange()
    }

    return {
        notifications: readonly(notifications),
        earlier: readonly(earlier),
        loading: readonly(loading),
        load,
        loadEarlier,
        acknowledge,
        acknowledgeAll,
    }
}

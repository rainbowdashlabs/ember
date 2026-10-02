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
 * @param api      the inbox to read
 * @param onChange called after something was marked read, for the counters that show the unread
 */
export function useNotificationInbox(api: NotificationInboxApi, onChange: () => void = () => {}) {
    const notifications = ref<NotificationResponse[]>([])
    const loading = ref(true)

    /** Reads what is waiting, keeping an empty list where the inbox cannot be read. */
    async function load() {
        loading.value = true
        notifications.value = await api.listUnread().catch(() => [])
        loading.value = false
    }

    /** Marks one notification read and takes it off the list. */
    async function acknowledge(id: number) {
        if (!notifications.value.some(entry => entry.id === id)) return
        notifications.value = notifications.value.filter(entry => entry.id !== id)
        await api.acknowledge(id)
        onChange()
    }

    /** Marks everything read and empties the list. */
    async function acknowledgeAll() {
        await api.acknowledgeAll()
        notifications.value = []
        onChange()
    }

    return {
        notifications: readonly(notifications),
        loading: readonly(loading),
        load,
        acknowledge,
        acknowledgeAll,
    }
}

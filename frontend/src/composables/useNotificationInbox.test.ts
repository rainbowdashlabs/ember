/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {useNotificationInbox} from './useNotificationInbox'
import type {NotificationInboxApi} from '@/api/notifications'
import type {NotificationResponse} from '@/api/generated/schema'

function waiting(id: number): NotificationResponse {
    return {
        id,
        type: 'CLUSTER_APPLICATION_SUBMITTED',
        localeKey: 'notification.clusterApplicationSubmitted',
        params: {stationName: 'Wache Süd'},
        link: {route: 'cluster-applications', routeParams: {}, query: null},
        createdAt: '2026-10-01T08:00:00Z',
        acknowledgedAt: null,
    }
}

function fakeInbox(entries: NotificationResponse[]) {
    return {
        listUnread: vi.fn(async () => entries),
        listRecent: vi.fn(async () => entries),
        count: vi.fn(async () => entries.length),
        acknowledge: vi.fn(async () => {}),
        acknowledgeAll: vi.fn(async () => {}),
    } satisfies NotificationInboxApi
}

/** One inbox, whichever membership it belongs to: read, marked read one by one or all at once. */
describe('useNotificationInbox', () => {
    it('lists what is waiting', async () => {
        const inbox = useNotificationInbox(fakeInbox([waiting(1), waiting(2)]))

        await inbox.load()

        expect(inbox.notifications.value.map(entry => entry.id)).toEqual([1, 2])
        expect(inbox.loading.value).toBe(false)
    })

    it('marks one read once, however often it is pressed, and says so once', async () => {
        const api = fakeInbox([waiting(1), waiting(2)])
        const changed = vi.fn()
        const inbox = useNotificationInbox(api, changed)
        await inbox.load()

        await Promise.all([inbox.acknowledge(1), inbox.acknowledge(1)])

        expect(api.acknowledge).toHaveBeenCalledTimes(1)
        expect(api.acknowledge).toHaveBeenCalledWith(1)
        expect(changed).toHaveBeenCalledTimes(1)
        expect(inbox.notifications.value.map(entry => entry.id)).toEqual([2])
    })

    it('marks everything read', async () => {
        const api = fakeInbox([waiting(1), waiting(2)])
        const changed = vi.fn()
        const inbox = useNotificationInbox(api, changed)
        await inbox.load()

        await inbox.acknowledgeAll()

        expect(api.acknowledgeAll).toHaveBeenCalledTimes(1)
        expect(changed).toHaveBeenCalledTimes(1)
        expect(inbox.notifications.value).toEqual([])
    })

    it('lists only the read ones as earlier, and takes in what is marked read while they are shown', async () => {
        const read = {...waiting(3), createdAt: '2026-09-01T08:00:00Z', acknowledgedAt: '2026-09-02T08:00:00Z'}
        const api = fakeInbox([waiting(1), waiting(2)])
        api.listRecent.mockResolvedValueOnce([waiting(1), waiting(2), read])
        const inbox = useNotificationInbox(api)
        await inbox.load()

        expect(inbox.earlier.value).toBeNull()
        await inbox.loadEarlier()
        expect(inbox.earlier.value?.map(entry => entry.id)).toEqual([3])

        await inbox.acknowledge(1)

        expect(inbox.earlier.value?.map(entry => entry.id)).toEqual([1, 3])
        expect(inbox.earlier.value?.[0]?.acknowledgedAt).not.toBeNull()
    })

    it('shows an empty inbox where it cannot be read', async () => {
        const api = fakeInbox([])
        api.listUnread.mockRejectedValueOnce(new Error('offline'))
        const inbox = useNotificationInbox(api)

        await inbox.load()

        expect(inbox.notifications.value).toEqual([])
        expect(inbox.loading.value).toBe(false)
    })
})

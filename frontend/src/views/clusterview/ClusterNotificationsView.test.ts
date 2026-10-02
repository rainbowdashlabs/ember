/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import ClusterNotificationsView from './ClusterNotificationsView.vue'

const listUnread = vi.fn()
const count = vi.fn()
const acknowledge = vi.fn()

vi.mock('@/api', () => ({
    clusters: {
        getMailSettings: vi.fn(async () => ({emailEnabled: false, mailAvailable: true})),
        updateMailSettings: vi.fn(),
    },
}))

vi.mock('@/api/notifications', () => ({
    clusterInbox: {
        listUnread: (...args: unknown[]) => listUnread(...args),
        count: (...args: unknown[]) => count(...args),
        acknowledge: (...args: unknown[]) => acknowledge(...args),
        acknowledgeAll: vi.fn(),
    },
}))

/**
 * The association's notifications page.
 *
 * <p>The association wrote its notifications and the server served them, but no screen ever asked,
 * so nobody saw one and every reminder meant to arrive once while unread stayed unread for good and
 * blocked the next. The page now shows them, and marking one read moves the number on the bell.
 */
describe('ClusterNotificationsView', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        listUnread.mockResolvedValue([{
            id: 9,
            type: 'CLUSTER_APPLICATION_SUBMITTED',
            localeKey: 'notification.clusterApplicationSubmitted',
            params: {stationName: 'Wache Süd'},
            link: {route: 'cluster-applications', routeParams: {}, query: null},
            createdAt: '2026-10-01T08:00:00Z',
            acknowledgedAt: null,
        }])
        count.mockResolvedValue(0)
        acknowledge.mockResolvedValue(undefined)
    })

    it('shows what the association has told the reader', async () => {
        const view = await mountSuspended(ClusterNotificationsView)
        await flushPromises()

        expect(view.findAll('[data-testid="notification-entry"]')).toHaveLength(1)
        expect(view.text()).toContain('Wache Süd')
    })

    it('reads the count again once something was marked read', async () => {
        const view = await mountSuspended(ClusterNotificationsView)
        await flushPromises()

        await view.find('[data-testid="notification-entry"]').trigger('click')
        await flushPromises()

        expect(acknowledge).toHaveBeenCalledWith(9)
        expect(count).toHaveBeenCalled()
    })
})

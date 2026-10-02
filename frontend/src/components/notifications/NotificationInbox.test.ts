/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createMemoryHistory, createRouter} from 'vue-router'
import NotificationInbox from './NotificationInbox.vue'
import type {NotificationInboxApi} from '@/api/notifications'
import type {NotificationResponse} from '@/api/generated/schema'

const router = createRouter({
    history: createMemoryHistory(),
    routes: [
        {path: '/cluster/applications', name: 'cluster-applications', component: {template: '<div/>'}},
        {path: '/:rest(.*)*', component: {template: '<div/>'}},
    ],
})

function waiting(id: number, route: string): NotificationResponse {
    return {
        id,
        type: 'CLUSTER_APPLICATION_SUBMITTED',
        localeKey: 'notification.clusterApplicationSubmitted',
        params: {stationName: 'Wache Süd'},
        link: {route, routeParams: {}, query: null},
        createdAt: '2026-10-01T08:00:00Z',
        acknowledgedAt: null,
    }
}

async function inboxWith(entries: NotificationResponse[]) {
    const api: NotificationInboxApi = {
        listUnread: async () => entries,
        count: async () => entries.length,
        acknowledge: vi.fn(async () => {}),
        acknowledgeAll: vi.fn(async () => {}),
    }
    const view = mount(NotificationInbox, {props: {api}, global: {plugins: [router]}})
    await flushPromises()
    return {view, api}
}

/**
 * The inbox an association member reads under the association's bell, and a station member on
 * the dashboard: what is waiting, worded, linked where the app knows the page, and marked read.
 */
describe('NotificationInbox', () => {
    it('shows what is waiting, in words', async () => {
        const {view} = await inboxWith([waiting(1, 'cluster-applications')])

        expect(view.findAll('[data-testid="notification-entry"]')).toHaveLength(1)
        expect(view.text()).toContain('Wache Süd')
    })

    it('links a notice to its page, and leaves one naming a page the app does not know unlinked', async () => {
        const {view} = await inboxWith([waiting(1, 'cluster-applications'), waiting(2, 'no-such-page')])

        const rows = view.findAll('[data-testid="notification-entry"]')
        expect(rows).toHaveLength(2)
        expect(rows[0]!.element.closest('a')).not.toBeNull()
        expect(rows[1]!.element.closest('a')).toBeNull()
    })

    it('tells whoever counts when something was marked read', async () => {
        const {view, api} = await inboxWith([waiting(1, 'cluster-applications')])

        await view.find('[data-testid="notification-entry"]').trigger('click')
        await flushPromises()

        expect(api.acknowledge).toHaveBeenCalledWith(1)
        expect(view.emitted('changed')).toHaveLength(1)
        expect(view.findAll('[data-testid="notification-entry"]')).toHaveLength(0)
    })
})

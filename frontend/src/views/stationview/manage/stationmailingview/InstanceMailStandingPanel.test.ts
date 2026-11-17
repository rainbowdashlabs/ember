/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import InstanceMailStandingPanel from './InstanceMailStandingPanel.vue'
import type {InstanceMailStation} from '@/api/generated/schema'

const api = vi.hoisted(() => ({
    getOwnInstanceMail: vi.fn(),
}))

vi.mock('@/api/instanceMail', () => api)

function standing(granted: boolean, dailyLimit: number | null): InstanceMailStation {
    return {
        stationUid: '00000000-0000-0000-0000-000000000004', name: 'Nord', granted,
        grantedAt: granted ? '2026-10-08T08:00:00Z' : null, dailyLimit, sentToday: 6,
    }
}

/**
 * What a station sees of the instance carrying its mail.
 */
describe('InstanceMailStandingPanel', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it('shows the grant with today\'s use against the station\'s own limit', async () => {
        api.getOwnInstanceMail.mockResolvedValue(standing(true, 20))
        const view = mount(InstanceMailStandingPanel)
        await flushPromises()

        expect(view.find('[data-testid="instance-mail-granted"]').exists()).toBe(true)
        expect(view.find('[data-testid="instance-mail-use"]').text()).toContain('6 von höchstens 20 Mails')
    })

    it('says so without a limit of its own', async () => {
        api.getOwnInstanceMail.mockResolvedValue(standing(true, null))
        const view = mount(InstanceMailStandingPanel)
        await flushPromises()

        expect(view.find('[data-testid="instance-mail-use"]').text()).toContain('keine eigene Grenze')
    })

    it('says who decides where the station is not granted', async () => {
        api.getOwnInstanceMail.mockResolvedValue(standing(false, null))
        const view = mount(InstanceMailStandingPanel)
        await flushPromises()

        expect(view.find('[data-testid="instance-mail-not-granted"]').text()).toContain('Verwaltung der Instanz')
        expect(view.find('[data-testid="instance-mail-use"]').exists()).toBe(false)
    })
})

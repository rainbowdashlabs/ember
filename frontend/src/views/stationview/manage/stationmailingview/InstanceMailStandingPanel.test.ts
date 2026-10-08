/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import InstanceMailStandingPanel from './InstanceMailStandingPanel.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type {InstanceMailStation} from '@/api/generated/schema'

const api = vi.hoisted(() => ({
    getOwnInstanceMail: vi.fn(),
    getStationReplyTo: vi.fn(),
    saveStationReplyTo: vi.fn(),
}))

vi.mock('@/api/instanceMail', () => api)

function standing(granted: boolean, dailyLimit: number | null): InstanceMailStation {
    return {
        stationUid: '00000000-0000-0000-0000-000000000004', name: 'Nord', granted,
        grantedAt: granted ? '2026-10-08T08:00:00Z' : null, dailyLimit, sentToday: 6,
    }
}

/**
 * What a station sees of the instance carrying its mail, and where replies to its mail go.
 */
describe('InstanceMailStandingPanel', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        api.getStationReplyTo.mockResolvedValue({replyTo: 'kontakt@nord.test'})
    })

    it('shows the grant with today\'s use against the station\'s own limit', async () => {
        api.getOwnInstanceMail.mockResolvedValue(standing(true, 20))
        const view = mount(InstanceMailStandingPanel)
        await flushPromises()

        expect(view.find('[data-testid="instance-mail-granted"]').exists()).toBe(true)
        expect(view.find('[data-testid="instance-mail-use"]').text()).toContain('6 von 20')
    })

    it('says so without a limit of its own', async () => {
        api.getOwnInstanceMail.mockResolvedValue(standing(true, null))
        const view = mount(InstanceMailStandingPanel)
        await flushPromises()

        expect(view.find('[data-testid="instance-mail-use"]').text()).toContain('kein eigenes Limit')
    })

    it('says who decides where the station is not granted', async () => {
        api.getOwnInstanceMail.mockResolvedValue(standing(false, null))
        const view = mount(InstanceMailStandingPanel)
        await flushPromises()

        expect(view.find('[data-testid="instance-mail-not-granted"]').text()).toContain('Verwaltung der Instanz')
        expect(view.find('[data-testid="instance-mail-use"]').exists()).toBe(false)
    })

    it('saves the reply address as typed, without the spaces around it', async () => {
        api.getOwnInstanceMail.mockResolvedValue(standing(false, null))
        api.saveStationReplyTo.mockResolvedValue({replyTo: 'neu@nord.test'})
        const view = mount(InstanceMailStandingPanel)
        await flushPromises()

        expect(view.findComponent(TextInput).props('modelValue')).toBe('kontakt@nord.test')
        view.findComponent(TextInput).vm.$emit('update:modelValue', ' neu@nord.test ')
        await view.findComponent(SaveButton).props('action')()

        expect(api.saveStationReplyTo).toHaveBeenCalledWith('neu@nord.test')
    })
})

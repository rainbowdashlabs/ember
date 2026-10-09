/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import InstanceMailGrantPanel from './InstanceMailGrantPanel.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import type {InstanceMailStation} from '@/api/generated/schema'

const api = vi.hoisted(() => ({
    getInstanceMailStation: vi.fn(),
    grantInstanceMail: vi.fn(),
    withdrawInstanceMail: vi.fn(),
}))

vi.mock('@/api/instanceMail', () => api)

const UID = '00000000-0000-0000-0000-000000000004'

function station(granted: boolean, dailyLimit: number | null = null): InstanceMailStation {
    return {stationUid: UID, name: 'Nord', granted, grantedAt: granted ? '2026-10-08T08:00:00Z' : null, dailyLimit, sentToday: 3}
}

/**
 * Whether one station may send through the instance's mail providers, set on the station's page by
 * an instance administrator.
 */
describe('InstanceMailGrantPanel', () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    async function mounted(loaded: InstanceMailStation) {
        api.getInstanceMailStation.mockResolvedValue(loaded)
        const view = mount(InstanceMailGrantPanel, {props: {stationUid: UID}})
        await flushPromises()
        return view
    }

    it('grants the station with the limit typed in', async () => {
        api.grantInstanceMail.mockResolvedValue(station(true, 30))
        const view = await mounted(station(false))

        view.findComponent(ToggleSetting).vm.$emit('update:modelValue', true)
        await flushPromises()
        view.findComponent(NumberInput).vm.$emit('update:modelValue', 30)
        await view.findComponent(SaveButton).props('action')()

        expect(api.grantInstanceMail).toHaveBeenCalledWith(UID, 30)
        expect(view.find('[data-testid="instance-mail-sent-today"]').text()).toContain('3')
    })

    it('grants without a limit of its own where the field is left empty', async () => {
        api.grantInstanceMail.mockResolvedValue(station(true))
        const view = await mounted(station(true))

        await view.findComponent(SaveButton).props('action')()

        expect(api.grantInstanceMail).toHaveBeenCalledWith(UID, null)
    })

    it('withdraws the grant once switched off', async () => {
        api.withdrawInstanceMail.mockResolvedValue(station(false))
        const view = await mounted(station(true, 30))

        view.findComponent(ToggleSetting).vm.$emit('update:modelValue', false)
        await view.findComponent(SaveButton).props('action')()

        expect(api.withdrawInstanceMail).toHaveBeenCalledWith(UID)
        expect(view.findComponent(NumberInput).exists()).toBe(false)
    })
})

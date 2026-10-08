/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import StationGrantsPanel from './StationGrantsPanel.vue'
import StationGrantsTable from './StationGrantsTable.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import type {InstanceMailStation} from '@/api/generated/schema'

const api = vi.hoisted(() => ({
    listInstanceMailStations: vi.fn(),
    grantInstanceMailTo: vi.fn(),
    withdrawInstanceMailFrom: vi.fn(),
}))

vi.mock('@/api/instanceMail', () => api)

const NORD: InstanceMailStation = {
    stationUid: '00000000-0000-0000-0000-000000000004', name: 'Nord', granted: false, grantedAt: null, dailyLimit: null, sentToday: 0,
}
const SUED: InstanceMailStation = {
    stationUid: '00000000-0000-0000-0000-000000000005', name: 'Süd', granted: true, grantedAt: '2026-10-08T08:00:00Z', dailyLimit: 20, sentToday: 7,
}

/**
 * Granting and withdrawing the instance's mail providers for many stations at once, in the
 * administration's mail settings.
 */
describe('StationGrantsPanel', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        api.listInstanceMailStations.mockResolvedValue([NORD, SUED])
    })

    it('grants the stations ticked, with the limit typed in, and clears the selection', async () => {
        api.grantInstanceMailTo.mockResolvedValue([{...NORD, granted: true, dailyLimit: 10}, SUED])
        const view = mount(StationGrantsPanel)
        await flushPromises()

        expect(view.find('[data-testid="instance-mail-grant"]').attributes('disabled')).toBeDefined()
        view.findComponent(StationGrantsTable).vm.$emit('toggle', NORD.stationUid)
        view.findComponent(NumberInput).vm.$emit('update:modelValue', 10)
        await flushPromises()
        await view.find('[data-testid="instance-mail-grant"]').trigger('click')
        await flushPromises()

        expect(api.grantInstanceMailTo).toHaveBeenCalledWith([NORD.stationUid], 10)
        expect(view.findComponent(StationGrantsTable).props('selected').size).toBe(0)
    })

    it('withdraws the stations ticked', async () => {
        api.withdrawInstanceMailFrom.mockResolvedValue([NORD, {...SUED, granted: false}])
        const view = mount(StationGrantsPanel)
        await flushPromises()

        view.findComponent(StationGrantsTable).vm.$emit('toggle', SUED.stationUid)
        view.findComponent(StationGrantsTable).vm.$emit('toggle', NORD.stationUid)
        view.findComponent(StationGrantsTable).vm.$emit('toggle', NORD.stationUid)
        await flushPromises()
        await view.find('[data-testid="instance-mail-withdraw"]').trigger('click')
        await flushPromises()

        expect(api.withdrawInstanceMailFrom).toHaveBeenCalledWith([SUED.stationUid])
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import BeaconFaultContext from './BeaconFaultContext.vue'

const faultContext = vi.hoisted(() => vi.fn())

vi.mock('@/api', () => ({beacon: {faultContext}}))

/**
 * The lines instances sent with a fault: each delivery under the instance and version it came from,
 * and a plain sentence where none came.
 */
describe('BeaconFaultContext', () => {
    beforeEach(() => faultContext.mockReset())

    it('lists each delivery under where it came from', async () => {
        faultContext.mockResolvedValue([{
            instanceId: 'inst-1', contactName: 'Wache Nord', version: '26.40.0',
            occurredAt: '2026-11-01T10:00:00Z', receivedAt: '2026-11-01T10:00:10Z',
            lines: [{loggedAt: '2026-11-01T10:00:00Z', level: 'ERROR', logger: 'dev.chojo.ember.A', thread: 'worker-1',
                message: 'Mail for [name] failed'}],
        }])

        const wrapper = mount(BeaconFaultContext, {props: {faultId: 3}})
        await flushPromises()

        expect(faultContext).toHaveBeenCalledWith(3)
        expect(wrapper.text()).toContain('Wache Nord')
        expect(wrapper.text()).toContain('26.40.0')
        expect(wrapper.text()).toContain('Mail for [name] failed')
    })

    it('says so where no lines came with the fault', async () => {
        faultContext.mockResolvedValue([])

        const wrapper = mount(BeaconFaultContext, {props: {faultId: 3}})
        await flushPromises()

        expect(wrapper.text()).toContain('keine Logzeilen mitgesendet')
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import StationModulesView from './StationModulesView.vue'
import StationModuleList from './stationmodulesview/StationModuleList.vue'

const getDisabledModules = vi.fn()
const setModuleDisabled = vi.fn()
const setDisabledModules = vi.fn()

vi.mock('@/api', () => ({
    stationManage: {
        getDisabledModules: (...args: unknown[]) => getDisabledModules(...args),
        setModuleDisabled: (...args: unknown[]) => setModuleDisabled(...args),
        setDisabledModules: (...args: unknown[]) => setDisabledModules(...args),
    },
}))

/**
 * Two managers switching different modules at once used to undo each other: every switch sent the
 * whole list the screen had loaded, and the second one wrote back what the first had just changed.
 * A switch now names its own module and nothing else.
 */
describe('StationModulesView', () => {
    beforeEach(() => {
        getDisabledModules.mockReset()
        setModuleDisabled.mockReset()
        setDisabledModules.mockReset()
        getDisabledModules.mockResolvedValue({disabledModules: ['ATTENDANCE'], clusterDeniedModules: [], clusterName: null})
        setModuleDisabled.mockResolvedValue({disabledModules: ['ATTENDANCE', 'NEWS'], clusterDeniedModules: [], clusterName: null})
    })

    it('switches only the module that was toggled', async () => {
        const wrapper = await mountSuspended(StationModulesView)
        await new Promise(resolve => setTimeout(resolve, 0))
        await wrapper.vm.$nextTick()

        wrapper.findComponent(StationModuleList).vm.$emit('toggle', 'NEWS')
        await new Promise(resolve => setTimeout(resolve, 0))

        expect(setModuleDisabled).toHaveBeenCalledWith('NEWS', true)
        expect(setDisabledModules).not.toHaveBeenCalled()
    })

    it('switches a module that is off back on', async () => {
        const wrapper = await mountSuspended(StationModulesView)
        await new Promise(resolve => setTimeout(resolve, 0))
        await wrapper.vm.$nextTick()

        wrapper.findComponent(StationModuleList).vm.$emit('toggle', 'ATTENDANCE')
        await new Promise(resolve => setTimeout(resolve, 0))

        expect(setModuleDisabled).toHaveBeenCalledWith('ATTENDANCE', false)
    })
})

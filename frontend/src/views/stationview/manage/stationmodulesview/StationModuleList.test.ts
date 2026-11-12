/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import StationModuleList from './StationModuleList.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {StationModule} from '@/api/generated/schema'

/**
 * The station's module switches: the parts of the inventory sit under it and follow it.
 */
describe('StationModuleList', () => {
    function rowOf(view: ReturnType<typeof mount>, module: StationModule) {
        const row = view.find(`[data-module="${module}"]`)
        return {row, toggle: row.findComponent(ToggleInput)}
    }

    function mountWith(disabled: StationModule[], denied: StationModule[] = []) {
        return mount(StationModuleList, {
            props: {
                disabledModules: new Set(disabled),
                clusterDenied: new Set(denied),
                clusterName: 'Kreisverband Nord',
                saving: false,
            },
        })
    }

    it('draws the parts of the inventory indented under it', () => {
        const view = mountWith([])

        expect(rowOf(view, StationModule.INVENTORY_LENDING).row.classes()).toContain('ml-8')
        expect(rowOf(view, StationModule.INVENTORY).row.classes()).not.toContain('ml-8')
    })

    it('shows a part off and locked while the inventory is off', () => {
        const view = mountWith([StationModule.INVENTORY])
        const lending = rowOf(view, StationModule.INVENTORY_LENDING)

        expect(lending.toggle.props('modelValue')).toBe(false)
        expect(lending.toggle.props('disabled')).toBe(true)
        expect(rowOf(view, StationModule.NEWS).toggle.props('disabled')).toBe(false)
    })

    it('lets a part be switched on its own while the inventory is on', () => {
        const view = mountWith([StationModule.INVENTORY_PROCUREMENT])
        const procurement = rowOf(view, StationModule.INVENTORY_PROCUREMENT)

        expect(procurement.toggle.props('modelValue')).toBe(false)
        expect(procurement.toggle.props('disabled')).toBe(false)
        procurement.toggle.vm.$emit('update:modelValue', true)
        expect(view.emitted('toggle')).toEqual([[StationModule.INVENTORY_PROCUREMENT]])
    })

    it('locks a part the cluster denies', () => {
        const view = mountWith([], [StationModule.INVENTORY_CHECKS])

        expect(rowOf(view, StationModule.INVENTORY_CHECKS).toggle.props('disabled')).toBe(true)
        expect(rowOf(view, StationModule.INVENTORY_CHECKS).row.text()).toContain('Kreisverband Nord')
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {ref} from 'vue'
import {SidebarMode} from '@/api/generated/schema'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SidebarModeSection from './SidebarModeSection.vue'

const tree = ref(false)
const choose = vi.fn(async () => {})

vi.mock('@/composables/useSidebarMode', () => ({
    useSidebarMode: () => ({tree, choose}),
}))

/** The switch between one section at a time and every section at once, saved with the account. */
describe('SidebarModeSection', () => {
    beforeEach(() => {
        tree.value = false
        choose.mockClear()
    })

    it('shows the choice the account made', async () => {
        tree.value = true
        const section = await mountSuspended(SidebarModeSection)

        expect(section.findComponent(ToggleSetting).props('modelValue')).toBe(true)
        expect(section.text()).toContain('Alle Bereiche auf einmal zeigen')
    })

    it('saves every section at once when switched on and one at a time when switched off', async () => {
        const section = await mountSuspended(SidebarModeSection)
        const toggle = section.findComponent(ToggleSetting)

        toggle.vm.$emit('update:modelValue', true)
        await flushPromises()
        toggle.vm.$emit('update:modelValue', false)
        await flushPromises()

        expect(choose.mock.calls).toEqual([[SidebarMode.TREE], [SidebarMode.SECTIONS]])
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import GearIconPicker from './GearIconPicker.vue'
import {GEAR_ICONS} from '@/util/gearIcons'

const i18n = createI18n({legacy: false, locale: 'de-DE', missingWarn: false, fallbackWarn: false, messages: {'de-DE': {}, en: {}}})

function picker(icon: string | null) {
    return mount(GearIconPicker, {
        global: {plugins: [i18n], stubs: {'font-awesome-icon': true, Spinner: true, GearGlyph: true, AppIcon: true}},
        props: {icon, 'onUpdate:icon': () => undefined},
    })
}

async function opened(icon: string | null) {
    const wrapper = picker(icon)
    await wrapper.find('input').trigger('focusin')
    await vi.waitUntil(() => wrapper.findAll('[role="option"]').length > 0)
    return wrapper
}

/**
 * A picture already chosen is replaced by picking another, and the list offers the whole catalogue
 * rather than the first page of it.
 */
describe('GearIconPicker', () => {
    it('keeps the search open while a picture is chosen', () => {
        const wrapper = picker('helmet-safety')

        expect(wrapper.find('input').exists()).toBe(true)
    })

    it('lists every picture of the catalogue', async () => {
        const wrapper = await opened(null)

        expect(wrapper.findAll('[role="option"]')).toHaveLength(GEAR_ICONS.length)
    })

    it('replaces the chosen picture with the one picked', async () => {
        const wrapper = await opened('helmet-safety')

        const [, second] = GEAR_ICONS
        await wrapper.findAll('[role="option"]').at(1)?.find('button').trigger('click')

        expect(wrapper.emitted('update:icon')?.at(-1)).toEqual([second?.name])
    })
})

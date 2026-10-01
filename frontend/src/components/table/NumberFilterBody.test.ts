/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import NumberFilterBody from './NumberFilterBody.vue'
import deDE from '@/i18n/de-DE'

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})

function body(selected: Set<string>) {
    const updates: Set<string>[] = []
    const wrapper = mount(NumberFilterBody, {
        global: {plugins: [i18n]},
        props: {
            choices: [{value: '6', label: '6'}, {value: '9', label: '9'}],
            modelValue: selected,
            'onUpdate:modelValue': (next: Set<string>) => updates.push(next),
        },
    })
    return {wrapper, updates}
}

describe('NumberFilterBody', () => {
    it('lists the values the rows hold to tick one by one', () => {
        const {wrapper} = body(new Set())

        expect(wrapper.text()).toContain('6')
        expect(wrapper.text()).toContain('9')
    })

    it('keeps the bounds when a value is ticked', async () => {
        const {wrapper, updates} = body(new Set(['min:5']))

        await wrapper.findAll('input[type="checkbox"]')[1]!.setValue(true)

        expect([...updates.at(-1)!].toSorted()).toEqual(['9', 'min:5'])
    })
})

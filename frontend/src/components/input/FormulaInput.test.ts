/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import FormulaInput from './FormulaInput.vue'

/**
 * The completion behind the scoring formula, which is the only place the field names are written
 * out by hand. It offered nothing at all when the bracket had just been opened, which is exactly
 * the moment somebody who does not remember the names needs it.
 */
function mountInput(value = '') {
    const wrapper = mount(FormulaInput, {
        props: {
            modelValue: value,
            'onUpdate:modelValue': (next: string) => wrapper.setProps({modelValue: next}),
            fields: [
                {name: 'Alter', type: 'NUMBER'},
                {name: 'Erfahrung', type: 'ENUM'},
            ],
        },
    })
    return wrapper
}

async function type(wrapper: ReturnType<typeof mountInput>, text: string) {
    const input = wrapper.get('input')
    const element = input.element as HTMLInputElement
    element.value = text
    element.setSelectionRange(text.length, text.length)
    await input.trigger('input')
}

describe('FormulaInput', () => {
    it('offers every field as soon as the bracket is opened', async () => {
        const wrapper = mountInput()

        await type(wrapper, '[')

        expect(wrapper.text()).toContain('[Alter]')
        expect(wrapper.text()).toContain('[Erfahrung]')
    })

    it('narrows to what is being typed', async () => {
        const wrapper = mountInput()

        await type(wrapper, '[Alt')

        expect(wrapper.text()).toContain('[Alter]')
        expect(wrapper.text()).not.toContain('[Erfahrung]')
    })

    it('offers the waiting time and the age function too', async () => {
        const wrapper = mountInput()

        await type(wrapper, '[')

        expect(wrapper.text()).toContain('[wartezeit_monate]')
        expect(wrapper.text()).toContain('age([Alter])')
    })

    it('offers nothing outside a bracket', async () => {
        const wrapper = mountInput()

        await type(wrapper, 'Alter')

        expect(wrapper.text()).not.toContain('[Alter]')
    })

    it('closes the bracket when a field is taken', async () => {
        const wrapper = mountInput()
        await type(wrapper, '[Alt')

        await wrapper.findAll('button')[0]?.trigger('mousedown')

        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['[Alter]'])
    })
})

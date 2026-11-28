/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it} from 'vitest'
import IconButton from '@/components/button/IconButton.vue'
import QuantityStepper from './QuantityStepper.vue'

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

function stepper(value: number, max = 3) {
    const wrapper = mount(QuantityStepper, {
        props: {modelValue: value, max, label: 'Anzahl', 'onUpdate:modelValue': (next: number) => wrapper.setProps({modelValue: next})},
        global: {plugins: [i18n]},
    })
    return wrapper
}

/** A count moved one at a time within its range, and pulled back into it when typed outside. */
describe('QuantityStepper', () => {
    it('steps up to the highest value and no further', async () => {
        const wrapper = stepper(2)
        const [less, more] = wrapper.findAllComponents(IconButton)

        await more!.vm.$emit('click')
        expect(wrapper.props('modelValue')).toBe(3)
        expect(more!.props('disabled')).toBe(true)
        expect(less!.props('disabled')).toBe(false)
    })

    it('takes a typed value only once the field is left, pulled back into the range', async () => {
        const wrapper = stepper(1)
        const input = wrapper.get('input')
        const field = input.element as HTMLInputElement

        field.value = '9'
        await input.trigger('input')
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
        await input.trigger('change')
        expect(wrapper.props('modelValue')).toBe(3)
    })
})

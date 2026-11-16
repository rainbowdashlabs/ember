/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import WidthField from './WidthField.vue'
import {FieldWidths} from './fieldLayout'

/** The width of a field, picked on a slider with one stop per width, narrowest to full. */
describe('WidthField', () => {
    function slider(modelValue: string) {
        return mount(WidthField, {props: {modelValue}})
    }

    it('offers six stops, narrowest to full', () => {
        const view = slider(FieldWidths.FULL)

        expect(view.findAll('[data-testid="field-width-stop"]').map(stop => stop.text()))
            .toEqual(['1/4', '1/3', '1/2', '2/3', '3/4', 'voll'])
        const range = view.find('input[type="range"]')
        expect(range.attributes('min')).toBe('0')
        expect(range.attributes('max')).toBe('5')
        expect(range.attributes('step')).toBe('1')
    })

    it('stands at the stored width and names it for a screen reader', () => {
        const range = slider(FieldWidths.TWO_THIRDS).find('input[type="range"]')

        expect((range.element as HTMLInputElement).value).toBe('3')
        expect(range.attributes('aria-valuetext')).toBe('2/3')
    })

    it('hands on the width of the stop it is moved to', async () => {
        const view = slider(FieldWidths.FULL)

        await view.find('input[type="range"]').setValue('0')
        await view.find('input[type="range"]').setValue('4')

        expect(view.emitted('update:modelValue')).toEqual([[FieldWidths.QUARTER], [FieldWidths.THREE_QUARTERS]])
    })

    it('keeps a width it does not know at full', () => {
        const view = slider('enormous')

        expect((view.find('input[type="range"]').element as HTMLInputElement).value).toBe('5')
        expect(view.emitted('update:modelValue')).toBeUndefined()
    })
})

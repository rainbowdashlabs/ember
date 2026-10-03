/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import FontFamilyPicker from './FontFamilyPicker.vue'
import {FontOrigin, FontStyle, type FontFamilyOption} from '@/api/generated/schema'

const FONTS: FontFamilyOption[] = [
    {family: 'Hausschrift', origin: FontOrigin.ASSOCIATION, styles: [FontStyle.REGULAR], printsOnPdf: true},
    {family: 'Zierschrift', origin: FontOrigin.STATION, styles: [FontStyle.REGULAR], printsOnPdf: false},
]

/**
 * The family picker of letters and PDF fields: the default font first, who uploaded each family, and a
 * family the template names but no longer reaches kept rather than swapped.
 */
describe('FontFamilyPicker', () => {
    function pick(modelValue: string | null, pdfOnly = false) {
        return mount(FontFamilyPicker, {props: {modelValue, label: 'Schrift', fonts: FONTS, pdfOnly}})
    }

    function values(wrapper: ReturnType<typeof pick>) {
        return wrapper.findAll('option').map(option => option.attributes('value'))
    }

    it('offers the default font first and names who uploaded each family', () => {
        const wrapper = pick(null)
        expect(values(wrapper)).toEqual(['', 'Hausschrift', 'Zierschrift'])
        const labels = wrapper.findAll('option').map(option => option.text())
        expect(labels[0]).toBe('Standard (Liberation Sans)')
        expect(labels[1]).toBe('Hausschrift (Verband)')
        expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('')
    })

    it('keeps a family that is no longer reached and says it prints in the default font', () => {
        const wrapper = pick('Gelöscht')
        expect(values(wrapper)).toContain('Gelöscht')
        expect(wrapper.findAll('option').at(-1)?.text()).toContain('Liberation Sans')
        expect((wrapper.find('select').element as HTMLSelectElement).value).toBe('Gelöscht')
    })

    it('offers a PDF field only what it can embed', () => {
        expect(values(pick(null, true))).toEqual(['', 'Hausschrift'])
    })

    it('stores the default font as no family', async () => {
        const wrapper = pick('Hausschrift')
        await wrapper.find('select').setValue('')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([null])
        await wrapper.find('select').setValue('Zierschrift')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['Zierschrift'])
    })
})

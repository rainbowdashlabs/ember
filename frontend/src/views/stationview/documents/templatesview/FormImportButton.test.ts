/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import FontFamilyPicker from '@/components/documents/fonts/FontFamilyPicker.vue'
import type {TemplateScreens} from '../templateScreens'
import FormImportButton from './FormImportButton.vue'

const formFonts = vi.fn()
const importForm = vi.fn()
const listFonts = vi.fn()

const screens = {
    editRoute: 'document-template-edit',
    source: {formFonts, importForm},
    fonts: {list: listFonts, sample: () => ''},
} as unknown as TemplateScreens

const FORM = new File(['docx'], 'Änderungsmeldung.docx')

const IMPORTED = {
    template: {id: 5, name: 'Änderungsmeldung'},
    recognised: [],
    asked: [],
    unplaced: [],
    fonts: [{family: 'Frutiger 45 Light', replacement: 'Hausschrift'}],
}

async function choose() {
    const button = await mountSuspended(FormImportButton, {
        props: {screens},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
    })
    const input = button.find<HTMLInputElement>('[data-testid="template-import-form-file"]')
    Object.defineProperty(input.element, 'files', {value: [FORM]})
    await input.trigger('change')
    await flushPromises()
    return button
}

/** A Word form is converted only once a replacement is chosen for every font the owner has not got. */
describe('FormImportButton', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        importForm.mockResolvedValue(IMPORTED)
        listFonts.mockResolvedValue({reachable: [], own: [], defaultFamily: 'Berlin Type', defaultStyles: []})
    })

    it('converts a form whose fonts are all there right away', async () => {
        formFonts.mockResolvedValue({missing: [], suggested: {}, defaultFamily: 'Berlin Type'})

        const button = await choose()

        expect(importForm).toHaveBeenCalledWith(FORM, {})
        expect(button.find('[data-testid="form-font-choice"]').exists()).toBe(false)
        expect(button.find('[data-testid="form-import-report"]').text()).toContain('Frutiger 45 Light durch Hausschrift')
    })

    it('asks for a replacement of each missing font, a look-alike first, and converts with the ones chosen', async () => {
        formFonts.mockResolvedValue({
            missing: ['Frutiger 45 Light', 'Futura', 'Garamond', 'Calibri'],
            suggested: {
                'Frutiger 45 Light': {family: 'Open Sans', sameMeasure: false},
                Calibri: {family: 'Carlito', sameMeasure: true},
            },
            defaultFamily: 'Berlin Type',
        })

        const button = await choose()

        expect(importForm).not.toHaveBeenCalled()
        const pickers = button.findAllComponents(FontFamilyPicker)
        expect(pickers.map(picker => picker.props('modelValue'))).toEqual(['Open Sans', null, null, 'Carlito'])
        expect(pickers[3]!.props('label')).toBe('Statt Calibri (Carlito hat dieselben Buchstabenbreiten)')
        pickers[1]!.vm.$emit('update:modelValue', 'Hausschrift')
        await flushPromises()
        await button.find('[data-testid="form-font-confirm"]').trigger('click')
        await flushPromises()

        expect(importForm).toHaveBeenCalledWith(
            FORM, {'Frutiger 45 Light': 'Open Sans', Futura: 'Hausschrift', Garamond: '', Calibri: 'Carlito'})
    })
})

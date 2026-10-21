/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import FontFamilyPicker from '@/components/documents/fonts/FontFamilyPicker.vue'
import type {MissingFonts} from '@/api/generated/schema'
import type {TemplateScreens} from '../templateScreens'
import FormImportButton from './FormImportButton.vue'

const formFonts = vi.fn()
const importForm = vi.fn()
const formPreview = vi.fn()
const listFonts = vi.fn()

const screens = {
    editRoute: 'document-template-edit',
    source: {formFonts, importForm, formPreview},
    fonts: {list: listFonts, sample: () => ''},
} as unknown as TemplateScreens

const FORM = new File(['docx'], 'Änderungsmeldung.docx')

const IMPORTED = {
    template: {id: 5, name: 'Änderungsmeldung'},
    recognised: [],
    asked: [],
    unplaced: [],
    fonts: [{family: 'Frutiger 45 Light', replacement: 'Hausschrift', size: 95}],
}

const PREVIEW = new Blob(['%PDF'], {type: 'application/pdf'})

/** What the server says of a form's fonts, where Open Sans ships a light cut and nothing is embedded. */
function missingFonts(fonts: Partial<MissingFonts> & Pick<MissingFonts, 'missing'>): MissingFonts {
    return {
        suggested: {},
        defaultFamily: 'Berlin Type',
        embedded: [],
        light: fonts.missing.filter(family => family.includes('Light')),
        lightCuts: ['Open Sans'],
        ...fonts,
    }
}

async function choose() {
    const button = await mountSuspended(FormImportButton, {
        props: {screens},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}, PdfCanvas: true}},
    })
    const input = button.find<HTMLInputElement>('[data-testid="template-import-form-file"]')
    Object.defineProperty(input.element, 'files', {value: [FORM]})
    await input.trigger('change')
    await flushPromises()
    return button
}

/**
 * A Word form is converted only once a replacement and its size are chosen for every font the owner has
 * not got, compared on a preview of the original beside the form drawn with them.
 */
describe('FormImportButton', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        importForm.mockResolvedValue(IMPORTED)
        formPreview.mockResolvedValue(PREVIEW)
        listFonts.mockResolvedValue({reachable: [], own: [], defaultFamily: 'Berlin Type', defaultStyles: []})
    })

    it('converts a form whose fonts are all there right away', async () => {
        formFonts.mockResolvedValue(missingFonts({missing: []}))

        const button = await choose()

        expect(importForm).toHaveBeenCalledWith(FORM, {})
        expect(formPreview).not.toHaveBeenCalled()
        expect(button.find('[data-testid="form-font-choice"]').exists()).toBe(false)
        expect(button.find('[data-testid="form-import-report"]').text())
            .toContain('Frutiger 45 Light durch Hausschrift (95 %)')
    })

    it('asks for a replacement of each missing font, a look-alike first, and converts with the ones chosen', async () => {
        formFonts.mockResolvedValue(missingFonts({
            missing: ['Frutiger 45 Light', 'Futura', 'Garamond', 'Calibri'],
            suggested: {
                'Frutiger 45 Light': {family: 'Open Sans', sameMeasure: false},
                Calibri: {family: 'Carlito', sameMeasure: true},
            },
            embedded: ['Futura'],
        }))

        const button = await choose()

        expect(importForm).not.toHaveBeenCalled()
        const pickers = button.findAllComponents(FontFamilyPicker)
        expect(pickers.map(picker => picker.props('modelValue'))).toEqual(['Open Sans', null, null, 'Carlito'])
        expect(pickers[3]!.props('label')).toBe('Statt Calibri (Carlito hat dieselben Buchstabenbreiten)')
        pickers[1]!.vm.$emit('update:modelValue', 'Hausschrift')
        await button.find('[data-testid="form-font-Frutiger 45 Light-size"]').setValue('92')
        await flushPromises()
        await button.find('[data-testid="form-font-confirm"]').trigger('click')
        await flushPromises()

        expect(importForm).toHaveBeenCalledWith(FORM, {
            'Frutiger 45 Light': {family: 'Open Sans', size: 92, weight: 'LIGHT'},
            Futura: {family: 'Hausschrift', size: 100, weight: 'REGULAR'},
            Garamond: {family: '', size: 100, weight: 'REGULAR'},
            Calibri: {family: 'Carlito', size: 100, weight: 'REGULAR'},
        })
    })

    it('offers light only where the family has a light cut and goes back to regular without one', async () => {
        formFonts.mockResolvedValue(missingFonts({
            missing: ['Frutiger 45 Light'],
            suggested: {'Frutiger 45 Light': {family: 'Open Sans', sameMeasure: false}},
        }))

        const button = await choose()
        const weight = button.find<HTMLSelectElement>('[data-testid="form-font-Frutiger 45 Light-weight"]')
        expect(weight.element.value).toBe('LIGHT')
        expect(weight.attributes('disabled')).toBeUndefined()

        button.findComponent(FontFamilyPicker).vm.$emit('update:modelValue', 'Carlito')
        await flushPromises()

        expect(weight.element.value).toBe('REGULAR')
        expect(weight.attributes('disabled')).toBeDefined()
    })

    it('draws the original with a file for each font it lacks and says where it shows a look-alike', async () => {
        formFonts.mockResolvedValue(missingFonts({
            missing: ['Frutiger 45 Light'],
            suggested: {'Frutiger 45 Light': {family: 'Open Sans', sameMeasure: false}},
        }))
        const fontFile = new File(['font'], 'FrutigerLTStd-Light.otf')

        const button = await choose()

        const choices = {'Frutiger 45 Light': {family: 'Open Sans', size: 100, weight: 'LIGHT'}}
        expect(formPreview).toHaveBeenCalledWith(FORM, 'original', choices, {})
        expect(formPreview).toHaveBeenCalledWith(FORM, 'replaced', choices, {})
        expect(button.find('[data-testid="form-preview-guessed"]').text()).toContain('Frutiger 45 Light')

        await button.find('[data-testid="form-original-font-Frutiger 45 Light-add"]').trigger('click')
        const input = button.find<HTMLInputElement>('[data-testid="form-original-fonts-file"]')
        Object.defineProperty(input.element, 'files', {value: [fontFile], configurable: true})
        await input.trigger('change')
        await button.find('[data-testid="form-preview-refresh"]').trigger('click')
        await flushPromises()

        expect(formPreview).toHaveBeenCalledWith(FORM, 'original', choices, {'Frutiger 45 Light': fontFile})
        expect(button.find('[data-testid="form-preview-guessed"]').exists()).toBe(false)
    })

    it('holds back the conversion and the preview while a size is outside what a form takes', async () => {
        formFonts.mockResolvedValue(missingFonts({missing: ['Futura']}))

        const button = await choose()
        await button.find('[data-testid="form-font-Futura-size"]').setValue('151')

        expect(button.text()).toContain('Zwischen 50 und 150 %')
        expect(button.find('[data-testid="form-font-confirm"]').attributes('disabled')).toBeDefined()
        expect(button.find('[data-testid="form-preview-refresh"]').attributes('disabled')).toBeDefined()
    })
})

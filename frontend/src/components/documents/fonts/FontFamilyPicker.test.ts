/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import {defineComponent, h} from 'vue'
import FontFamilyPicker from './FontFamilyPicker.vue'
import {FontOrigin, FontStyle, type FontFamilyOption} from '@/api/generated/schema'
import {provideFontSamples} from '@/composables/useFontSamples'
import {options, page, panelIsOpen, press} from '@/test/dropdown'

enableAutoUnmount(afterEach)

const FONTS: FontFamilyOption[] = [
    {family: 'Hausschrift', origin: FontOrigin.ASSOCIATION, styles: [FontStyle.REGULAR], printsOnPdf: true, sample: 'a1'},
    {family: 'Liberation Serif', origin: FontOrigin.BUILT_IN, styles: [FontStyle.REGULAR], printsOnPdf: true, sample: 'b2'},
    {family: 'Zierschrift', origin: FontOrigin.STATION, styles: [FontStyle.REGULAR], printsOnPdf: false, sample: 'c3'},
]

/** Draws the picture where it is asked for, so a test reads the address a sample is drawn from. */
const ShownImage = defineComponent({
    props: {src: {type: String, required: true}, alt: {type: String, default: ''}},
    setup: props => () => h('img', {src: props.src, alt: props.alt, 'data-testid': 'font-sample'}),
})

function address(family: string | null, style: FontStyle, version: string): string {
    return `/samples/${family ?? 'standard'}/${style}/${version}`
}

/**
 * The family picker of letters and PDF fields: a list with the default font first, where each family
 * comes from and a line of sample text the server draws, walked by the keyboard, and a family the
 * template names but no longer reaches kept rather than swapped.
 */
describe('FontFamilyPicker', () => {
    type PickerProps = Partial<InstanceType<typeof FontFamilyPicker>['$props']>

    async function open(props: PickerProps = {}, samples = true) {
        const host = defineComponent({
            setup() {
                if (samples) provideFontSamples(address)
                return () => h(FontFamilyPicker, {modelValue: null, label: 'Schrift', fonts: FONTS, ...props})
            },
        })
        const wrapper = mount(host, {attachTo: document.body, global: {stubs: {AuthImage: ShownImage}}})
        await wrapper.get('button').trigger('click')
        await flushPromises()
        return wrapper
    }

    function emitted(wrapper: Awaited<ReturnType<typeof open>>) {
        return wrapper.findComponent(FontFamilyPicker).emitted('update:modelValue')
    }

    function samples(): (string | undefined)[] {
        return page().findAll('[data-testid="font-sample"]').map(image => image.attributes('src'))
    }

    it('lists the default font first, then every family with its origin and a sample of it', async () => {
        await open()
        expect(panelIsOpen()).toBe(true)
        const entries = options().map(option => option.text())
        expect(entries).toEqual(['Liberation SansStandard', 'HausschriftVerband', 'Liberation SerifMitgeliefert', 'ZierschriftWache'])
        expect(samples()).toEqual([
            '/samples/standard/REGULAR/Liberation Sans',
            '/samples/Hausschrift/REGULAR/a1',
            '/samples/Liberation Serif/REGULAR/b2',
            '/samples/Zierschrift/REGULAR/c3',
        ])
        expect(page().find('[role="listbox"]').attributes('aria-label')).toBe('Schrift')
    })

    it('takes a family with the keyboard and closes', async () => {
        const wrapper = await open()
        await press('ArrowDown')
        await press('ArrowDown')
        await press('Enter')
        expect(emitted(wrapper)?.at(-1)).toEqual(['Liberation Serif'])
        expect(panelIsOpen()).toBe(false)
    })

    it('stores the default font as no family', async () => {
        const wrapper = await open({modelValue: 'Hausschrift'})
        expect(options()[1]?.attributes('aria-selected')).toBe('true')
        await options()[0]?.trigger('click')
        expect(emitted(wrapper)?.at(-1)).toEqual([null])
    })

    it('keeps a family that is no longer reached, marked and without a sample', async () => {
        const wrapper = await open({modelValue: 'Gelöscht', defaultFamily: 'Berlin Type Office'})
        expect(wrapper.get('button').text()).toBe('Gelöscht (nicht mehr verfügbar, gedruckt in Berlin Type Office)')
        const missing = options().at(-1)
        expect(missing?.text()).toContain('Nicht mehr verfügbar')
        expect(missing?.text()).toContain('Gedruckt in Berlin Type Office')
        expect(missing?.find('[data-testid="font-sample"]').exists()).toBe(false)
        expect(samples()[0]).toBe('/samples/standard/REGULAR/Berlin Type Office')
    })

    it('offers a PDF field only what it can embed', async () => {
        await open({pdfOnly: true})
        expect(options().map(option => option.text())).toEqual(['Liberation SansStandard', 'HausschriftVerband', 'Liberation SerifMitgeliefert'])
    })

    it('shows names only where no screen says where samples are drawn', async () => {
        await open({}, false)
        expect(options()).toHaveLength(4)
        expect(samples()).toEqual([])
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import EditorColorPicker from './EditorColorPicker.vue'

/** One colour entry of the editor's menu: swatches, a hex code and the browser's own picker. */
describe('EditorColorPicker', () => {
    async function opened(current: string | null = null) {
        const wrapper = mount(EditorColorPicker, {
            props: {
                open: false,
                icon: ['fas', 'palette'],
                label: 'Textfarbe',
                swatches: ['', '#ec2929'],
                resetLabel: 'Standard',
                current,
                active: false,
            },
        })
        await wrapper.setProps({open: true})
        return wrapper
    }

    it('applies a swatch and closes', async () => {
        const wrapper = await opened()
        await wrapper.find('[aria-label="#ec2929"]').trigger('click')

        expect(wrapper.emitted('pick')).toEqual([['#ec2929']])
        expect(wrapper.emitted('update:open')).toEqual([[false]])
    })

    it('applies a hex code confirmed with enter, written out in full', async () => {
        const wrapper = await opened()
        const hex = wrapper.find('[data-testid="editor-color-hex"]')
        await hex.setValue('#AbC')
        await hex.trigger('keydown', {key: 'Enter'})

        expect(wrapper.emitted('pick')).toEqual([['#aabbcc']])
        expect(wrapper.emitted('update:open')).toEqual([[false]])
    })

    it('applies a hex code when the field is left and stays open', async () => {
        const wrapper = await opened()
        const hex = wrapper.find('[data-testid="editor-color-hex"]')
        await hex.setValue('#123456')
        await hex.trigger('blur')

        expect(wrapper.emitted('pick')).toEqual([['#123456']])
        expect(wrapper.emitted('update:open')).toBeUndefined()
    })

    it('refuses what is no hex code and says so', async () => {
        const wrapper = await opened()
        const hex = wrapper.find('[data-testid="editor-color-hex"]')
        await hex.setValue('#12345')
        await hex.trigger('keydown', {key: 'Enter'})

        expect(wrapper.emitted('pick')).toBeUndefined()
        expect(hex.attributes('aria-invalid')).toBe('true')
        expect(wrapper.text()).toContain('#rgb oder #rrggbb')
    })

    it('shows the colour at the cursor as its hex code', async () => {
        const wrapper = await opened('rgb(26, 43, 60)')

        expect((wrapper.find('[data-testid="editor-color-hex"]').element as HTMLInputElement).value).toBe('#1a2b3c')
        expect((wrapper.find('[data-testid="editor-color-spectrum"]').element as HTMLInputElement).value).toBe('#1a2b3c')
    })

    it('applies a colour from the browser picker', async () => {
        const wrapper = await opened()
        const spectrum = wrapper.find('[data-testid="editor-color-spectrum"]')
        ;(spectrum.element as HTMLInputElement).value = '#654321'
        await spectrum.trigger('input')
        await spectrum.trigger('change')

        expect(wrapper.emitted('pick')).toEqual([['#654321']])
    })
})

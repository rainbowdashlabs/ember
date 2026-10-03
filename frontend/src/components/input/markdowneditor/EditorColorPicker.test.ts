/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {DOMWrapper, enableAutoUnmount, mount} from '@vue/test-utils'
import {nextTick} from 'vue'
import EditorColorPicker from './EditorColorPicker.vue'

enableAutoUnmount(afterEach)

/**
 * One colour entry of the editor's menu: swatches, a hex code and the browser's own picker, in a
 * panel rendered at the end of the page so the edge of the text field cannot cut it off.
 */
describe('EditorColorPicker', () => {
    async function opened(current: string | null = null) {
        const wrapper = mount(EditorColorPicker, {
            attachTo: document.body,
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

    function panel(): HTMLElement | null {
        return document.querySelector<HTMLElement>('[role="dialog"][aria-label="Textfarbe"]')
    }

    function inPanel(selector: string): DOMWrapper<HTMLInputElement> {
        return new DOMWrapper(panel()!.querySelector<HTMLInputElement>(selector)!)
    }

    function pressOn(target: EventTarget) {
        target.dispatchEvent(new PointerEvent('pointerdown', {bubbles: true, button: 0}))
    }

    it('opens its panel at the end of the page, placed by hand', async () => {
        const wrapper = await opened()

        expect(panel()?.parentElement).toBe(document.body)
        expect(wrapper.element.contains(panel())).toBe(false)
        expect(panel()?.style.position).toBe('fixed')
    })

    it('stays open on a press inside its panel', async () => {
        const wrapper = await opened()

        pressOn(inPanel('[data-testid="editor-color-hex"]').element)
        pressOn(inPanel('[aria-label="#ec2929"]').element)
        await nextTick()

        expect(wrapper.emitted('update:open')).toBeUndefined()
    })

    it('closes on a press outside without applying anything', async () => {
        const wrapper = await opened()

        pressOn(document.body)
        await nextTick()

        expect(wrapper.emitted('update:open')).toEqual([[false]])
        expect(wrapper.emitted('pick')).toBeUndefined()
    })

    it('closes on escape', async () => {
        const wrapper = await opened()

        document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))
        await nextTick()

        expect(wrapper.emitted('update:open')).toEqual([[false]])
    })

    it('applies a swatch and closes', async () => {
        const wrapper = await opened()
        await inPanel('[aria-label="#ec2929"]').trigger('click')

        expect(wrapper.emitted('pick')).toEqual([['#ec2929']])
        expect(wrapper.emitted('update:open')).toEqual([[false]])
    })

    it('applies a hex code confirmed with enter, written out in full', async () => {
        const wrapper = await opened()
        const hex = inPanel('[data-testid="editor-color-hex"]')
        await hex.setValue('#AbC')
        await hex.trigger('keydown', {key: 'Enter'})

        expect(wrapper.emitted('pick')).toEqual([['#aabbcc']])
        expect(wrapper.emitted('update:open')).toEqual([[false]])
    })

    it('applies a hex code when the field is left and stays open', async () => {
        const wrapper = await opened()
        const hex = inPanel('[data-testid="editor-color-hex"]')
        await hex.setValue('#123456')
        await hex.trigger('blur')

        expect(wrapper.emitted('pick')).toEqual([['#123456']])
        expect(wrapper.emitted('update:open')).toBeUndefined()
    })

    it('refuses what is no hex code and says so', async () => {
        const wrapper = await opened()
        const hex = inPanel('[data-testid="editor-color-hex"]')
        await hex.setValue('#12345')
        await hex.trigger('keydown', {key: 'Enter'})

        expect(wrapper.emitted('pick')).toBeUndefined()
        expect(hex.attributes('aria-invalid')).toBe('true')
        expect(panel()?.textContent).toContain('#rgb oder #rrggbb')
    })

    it('shows the colour at the cursor as its hex code', async () => {
        await opened('rgb(26, 43, 60)')

        expect(inPanel('[data-testid="editor-color-hex"]').element.value).toBe('#1a2b3c')
        expect(inPanel('[data-testid="editor-color-spectrum"]').element.value).toBe('#1a2b3c')
    })

    it('applies a colour from the browser picker', async () => {
        const wrapper = await opened()
        const spectrum = inPanel('[data-testid="editor-color-spectrum"]')
        spectrum.element.value = '#654321'
        await spectrum.trigger('input')
        await spectrum.trigger('change')

        expect(wrapper.emitted('pick')).toEqual([['#654321']])
    })
})

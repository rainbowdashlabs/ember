/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import {defineComponent, h, ref} from 'vue'
import Modal from '@/components/feedback/Modal.vue'
import SingleSelectDropdown from './SingleSelectDropdown.vue'
import {options, page, panelIsOpen, press, pressOutside} from '@/test/dropdown'

enableAutoUnmount(afterEach)

const FRUIT = [
    {value: 'a', label: 'Apfel', group: 'Obst'},
    {value: 'b', label: 'Birne', group: 'Obst'},
    {value: 'k', label: 'Kohl', group: 'Gemüse'},
]

async function openDropdown(props: Record<string, unknown> = {}) {
    const wrapper = mount(SingleSelectDropdown, {
        props: {modelValue: '', options: FRUIT, ...props},
        attachTo: document.body,
    })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    return wrapper
}

describe('SingleSelectDropdown', () => {
    it('opens a list of options under their headings', async () => {
        await openDropdown()
        expect(panelIsOpen()).toBe(true)
        expect(options().map(option => option.text())).toEqual(['Apfel', 'Birne', 'Kohl'])
        expect(page().text()).toContain('Gemüse')
    })

    it('marks the current choice', async () => {
        await openDropdown({modelValue: 'b'})
        expect(options()[1]!.attributes('aria-selected')).toBe('true')
    })

    it('takes an option with the keyboard and closes', async () => {
        const wrapper = await openDropdown()
        await press('ArrowDown')
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['b'])
        expect(panelIsOpen()).toBe(false)
    })

    it('takes an option by a click', async () => {
        const wrapper = await openDropdown()
        await options()[2]!.trigger('click')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['k'])
    })

    it('closes on a press outside without taking anything', async () => {
        const wrapper = await openDropdown()
        await pressOutside()
        expect(panelIsOpen()).toBe(false)
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    })

    it('closes on Escape', async () => {
        await openDropdown()
        await press('Escape')
        expect(panelIsOpen()).toBe(false)
    })

    it('takes the choice back where it may', async () => {
        const wrapper = await openDropdown({modelValue: 'a', clearable: true})
        await page().get('button.text-error').trigger('click')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([''])
    })

    /**
     * Inside a dialog the panel is a layer of its own above it: taking an option leaves the dialog
     * open, and Escape closes the panel before it closes the dialog.
     */
    it('works inside a dialog without closing it', async () => {
        const dialogOpen = ref(true)
        const choice = ref('')
        mount(defineComponent({
            setup: () => () => h(Modal, {modelValue: dialogOpen.value, 'onUpdate:modelValue': (v: boolean) => dialogOpen.value = v}, () =>
                h(SingleSelectDropdown, {modelValue: choice.value, options: FRUIT, 'onUpdate:modelValue': (v: string) => choice.value = v})),
        }), {attachTo: document.body})
        await flushPromises()
        await new Promise(resolve => setTimeout(resolve, 0))

        await page().get('[role="dialog"] button:not([data-cancel])').trigger('click')
        await flushPromises()
        await options()[1]!.trigger('pointerdown')
        await options()[1]!.trigger('click')
        await flushPromises()
        expect(choice.value).toBe('b')
        expect(dialogOpen.value).toBe(true)

        await page().get('[role="dialog"] button:not([data-cancel])').trigger('click')
        await flushPromises()
        await press('Escape')
        expect(panelIsOpen()).toBe(false)
        expect(dialogOpen.value).toBe(true)
    })

    it('narrows the options to what was typed', async () => {
        await openDropdown({searchable: true})
        await page().get('input').setValue('bir')
        expect(options().map(option => option.text())).toEqual(['Birne'])
    })
})

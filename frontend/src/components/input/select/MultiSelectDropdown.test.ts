/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import MultiSelectDropdown from './MultiSelectDropdown.vue'
import {options, page, panelIsOpen, press, pressOutside} from '@/test/dropdown'

enableAutoUnmount(afterEach)

const DAYS = [
    {value: 'mo', label: 'Montag'},
    {value: 'di', label: 'Dienstag'},
    {value: 'mi', label: 'Mittwoch'},
]

async function openDropdown(modelValue: string[] = [], props: Record<string, unknown> = {}) {
    const wrapper = mount(MultiSelectDropdown, {
        props: {modelValue, options: DAYS, ...props},
        attachTo: document.body,
    })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    return wrapper
}

describe('MultiSelectDropdown', () => {
    it('is a list that takes several', async () => {
        await openDropdown(['di'])
        expect(page().get('[role="listbox"]').attributes('aria-multiselectable')).toBe('true')
        expect(options()[1]!.attributes('aria-selected')).toBe('true')
    })

    it('adds an option with the keyboard and stays open', async () => {
        const wrapper = await openDropdown(['mo'])
        await press('ArrowDown')
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['mo', 'di']])
        expect(panelIsOpen()).toBe(true)
    })

    it('takes an option back off by a click', async () => {
        const wrapper = await openDropdown(['mo', 'di'])
        await options()[0]!.trigger('click')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['di']])
    })

    it('takes all and none at once', async () => {
        const wrapper = await openDropdown(['mo'])
        await page().get('[role="dialog"] button:not([disabled])').trigger('click')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['mo', 'di', 'mi']])
    })

    it('closes on a press outside', async () => {
        await openDropdown()
        await pressOutside()
        expect(panelIsOpen()).toBe(false)
    })

    it('names the first two choices on its button and counts the rest', () => {
        const wrapper = mount(MultiSelectDropdown, {props: {modelValue: ['mo', 'di', 'mi'], options: DAYS}})
        expect(wrapper.get('button').text()).toContain('Montag, Dienstag +1')
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import MultiSelectInput from './MultiSelectInput.vue'
import {options, panelIsOpen, press, pressOutside} from '@/test/dropdown'

enableAutoUnmount(afterEach)

const ROLES = [
    {value: 'lead', label: 'Leitung'},
    {value: 'crew', label: 'Mannschaft'},
    {value: 'guest', label: 'Gast'},
]

async function openAdder(modelValue: string[]) {
    const wrapper = mount(MultiSelectInput, {
        props: {modelValue, options: ROLES},
        attachTo: document.body,
    })
    await wrapper.findAll('button').at(-1)!.trigger('click')
    await flushPromises()
    return wrapper
}

describe('MultiSelectInput', () => {
    it('offers only what is not chosen yet', async () => {
        await openAdder(['crew'])
        expect(options().map(option => option.text())).toEqual(['Leitung', 'Gast'])
    })

    it('adds one with the keyboard and closes', async () => {
        const wrapper = await openAdder(['crew'])
        await press('ArrowDown')
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['crew', 'guest']])
        expect(panelIsOpen()).toBe(false)
    })

    it('takes a chip away', async () => {
        const wrapper = mount(MultiSelectInput, {props: {modelValue: ['crew', 'lead'], options: ROLES}})
        await wrapper.get('span button').trigger('click')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([['lead']])
    })

    it('closes on a press outside', async () => {
        await openAdder([])
        await pressOutside()
        expect(panelIsOpen()).toBe(false)
    })
})

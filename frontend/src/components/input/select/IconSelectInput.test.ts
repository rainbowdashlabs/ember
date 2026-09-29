/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import IconSelectInput from './IconSelectInput.vue'
import {options, panelIsOpen, press, pressOutside} from '@/test/dropdown'

enableAutoUnmount(afterEach)

const PRIORITIES = [
    {value: 'LOW', label: 'Niedrig', icon: ['fas', 'angle-down']},
    {value: 'MEDIUM', label: 'Mittel', icon: ['fas', 'equals']},
    {value: 'HIGH', label: 'Hoch', icon: ['fas', 'angle-up']},
]

async function mountSelect(props: Record<string, unknown> = {}) {
    const wrapper = mount(IconSelectInput, {
        props: {modelValue: 'LOW', options: PRIORITIES, ...props},
        attachTo: document.body,
    })
    await flushPromises()
    return wrapper
}

describe('IconSelectInput', () => {
    it('opens by itself where it is asked to', async () => {
        await mountSelect({autoOpen: true})
        expect(panelIsOpen()).toBe(true)
    })

    it('stays closed until pressed otherwise', async () => {
        const wrapper = await mountSelect()
        expect(panelIsOpen()).toBe(false)
        await wrapper.get('button').trigger('click')
        await flushPromises()
        expect(options().map(option => option.text())).toEqual(['Niedrig', 'Mittel', 'Hoch'])
    })

    it('starts on the current choice and takes the next one with the keyboard', async () => {
        const wrapper = await mountSelect({autoOpen: true, modelValue: 'MEDIUM'})
        await press('ArrowDown')
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['HIGH'])
        expect(panelIsOpen()).toBe(false)
    })

    it('closes on a press outside', async () => {
        await mountSelect({autoOpen: true})
        await pressOutside()
        expect(panelIsOpen()).toBe(false)
    })
})

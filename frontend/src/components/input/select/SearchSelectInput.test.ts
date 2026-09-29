/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import SearchSelectInput from './SearchSelectInput.vue'
import {options, page, panelIsOpen, press, pressOutside} from '@/test/dropdown'

enableAutoUnmount(afterEach)

const ZONES = [
    {value: 'Europe/Berlin', label: 'Berlin'},
    {value: 'Europe/Paris', label: 'Paris'},
    {value: 'America/New_York', label: 'New York'},
]

async function openDropdown(modelValue = '') {
    const wrapper = mount(SearchSelectInput, {
        props: {modelValue, options: ZONES},
        attachTo: document.body,
    })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    return wrapper
}

describe('SearchSelectInput', () => {
    it('reads the current choice, or the value where it is not on offer', () => {
        expect(mount(SearchSelectInput, {props: {modelValue: 'Europe/Paris', options: ZONES}}).text()).toBe('Paris')
        expect(mount(SearchSelectInput, {props: {modelValue: 'Asia/Tokyo', options: ZONES}}).text()).toBe('Asia/Tokyo')
    })

    it('finds by label and by value, and takes the first match on Enter', async () => {
        const wrapper = await openDropdown()
        await page().get('input').setValue('america')
        expect(options().map(option => option.text())).toEqual(['New York'])
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['America/New_York'])
        expect(panelIsOpen()).toBe(false)
    })

    it('walks the matches with the arrow keys', async () => {
        const wrapper = await openDropdown()
        await press('ArrowDown')
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['Europe/Paris'])
    })

    it('says so when nothing matches', async () => {
        await openDropdown()
        await page().get('input').setValue('xyz')
        expect(page().text()).toContain('Keine Ergebnisse')
    })

    it('closes on a press outside without taking anything', async () => {
        const wrapper = await openDropdown()
        await pressOutside()
        expect(panelIsOpen()).toBe(false)
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    })
})

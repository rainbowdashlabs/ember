/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import LinkSearchInput from './LinkSearchInput.vue'
import {options, panelIsOpen, press, pressOutside} from '@/test/dropdown'

vi.mock('@/api/publicPages', () => ({
    listPublicPages: vi.fn().mockResolvedValue([{title: 'Jugendfeuerwehr', path: 'jugend'}]),
}))

vi.mock('@/api/media', () => ({
    listMediaFiles: vi.fn().mockResolvedValue([]),
}))

enableAutoUnmount(afterEach)

const BASE = '/public/station/wache'

async function focusField(modelValue = '') {
    const wrapper = mount(LinkSearchInput, {
        props: {modelValue, stationUid: 'wache', noFiles: true},
        attachTo: document.body,
    })
    const field = wrapper.get('input')
    ;(field.element as HTMLInputElement).focus()
    await field.trigger('focus')
    await flushPromises()
    return wrapper
}

describe('LinkSearchInput', () => {
    it('is a combobox that suggests the station\'s own places as it gets the focus', async () => {
        const wrapper = await focusField()
        expect(wrapper.get('input').attributes('role')).toBe('combobox')
        expect(options().map(option => option.text())).toEqual([
            expect.stringContaining('/calendar'),
            expect.stringContaining('/knowledge'),
            expect.stringContaining('/page/jugend'),
        ])
    })

    it('takes a suggestion with the keyboard from inside the field', async () => {
        const wrapper = await focusField()
        const field = wrapper.get('input').element
        await press('ArrowDown', field)
        await press('Enter', field)
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([`${BASE}/knowledge`])
        expect(panelIsOpen()).toBe(false)
    })

    it('narrows the suggestions to what was typed', async () => {
        const wrapper = await focusField()
        await wrapper.get('input').setValue('jugend')
        await flushPromises()
        expect(options()).toHaveLength(1)
    })

    it('suggests nothing for an address typed out in full', async () => {
        const wrapper = await focusField()
        await wrapper.get('input').setValue('https://example.org')
        await flushPromises()
        expect(panelIsOpen()).toBe(false)
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['https://example.org'])
    })

    it('closes on a press outside', async () => {
        await focusField()
        await pressOutside()
        expect(panelIsOpen()).toBe(false)
    })
})

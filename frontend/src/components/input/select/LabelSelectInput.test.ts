/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import LabelSelectInput, {type SelectableOption} from './LabelSelectInput.vue'
import {options, page, panelIsOpen, press, pressOutside} from '@/test/dropdown'

enableAutoUnmount(afterEach)

const LABELS: SelectableOption[] = [
    {id: 1, name: 'Dringend', color: '#ff0000'},
    {id: 2, name: 'Fahrzeug', color: null},
    {id: 3, name: 'Funk'},
]

async function openPicker(props: Record<string, unknown> = {}) {
    const wrapper = mount(LabelSelectInput, {
        props: {labels: LABELS, selected: LABELS.slice(0, 1), ...props},
        attachTo: document.body,
    })
    const trigger = wrapper.get('[data-testid="label-select"]')
    ;(trigger.element as HTMLElement).focus()
    await trigger.trigger('keydown', {key: 'Enter'})
    await flushPromises()
    return wrapper
}

async function type(text: string) {
    await page().get('input').setValue(text)
    await flushPromises()
}

describe('LabelSelectInput', () => {
    it('opens from the keyboard and marks what is picked', async () => {
        await openPicker()
        expect(panelIsOpen()).toBe(true)
        expect(options()[0]!.attributes('aria-selected')).toBe('true')
    })

    /** A word that is not a label yet is offered to be made first, so Enter alone makes it. */
    it('hands a picked label to the parent rather than keeping it', async () => {
        const wrapper = await openPicker()
        await type('fahr')
        await press('ArrowDown')
        await press('Enter')
        expect(wrapper.emitted('toggle')).toEqual([[2]])
        expect(panelIsOpen(), 'several may be picked, so it stays open').toBe(true)
    })

    it('makes a new word on Enter', async () => {
        const wrapper = await openPicker()
        await type('Atemschutz')
        await press('Enter')
        expect(wrapper.emitted('create')).toEqual([['Atemschutz']])
    })

    it('offers to make a word only where it is new', async () => {
        await openPicker()
        await type('funk')
        expect(page().find('[data-testid="label-select-create"]').exists()).toBe(false)
        await type('Funkgerät')
        expect(page().find('[data-testid="label-select-create"]').exists()).toBe(true)
    })

    it('hands a new word back as a draft where making waits for the form', async () => {
        const wrapper = await openPicker({deferCreate: true, single: true, selected: []})
        await type('Kiste')
        await page().get('[data-testid="label-select-create"]').trigger('click')
        expect(wrapper.emitted('update:drafts')).toEqual([[['Kiste']]])
        expect(panelIsOpen(), 'a single choice closes once made').toBe(false)
    })

    it('takes a picked label off from its chip', async () => {
        const wrapper = mount(LabelSelectInput, {props: {labels: LABELS, selected: LABELS.slice(0, 1)}})
        await wrapper.get('[data-testid="label-select"] span.cursor-pointer').trigger('click')
        expect(wrapper.emitted('toggle')).toEqual([[1]])
    })

    it('closes on a press outside', async () => {
        await openPicker()
        await pressOutside()
        expect(panelIsOpen()).toBe(false)
    })
})

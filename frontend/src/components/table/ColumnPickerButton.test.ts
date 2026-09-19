/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {afterEach, describe, expect, it} from 'vitest'
import {mount, type VueWrapper} from '@vue/test-utils'
import {nextTick} from 'vue'
import ColumnPickerButton from './ColumnPickerButton.vue'
import type {ColumnPickerOption} from './columns'

let wrapper: VueWrapper | null = null

afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
})

function options(count: number, visibleFrom = count): ColumnPickerOption[] {
    return Array.from({length: count}, (_, index) => ({key: `c${index}`, label: `Spalte ${index}`, visible: index >= visibleFrom}))
}

async function openPicker(list: ColumnPickerOption[]) {
    wrapper = mount(ColumnPickerButton, {attachTo: document.body, props: {options: list}})
    await wrapper.get('[data-testid="column-picker-trigger"]').trigger('click')
    await nextTick()
    return wrapper
}

function press(testId: string) {
    document.body.querySelector<HTMLElement>(`[data-testid="${testId}"]`)!.click()
}

describe('ColumnPickerButton', () => {
    it('shows every hidden column at once with all', async () => {
        const picker = await openPicker(options(4, 2))

        press('column-picker-all')

        expect(picker.emitted('setVisible')).toEqual([[['c0', 'c1'], true]])
    })

    it('hides every shown column at once with none', async () => {
        const picker = await openPicker(options(4, 2))

        press('column-picker-none')

        expect(picker.emitted('setVisible')).toEqual([[['c2', 'c3'], false]])
    })

    it('lays a list taller than the screen out in several columns', async () => {
        window.innerHeight = 600
        await openPicker(options(60))

        const list = document.body.querySelector<HTMLElement>('[data-testid="column-picker"] .grid')!
        const rows = Number(/repeat\((\d+)/.exec(list.style.gridTemplateRows)?.[1])

        expect(rows).toBeLessThan(60)
        expect(rows).toBeGreaterThan(0)
    })
})

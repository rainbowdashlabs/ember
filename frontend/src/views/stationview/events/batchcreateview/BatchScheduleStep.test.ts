/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {BatchRow} from '@/api/generated/schema'
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {localInputToInstant} from '@/util/format'
import BatchScheduleStep from './BatchScheduleStep.vue'

vi.mock('@/api', () => ({
    events: {generateDates: vi.fn()},
}))

const ADVENT = '﻿ ImprDatum,Von,Bis,Titel\n'
    + '04.12.26,16:30,19:00,Adventszauber Freitag 1\n'
    + '04.12.26,19:00,21:30,Adventszauber Freitag 2\n'
    + '05.12.26,16:30,19:00,Adventszauber Samstag 1\n'
    + '05.12.26,19:00,21:30,Adventszauber Samstag 2\n'

async function openCsvImport() {
    const wrapper = await mountSuspended(BatchScheduleStep, {props: {fieldDefs: []}})
    wrapper.findComponent(ToggleSwitch).vm.$emit('update:modelValue', 'csv')
    await flushPromises()
    return wrapper
}

async function uploadFile(content: string) {
    const wrapper = await openCsvImport()
    const input = wrapper.find('input[type="file"]')
    const file = new File([new TextEncoder().encode(content)], 'advent.csv', {type: 'text/csv'})
    Object.defineProperty(input.element, 'files', {value: [file], configurable: true})
    await input.trigger('change')
    await flushPromises()
    return wrapper
}

async function importFile(content: string) {
    const wrapper = await uploadFile(content)
    await vi.waitFor(() => expect(wrapper.text()).toContain('Spalten zuordnen'))
    const buttons = wrapper.findAllComponents(PrimaryButton)
    await buttons[buttons.length - 1]!.trigger('click')
    await flushPromises()
    return wrapper
}

/** Importing the dates of a batch from a file exported by a spreadsheet. */
describe('BatchScheduleStep', () => {
    it('imports a spreadsheet export on the dates and times it names', async () => {
        const wrapper = await importFile(ADVENT)

        const rows = wrapper.emitted('done')?.[0]?.[0] as BatchRow[]
        expect(rows.map(row => [row.name, row.startTime, row.endTime])).toEqual([
            ['Adventszauber Freitag 1', localInputToInstant('2026-12-04T16:30'), localInputToInstant('2026-12-04T19:00')],
            ['Adventszauber Freitag 2', localInputToInstant('2026-12-04T19:00'), localInputToInstant('2026-12-04T21:30')],
            ['Adventszauber Samstag 1', localInputToInstant('2026-12-05T16:30'), localInputToInstant('2026-12-05T19:00')],
            ['Adventszauber Samstag 2', localInputToInstant('2026-12-05T19:00'), localInputToInstant('2026-12-05T21:30')],
        ])
    })

    it('names a line whose date cannot be read instead of placing it at the moment of the import', async () => {
        const wrapper = await importFile('Datum,Von,Bis\n04.12.26,16:30,19:00\nsoon,16:30,19:00\n')

        expect(wrapper.emitted('done')).toBeUndefined()
        expect(wrapper.find('[role="alert"]').text()).toContain('3')
        expect(wrapper.find('[role="alert"]').text()).toContain('soon')
    })

    it('says a file holds no lines and drops the table of the file before it', async () => {
        const wrapper = await uploadFile('Datum,Von,Bis\n04.12.26,16:30,19:00\n')
        await vi.waitFor(() => expect(wrapper.text()).toContain('Spalten zuordnen'))
        const input = wrapper.find('input[type="file"]')
        const empty = new File([new TextEncoder().encode('Datum,Von,Bis\n,,\n')], 'leer.csv', {type: 'text/csv'})
        Object.defineProperty(input.element, 'files', {value: [empty], configurable: true})
        await input.trigger('change')
        await vi.waitFor(() => expect(wrapper.find('[role="alert"]').exists()).toBe(true))

        expect(wrapper.find('[role="alert"]').text()).toBe('Die Datei enthält keine Zeilen mit Terminen.')
        expect(wrapper.text()).not.toContain('Spalten zuordnen')
        expect(wrapper.text()).toContain('leer.csv')
    })
})

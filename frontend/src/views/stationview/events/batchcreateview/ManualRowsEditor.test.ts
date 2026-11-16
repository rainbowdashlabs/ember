/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {flushPromises, type VueWrapper} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {BatchRow} from '@/api/generated/schema'
import DateInput from '@/components/input/datetime/DateInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {localInputToInstant} from '@/util/format'
import ManualRowsEditor from './ManualRowsEditor.vue'
import {nextDraftRow, type KeyedDraftRow} from './draftRows'

/** The editor with its rows held the way the page holds them, so every change comes back in. */
async function mountEditor() {
    const wrapper: VueWrapper = await mountSuspended(ManualRowsEditor, {
        props: {
            drafts: [nextDraftRow([])],
            'onUpdate:drafts': (drafts: KeyedDraftRow[]) => wrapper.setProps({drafts}),
        },
    })
    return wrapper
}

async function fillRow(wrapper: VueWrapper, index: number, date: string, start: string, end: string, name = '') {
    wrapper.findAllComponents(DateInput)[index]!.vm.$emit('update:modelValue', date)
    await flushPromises()
    wrapper.findAllComponents(TimeShortInput)[index * 2]!.vm.$emit('update:modelValue', start)
    await flushPromises()
    wrapper.findAllComponents(TimeShortInput)[index * 2 + 1]!.vm.$emit('update:modelValue', end)
    await flushPromises()
    wrapper.findAllComponents(TextInput)[index]!.vm.$emit('update:modelValue', name)
    await flushPromises()
}

async function next(wrapper: VueWrapper) {
    await wrapper.findComponent(PrimaryButton).trigger('click')
    await flushPromises()
}

/** Writing the appointments of a batch down one by one instead of generating or importing them. */
describe('ManualRowsEditor', () => {
    it('hands on the rows written down, each on its own day and times', async () => {
        const wrapper = await mountEditor()
        await fillRow(wrapper, 0, '2026-12-04', '16:30', '19:00', 'Adventszauber Freitag 1')
        await wrapper.findComponent(SecondaryButton).trigger('click')
        await flushPromises()
        wrapper.findAllComponents(TimeShortInput)[2]!.vm.$emit('update:modelValue', '19:00')
        await flushPromises()
        wrapper.findAllComponents(TimeShortInput)[3]!.vm.$emit('update:modelValue', '21:30')
        await flushPromises()

        await next(wrapper)

        const rows = wrapper.emitted('done')?.[0]?.[0] as BatchRow[]
        expect(rows.map(row => [row.name, row.startTime, row.endTime])).toEqual([
            ['Adventszauber Freitag 1', localInputToInstant('2026-12-04T16:30'), localInputToInstant('2026-12-04T19:00')],
            [null, localInputToInstant('2026-12-04T19:00'), localInputToInstant('2026-12-04T21:30')],
        ])
    })

    it('marks a row without a day and an end before its start instead of going on', async () => {
        const wrapper = await mountEditor()
        await fillRow(wrapper, 0, '2026-12-04', '19:00', '16:30')
        await wrapper.findComponent(SecondaryButton).trigger('click')
        await flushPromises()
        wrapper.findAllComponents(DateInput)[1]!.vm.$emit('update:modelValue', '')
        await flushPromises()

        await next(wrapper)

        expect(wrapper.emitted('done')).toBeUndefined()
        const errors = wrapper.findAll('[role="alert"]').map(error => error.text())
        expect(errors).toEqual(['Endet nicht nach dem Beginn', 'Datum fehlt'])
    })

    it('removes the row asked for and keeps what the others hold', async () => {
        const wrapper = await mountEditor()
        await fillRow(wrapper, 0, '2026-12-04', '16:30', '19:00', 'Erster')
        await wrapper.findComponent(SecondaryButton).trigger('click')
        await flushPromises()
        wrapper.findAllComponents(TextInput)[1]!.vm.$emit('update:modelValue', 'Zweiter')
        await flushPromises()
        expect(wrapper.findAll('li')).toHaveLength(2)

        await wrapper.findAllComponents(DeleteButton)[0]!.trigger('click')
        await flushPromises()

        expect(wrapper.findAll('li')).toHaveLength(1)
        expect(wrapper.findComponent(TextInput).props('modelValue')).toBe('Zweiter')
    })

    it('refuses an appointment that ends when it starts', async () => {
        const wrapper = await mountEditor()
        await fillRow(wrapper, 0, '2026-12-04', '18:22', '18:22')

        await next(wrapper)

        expect(wrapper.emitted('done')).toBeUndefined()
        expect(wrapper.find('[role="alert"]').text()).toBe('Endet nicht nach dem Beginn')
    })
})

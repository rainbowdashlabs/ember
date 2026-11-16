/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import type {EventFieldEntry} from '@/api/generated/schema'
import EventFieldRow from './EventFieldRow.vue'

function row(field: Partial<EventFieldEntry>, extra: Record<string, unknown> = {}) {
    return mount(EventFieldRow, {
        props: {
            modelValue: {name: 'Ort', fieldType: 'TEXT', value: 'Gerätehaus', overview: true, isPublic: false, ...field} as EventFieldEntry,
            showValue: true,
            ...extra,
        },
        global: {stubs: {'font-awesome-icon': true, EventQuestionEditor: true}},
    })
}

/**
 * A field of an appointment takes one line: name, value, type and three marks. Its other settings
 * open on request.
 */
describe('EventFieldRow', () => {
    it('shows name, value and type on one line', () => {
        const wrapper = row({})

        expect((wrapper.get('[data-testid="event-field-row-name"]').element as HTMLInputElement).value).toBe('Ort')
        expect(wrapper.find('[data-testid="event-field-row-value"]').exists()).toBe(true)
        expect(wrapper.get('[data-testid="event-field-row-type"]').text()).not.toBe('')
        expect(wrapper.find('event-question-editor-stub').exists()).toBe(false)
    })

    it('marks overview and public and switches them with a click', async () => {
        const wrapper = row({})
        const overview = wrapper.get('[data-testid="event-field-row-overview"]')
        const isPublic = wrapper.get('[data-testid="event-field-row-public"]')
        expect(overview.attributes('aria-pressed')).toBe('true')
        expect(isPublic.attributes('aria-pressed')).toBe('false')

        await isPublic.trigger('click')

        expect(wrapper.emitted('update:modelValue')?.at(-1)?.[0]).toMatchObject({isPublic: true, overview: true})
    })

    it('marks a field that fills in the attendance sheet', () => {
        const linked = row({attendanceFieldId: 7}, {attendanceFields: [{id: 7, name: 'Fahrzeug'}]})

        expect(linked.get('[data-testid="event-field-row-attendance"]').text()).toContain('Fahrzeug')
        expect(row({}).find('[data-testid="event-field-row-attendance"]').exists()).toBe(false)
    })

    it('opens the full settings on request and closes them again', async () => {
        const wrapper = row({})

        await wrapper.get('[data-testid="event-field-row-open"]').trigger('click')
        expect(wrapper.find('event-question-editor-stub').exists()).toBe(true)

        await wrapper.get('[data-testid="event-field-row-close"]').trigger('click')
        expect(wrapper.find('event-question-editor-stub').exists()).toBe(false)
    })

    it('opens a field without a name straight away', () => {
        expect(row({name: ''}).find('event-question-editor-stub').exists()).toBe(true)
    })

    it('names the value box after its field', () => {
        expect(row({}).get('[role="group"]').attributes('aria-label')).toBe('Ort')
    })
})

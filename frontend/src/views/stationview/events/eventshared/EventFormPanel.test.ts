/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import EventFormPanel from './EventFormPanel.vue'

const NO_RESTRICTION = {mode: 'OR' as const, userTypes: [], groupIds: [], tagIds: [], memberIds: []}

function panel(eventType: string, extra: Record<string, unknown> = {}) {
    return mount(EventFormPanel, {
        props: {
            categories: [],
            templates: [],
            name: '',
            description: '',
            categoryId: '',
            templateId: '',
            fields: [],
            restriction: NO_RESTRICTION,
            viewRestriction: NO_RESTRICTION,
            eventType,
            dayOfWeek: '5',
            startTime: '2026-11-20T19:00',
            endTime: '2026-11-20T21:00',
            showSchedule: true,
            ...extra,
        },
        global: {stubs: {'font-awesome-icon': true, MarkdownEditor: true, EventFieldList: true, RestrictionsField: true}},
    })
}

/**
 * Whether an appointment repeats is one switch on the line under its times, with the rhythm and the
 * weekday beside it once it does.
 */
describe('EventFormPanel repeat line', () => {
    it('offers rhythm and weekday only once the appointment repeats', () => {
        const single = panel('ONE_TIME')
        expect(single.find('[aria-label="Wochentag"]').exists()).toBe(false)
        expect(single.find('[aria-label="Art"]').exists()).toBe(false)

        const series = panel('RECURRING')
        expect(series.find('[aria-label="Wochentag"]').exists()).toBe(true)
        expect(series.find('[aria-label="Art"]').exists()).toBe(true)
    })

    it('turns a single appointment into a weekly series and back', async () => {
        const wrapper = panel('ONE_TIME')
        const toggle = wrapper.get('[data-testid="event-type-toggle"] [role="switch"]')

        await toggle.trigger('click')
        expect(wrapper.emitted('update:eventType')?.at(-1)).toEqual(['RECURRING'])

        await wrapper.setProps({eventType: 'RECURRING'})
        await toggle.trigger('click')
        expect(wrapper.emitted('update:eventType')?.at(-1)).toEqual(['ONE_TIME'])
    })

    it('leaves the repeat line out for a part of an event group', () => {
        expect(panel('ONE_TIME', {eventGroupPart: true}).find('[data-testid="event-type-toggle"]').exists()).toBe(false)
    })

    it('puts category and attendance template, then the fields, then the description', () => {
        const html = panel('ONE_TIME').html()

        const template = html.indexOf('event-attendance-template')
        const fields = html.indexOf('event-field-list-stub')
        const description = html.indexOf('markdown-editor-stub')

        expect(template).toBeGreaterThan(-1)
        expect(template).toBeLessThan(fields)
        expect(fields).toBeLessThan(description)
    })
})

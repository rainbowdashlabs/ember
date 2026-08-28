/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import EventFieldList from './EventFieldList.vue'
import type {EventFieldEntry} from '@/api/events'

/**
 * The order of the questions is the order they are asked in, and moving one used to mean deleting
 * everything below it and typing it in again.
 */
describe('EventFieldList', () => {
    function twoFields(): EventFieldEntry[] {
        return [
            {name: 'Ort', fieldType: 'STRING', config: {}, value: '', overview: true, attendanceFieldId: null},
            {name: 'Treffpunkt', fieldType: 'STRING', config: {}, value: '', overview: true, attendanceFieldId: null},
        ]
    }

    it('moves a question past the one below it, form and all', async () => {
        const list = await mountSuspended(EventFieldList, {props: {fields: twoFields()}})

        await list.findAll('[data-testid="move-down"]')[0]!.trigger('click')
        await list.vm.$nextTick()

        const shown = list.findAll('[data-testid="event-field-name"]').map(input => (input.element as HTMLInputElement).value)
        expect(shown).toEqual(['Treffpunkt', 'Ort'])

        const handedBack = list.emitted('update:fields')?.at(-1)?.[0] as EventFieldEntry[]
        expect(handedBack.map(field => field.name)).toEqual(['Treffpunkt', 'Ort'])
    })

    it('shows the value a question starts off with, under the label it was given', async () => {
        const list = await mountSuspended(EventFieldList, {
            props: {fields: twoFields().slice(0, 1), showValue: true, valueLabel: 'Standardwert'},
        })

        const value = list.find('[data-testid="event-field-value"]')
        expect(value.exists()).toBe(true)
        expect(value.text()).toContain('Standardwert')
    })

    it('asks for no value where the answer would be a member nobody handed over', async () => {
        const fields: EventFieldEntry[] = [
            {name: 'Ausbilder', fieldType: 'MEMBER', config: {}, value: '', overview: false, attendanceFieldId: null},
        ]

        const list = await mountSuspended(EventFieldList, {props: {fields, showValue: true}})

        expect(list.find('[data-testid="event-field-value"]').exists()).toBe(false)
    })
})

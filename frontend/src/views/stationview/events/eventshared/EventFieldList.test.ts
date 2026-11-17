/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {defineComponent, h, reactive} from 'vue'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import EventFieldList from './EventFieldList.vue'
import type {EventFieldEntry} from '@/api/generated/schema'
import {emptySettings} from './eventQuestions'

/**
 * The order of the questions is the order they are asked in, and moving one used to mean deleting
 * everything below it and typing it in again.
 */
describe('EventFieldList', () => {
    const plain = emptySettings()

    function twoFields(): EventFieldEntry[] {
        return [
            {name: 'Ort', fieldType: 'TEXT', config: plain, value: '', overview: true, attendanceFieldId: null},
            {name: 'Treffpunkt', fieldType: 'TEXT', config: plain, value: '', overview: true, attendanceFieldId: null},
        ]
    }

    it('moves a question past the one below it, form and all', async () => {
        const list = await mountSuspended(EventFieldList, {props: {fields: twoFields()}})

        await list.findAll('[data-testid="move-down"]')[0]!.trigger('click')
        await list.vm.$nextTick()

        const shown = list.findAll('[data-testid="question-name"]').map(input => (input.element as HTMLInputElement).value)
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
            {name: 'Ausbilder', fieldType: 'MEMBER', config: plain, value: '', overview: false, attendanceFieldId: null},
        ]

        const list = await mountSuspended(EventFieldList, {props: {fields, showValue: true}})

        expect(list.find('[data-testid="event-field-value"]').exists()).toBe(false)
    })

    it('draws one line per field in compact form, with the ways to add one below the list', async () => {
        const list = await mountSuspended(EventFieldList, {props: {fields: twoFields(), compact: true}})

        expect(list.findAll('[data-testid="question-row"]')).toHaveLength(2)
        expect(list.find('[data-testid="question-name"]').exists()).toBe(false)

        const html = list.html()
        expect(html.indexOf('event-field-list')).toBeLessThan(html.indexOf('event-field-add-line'))
    })

    it('keeps a field\'s opened settings with it when a field before it is removed', async () => {
        const fields: EventFieldEntry[] = [...twoFields(), {name: '', fieldType: 'TEXT', config: plain, value: '', overview: false, attendanceFieldId: null}]
        const list = await mountSuspended(EventFieldList, {props: {fields, compact: true}})
        expect(list.findAll('[data-testid="question-name"]')).toHaveLength(1)

        await list.findAll('[data-testid="question-row-remove"]')[0]!.trigger('click')
        await list.setProps({fields: list.emitted('update:fields')!.at(-1)![0] as EventFieldEntry[]})

        expect(list.findAll('[data-testid="question-name"]')).toHaveLength(1)
        expect(list.findAll('[data-testid="question-row-name"]').map(n => (n.element as HTMLInputElement).value)).toEqual(['Treffpunkt'])
    })

    it('keeps a line in place while it is typed into on a page holding the fields reactively', async () => {
        const state = reactive({fields: [...twoFields(), {name: '', fieldType: 'TEXT', config: plain, value: '', overview: false, attendanceFieldId: null}] as EventFieldEntry[]})
        const Page = defineComponent({
            setup: () => () => h(EventFieldList, {
                fields: state.fields,
                compact: true,
                'onUpdate:fields': (next: EventFieldEntry[]) => { state.fields = next },
            }),
        })
        const page = await mountSuspended(Page)

        const closedName = page.findAll('[data-testid="question-row-name"]')[0]!
        const before = closedName.element
        await closedName.setValue('Ortschaft')
        expect(page.findAll('[data-testid="question-row-name"]')[0]!.element).toBe(before)

        await page.get('[data-testid="question-name"]').setValue('N')
        expect(page.find('[data-testid="question-name"]').exists()).toBe(true)
        expect(state.fields.map(field => field.name)).toEqual(['Ortschaft', 'Treffpunkt', 'N'])
    })

    it('adds the place and meeting point quick fields as place fields, the topic as text', async () => {
        const list = await mountSuspended(EventFieldList, {props: {fields: []}})

        for (const name of ['Ort', 'Treffpunkt', 'Thema']) {
            const button = list.findAll('button').find(candidate => candidate.text() === `+ ${name}`)
            await button!.trigger('click')
        }

        const handedBack = list.emitted('update:fields')?.at(-1)?.[0] as EventFieldEntry[]
        expect(handedBack.map(field => [field.name, field.fieldType])).toEqual([
            ['Ort', 'LOCATION'],
            ['Treffpunkt', 'LOCATION'],
            ['Thema', 'TEXT'],
        ])
    })
})

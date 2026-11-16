/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import DateTimeInput from './DateTimeInput.vue'

function fields(wrapper: ReturnType<typeof mount>) {
    const [day, time] = wrapper.findAll('input')
    return {day: day!, time: time!}
}

/**
 * A moment asked for as a day and a time of day. A phone opens one combined field as a calendar and then
 * a clock that names no field, which read as the end being asked for when only the start was changed.
 */
describe('DateTimeInput', () => {
    it('shows the day and the time in fields of their own', () => {
        const {day, time} = fields(mount(DateTimeInput, {props: {modelValue: '2026-11-16T18:30'}}))

        expect(day.attributes('type')).toBe('date')
        expect((day.element as HTMLInputElement).value).toBe('2026-11-16')
        expect(time.attributes('type')).toBe('time')
        expect((time.element as HTMLInputElement).value).toBe('18:30')
    })

    it('keeps the time when only the day is changed', async () => {
        const wrapper = mount(DateTimeInput, {props: {modelValue: '2026-11-16T18:30'}})

        await fields(wrapper).day.setValue('2026-11-20')

        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2026-11-20T18:30'])
    })

    it('hands out no moment while the time is still missing', async () => {
        const wrapper = mount(DateTimeInput, {props: {modelValue: ''}})

        await fields(wrapper).day.setValue('2026-11-20')
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()

        await fields(wrapper).time.setValue('07:15')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['2026-11-20T07:15'])
    })

    it('empties the moment when the day is cleared', async () => {
        const wrapper = mount(DateTimeInput, {props: {modelValue: '2026-11-16T18:30'}})

        await fields(wrapper).day.setValue('')

        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([''])
    })

    it('reports the whole moment once a half is left', async () => {
        const wrapper = mount(DateTimeInput, {props: {modelValue: '2026-11-16T18:30'}})

        const {time} = fields(wrapper)
        await time.setValue('19:00')
        await time.trigger('change')

        expect(wrapper.emitted('change')?.at(-1)).toEqual(['2026-11-16T19:00'])
    })

    it('limits the calendar to the day of the earliest moment', () => {
        const {day} = fields(mount(DateTimeInput, {props: {modelValue: '', min: '2026-11-16T18:30'}}))

        expect(day.attributes('min')).toBe('2026-11-16')
    })

    it('reads its own label out before each half', () => {
        const {day, time} = fields(mount(DateTimeInput, {props: {modelValue: '', label: 'Beginn'}}))

        expect(day.attributes('aria-label')).toBe('Beginn, Datum')
        expect(time.attributes('aria-label')).toBe('Beginn, Uhrzeit')
    })
})

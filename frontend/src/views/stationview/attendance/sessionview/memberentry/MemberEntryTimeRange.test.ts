/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {afterEach, beforeEach, describe, expect, it} from 'vitest'
import MemberEntryTimeRange from './MemberEntryTimeRange.vue'

const i18n = {global: {stubs: {'font-awesome-icon': true}}}

const originalZone = process.env.TZ

beforeEach(() => {
    process.env.TZ = 'Europe/Berlin'
})
afterEach(() => {
    process.env.TZ = originalZone
})

const SESSION_START = '2026-03-17T17:00:00.000Z'
const SESSION_END = '2026-03-17T21:00:00.000Z'

/**
 * When somebody came and went. On a sheet over one day a time is enough; on one that runs over a
 * weekend the same three characters name two different moments, and the field has to say which.
 */
describe('MemberEntryTimeRange', () => {
    it('asks for a time alone on a sheet that runs for one day', () => {
        const wrapper = mount(MemberEntryTimeRange, {
            props: {sessionStart: SESSION_START, sessionEnd: SESSION_END},
            ...i18n,
        })

        const fields = wrapper.findAll('input')
        expect(fields).toHaveLength(2)
        expect(fields.at(0)?.attributes('type')).toBe('time')
        expect((fields.at(0)?.element as HTMLInputElement).value).toBe('18:00')
    })

    it('asks for the day as well on a sheet that runs over several', () => {
        const wrapper = mount(MemberEntryTimeRange, {
            props: {
                sessionStart: SESSION_START,
                sessionEnd: '2026-03-19T15:00:00.000Z',
                spansDays: true,
            },
            ...i18n,
        })

        const values = wrapper.findAll('input').map(field => (field.element as HTMLInputElement).value)
        expect(wrapper.findAll('input').map(field => field.attributes('type'))).toEqual(['date', 'time', 'date', 'time'])
        expect(values).toEqual(['2026-03-17', '18:00', '2026-03-19', '16:00'])
    })

    it('hands over the whole moment when only the day of an arrival is changed', async () => {
        const wrapper = mount(MemberEntryTimeRange, {
            props: {
                sessionStart: SESSION_START,
                sessionEnd: '2026-03-19T15:00:00.000Z',
                spansDays: true,
            },
            ...i18n,
        })

        const day = wrapper.findAll('input').at(0)!
        await day.setValue('2026-03-18')
        await day.trigger('change')

        expect(wrapper.emitted('checkIn')?.at(-1)).toEqual(['2026-03-18T18:00'])
    })

    /** Nothing is stored for a member who was simply there, so the sheet's own times stand in. */
    it('shows the sheet\'s times faintly where the member wrote none', () => {
        const wrapper = mount(MemberEntryTimeRange, {
            props: {sessionStart: SESSION_START, sessionEnd: SESSION_END},
            ...i18n,
        })

        expect(wrapper.findAll('input').at(0)?.classes()).toContain('opacity-60')
    })

    it('offers to throw a member\'s own times away once there are any', () => {
        const written = mount(MemberEntryTimeRange, {
            props: {checkIn: SESSION_START, sessionStart: SESSION_START, sessionEnd: SESSION_END},
            ...i18n,
        })
        expect(written.find('button').exists()).toBe(true)

        const untouched = mount(MemberEntryTimeRange, {
            props: {sessionStart: SESSION_START, sessionEnd: SESSION_END},
            ...i18n,
        })
        expect(untouched.find('button').exists()).toBe(false)
    })
})

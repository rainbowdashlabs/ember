/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import SessionHeader from './SessionHeader.vue'
import type {AttendanceSession} from '@/api/attendance'

const i18n = {global: {stubs: {'font-awesome-icon': true}}}

function sheet(overrides: Partial<AttendanceSession> = {}): AttendanceSession {
    return {
        id: 1,
        templateId: 1,
        startTime: '2026-03-17T17:00:00.000Z',
        endTime: '2026-03-17T21:00:00.000Z',
        title: 'Übung',
        ...overrides,
    }
}

/**
 * The sheet's own time frame and worth. The times were once shown only where no appointment stood
 * behind the sheet, which left an appointment-made sheet unable to say that it started late.
 */
describe('SessionHeader', () => {
    it('lets the times be corrected on a sheet an appointment made', () => {
        const wrapper = mount(SessionHeader, {props: {session: sheet({eventId: 7})}, ...i18n})

        expect(wrapper.findAll('input[type="datetime-local"]')).toHaveLength(2)
    })

    it('shows what a whole presence counts as, in hours', () => {
        const wrapper = mount(SessionHeader, {props: {session: sheet({countedMinutes: 180})}, ...i18n})

        expect((wrapper.find('input[type="number"]').element as HTMLInputElement).value).toBe('3')
    })

    it('hands back nothing at all when the counted hours are cleared', async () => {
        const wrapper = mount(SessionHeader, {props: {session: sheet({countedMinutes: 180})}, ...i18n})

        await wrapper.find('input[type="number"]').setValue('')

        expect(wrapper.emitted('updateCountedHours')?.at(-1)).toEqual([null])
    })

    it('offers the appointment\'s times back only once the sheet has moved off them', () => {
        const following = mount(SessionHeader, {
            props: {
                session: sheet({eventId: 7}),
                eventStartTime: '2026-03-17T17:00:00.000Z',
                eventEndTime: '2026-03-17T21:00:00.000Z',
            },
            ...i18n,
        })
        expect(following.text()).not.toContain('übernehmen')

        const moved = mount(SessionHeader, {
            props: {
                session: sheet({eventId: 7}),
                eventStartTime: '2026-03-17T16:00:00.000Z',
                eventEndTime: '2026-03-17T21:00:00.000Z',
            },
            ...i18n,
        })
        expect(moved.text()).toContain('übernehmen')
    })

    it('writes nothing on a sheet that is closed', () => {
        const wrapper = mount(SessionHeader, {props: {session: sheet(), readonly: true}, ...i18n})

        expect(wrapper.findAll('input')).toHaveLength(0)
    })
})

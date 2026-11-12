/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {useEventForm} from './useEventForm'
import {RestrictionMode, type EventTemplate, type RestrictionAudience, type TemplateDetailResponse} from '@/api/generated/schema'

vi.mock('@/api', () => ({events: {}}))

const nobody: RestrictionAudience = {groupIds: [], memberIds: [], mode: RestrictionMode.AND, tagIds: [], userTypes: []}

/** A template with nothing set, so a test names only the setting it is about. */
function templateDetail(overrides: Partial<EventTemplate> = {}): TemplateDetailResponse {
    return {
        fields: [],
        registrationFields: [],
        reminderDays: [],
        restriction: {register: nobody, view: nobody},
        template: {
            attendanceTemplateId: null,
            categoryId: null,
            description: null,
            eventType: null,
            id: 4,
            name: 'Übungsdienst',
            registrationDeadlineOffsetHours: null,
            minRegistrations: null,
            thresholdDays: null,
            registrationCloseDays: null,
            registrationLimit: null,
            requiresConfirmation: null,
            requiresRegistration: null,
            restrictionMode: null,
            stationId: '00000000-0000-0000-0000-000000000000',
            title: null,
            viewRestrictionMode: RestrictionMode.AND,
            ...overrides,
        },
    }
}

describe('useEventForm.applyTemplate', () => {
    it('takes the participant limit from the template', () => {
        const form = useEventForm()

        form.applyTemplate(templateDetail({requiresRegistration: true, registrationLimit: 12}))

        expect(form.state.registrationLimit).toBe(12)
    })

    it('keeps the limit already entered when the template sets none', () => {
        const form = useEventForm()
        form.state.registrationLimit = 8

        form.applyTemplate(templateDetail())

        expect(form.state.registrationLimit).toBe(8)
    })
})

describe('useEventForm.applyTemplate sign-up rules', () => {
    it('takes the minimum, the threshold and the close days from the template', () => {
        const form = useEventForm()

        form.applyTemplate(templateDetail({minRegistrations: 5, thresholdDays: 2, registrationCloseDays: 1}))

        expect(form.state.minRegistrations).toBe(5)
        expect(form.state.thresholdDays).toBe(2)
        expect(form.state.registrationCloseDays).toBe(1)
    })

    it('keeps the rules already entered where the template sets none', () => {
        const form = useEventForm()
        form.state.minRegistrations = 3
        form.state.thresholdDays = 4
        form.state.registrationCloseDays = 6

        form.applyTemplate(templateDetail())

        expect(form.state.minRegistrations).toBe(3)
        expect(form.state.thresholdDays).toBe(4)
        expect(form.state.registrationCloseDays).toBe(6)
    })
})

/**
 * A template knows no date, so its deadline is hours before the start. The form counts back from
 * the start once there is one, and keeps doing so until the deadline is changed by hand.
 */
describe('useEventForm.applyTemplate deadline offset', () => {
    it('sets the deadline that many hours before an entered start', () => {
        const form = useEventForm()
        form.state.startTime = '2026-12-10T19:00'

        form.applyTemplate(templateDetail({registrationDeadlineOffsetHours: 30}))

        expect(form.state.hasDeadline).toBe(true)
        expect(form.state.registrationDeadline).toBe('2026-12-09T13:00')
    })

    it('waits for a start time and sets the deadline once one is entered', () => {
        const form = useEventForm()

        form.applyTemplate(templateDetail({registrationDeadlineOffsetHours: 24}))
        expect(form.state.hasDeadline).toBe(true)
        expect(form.state.registrationDeadline).toBe('')

        form.state.startTime = '2026-12-10T19:00'

        expect(form.state.registrationDeadline).toBe('2026-12-09T19:00')
    })

    it('follows the start while the deadline is left alone', () => {
        const form = useEventForm()
        form.state.startTime = '2026-12-10T19:00'
        form.applyTemplate(templateDetail({registrationDeadlineOffsetHours: 2}))

        form.state.startTime = '2026-12-11T08:00'

        expect(form.state.registrationDeadline).toBe('2026-12-11T06:00')
    })

    it('stops following once the deadline is changed by hand', () => {
        const form = useEventForm()
        form.state.startTime = '2026-12-10T19:00'
        form.applyTemplate(templateDetail({registrationDeadlineOffsetHours: 2}))

        form.state.registrationDeadline = '2026-12-01T12:00'
        form.state.startTime = '2026-12-11T08:00'

        expect(form.state.registrationDeadline).toBe('2026-12-01T12:00')
    })

    it('keeps a deadline set by hand before the start was entered', () => {
        const form = useEventForm()
        form.applyTemplate(templateDetail({registrationDeadlineOffsetHours: 2}))

        form.state.registrationDeadline = '2026-12-01T12:00'
        form.state.startTime = '2026-12-11T08:00'

        expect(form.state.registrationDeadline).toBe('2026-12-01T12:00')
    })

    it('leaves the deadline alone where the template sets no offset', () => {
        const form = useEventForm()
        form.state.startTime = '2026-12-10T19:00'

        form.applyTemplate(templateDetail())

        expect(form.state.hasDeadline).toBe(false)
        expect(form.state.registrationDeadline).toBe('')
    })

    /**
     * Whole days are counted back on the calendar, so a deadline three days before 18:00 is at 18:00
     * even where summer time ends in between: on 25 October 2026 in Berlin the clocks go back.
     */
    it.each([
        ['2026-10-27T18:00', 72, '2026-10-24T18:00'],
        ['2026-03-30T18:00', 72, '2026-03-27T18:00'],
        ['2026-10-26T01:00', 26, '2026-10-24T23:00'],
    ])('keeps the time of day across a change of summer time, from %s back %i hours', (start, offset, expected) => {
        const previous = process.env.TZ
        process.env.TZ = 'Europe/Berlin'
        try {
            expect(new Date(2026, 9, 24).getTimezoneOffset(), 'the zone is in force').not.toBe(
                new Date(2026, 9, 27).getTimezoneOffset())
            const form = useEventForm()
            form.state.startTime = start

            form.applyTemplate(templateDetail({registrationDeadlineOffsetHours: offset}))

            expect(form.state.registrationDeadline).toBe(expected)
        } finally {
            process.env.TZ = previous
        }
    })
})

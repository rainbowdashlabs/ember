/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {nextTick} from 'vue'
import {useEventForm, type EventEditDetail} from './useEventForm'
import {
    EventType,
    FieldType,
    RestrictionMode,
    type AppointmentField,
    type EventTemplate,
    type RestrictionAudience,
    type StationEvent,
    type TemplateDetailResponse,
} from '@/api/generated/schema'

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

/** An appointment of the station with nothing special set, so a test names only what it is about. */
function original(overrides: Partial<StationEvent> = {}): StationEvent {
    return {
        cancelled: false,
        cancelledAt: null,
        cancelReason: null,
        categoryId: 3,
        dayOfWeek: null,
        description: 'Knoten und Stiche',
        endTime: new Date('2026-05-12T20:30').toISOString(),
        eventType: EventType.ONE_TIME,
        id: 17,
        isPublic: null,
        minRegistrations: 4,
        name: 'Dienstabend',
        recurring: false,
        registrationCloseDays: 1,
        registrationDeadline: null,
        registrationLimit: 20,
        repeatCount: null,
        repeatUntil: null,
        requiresConfirmation: true,
        requiresRegistration: true,
        restricted: false,
        restrictionMode: RestrictionMode.AND,
        startTime: new Date('2026-05-12T18:00').toISOString(),
        stationId: '00000000-0000-0000-0000-000000000000',
        templateId: 6,
        thresholdDays: 2,
        viewRestrictionMode: RestrictionMode.AND,
        ...overrides,
    }
}

/** A field of the original, a plain line unless a test says otherwise. */
function field(overrides: Partial<AppointmentField> = {}): AppointmentField {
    return {
        attendanceFieldId: null,
        config: {perDate: false, selfRegistration: false, required: false, managersOnly: false},
        eventId: 17,
        fieldType: FieldType.TEXT,
        id: 30,
        isPublic: false,
        name: 'Ort',
        overview: true,
        position: 0,
        readerInWholeGroup: false,
        selfRegistrationState: null,
        value: 'Gerätehaus',
        ...overrides,
    }
}

const drivers: RestrictionAudience = {...nobody, groupIds: [2]}

/** What the editor reads of an original: kept to one group, with two reminders. */
function copyDetail(event: StationEvent, fields: AppointmentField[] = []): EventEditDetail {
    return {event, restrictions: {register: drivers, view: nobody}, fields, reminders: [7, 1]}
}

describe('useEventForm.applyCopy', () => {
    it('fills the form from the original and names it in the request', () => {
        const form = useEventForm()

        form.applyCopy(copyDetail(original(), [field()]), '2026-11-18', null)

        expect(form.state.name).toBe('Dienstabend')
        expect(form.state.description).toBe('Knoten und Stiche')
        expect(form.state.categoryId).toBe('3')
        expect(form.state.templateId).toBe('6')
        expect(form.state.requiresRegistration).toBe(true)
        expect(form.state.requiresConfirmation).toBe(true)
        expect(form.state.registrationLimit).toBe(20)
        expect(form.state.minRegistrations).toBe(4)
        expect(form.state.registrationCloseDays).toBe(1)
        expect(form.state.restriction.groupIds).toEqual([2])
        expect(form.state.reminders).toEqual([7, 1])
        expect(form.state.fields).toEqual([expect.objectContaining({name: 'Ort', value: 'Gerätehaus'})])
        expect(form.state.fields[0]).not.toHaveProperty('id')
        const payload = form.buildPayload()
        expect(payload.copiedFromEventId).toBe(17)
        expect(payload.eventTemplateId).toBeNull()
    })

    it('leaves out the values of fields that name people', () => {
        const form = useEventForm()

        form.applyCopy(copyDetail(original(), [
            field(),
            field({id: 31, name: 'Leitung', fieldType: FieldType.MEMBER, value: '12'}),
        ]), '2026-11-18', null)

        expect(form.state.fields.map(entry => entry.value)).toEqual(['Gerätehaus', ''])
    })

    it('starts a one-time copy without a date, then follows the start with the original length and deadline', async () => {
        const form = useEventForm()
        const deadline = new Date('2026-05-11T18:00').toISOString()

        form.applyCopy(copyDetail(original({registrationDeadline: deadline})), '2026-11-18', null)

        expect(form.state.startTime).toBe('')
        expect(form.state.endTime).toBe('')
        expect(form.state.registrationDeadline).toBe('')
        expect(form.buildPayload().registrationDeadline).toBeNull()

        form.state.startTime = '2026-12-01T18:00'
        await nextTick()

        expect(form.state.endTime).toBe('2026-12-01T20:30')
        expect(form.state.registrationDeadline).toBe('2026-11-30T18:00')
    })

    it('moves a series to its next day from today and keeps its rule, times and end', () => {
        const form = useEventForm()
        const series = original({eventType: EventType.RECURRING, dayOfWeek: 2, recurring: true, repeatCount: 10})

        form.applyCopy(copyDetail(series), '2026-11-18', '2026-11-24')

        expect(form.state.eventType).toBe(EventType.RECURRING)
        expect(form.state.dayOfWeek).toBe('2')
        expect(form.state.startTime).toBe('2026-11-24T18:00')
        expect(form.state.endTime).toBe('2026-11-24T20:30')
        expect(form.state.repeatCount).toBe(10)
    })

    it('starts a series on the next date the server names for it', () => {
        const form = useEventForm()
        const quarterly = original({eventType: EventType.QUARTERLY, dayOfWeek: 2, recurring: true})

        form.applyCopy(copyDetail(quarterly), '2026-11-18', '2027-02-02')

        expect(form.state.startTime).toBe('2027-02-02T18:00')
    })

    it('drops the last day of an ended series and starts it again from today', () => {
        const form = useEventForm()
        const ended = original({eventType: EventType.RECURRING, dayOfWeek: 2, recurring: true, repeatUntil: '2026-06-30'})

        form.applyCopy(copyDetail(ended), '2026-11-18', '2026-06-30')

        expect(form.state.startTime).toBe('2026-11-24T18:00')
        expect(form.state.repeatUntil).toBe('')
        expect(form.buildPayload().repeatUntil).toBeNull()
    })

    it('keeps a last day that still lies after the new start', () => {
        const form = useEventForm()
        const running = original({eventType: EventType.RECURRING, dayOfWeek: 2, recurring: true, repeatUntil: '2027-06-29'})

        form.applyCopy(copyDetail(running), '2026-11-18', '2026-11-24')

        expect(form.state.repeatUntil).toBe('2027-06-29')
    })
})

/**
 * Moving an appointment to another day used to leave its end behind, so the form refused it for ending
 * before it begins until the end was changed by hand as well.
 */
describe('useEventForm start and end', () => {
    it('moves the end along when only the start is changed', () => {
        const form = useEventForm()
        form.state.startTime = '2026-12-10T19:00'
        form.state.endTime = '2026-12-10T21:30'

        form.state.startTime = '2026-12-17T19:00'

        expect(form.state.endTime).toBe('2026-12-17T21:30')
    })
})

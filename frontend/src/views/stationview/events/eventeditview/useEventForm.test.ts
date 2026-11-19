/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {nextTick} from 'vue'
import {useEventForm, type EventEditDetail} from './useEventForm'
import {events} from '@/api'
import {noDeadline} from '../eventshared/registrationDeadline'
import {
    EventType,
    FieldType,
    QueueOrder,
    RestrictionMode,
    type AppointmentField,
    type EventTemplate,
    type RestrictionAudience,
    type StationEvent,
    type TemplateDetailResponse,
} from '@/api/generated/schema'

vi.mock('@/api', () => ({events: {}}))

const weekBeforeAtMidnight = {kind: 'RELATIVE' as const, at: null, offsetMinutes: 10080, anchor: 'MIDNIGHT' as const}

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
            registrationDeadline: noDeadline(),
            minRegistrations: null,
            thresholdDays: null,
            registrationLimit: null,
            queueOrder: null,
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

/**
 * The waiting list order is saved after the appointment, only where it means something: with a
 * participant limit, or where it was changed.
 */
describe('useEventForm waiting list order', () => {
    it('takes the order from the template', () => {
        const form = useEventForm()

        form.applyTemplate(templateDetail({registrationLimit: 12, queueOrder: QueueOrder.FAIRNESS}))

        expect(form.state.queueOrder).toBe(QueueOrder.FAIRNESS)
        expect(form.queueOrderToSave()).toBe(QueueOrder.FAIRNESS)
    })

    it('saves nothing for an appointment without a limit whose order nobody touched', () => {
        const form = useEventForm()

        expect(form.queueOrderToSave()).toBeNull()

        form.state.queueOrder = QueueOrder.FAIRNESS
        expect(form.queueOrderToSave()).toBe(QueueOrder.FAIRNESS)
    })

    it('reads the order of an appointment back and saves it with its limit', () => {
        const form = useEventForm()

        form.applyCopy(copyDetail(original({queueOrder: QueueOrder.FAIRNESS}), []), '2026-11-18', null)

        expect(form.state.queueOrder).toBe(QueueOrder.FAIRNESS)
        expect(form.queueOrderToSave()).toBe(QueueOrder.FAIRNESS)
    })
})

describe('useEventForm.applyTemplate sign-up rules', () => {
    it('takes the minimum and the threshold from the template', () => {
        const form = useEventForm()

        form.applyTemplate(templateDetail({minRegistrations: 5, thresholdDays: 2}))

        expect(form.state.minRegistrations).toBe(5)
        expect(form.state.thresholdDays).toBe(2)
    })

    it('keeps the rules already entered where the template sets none', () => {
        const form = useEventForm()
        form.state.minRegistrations = 3
        form.state.thresholdDays = 4

        form.applyTemplate(templateDetail())

        expect(form.state.minRegistrations).toBe(3)
        expect(form.state.thresholdDays).toBe(4)
    })
})

/**
 * A template knows no date, so its deadline is a span before the start. The appointment takes the
 * rule over as it is and the server works out each date's deadline from it.
 */
describe('useEventForm.applyTemplate deadline', () => {
    it('takes the span before the start over unchanged', () => {
        const form = useEventForm()
        form.state.startTime = '2026-12-10T19:00'

        form.applyTemplate(templateDetail({registrationDeadline: weekBeforeAtMidnight, requiresRegistration: true}))

        expect(form.state.registrationDeadline).toEqual(weekBeforeAtMidnight)
        expect(form.buildPayload().registrationDeadline).toEqual(weekBeforeAtMidnight)
    })

    it('sends no deadline while sign-up is off', () => {
        const form = useEventForm()

        form.applyTemplate(templateDetail({registrationDeadline: weekBeforeAtMidnight}))

        expect(form.state.registrationDeadline).toEqual(weekBeforeAtMidnight)
        expect(form.buildPayload().registrationDeadline).toEqual(noDeadline())
    })

    it('leaves the deadline alone where the template sets none', () => {
        const form = useEventForm()
        const fixed = {kind: 'FIXED' as const, at: '2026-12-01T11:00:00.000Z', offsetMinutes: null, anchor: null}
        form.state.registrationDeadline = fixed

        form.applyTemplate(templateDetail())

        expect(form.state.registrationDeadline).toEqual(fixed)
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
        registrationDeadline: weekBeforeAtMidnight,
        registrationLimit: 20,
        queueOrder: null,
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
        expect(form.state.registrationDeadline).toEqual(weekBeforeAtMidnight)
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

    it('starts a one-time copy without a date, then follows the start with the original length', async () => {
        const form = useEventForm()

        form.applyCopy(copyDetail(original()), '2026-11-18', null)

        expect(form.state.startTime).toBe('')
        expect(form.state.endTime).toBe('')

        form.state.startTime = '2026-12-01T18:00'
        await nextTick()

        expect(form.state.endTime).toBe('2026-12-01T20:30')
    })

    it('drops a fixed deadline with the original dates and keeps one before the start', () => {
        const fixed = {kind: 'FIXED' as const, at: new Date('2026-05-11T18:00').toISOString(), offsetMinutes: null, anchor: null}
        const withFixed = useEventForm()
        withFixed.applyCopy(copyDetail(original({registrationDeadline: fixed})), '2026-11-18', null)
        expect(withFixed.state.registrationDeadline).toEqual(noDeadline())
        expect(withFixed.buildPayload().registrationDeadline).toEqual(noDeadline())

        const withSpan = useEventForm()
        withSpan.applyCopy(copyDetail(original()), '2026-11-18', null)
        expect(withSpan.buildPayload().registrationDeadline).toEqual(weekBeforeAtMidnight)
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

/**
 * A series runs on the weekday it starts on unless somebody says otherwise, so the weekday is not a
 * second thing to set after picking the date.
 */
describe('useEventForm weekday', () => {
    async function loaded(eventType: string, dayOfWeek: number | null, startTime: string) {
        Object.assign(events, {
            getEvent: vi.fn().mockResolvedValue({id: 1, name: 'Dienst', eventType, dayOfWeek, startTime, endTime: startTime}),
            getRestrictions: vi.fn().mockResolvedValue({register: null, view: null}),
            getEventFields: vi.fn().mockResolvedValue([]),
            getEventReminders: vi.fn().mockResolvedValue([]),
        })
        const form = useEventForm()
        await form.loadEvent(1)
        return form
    }

    it('takes the weekday of the start', () => {
        const form = useEventForm()

        form.state.startTime = '2026-11-20T19:00'

        expect(form.state.dayOfWeek).toBe('5')
    })

    it('follows a moved start while the weekday was left alone', () => {
        const form = useEventForm()
        form.state.startTime = '2026-11-20T19:00'

        form.state.startTime = '2026-11-24T19:00'

        expect(form.state.dayOfWeek).toBe('2')
    })

    it('keeps a weekday picked by hand', () => {
        const form = useEventForm()
        form.state.startTime = '2026-11-20T19:00'
        form.state.dayOfWeek = '3'

        form.state.startTime = '2026-11-24T19:00'

        expect(form.state.dayOfWeek).toBe('3')
    })

    it('keeps a weekday picked before any start was entered', () => {
        const form = useEventForm()
        form.state.dayOfWeek = '3'

        form.state.startTime = '2026-11-20T19:00'

        expect(form.state.dayOfWeek).toBe('3')
    })

    it('keeps a picked weekday while the start is cleared and entered again', () => {
        const form = useEventForm()
        form.state.startTime = '2026-11-20T19:00'
        form.state.dayOfWeek = '3'

        form.state.startTime = ''
        form.state.startTime = '2026-11-24T19:00'

        expect(form.state.dayOfWeek).toBe('3')
    })

    it('loads a single appointment on the weekday of its start', async () => {
        const form = await loaded('ONE_TIME', null, '2026-11-20T12:00:00.000Z')

        expect(form.state.dayOfWeek).toBe('5')
    })

    it('keeps the weekday a series was saved with when it differs from its start', async () => {
        const form = await loaded('RECURRING', 3, '2026-11-20T12:00:00.000Z')
        expect(form.state.dayOfWeek).toBe('3')

        form.state.startTime = '2026-11-24T19:00'

        expect(form.state.dayOfWeek).toBe('3')
    })

    it('lets a series saved on its start\'s weekday follow a moved start', async () => {
        const form = await loaded('RECURRING', 5, '2026-11-20T12:00:00.000Z')

        form.state.startTime = '2026-11-24T19:00'

        expect(form.state.dayOfWeek).toBe('2')
    })
})

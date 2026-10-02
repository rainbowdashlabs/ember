/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {EventTypes, type EventTypeName} from '@/api/events'
import type {EventFieldEntry} from '@/api/generated/schema'
import {emptyRestriction} from '@/components/input/restriction'

/**
 * The values a freshly opened event editor starts with. Every key is named
 * exactly like the prop the editor body binds for it.
 */
export function createEventFormState() {
  return {
    name: '',
    description: '',
    categoryId: '',
    templateId: '',
    eventType: EventTypes.ONE_TIME as string,
    dayOfWeek: '1',
    startTime: '',
    endTime: '',
    repeatUntil: '',
    repeatCount: undefined as number | undefined,
    requiresRegistration: false,
    requiresConfirmation: false,
    hasDeadline: false,
    registrationDeadline: '',
    registrationLimit: undefined as number | undefined,
    minRegistrations: undefined as number | undefined,
    thresholdDays: undefined as number | undefined,
    registrationCloseDays: undefined as number | undefined,
    restriction: emptyRestriction(),
    viewRestriction: emptyRestriction(),
    fields: [] as EventFieldEntry[],
    reminders: [] as number[],
  }
}

/**
 * The kind of appointment a form names, read back from the known kinds, or nothing where the form
 * holds a value that is none of them.
 */
export function eventTypeNamed(value: string | null | undefined): EventTypeName | undefined {
  return Object.values(EventTypes).find(type => type === value)
}

/**
 * Shape of the event editor form state.
 */
export type EventFormState = ReturnType<typeof createEventFormState>

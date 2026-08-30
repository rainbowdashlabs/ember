/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {EventTypes, type EventFieldEntry} from '@/api/events'
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
    hasThreshold: false,
    thresholdDate: '',
    registrationCloseDays: undefined as number | undefined,
    restriction: emptyRestriction(),
    viewRestriction: emptyRestriction(),
    fields: [] as EventFieldEntry[],
    reminders: [] as number[],
  }
}

/**
 * Shape of the event editor form state.
 */
export type EventFormState = ReturnType<typeof createEventFormState>

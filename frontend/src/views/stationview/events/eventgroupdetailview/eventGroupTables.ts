/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {registrationStatusLabel} from '../eventshared/registrationStatusLabel'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import {
    RegistrationStatus,
    type EventGroupAttendance,
    type EventGroupSet,
    type EventGroupSignUp,
    type SetResult,
} from '@/api/generated/schema'
import {isMet} from '@/util/eventGroupChoice'

type Translate = (key: string, values?: Record<string, unknown>) => string

/**
 * The statuses the sign-up list holds, in the order the status filter offers them: waiting for a
 * decision, holding a place, on the waiting list, or turned down.
 */
const STATUS_ORDER = [
    RegistrationStatus.PENDING,
    RegistrationStatus.ACCEPTED,
    RegistrationStatus.QUEUED,
    RegistrationStatus.DENIED,
] as const

/** Whether a sign-up's parts meet every set and every question it must answer is answered. */
export function isSignUpComplete(entry: EventGroupSignUp): boolean {
    return entry.sets.every(isMet) && !entry.answersMissing
}

/**
 * The columns of the sign-up list of an event group: who, in which status, since when, how many parts,
 * and whether the sign-up is complete. Filtering the last one to "incomplete" leaves the sign-ups short
 * of a set or owing an answer.
 */
export function signUpColumns(t: Translate): TableColumn<EventGroupSignUp>[] {
    return [
        {key: 'member', label: t('eventGroups.colPerson'), type: ColumnTypes.TEXT, value: entry => entry.member.name, pinned: true},
        {
            key: 'status', label: t('eventGroups.colStatus'), type: ColumnTypes.ENUM, value: entry => entry.status,
            options: STATUS_ORDER.map(status => ({value: status, label: registrationStatusLabel(status, t)})),
        },
        {key: 'registeredAt', label: t('eventGroups.colSignedUpAt'), type: ColumnTypes.DATE_TIME, value: entry => entry.registeredAt},
        {
            key: 'parts', label: t('eventGroups.colParts'), type: ColumnTypes.NUMBER, value: entry => entry.parts.length,
            display: entry => t('eventGroups.chosen', {count: entry.parts.length}),
        },
        {
            key: 'complete', label: t('eventGroups.colComplete'), type: ColumnTypes.BOOLEAN, value: isSignUpComplete,
            booleanLabels: {yes: t('eventGroups.signUpComplete'), no: t('eventGroups.signUpIncomplete')},
        },
    ]
}

/** How a participant stands in one set, or nothing where the set is not counted for them. */
function resultIn(row: EventGroupAttendance, setId: number): SetResult | undefined {
    return row.sets.find(result => result.setId === setId)
}

/**
 * The columns of the attendance overview of an event group: the participant, one column per set with
 * the parts attended against its minimum, and whether every set is met.
 *
 * @param sets the event group's sets, in order
 * @param t    the translator
 */
export function attendanceColumns(sets: readonly EventGroupSet[], t: Translate): TableColumn<EventGroupAttendance>[] {
    return [
        {key: 'member', label: t('eventGroups.colPerson'), type: ColumnTypes.TEXT, value: row => row.member.name, pinned: true},
        ...sets.map((set): TableColumn<EventGroupAttendance> => ({
            key: `set-${set.id}`,
            label: set.name,
            type: ColumnTypes.NUMBER,
            value: row => resultIn(row, set.id)?.chosen,
            display: row => {
                const result = resultIn(row, set.id)
                return result ? t('eventGroups.attendanceOf', {attended: result.chosen, required: result.required}) : ''
            },
        })),
        {
            key: 'complete', label: t('eventGroups.colAttendanceComplete'), type: ColumnTypes.BOOLEAN, value: row => row.complete,
            booleanLabels: {yes: t('eventGroups.attendanceComplete'), no: t('eventGroups.attendanceOpen')},
        },
    ]
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {RegistrationStatus, type RegistrationStatusName} from '@/api/events'
import {FieldTypes} from '@/api/fieldTypes'
import type {
    EventRegistrationFieldValue,
    EventRegistrationField,
    RegistrationResponse,
} from '@/api/generated/schema'

/**
 * Somebody an appointment can be answered for, however the screen identifies them.
 *
 * <p>A local member is a number and a member of a partner station is a string, and the machinery
 * that takes an answer cares about neither: it shows names and hands the key back. Keeping that open
 * is what lets one set of controls serve both, rather than a second copy drifting away from the
 * first.
 */
export interface AnswerablePerson<K extends string | number = number> {
    key: K
    name: string
}

/**
 * An answer somebody has given, and what taking it back refers to.
 *
 * @param undo what the caller needs in order to delete this answer, which is a registration id
 *             locally and the person themselves across stations
 */
export interface GivenAnswer<K extends string | number = number, U = number> extends AnswerablePerson<K> {
    status: RegistrationStatusName
    undo: U
    /** Who answered on this person's behalf, where somebody did. */
    createdByName?: string | null
    /** Whether the answers behind this one can be opened and written again. */
    canUpdate?: boolean
    /** Whether the appointment asks something this answer has not answered. */
    answersMissing?: boolean
    /**
     * The question of the appointment that puts this person on the list, where one does.
     *
     * <p>Such a place is not an answer they gave, so there is nothing to take back: the way off the
     * list is out of the question, and this is what says so in place of the button.
     */
    heldByField?: string | null
}

/** One person's answer to the appointment's questions, ready to be sent. */
export interface PersonAnswer<K extends string | number = number> {
    key: K
    fields: EventRegistrationFieldValue[]
}

/** Whether an answer says somebody is not coming, which is what taking it back would undo. */
export function isRefusal(status: RegistrationStatusName): boolean {
    return status === RegistrationStatus.DECLINED || status === RegistrationStatus.DENIED
}

/**
 * Whether a registration is still an answer somebody holds.
 *
 * <p>A place that was given back is kept for whoever runs the appointment, so the row outlives the
 * place. To the member it is not an answer they are holding: they hold nothing, they owe one, and
 * they may sign up again.
 */
export function isStandingAnswer(registration: Pick<RegistrationResponse, 'status'>): boolean {
    return registration.status !== RegistrationStatus.WITHDRAWN
}

/** Whether a registration holds a place or is waiting for one to be confirmed. */
export function holdsOrAwaitsPlace(registration: Pick<RegistrationResponse, 'status'>): boolean {
    return isStandingAnswer(registration) && !isRefusal(registration.status)
}

/**
 * The rows of one occurrence of an appointment.
 *
 * <p>Registrations are kept per appointment and date, and a repeating appointment's list carries
 * every date it ever had. Reading that list as one occurrence showed a member's answer from another
 * week as theirs for this one, and signing off acted on whichever row came first.
 *
 * @param rows   the registrations of every date
 * @param date   the occurrence in view, or null where the appointment has only the one
 * @param dateOf where a row keeps its date
 */
export function rowsOnDate<T>(rows: T[], date: string | null, dateOf: (row: T) => string): T[] {
    return date === null ? rows : rows.filter(row => dateOf(row) === date)
}

/**
 * The members whoever runs an appointment can still put on its list.
 *
 * <p>Everybody who neither holds a place nor waits for one. A place given back, a refusal and a
 * request turned away all keep their row, but none of them is a place, and leaving those members out
 * meant not even the appointment's managers could put them back on.
 *
 * @param members       everybody who could be registered, as the member menu offers them
 * @param registrations the registrations of the occurrence the member would be added to
 */
export function membersToRegister<M extends {value: string}>(
    members: M[],
    registrations: Pick<RegistrationResponse, 'status' | 'memberId'>[],
): M[] {
    const placed = new Set(registrations
        .filter(holdsOrAwaitsPlace)
        .map(registration => Number(registration.memberId)))
    return members.filter(member => !placed.has(Number(member.value)))
}

/** What is known about a member somebody answers for. */
interface ManagedMember {
    id: number
    name?: string
    email?: string | null
}

/**
 * Who an appointment can be answered for, in the order they are offered.
 *
 * <p>Oneself and whoever one answers for, narrowed to those the appointment is open to. The server
 * answers every appointment the reader can see, one open to nobody here with an empty list, so an
 * absent entry only means the answer is not known, and the server is then left to decide.
 *
 * @param eventId  the appointment
 * @param eligible who each appointment is open to, as the server answered it
 * @param selfId   the acting member
 * @param managed  the members the acting member answers for
 * @param selfLabel what to call the acting member in the list
 */
export function answerableMembers(
    eventId: number,
    eligible: Record<number, number[]>,
    selfId: number,
    managed: ManagedMember[],
    selfLabel: string,
): AnswerablePerson[] {
    const ids = eligible[eventId] ?? [selfId, ...managed.map(member => member.id)]
    const answerable: AnswerablePerson[] = []
    for (const id of ids) {
        if (id === selfId) {
            answerable.push({key: id, name: selfLabel})
            continue
        }
        const member = managed.find(candidate => candidate.id === id)
        if (member) answerable.push({key: id, name: member.name ?? member.email ?? `#${id}`})
    }
    return answerable
}

/**
 * What an appointment says to a reader it is open to nobody for, where there is nothing to press.
 *
 * <p>An appointment narrowed for registration is still shown to everybody, so without a word the
 * reader would meet a date with no answer on it and no reason given.
 *
 * @param hasManagedMembers whether the reader answers for others too, who are then named as well
 */
export function notOpenLabelKey(hasManagedMembers: boolean): string {
    return hasManagedMembers ? 'events.notOpenToHousehold' : 'events.notOpenToYou'
}

/**
 * What each of these people has answered about one appointment on one date.
 *
 * <p>The station's own answers, mapped onto the shape the shared controls read. Taking one back
 * refers to the registration row itself, which is what the server deletes. A place given back is no
 * answer, so whoever gave it back is offered to sign up again rather than to give it back twice.
 *
 * @param asksQuestions whether the appointment asks anything, which is what makes an answer worth
 *                      opening again
 */
export function localAnswers(
    people: AnswerablePerson[],
    registrations: RegistrationResponse[],
    asksQuestions = false,
): GivenAnswer[] {
    const answers: GivenAnswer[] = []
    for (const person of people) {
        const registration = registrations.find(entry => entry.memberId === person.key)
        if (!registration || !isStandingAnswer(registration)) continue
        answers.push({
            key: person.key,
            name: person.name,
            status: registration.status,
            undo: registration.id,
            createdByName: registration.createdByName,
            canUpdate: asksQuestions,
            answersMissing: registration.answersMissing,
            heldByField: registration.fromField ? (registration.fieldName ?? '') : null,
        })
    }
    return answers
}

/** One line of the totals: the question, and what the answers to it add up to. */
export interface AnswerTotal {
    label: string
    text: string
}

/**
 * What a station plans from: the answers to the countable questions, added up.
 *
 * <p>A number question is summed and a choice question is counted per option. Free text has no
 * total worth showing, so it gets none.
 *
 * <p>Only the people who have a place are counted. An answer outlives the place it was given with:
 * somebody turned away or who called off still has their catering choice on file, and counting
 * those left a station ordering food for people who were told not to come. A place is the same
 * thing here as everywhere else in the product, down to the count that decides whether the event
 * happens at all: accepted, and nothing else. Somebody still waiting on an answer has asked for a
 * place rather than been given one.
 */
export function answerTotals(
    fields: EventRegistrationField[],
    registrations: Pick<RegistrationResponse, 'status' | 'fields'>[],
): AnswerTotal[] {
    const counted = registrations.filter(registration => registration.status === RegistrationStatus.ACCEPTED)
    const answersOf = (fieldId: number) => counted
        .map(registration => registration.fields.find(value => value.fieldId === fieldId)?.value)
        .filter((value): value is string => value != null && value !== '')

    const totals: AnswerTotal[] = []
    for (const field of fields) {
        if (field.fieldType === FieldTypes.NUMBER) {
            const sum = answersOf(field.id)
                .map(Number)
                .filter(value => !Number.isNaN(value))
                .reduce((running, value) => running + value, 0)
            totals.push({label: field.name, text: String(sum)})
            continue
        }
        if (field.fieldType !== FieldTypes.CHOICE) continue
        const answers = answersOf(field.id)
        const perOption = (field.config.options ?? [])
            .map(option => ({option, count: answers.filter(answer => answer === option).length}))
            .filter(entry => entry.count > 0)
        if (perOption.length > 0) {
            totals.push({label: field.name, text: perOption.map(e => `${e.option} ${e.count}`).join(', ')})
        }
    }
    return totals
}

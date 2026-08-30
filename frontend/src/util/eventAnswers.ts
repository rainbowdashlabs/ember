/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    EventFieldTypes,
    RegistrationStatus,
    type EventRegistrationEntry,
    type EventRegistrationField,
    type RegistrationFieldValue,
    type RegistrationStatusName,
} from '@/api/events'

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
}

/** One person's answer to the appointment's questions, ready to be sent. */
export interface PersonAnswer<K extends string | number = number> {
    key: K
    fields: RegistrationFieldValue[]
}

/** Whether an answer says somebody is not coming, which is what taking it back would undo. */
export function isRefusal(status: RegistrationStatusName): boolean {
    return status === RegistrationStatus.DECLINED || status === RegistrationStatus.DENIED
}

/** What is known about a member somebody answers for. */
interface ManagedMember {
    id: number
    name?: string
    email?: string
}

/**
 * Who an appointment can be answered for, in the order they are offered.
 *
 * <p>Oneself and whoever one answers for, narrowed to those the appointment is open to. An
 * appointment that names nobody is open to all of them, which is why an absent entry reads as
 * everybody rather than as nobody.
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
 * What each of these people has answered about one appointment on one date.
 *
 * <p>The station's own answers, mapped onto the shape the shared controls read. Taking one back
 * refers to the registration row itself, which is what the server deletes.
 */
export function localAnswers(
    people: AnswerablePerson[],
    registrations: EventRegistrationEntry[],
): GivenAnswer[] {
    const answers: GivenAnswer[] = []
    for (const person of people) {
        const registration = registrations.find(entry => entry.memberId === person.key)
        if (!registration) continue
        answers.push({
            key: person.key,
            name: person.name,
            status: registration.status as RegistrationStatusName,
            undo: registration.id,
            createdByName: registration.createdByName,
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
    registrations: EventRegistrationEntry[],
): AnswerTotal[] {
    const counted = registrations.filter(registration => registration.status === RegistrationStatus.ACCEPTED)
    const answersOf = (fieldId: number) => counted
        .map(registration => registration.fields?.find(value => value.fieldId === fieldId)?.value)
        .filter((value): value is string => value != null && value !== '')

    const totals: AnswerTotal[] = []
    for (const field of fields) {
        if (field.fieldType === EventFieldTypes.NUMBER) {
            const sum = answersOf(field.id)
                .map(Number)
                .filter(value => !Number.isNaN(value))
                .reduce((running, value) => running + value, 0)
            totals.push({label: field.name, text: String(sum)})
            continue
        }
        if (field.fieldType !== EventFieldTypes.ENUM) continue
        const answers = answersOf(field.id)
        const perOption = (field.config?.options ?? [])
            .map(option => ({option, count: answers.filter(answer => answer === option).length}))
            .filter(entry => entry.count > 0)
        if (perOption.length > 0) {
            totals.push({label: field.name, text: perOption.map(e => `${e.option} ${e.count}`).join(', ')})
        }
    }
    return totals
}

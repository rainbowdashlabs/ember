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
} from '@/api/events'

/** Somebody an answer to an appointment can be given for. */
export interface AnswerableMember {
    id: number
    name: string
}

/** What is known about a member somebody answers for. */
interface ManagedMember {
    id: number
    name?: string
    email?: string
}

/**
 * The person a picker is actually pointing at.
 *
 * <p>The list of people still to answer shrinks as they answer, and the choice made before that
 * stays behind in the select. A guardian who answered for one child was then holding a choice that
 * is no longer on the list: the box went blank, the button beside it lost the name it says, and
 * pressing it would have answered a second time for somebody who had already answered.
 *
 * <p>One person left is that person, without asking: a picker with a single entry is a question
 * with one answer.
 *
 * @param chosen  what the picker holds, or null where nothing was picked
 * @param offered who is still to answer
 * @return the person to answer for, or null where the picker points at nobody
 */
export function chosenIfStillOffered<Id>(chosen: Id | null, offered: Id[]): Id | null {
    if (offered.length === 1) return offered[0]!
    return chosen !== null && offered.includes(chosen) ? chosen : null
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
): AnswerableMember[] {
    const ids = eligible[eventId] ?? [selfId, ...managed.map(member => member.id)]
    const answerable: AnswerableMember[] = []
    for (const id of ids) {
        if (id === selfId) {
            answerable.push({id, name: selfLabel})
            continue
        }
        const member = managed.find(candidate => candidate.id === id)
        if (member) answerable.push({id, name: member.name ?? member.email ?? `#${id}`})
    }
    return answerable
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

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
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

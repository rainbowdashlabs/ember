/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    RegistrationStatus,
    type EventGroupAttendance,
    type EventGroupSet,
    type EventGroupSignUp,
    type MemberIdentity,
    type SetResult,
} from '@/api/generated/schema'
import {isSignUpComplete} from '@/views/stationview/events/eventgroupdetailview/eventGroupTables'

type Translate = (key: string) => string

/** A person of the example tables, under the name the help text gives them. */
function examplePerson(name: string, index: number): MemberIdentity {
    return {memberUid: `00000000-0000-4000-8000-00000000000${index}`, stationUid: '00000000-0000-4000-8000-000000000000',
        name, nameColor: null, displayTag: null, stationName: null}
}

/** The two sets of the example event group: practice days and the test. */
export function exampleSets(t: Translate): EventGroupSet[] {
    return [
        {id: 1, eventGroupId: 1, name: t('helpCenter.eventGroupDetail.practice'), minimum: 2, maximum: null, position: 0},
        {id: 2, eventGroupId: 1, name: t('helpCenter.eventGroupDetail.test'), minimum: 1, maximum: 1, position: 1},
    ]
}

/** How a set of the example stands for a sign-up that chose this many of its parts. */
function result(set: EventGroupSet, required: number, chosen: number): SetResult {
    return {setId: set.id, name: set.name, required, chosen, missingRequired: [], maximum: set.maximum, tooMany: 0}
}

/**
 * Three sign-ups of the example event group: one complete and accepted, one waiting for a decision
 * and short of both sets, and one complete and waiting on the waiting list.
 */
export function exampleSignUps(t: Translate): EventGroupSignUp[] {
    const [practice, test] = exampleSets(t)
    const signUp = {answers: [], answersMissing: false, mayRegister: true}
    return [
        {...signUp, memberId: 1, member: examplePerson(t('helpCenter.eventGroupDetail.exampleMember'), 1), status: RegistrationStatus.ACCEPTED,
            registeredAt: '2026-11-02T18:30:00Z', parts: [1, 2, 4], queuePosition: null,
            sets: [result(practice!, 2, 2), result(test!, 1, 1)]},
        {...signUp, memberId: 2, member: examplePerson(t('helpCenter.eventGroupDetail.exampleOther'), 2), status: RegistrationStatus.PENDING,
            registeredAt: '2026-11-04T09:15:00Z', parts: [1], queuePosition: null,
            sets: [result(practice!, 2, 1), result(test!, 1, 0)]},
        {...signUp, memberId: 3, member: examplePerson(t('helpCenter.eventGroupDetail.exampleWaiting'), 3), status: RegistrationStatus.QUEUED,
            registeredAt: '2026-11-05T20:00:00Z', parts: [1, 3, 4], queuePosition: 1,
            sets: [result(practice!, 2, 2), result(test!, 1, 1)]},
    ]
}

/** How the two example participants attended, as the attendance overview shows it. */
export function exampleAttendance(t: Translate): EventGroupAttendance[] {
    return exampleSignUps(t).filter(entry => entry.status !== RegistrationStatus.QUEUED).map(entry => ({memberId: entry.memberId, member: entry.member, sets: entry.sets, complete: isSignUpComplete(entry)}))
}

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
        {id: 1, eventGroupId: 1, name: t('helpCenter.eventGroupDetail.practice'), minimum: 2, position: 0},
        {id: 2, eventGroupId: 1, name: t('helpCenter.eventGroupDetail.test'), minimum: 1, position: 1},
    ]
}

/** Two sign-ups of the example event group: one complete and accepted, one waiting and short of both sets. */
export function exampleSignUps(t: Translate): EventGroupSignUp[] {
    const [practice, test] = exampleSets(t)
    return [
        {memberId: 1, member: examplePerson(t('helpCenter.eventGroupDetail.exampleMember'), 1), status: RegistrationStatus.ACCEPTED,
            registeredAt: '2026-11-02T18:30:00Z', parts: [1, 2, 4], answers: [], answersMissing: false, mayRegister: true,
            sets: [{setId: 1, name: practice!.name, required: 2, chosen: 2, missingRequired: []},
                {setId: 2, name: test!.name, required: 1, chosen: 1, missingRequired: []}]},
        {memberId: 2, member: examplePerson(t('helpCenter.eventGroupDetail.exampleOther'), 2), status: RegistrationStatus.PENDING,
            registeredAt: '2026-11-04T09:15:00Z', parts: [1], answers: [], answersMissing: false, mayRegister: true,
            sets: [{setId: 1, name: practice!.name, required: 2, chosen: 1, missingRequired: []},
                {setId: 2, name: test!.name, required: 1, chosen: 0, missingRequired: []}]},
    ]
}

/** How the two example participants attended, as the attendance overview shows it. */
export function exampleAttendance(t: Translate): EventGroupAttendance[] {
    return exampleSignUps(t).map(entry => ({memberId: entry.memberId, member: entry.member, sets: entry.sets, complete: isSignUpComplete(entry)}))
}

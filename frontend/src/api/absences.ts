/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {AbsenceRequest, AbsenceResponse, MemberAbsence, MyAbsenceRequest} from './generated/schema'

const ownAbsences = createCrudResource<
    AbsenceResponse,
    MyAbsenceRequest,
    MyAbsenceRequest,
    AbsenceResponse,
    AbsenceResponse[]
>('/profile/absences')

const memberAbsences = createCrudResource<MemberAbsence, AbsenceRequest>('/attendance/absences')

export const listMyAbsences = ownAbsences.list
export const createAbsence = ownAbsences.create
export const deleteAbsence = ownAbsences.remove

export const createMemberAbsence = memberAbsences.create
export const deleteMemberAbsence = memberAbsences.remove

export async function listMemberAbsences(memberId: number): Promise<MemberAbsence[]> {
    const res = await client.get<MemberAbsence[]>(`/attendance/absences/member/${memberId}`)
    return res.data
}

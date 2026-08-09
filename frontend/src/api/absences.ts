/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {MemberIdentity} from '@/api/types'

export interface MemberAbsence {
    id: number
    memberId: number
    absentFrom?: string
    absentUntil?: string
    reason?: string
    createdAt?: string
    createdByName?: string | null
    memberIdentity?: MemberIdentity | null
}

interface OwnAbsenceRequest {
    absentFrom: string
    absentUntil: string
    reason?: string
    memberIds?: number[]
}

interface MemberAbsenceRequest {
    memberId: number
    absentFrom: string
    absentUntil: string
    reason?: string
}

const ownAbsences = createCrudResource<
    MemberAbsence,
    OwnAbsenceRequest,
    OwnAbsenceRequest,
    MemberAbsence,
    MemberAbsence[]
>('/profile/absences')

const memberAbsences = createCrudResource<MemberAbsence, MemberAbsenceRequest>('/attendance/absences')

export const listMyAbsences = ownAbsences.list
export const createAbsence = ownAbsences.create
export const deleteAbsence = ownAbsences.remove

export const createMemberAbsence = memberAbsences.create
export const deleteMemberAbsence = memberAbsences.remove

export async function listMemberAbsences(memberId: number): Promise<MemberAbsence[]> {
    const res = await client.get<MemberAbsence[]>(`/attendance/absences/member/${memberId}`)
    return res.data
}

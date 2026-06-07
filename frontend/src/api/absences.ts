/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface MemberAbsence {
    id: number
    memberId: number
    absentFrom?: string
    absentUntil?: string
    reason?: string
    createdAt?: string
    createdByName?: string | null
}

export async function listMyAbsences(): Promise<MemberAbsence[]> {
    const res = await client.get<MemberAbsence[]>('/profile/absences')
    return res.data
}

export async function createAbsence(data: {
    absentFrom: string;
    absentUntil: string;
    reason?: string;
    memberIds?: number[]
}): Promise<MemberAbsence[]> {
    const res = await client.post<MemberAbsence[]>('/profile/absences', data)
    return res.data
}

export async function deleteAbsence(id: number): Promise<void> {
    await client.delete(`/profile/absences/${id}`)
}

export async function listMemberAbsences(memberId: number): Promise<MemberAbsence[]> {
    const res = await client.get<MemberAbsence[]>(`/attendance/absences/member/${memberId}`)
    return res.data
}

export async function createMemberAbsence(data: {
    memberId: number;
    absentFrom: string;
    absentUntil: string;
    reason?: string;
}): Promise<MemberAbsence> {
    const res = await client.post<MemberAbsence>('/attendance/absences', data)
    return res.data
}

export async function deleteMemberAbsence(id: number): Promise<void> {
    await client.delete(`/attendance/absences/${id}`)
}

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

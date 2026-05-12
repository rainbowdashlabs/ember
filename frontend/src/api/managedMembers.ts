/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {ProfileField, ProfileFieldValue} from './types'

export interface ManagedMember {
    id: number
    stationId: number
    accountId: number
    name: string
    email: string
}

export async function listManaged(): Promise<ManagedMember[]> {
    const res = await client.get<ManagedMember[]>('/managed-members')
    return res.data
}

export interface MemberProfile {
    fields: ProfileField[]
    values: ProfileFieldValue[]
}

export async function getProfile(memberId: number): Promise<MemberProfile> {
    const res = await client.get<MemberProfile>(`/managed-members/${memberId}/profile`)
    return res.data
}

export async function setProfile(memberId: number, values: {
    fieldId: number;
    value: string
}[]): Promise<ProfileFieldValue[]> {
    const res = await client.put<ProfileFieldValue[]>(`/managed-members/${memberId}/profile`, {values})
    return res.data
}

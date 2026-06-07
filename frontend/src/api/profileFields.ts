/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {ProfileField, ProfileFieldRequest, ProfileFieldValue, SetValuesRequest,} from './types'

// -- Field Definitions --

export async function listFields(): Promise<ProfileField[]> {
    const res = await client.get<ProfileField[]>('/profile-fields')
    return res.data
}

export async function getField(id: number): Promise<ProfileField> {
    const res = await client.get<ProfileField>(`/profile-fields/${id}`)
    return res.data
}

export async function createField(data: ProfileFieldRequest): Promise<ProfileField> {
    const res = await client.post<ProfileField>('/profile-fields', data)
    return res.data
}

export async function updateField(id: number, data: ProfileFieldRequest): Promise<ProfileField> {
    const res = await client.put<ProfileField>(`/profile-fields/${id}`, data)
    return res.data
}

export async function deleteField(id: number): Promise<void> {
    await client.delete(`/profile-fields/${id}`)
}

export async function getMemberFields(memberId: number): Promise<ProfileField[]> {
    const res = await client.get<ProfileField[]>(`/station-members/${memberId}/fields`)
    return res.data
}

// -- Field Values --

export async function getValues(memberId: number): Promise<ProfileFieldValue[]> {
    const res = await client.get<ProfileFieldValue[]>(`/station-members/${memberId}/profile`)
    return res.data
}

export async function setValues(memberId: number, data: SetValuesRequest): Promise<ProfileFieldValue[]> {
    const res = await client.put<ProfileFieldValue[]>(`/station-members/${memberId}/profile`, data)
    return res.data
}

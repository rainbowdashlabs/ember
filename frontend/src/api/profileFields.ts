/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {ProfileField, ProfileFieldRequest, ProfileFieldValue, SetValuesRequest,} from './types'

// -- Field Definitions --

const fields = createCrudResource<ProfileField, ProfileFieldRequest>('/profile-fields')

export const listFields = fields.list
export const getField = fields.get
export const createField = fields.create
export const updateField = fields.update
export const deleteField = fields.remove

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

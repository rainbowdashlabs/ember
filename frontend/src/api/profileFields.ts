/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
export const FieldTypes = {
    TEXT: 'TEXT',
    NUMBER: 'NUMBER',
    DATE: 'DATE',
    BOOLEAN: 'BOOLEAN',
    ENUM: 'ENUM',
    AGE: 'AGE',
    /** A date field holding the date of birth. A station may declare at most one. */
    BIRTH_DATE: 'BIRTH_DATE',
} as const

/** Field types that hold a date and can therefore serve as the source of a calculated age. */
export const DATE_FIELD_TYPES: readonly string[] = [FieldTypes.DATE, FieldTypes.BIRTH_DATE]

export type FieldTypeName = (typeof FieldTypes)[keyof typeof FieldTypes]

export interface ProfileField {
    id: number
    stationId: string
    name?: string
    fieldType?: string
    config?: string | Record<string, unknown>
    position: number
    scope?: string
    keepOnArchive?: boolean
}

export function parseFieldConfig(config: string | Record<string, unknown> | undefined | null): Record<string, unknown> {
    if (!config) return {}
    if (typeof config === 'object') return config
    try { return JSON.parse(config) } catch { return {} }
}

export interface ProfileFieldRequest {
    name?: string
    fieldType?: string
    config?: string
    position: number
    scope?: string
    keepOnArchive?: boolean
}

export interface ProfileFieldValue {
    memberId: number
    fieldId: number
    value?: string
}

export interface ProfileFieldValueEntry {
    fieldId: number
    value?: string
}

export interface SetValuesRequest {
    values?: ProfileFieldValueEntry[]
}

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

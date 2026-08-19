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

/**
 * A field's settings. They travel as an object in both directions: as JSON text on the way in they
 * were parsed against a shape that named only some of them, and everything else was dropped without
 * a word.
 */
export type ProfileFieldConfig = Record<string, unknown>

export interface ProfileField {
    id: number
    stationId: string
    name?: string
    fieldType?: string
    config?: ProfileFieldConfig
    position: number
    scope?: string
    keepOnArchive?: boolean
}

/** The settings of a field that names none, so a reader never has to check for their absence. */
export function parseFieldConfig(config: ProfileFieldConfig | undefined | null): ProfileFieldConfig {
    return config ?? {}
}

export interface ProfileFieldRequest {
    name?: string
    fieldType?: string
    config?: ProfileFieldConfig
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

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {FieldOrigin, MergedProfileField} from '@/util/profileFields'
export const FieldTypes = {
    TEXT: 'TEXT',
    NUMBER: 'NUMBER',
    DATE: 'DATE',
    BOOLEAN: 'BOOLEAN',
    ENUM: 'ENUM',
    AGE: 'AGE',
    /** A date field holding the date of birth. A station may declare at most one. */
    BIRTH_DATE: 'BIRTH_DATE',
    /** A heading between fields rather than a field. It holds no answer and is asked of nobody. */
    SECTION: 'SECTION',
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
    /** Absent on a field an association declares, which is kept for no single station. */
    stationId?: string
    name?: string
    fieldType?: string
    config?: ProfileFieldConfig
    position: number
    scope?: string
    keepOnArchive?: boolean
    /**
     * Whether the people at the station may read the answer but not write it. Only ever set on a field
     * an association declares: a station has nobody above it to lock out.
     */
    stationReadonly?: boolean
    /**
     * The group of stations this question is asked of. Only ever set on a field an association declares;
     * absent means every station of the association.
     */
    stationGroupId?: number | null
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
    /** Sent only by an association's own screens; a station's endpoint neither expects nor reads it. */
    stationReadonly?: boolean
    /** Sent only by an association's own screens; absent means every station of the association. */
    stationGroupId?: number | null
}

export interface ProfileFieldValue {
    memberId: number
    fieldId: number
    value?: string
}

export interface ProfileFieldValueEntry {
    fieldId: number
    value?: string
    /** Which table the question lives in. Absent means the station's own, which is what most callers send. */
    origin?: FieldOrigin
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

/**
 * Puts the fields in a given order in one request.
 *
 * <p>Dragging one field moves every field below it, and writing that a field at a time meant one request
 * per field for a single drag.
 */
export async function reorderFields(fieldIds: number[]): Promise<void> {
    await client.put('/profile-fields/order', {fieldIds})
}

/**
 * The questions this member's profile asks: the station's own and the ones its cluster adds.
 *
 * One list rather than two, so the profile lays out as one form. Each entry says who asked, which is what
 * decides whether the people at the station may write the answer.
 */
export async function getMemberFields(memberId: number): Promise<MergedProfileField[]> {
    const res = await client.get<MergedProfileField[]>(`/station-members/${memberId}/fields`)
    return res.data
}

// -- Field Values --

export async function getValues(memberId: number): Promise<ProfileFieldValue[]> {
    const res = await client.get<ProfileFieldValue[]>(`/station-members/${memberId}/profile`)
    return res.data
}

/** The same answers, each saying which table its question lives in. */
export async function getMergedValues(memberId: number): Promise<MergedProfileValue[]> {
    const res = await client.get<MergedProfileValue[]>(`/station-members/${memberId}/profile`)
    return res.data
}

export async function setValues(memberId: number, data: SetValuesRequest): Promise<ProfileFieldValue[]> {
    const res = await client.put<ProfileFieldValue[]>(`/station-members/${memberId}/profile`, data)
    return res.data
}

/** An answer on the member profile, and which table its question lives in. */
export interface MergedProfileValue {
    fieldId: number
    value?: string | null
    origin: FieldOrigin
}

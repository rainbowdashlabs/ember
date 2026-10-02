/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import {
    FieldType,
    ProfileFieldScope,
    type AssignmentRequest,
    type MergedField,
    type MergedValue,
    type ProfileField,
    type ProfileFieldAssignment,
    type ProfileFieldConfig,
    type ProfileFieldRequest,
    type SetValuesRequest,
} from './generated/schema'

/**
 * Field types that hold a date and can therefore serve as the source of a calculated age. An expiry
 * date holds a date too, but nobody counts an age from the day a licence runs out.
 */
export const DATE_FIELD_TYPES: readonly string[] = [FieldType.DATE, FieldType.BIRTH_DATE]

/**
 * The kind of member a picked value names, or nothing where it names none the server knows. A group
 * is a target of its own, not one of these kinds.
 */
export function scopeOf(value: string | undefined): ProfileFieldScope | undefined {
    return value !== undefined && isScope(value) ? value : undefined
}

function isScope(value: string): value is ProfileFieldScope {
    return Object.hasOwn(ProfileFieldScope, value)
}

/**
 * A field's settings as a screen holds them while they are written.
 *
 * <p>The server sends every setting, unset ones as null. A draft names only the ones it sets, and the
 * server reads a setting left out the same as one sent as null.
 */
export type FieldSettings = Partial<ProfileFieldConfig>

/**
 * What a station's question and a question on a member's merged profile both carry, for the screens
 * that read either.
 */
export type ProfileQuestion = Pick<ProfileField, 'id' | 'name' | 'fieldType' | 'config' | 'required' | 'readonly' | 'width'>

/** The settings a field list switches on and off from its rows. */
export type FieldSwitchName = Extract<keyof ProfileFieldConfig, 'notifyOnChange' | 'overview'>

/**
 * A question as the shared field editor holds it, whether a station or an association asks it.
 *
 * <p>The association's two settings are absent on a station's question: a station has nobody below it
 * to lock out and files no stations.
 */
export type EditableField = Omit<ProfileField, 'stationId' | 'config'> & {
    config: FieldSettings
    stationReadonly?: boolean
    stationGroupId?: number | null
}

/**
 * A question as the shared field editor writes it back, whether to a station or to an association.
 *
 * <p>The association's two settings are only sent to the association's endpoint; a station's neither
 * expects nor reads them.
 */
export type EditableFieldRequest = Omit<ProfileFieldRequest, 'config'> & {
    config?: FieldSettings
    stationReadonly?: boolean
    stationGroupId?: number | null
}

/** Which audience an assignment is about: a kind of member, or one group. */
export type AssignmentTarget = Pick<AssignmentRequest, 'role' | 'groupId'>

/**
 * The question a calculated age is worked out from, as this build records it.
 *
 * <p>The identifier is what a field points at, because a name is what somebody changes their mind
 * about and a renamed birth date used to empty every age that read it. A field configured before
 * the identifier was recorded still names its source, so that is read where no identifier stands.
 *
 * @param config the age field's settings
 * @param fields the fields it may point at, which are the ones of its own owner
 */
export function ageSourceOf<T extends {id: number; name: string}>(
    config: FieldSettings, fields: readonly T[],
): T | undefined {
    const id = config.sourceFieldId
    if (typeof id === 'number') return fields.find(f => f.id === id)
    const name = config.sourceField
    return typeof name === 'string' ? fields.find(f => f.name === name) : undefined
}

/** The settings of a field that names none, so a reader never has to check for their absence. */
export function parseFieldConfig(config: FieldSettings | undefined | null): FieldSettings {
    return config ?? {}
}

const fields = createCrudResource<ProfileField, EditableFieldRequest>('/profile-fields')

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
export async function reorderFields(role: ProfileFieldScope, fieldIds: number[]): Promise<void> {
    await client.put('/profile-fields/order', {role, fieldIds})
}

/** Every assignment of this station's fields, which is what each audience's form is built from. */
export async function listAssignments(): Promise<ProfileFieldAssignment[]> {
    const res = await client.get<ProfileFieldAssignment[]>('/profile-fields/assignments')
    return res.data
}

/**
 * Asks an audience this question, or changes how it is put to them.
 *
 * Exactly one of role and groupId is given: naming both would ask it twice over, and naming neither
 * would ask nobody.
 */
export async function assignField(fieldId: number, assignment: AssignmentRequest): Promise<void> {
    await client.put(`/profile-fields/${fieldId}/assignments`, assignment)
}

/** Stops asking an audience this question. The definition and its answers stay. */
export async function unassignField(fieldId: number, target: AssignmentTarget): Promise<void> {
    await client.delete(`/profile-fields/${fieldId}/assignments`, {data: target})
}

/**
 * The questions this member's profile asks: the station's own and the ones its association adds.
 *
 * One list rather than two, so the profile lays out as one form. Each entry says who asked, which is what
 * decides whether the people at the station may write the answer.
 */
export async function getMemberFields(memberId: number): Promise<MergedField[]> {
    const res = await client.get<MergedField[]>(`/station-members/${memberId}/fields`)
    return res.data
}

/** A member's answers, each saying which table its question lives in. */
export async function getValues(memberId: number): Promise<MergedValue[]> {
    const res = await client.get<MergedValue[]>(`/station-members/${memberId}/profile`)
    return res.data
}

export async function setValues(memberId: number, data: SetValuesRequest): Promise<MergedValue[]> {
    const res = await client.put<MergedValue[]>(`/station-members/${memberId}/profile`, data)
    return res.data
}

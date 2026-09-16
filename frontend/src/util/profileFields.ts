/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type Ref} from 'vue'
import {ageSourceOf, FieldTypes, parseFieldConfig, type ProfileField} from '@/api/profileFields'
import {computeAge} from '@/util/age'

/**
 * The answer to a question that is worked out rather than given, or null where this question is
 * not one of those.
 *
 * <p>Every screen that shows such an answer has to work it out, because none is stored: the field
 * can hold one, and a question that was a number before it became a calculated age still carries
 * whatever was written back then. A screen that read the stored answer showed that old number and
 * went on showing it, beside a list that had already worked out the real one.
 *
 * @param field      the question being shown
 * @param fields     the questions it may count from, which are the ones of its own owner
 * @param rawValueOf reads the answer to another question of the same member
 */
export function calculatedAnswer(
    field: Pick<ProfileField, 'fieldType' | 'config'>,
    fields: readonly ProfileField[],
    rawValueOf: (fieldId: number) => unknown,
): string | null {
    if (field.fieldType !== FieldTypes.AGE) return null
    const config = parseFieldConfig(field.config)
    const source = ageSourceOf(config, fields)
    if (!source) return ''
    return computeAge(String(rawValueOf(source.id) ?? ''), (config.ageMode as string) ?? 'now')
}

/**
 * Reads a profile field value from a {@link Map}-backed {@link Ref}, returning
 * the empty string when the field is not yet set.
 */
export function getFieldValue(values: Ref<Map<number, string>>, fieldId: number): string {
    return values.value.get(fieldId) ?? ''
}

/**
 * Writes a profile field value into a {@link Map}-backed {@link Ref}.
 *
 * Replaces the underlying {@link Map} instance so Vue treats the assignment
 * as a reactive change.
 */
export function setFieldValue(values: Ref<Map<number, string>>, fieldId: number, value: string): void {
    const next = new Map(values.value)
    next.set(fieldId, value)
    values.value = next
}

/**
 * Shape of a single profile-field value entry as returned by the backend.
 */
export interface ProfileFieldValueEntry {
    fieldId: number
    value?: string | null
}

/**
 * Decodes a list of profile-field value entries into a {@link Map} keyed by
 * field id.
 *
 * Values arrive JSON-encoded; decoding falls back to the raw string when
 * parsing fails so legacy or hand-edited rows still load.
 */
export function decodeProfileValues(entries: ReadonlyArray<ProfileFieldValueEntry>): Map<number, string> {
    const map = new Map<number, string>()
    for (const entry of entries) {
        let val: unknown = entry.value ?? ''
        try {
            val = JSON.parse(val as string)
        } catch {
            void 0
        }
        map.set(entry.fieldId, typeof val === 'string' ? val : String(val))
    }
    return map
}

/** Who asked a profile question: the station itself, or the cluster above it. */
export type FieldOrigin = 'STATION' | 'CLUSTER'

/**
 * A field as the member profile sees it, whoever asked it.
 *
 * The profile is the one screen that shows both, so it is the one place that has to tell them apart. A
 * station numbers its own fields and a cluster numbers its own, in separate tables, so the id alone is
 * not a name: {@link profileKey} is.
 */
export interface MergedProfileField {
    id: number
    /** Absent on a cluster's question, which belongs to no one station. */
    stationId?: string
    name?: string
    fieldType?: string
    config?: Record<string, unknown>
    /** Whether this audience must answer, which their assignment may decide against the definition. */
    required: boolean
    /** Where the question sits on this audience's form. */
    position: number
    /** How much of a row it takes for this audience. Null or absent is the whole row. */
    width?: string | null
    /** Whether this audience may read the answer but not write it. */
    readonly: boolean
    /** The kind of member this form was built for. Absent where a group is asked. */
    role?: string
    origin: FieldOrigin
    /** Whether the people at the station may read the answer but not write it. Only a cluster field can be. */
    readonlyAtStation: boolean
}

/** Which kind of member a question is put to. A group is a target of its own, not one of these. */
export type ProfileFieldRole = 'TRIAL' | 'MEMBER' | 'GUARDIAN' | 'TEAM' | 'MANAGER'

/** What an assignment names: a kind of member, or one group of them. */
export type ProfileFieldTarget = 'ROLE' | 'GROUP'

/**
 * Who a field is asked of, and how it is put to them.
 *
 * The definition beside this says what the question is. The same question can stand in two forms
 * without being two questions: a manager may read a date the team writes.
 */
export interface ProfileFieldAssignment {
    id: number
    fieldId: number
    targetKind: ProfileFieldTarget
    /** Set where this names a kind of member. */
    role?: ProfileFieldRole | null
    /** Set where this names a group. */
    groupId?: number | null
    position: number
    /** Null means the definition's width stands, which is the ordinary case. */
    widthOverride?: string | null
    /** Null means the definition decides who may write the answer, which is the ordinary case. */
    readonlyOverride?: boolean | null
    /** Null means the definition decides, which is the ordinary case. */
    requiredOverride?: boolean | null
}

/** A station's question, defined once, whoever is asked it. */
export interface ProfileFieldDefinition {
    id: number
    stationId: number
    name: string
    fieldType: string
    config?: Record<string, unknown>
    required: boolean
    /** How much of a row it takes unless an audience says otherwise. Null is the whole row. */
    width?: string | null
    keepOnArchive: boolean
}

/**
 * The question as one audience meets it: the definition, with whatever their assignment says instead.
 *
 * <p>Width and whether an answer is expected are the definition's until an audience overrides them, so
 * the override standing at null is what keeps the definition's answer free to change underneath.
 */
export function asAsked<T extends {required?: boolean; readonly?: boolean; width?: string | null}>(
    field: T,
    assignment: ProfileFieldAssignment,
): T & {required: boolean; width: string | null; readonly: boolean; position: number} {
    return {
        ...field,
        required: assignment.requiredOverride ?? field.required ?? false,
        width: assignment.widthOverride ?? field.width ?? null,
        readonly: assignment.readonlyOverride ?? field.readonly ?? false,
        position: assignment.position,
    }
}

/** The key an answer is held under on the profile, which is the pair and not the id. */
export function profileKey(fieldId: number, origin: FieldOrigin): string {
    return `${origin}:${fieldId}`
}

/**
 * Decodes profile answers of both origins into a map keyed by {@link profileKey}.
 *
 * The plain {@link decodeProfileValues} keys by id alone, which is right for every list of fields that
 * has one origin and wrong for the profile, where two questions can carry the same number.
 */
export function decodeMergedValues(
    entries: ReadonlyArray<ProfileFieldValueEntry & {origin?: FieldOrigin}>,
): Map<string, string> {
    const map = new Map<string, string>()
    for (const entry of entries) {
        let val: unknown = entry.value ?? ''
        try {
            val = JSON.parse(val as string)
        } catch {
            void 0
        }
        map.set(profileKey(entry.fieldId, entry.origin ?? 'STATION'),
            typeof val === 'string' ? val : String(val))
    }
    return map
}

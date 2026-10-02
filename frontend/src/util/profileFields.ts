/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Ref} from 'vue'
import {ageSourceOf, parseFieldConfig, type FieldOriginName, type FieldSettings} from '@/api/profileFields'
import {FieldTypes} from '@/api/fieldTypes'
import type {MergedValue, ProfileFieldAssignment} from '@/api/generated/schema'
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
    field: {fieldType: string; config?: FieldSettings | null},
    fields: readonly {id: number; name: string}[],
    rawValueOf: (fieldId: number) => unknown,
): string | null {
    if (field.fieldType !== FieldTypes.AGE) return null
    const config = parseFieldConfig(field.config)
    const source = ageSourceOf(config, fields)
    if (!source) return ''
    return computeAge(String(rawValueOf(source.id) ?? ''), config.ageMode ?? 'now')
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

/** One stored answer, as every list of answers the backend sends carries it. */
export type ProfileFieldValueEntry = Pick<MergedValue, 'fieldId'> & {value?: string | null}

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

/**
 * The key an answer is held under on the profile, which is the pair and not the id.
 *
 * <p>A station numbers its own fields and an association numbers its own, in separate tables, so the
 * id alone does not name a question on a profile that shows both.
 */
export function profileKey(fieldId: number, origin: FieldOriginName): string {
    return `${origin}:${fieldId}`
}

/**
 * Decodes profile answers of both origins into a map keyed by {@link profileKey}, since two questions on
 * one profile can carry the same number.
 *
 * Values arrive JSON-encoded; decoding falls back to the raw string when parsing fails so legacy or
 * hand-edited rows still load.
 */
export function decodeMergedValues(
    entries: ReadonlyArray<ProfileFieldValueEntry & {origin?: FieldOriginName}>,
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

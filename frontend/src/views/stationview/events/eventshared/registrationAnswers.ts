/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldType, type EventRegistrationField, type EventRegistrationFieldValue} from '@/api/generated/schema'

/** The answers of a form asking registration questions, by question id. */
export type RegistrationAnswers = Record<number, string>

/**
 * What the form starts from: the answer already on file where there is one, otherwise the configured
 * default, otherwise nothing. A default is what the member starts from, not an answer already given on
 * their behalf, so it never stands in front of one that was.
 *
 * @param fields the questions
 * @param onFile the answers already given, where an existing registration is being corrected
 */
export function seedAnswers(fields: EventRegistrationField[], onFile: EventRegistrationFieldValue[] = []): RegistrationAnswers {
    const given = new Map(onFile.map(value => [value.fieldId, value.value]))
    const seeded: RegistrationAnswers = {}
    for (const field of fields) seeded[field.id] = given.get(field.id) ?? field.config?.defaultValue ?? ''
    return seeded
}

/** The required questions still without an answer. */
export function missingAnswers(fields: EventRegistrationField[], answers: RegistrationAnswers): EventRegistrationField[] {
    return fields.filter(field => field.config?.required && !(answers[field.id] ?? '').trim())
}

/** The answers as they are sent, blank ones left out. */
export function answersToSend(fields: EventRegistrationField[], answers: RegistrationAnswers): EventRegistrationFieldValue[] {
    return fields
        .map(field => ({fieldId: field.id, value: answers[field.id] ?? ''}))
        .filter(entry => entry.value.trim() !== '')
}

/**
 * The bounds a number question names, for the hint below its input, or null where it is no number
 * question or names none. A missing bound reads as an open end.
 */
export function numberRange(field: EventRegistrationField): {min: number | string, max: number | string} | null {
    if (field.fieldType !== FieldType.NUMBER) return null
    const {min, max} = field.config ?? {}
    if (min == null && max == null) return null
    return {min: min ?? '–', max: max ?? '–'}
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EventRegistrationField, EventRegistrationFieldValue} from '@/api/generated/schema'

/** What the sign-up form starts from: the configured default of every question, or nothing. */
export function seedAnswers(fields: EventRegistrationField[]): Record<number, string> {
    const seeded: Record<number, string> = {}
    for (const field of fields) seeded[field.id] = field.config?.defaultValue ?? ''
    return seeded
}

/** The required questions still without an answer. */
export function missingAnswers(fields: EventRegistrationField[], answers: Record<number, string>): EventRegistrationField[] {
    return fields.filter(field => field.config?.required && !(answers[field.id] ?? '').trim())
}

/** The answers as the sign-up sends them, blank ones left out. */
export function answersToSend(fields: EventRegistrationField[], answers: Record<number, string>): EventRegistrationFieldValue[] {
    return fields
        .map(field => ({fieldId: field.id, value: answers[field.id] ?? ''}))
        .filter(entry => entry.value.trim() !== '')
}

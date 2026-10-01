/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldTypes} from '@/api/fieldTypes'
import type {StationUserType} from '@/api/generated/schema'

/**
 * What a field says about the answer it takes, in the one shape every settings screen edits.
 *
 * <p>Each feature keeps its settings under its own keys, and most of the keys are the same: the
 * answers a choice offers, whether an answer is needed, what the form starts from, the bounds of a
 * number and which members a member field may name. A feature reads its settings into this and
 * writes them back out of it; what is truly its own stays beside it.
 *
 * <p>A starting value is text, the language every answer is checked in. Null means none.
 */
export interface QuestionSettingsModel {
    required?: boolean
    defaultValue?: string | null
    options?: string[]
    min?: number | null
    max?: number | null
    step?: number | null
    groupId?: number | null
    userType?: StationUserType | null
    tagId?: number | null
}

/**
 * One part of the settings a feature offers.
 *
 * <p>{@code todayDefault} is the date default of the profile and the attendance sheet: a date there
 * starts as today or not at all, because a fixed day goes stale the day after it is set.
 */
export type QuestionSetting = 'required' | 'default' | 'todayDefault' | 'options' | 'bounds' | 'step' | 'members'

/** The starting value a date field writes to mean the day the form is opened. */
export const TODAY = '__TODAY__'

/**
 * A starting value as text, however a feature stored it.
 *
 * <p>The profile and the attendance sheet keep a yes as {@code true} and a number as a number, the
 * appointment questions keep text. Null and undefined mean there is none.
 */
export function defaultAsText(value: unknown): string | null {
    if (value === null || value === undefined) return null
    return String(value)
}

/**
 * A starting value in the shape a feature that stores JSON keeps it: a yes as a yes, a number as a
 * number, everything else as text.
 *
 * <p>Undefined where there is none, so a feature writing its settings leaves the key out.
 */
export function typedDefault(fieldType: string, text: string | null | undefined): unknown {
    if (text === null || text === undefined) return undefined
    if (fieldType === FieldTypes.BOOLEAN) return text === 'true'
    if (fieldType === FieldTypes.NUMBER) {
        const number = Number(text)
        return text.trim() === '' || Number.isNaN(number) ? undefined : number
    }
    return text.trim()
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldTypes, parseFieldConfig} from '@/api/profileFields'

/**
 * The little a layout decision needs to know about a field.
 *
 * Narrow on purpose: a station's own question and one its cluster added are different rows in different
 * tables, and how wide either is drawn depends on neither of those things.
 */
export interface LayoutField {
    fieldType?: string
    config?: Record<string, unknown>
}

/**
 * How much of a row a field takes. A station with thirty fields reads as thirty boxes under each
 * other unless the short ones are allowed to stand beside each other.
 */
export const FieldWidths = {
    FULL: 'full',
    HALF: 'half',
    THIRD: 'third',
} as const

export type FieldWidthName = (typeof FieldWidths)[keyof typeof FieldWidths]

/** The row is six columns wide, which divides by two and by three. */
const SPANS: Record<FieldWidthName, string> = {
    [FieldWidths.FULL]: 'col-span-6',
    [FieldWidths.HALF]: 'col-span-6 sm:col-span-3',
    [FieldWidths.THIRD]: 'col-span-6 sm:col-span-2',
}

/**
 * A field's settings, whether they arrive as an object or as the text they are stored as.
 *
 * <p>Which of the two turns up depends on the screen: a field just typed carries its settings as an
 * object, while one read back from the server carries the text. Both say the same thing, and a width
 * that only applies to one of them is a width that appears to be ignored every other time.
 *
 * @param config what the field carries, in either shape
 * @return the settings, empty where there are none or where they cannot be read
 */
export function configOf(config?: string | Record<string, unknown> | null): Record<string, unknown> {
    if (!config) return {}
    if (typeof config === 'object') return config
    try {
        return JSON.parse(config) as Record<string, unknown>
    } catch {
        return {}
    }
}

/**
 * The grid class for a width on its own, for fields whose settings are a plain object.
 *
 * <p>An event's questions and an attendance sheet's are configured elsewhere and stored differently,
 * but a row is a row: they are laid out on the same six columns so that a station setting a width in
 * one place gets the same result in the other.
 *
 * @param width what was configured, or nothing
 * @return the grid class, a whole row where nothing was said
 */
export function spanForWidth(width?: unknown): string {
    return width === FieldWidths.HALF || width === FieldWidths.THIRD
        ? SPANS[width as FieldWidthName]
        : SPANS[FieldWidths.FULL]
}

/** A field that says nothing about its width takes the whole row, which is how it always was. */
export function widthOf(field: LayoutField): FieldWidthName {
    const width = parseFieldConfig(field.config).width
    return width === FieldWidths.HALF || width === FieldWidths.THIRD ? width : FieldWidths.FULL
}

/**
 * The grid class for a field. A heading always takes the whole row: it introduces what follows and
 * would say nothing standing beside it.
 */
export function spanClass(field: LayoutField): string {
    return isSection(field) ? SPANS[FieldWidths.FULL] : SPANS[widthOf(field)]
}

/** Whether the entry is a heading between fields rather than a field. */
export function isSection(field: LayoutField): boolean {
    return field.fieldType === FieldTypes.SECTION
}

/** The entries that hold an answer, which are the only ones worth saving or asking about. */
export function valueFields<T extends LayoutField>(fields: T[]): T[] {
    return fields.filter(field => !isSection(field))
}

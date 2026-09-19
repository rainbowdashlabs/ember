/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {SortValue} from '@/composables/useSortable'
import {formatDate, formatDateTime} from '@/util/format'

/**
 * What a column holds, which is all a table needs to know to sort it and to offer a filter for it.
 *
 * <p>Nobody wires a filter to a column by hand: a date column filters by day, a number by a range,
 * a choice by its labelled options, and everything else by the words in its cells.
 */
export const ColumnTypes = {
    TEXT: 'text',
    NUMBER: 'number',
    DATE: 'date',
    /** A moment rather than a day: shown with its time, filtered by the day it falls on. */
    DATE_TIME: 'dateTime',
    /** A date of birth: filtered like a date, with bounds on the age it gives as well. */
    BIRTH_DATE: 'birthDate',
    BOOLEAN: 'boolean',
    /** One of a fixed set of options, stored as a value and shown by its label. */
    ENUM: 'enum',
} as const

export type ColumnType = (typeof ColumnTypes)[keyof typeof ColumnTypes]

/** One scalar a cell holds. Dates are ISO days or instants, enums and booleans their stored value. */
export type CellScalar = string | number | boolean | null | undefined

/** What a cell holds: one scalar, or several where a cell lists things, such as a member's groups. */
export type CellValue = CellScalar | readonly CellScalar[]

/** One enum option: the value the rows hold and the words the reader sees. */
export interface ColumnOption {
    value: string
    label: string
}

/**
 * One column of a table of records.
 *
 * @typeParam Row the record one line of the table stands for
 */
export interface TableColumn<Row> {
    /** Stable across reloads and releases: remembered column choices and saved filters name it. */
    key: string
    label: string
    type: ColumnType
    /** The raw value, which is what the column sorts and filters by. */
    value: (row: Row) => CellValue
    /** The cell as the reader sees it. Worked out from the type and the value where absent. */
    display?: (row: Row) => string
    /**
     * What the column sorts by, where that is not its value: a column that filters by whether a
     * piece is held but sorts by who holds it. Null sorts last.
     */
    sortValue?: (row: Row) => SortValue
    /** The options of an enum column, in the order they sort in. */
    options?: readonly ColumnOption[]
    /** The words for yes and no of a boolean column, where the default ones do not fit. */
    booleanLabels?: {yes: string, no: string}
    /** Always shown and never offered in the column list. */
    pinned?: boolean
    /** Whether a reader who has chosen nothing sees it. Defaults to shown. */
    defaultVisible?: boolean
    sortable?: boolean
    filterable?: boolean
    /** Whether the plain-text search looks at this column. Defaults to text and enum columns. */
    searchable?: boolean
    align?: 'left' | 'center' | 'right'
}

/**
 * The options of an enum column, each worded by the given function.
 *
 * @param values the stored values, in the order they sort in
 * @param label  the words for one value, usually an i18n lookup
 */
export function enumOptions(values: readonly string[], label: (value: string) => string): ColumnOption[] {
    return values.map(value => ({value, label: label(value)}))
}

const TYPES_BY_FIELD: Record<string, ColumnType> = {
    NUMBER: ColumnTypes.NUMBER,
    AGE: ColumnTypes.NUMBER,
    DATE: ColumnTypes.DATE,
    BIRTH_DATE: ColumnTypes.BIRTH_DATE,
    BOOLEAN: ColumnTypes.BOOLEAN,
    ENUM: ColumnTypes.ENUM,
}

/**
 * The column type of a question, for the question types profile fields, waiting lists and drawn
 * member tables share. Whatever is not one of them reads as text.
 */
export function columnTypeOf(fieldType: string | null | undefined): ColumnType {
    return TYPES_BY_FIELD[fieldType ?? ''] ?? ColumnTypes.TEXT
}

/**
 * An answer of unknown shape as a cell: nothing, empty text and null are an empty cell, lists stay
 * lists, and anything structured is written out rather than shown as an object.
 */
export function toCellValue(answer: unknown): CellValue {
    if (answer === null || answer === undefined || answer === '') return null
    if (Array.isArray(answer)) return answer.map(String)
    return typeof answer === 'object' ? JSON.stringify(answer) : answer as string | number | boolean
}

/** The scalars of a cell, with the empty ones left out. */
export function scalarsOf(value: CellValue): CellScalar[] {
    const all = Array.isArray(value) ? value : [value as CellScalar]
    return all.filter(scalar => scalar !== null && scalar !== undefined && scalar !== '')
}

interface OptionIndex {
    label: Map<string, string>
    rank: Map<string, number>
}

const optionIndexes = new WeakMap<readonly ColumnOption[], OptionIndex>()

/** The labels and sort ranks of an enum's options, looked up once per options list. */
export function optionIndexOf(options: readonly ColumnOption[]): OptionIndex {
    let index = optionIndexes.get(options)
    if (!index) {
        index = {
            label: new Map(options.map(option => [option.value, option.label])),
            rank: new Map(options.map((option, rank) => [option.value, rank])),
        }
        optionIndexes.set(options, index)
    }
    return index
}

/**
 * The words a scalar reads as in a column of the given kind.
 *
 * @param yesNo the words a boolean reads as
 */
export function wordScalar<Row>(column: TableColumn<Row>, scalar: CellScalar, yesNo: {yes: string, no: string}): string {
    if (scalar === null || scalar === undefined || scalar === '') return ''
    switch (column.type) {
        case ColumnTypes.DATE:
        case ColumnTypes.BIRTH_DATE:
            return formatDate(String(scalar)) || String(scalar)
        case ColumnTypes.DATE_TIME:
            return formatDateTime(String(scalar)) || String(scalar)
        case ColumnTypes.BOOLEAN: {
            const labels = column.booleanLabels ?? yesNo
            return scalar === true || scalar === 'true' ? labels.yes : labels.no
        }
        case ColumnTypes.ENUM:
            return (column.options && optionIndexOf(column.options).label.get(String(scalar))) ?? String(scalar)
        default:
            return String(scalar)
    }
}

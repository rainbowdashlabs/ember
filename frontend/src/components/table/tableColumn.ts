/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {SortValue} from '@/composables/useSortable'
import {formatDate} from '@/util/format'

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

/** One option of an enum column: the value the rows hold and the words the reader sees. */
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

/** The scalars of a cell, with the empty ones left out. */
export function scalarsOf(value: CellValue): CellScalar[] {
    const all = Array.isArray(value) ? value : [value as CellScalar]
    return all.filter(scalar => scalar !== null && scalar !== undefined && scalar !== '')
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
        case ColumnTypes.BOOLEAN: {
            const labels = column.booleanLabels ?? yesNo
            return scalar === true || scalar === 'true' ? labels.yes : labels.no
        }
        case ColumnTypes.ENUM:
            return column.options?.find(option => option.value === String(scalar))?.label ?? String(scalar)
        default:
            return String(scalar)
    }
}

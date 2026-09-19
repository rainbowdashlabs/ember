/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {compareSortValues, type SortValue} from '@/composables/useSortable'
import {matchesDateFilter, splitDateTokens} from '@/util/dateFilter'
import {ColumnTypes, scalarsOf, wordScalar, type CellScalar, type TableColumn} from './tableColumn'

/** Which filter body a column's type opens. */
export type FilterKind = 'values' | 'number' | 'date' | 'birthDate'

/** One entry of a filter's list: what is matched against and what the reader ticks. */
export interface FilterChoice {
    value: string
    label: string
}

/** The words a boolean reads as where the column names none of its own. */
export interface YesNo {
    yes: string
    no: string
}

export function filterKindOf(type: string): FilterKind {
    if (type === ColumnTypes.NUMBER) return 'number'
    if (type === ColumnTypes.DATE) return 'date'
    if (type === ColumnTypes.BIRTH_DATE) return 'birthDate'
    return 'values'
}

/**
 * The strings a filter compares one row's cell against.
 *
 * <p>Text is compared by the words the reader sees, so the list in the filter is the list on the
 * screen. Enums and booleans are compared by what they store, so a filter survives an option being
 * renamed, and are labelled in the list instead. Dates and numbers stay raw for their bounds.
 */
export function filterValuesOf<Row>(column: TableColumn<Row>, row: Row, yesNo: YesNo): string[] {
    const scalars = scalarsOf(column.value(row))
    if (column.type === ColumnTypes.TEXT) return scalars.map(scalar => wordScalar(column, scalar, yesNo))
    return scalars.map(scalar => String(scalar))
}

/**
 * The entries a column's filter offers, drawn from the rows it filters.
 *
 * <p>An enum offers every option it has, in its own order, because an option nobody holds yet is
 * still one somebody may want to narrow to later. A boolean always offers both answers.
 */
export function choicesFor<Row>(column: TableColumn<Row>, rows: readonly Row[], yesNo: YesNo): FilterChoice[] {
    const held = new Set(rows.flatMap(row => filterValuesOf(column, row, yesNo)))
    if (column.type === ColumnTypes.BOOLEAN) {
        return ['true', 'false'].map(value => ({value, label: wordScalar(column, value, yesNo)}))
    }
    if (column.type === ColumnTypes.ENUM) {
        const options = column.options ?? []
        const known = new Set(options.map(option => option.value))
        const strays = [...held].filter(value => !known.has(value)).toSorted()
        return [...options, ...strays.map(value => ({value, label: value}))]
    }
    const values = [...held]
    const sorted = column.type === ColumnTypes.NUMBER
        ? values.toSorted((a, b) => Number(a) - Number(b))
        : values.toSorted((a, b) => compareSortValues(a, b))
    return sorted.map(value => ({value, label: value}))
}

/** The bounds a number filter carries as tokens, both inclusive, and any single values ticked. */
export interface NumberFilterTokens {
    min: number | null
    max: number | null
    exact: string[]
}

function boundOf(token: string, name: string): number | null {
    if (!token.startsWith(name + ':')) return null
    const value = Number(token.slice(name.length + 1))
    return Number.isFinite(value) ? value : null
}

/**
 * Reads a number filter's set. A token that is no bound is a single value, which is what a filter
 * saved before numbers had a range of their own holds.
 */
export function splitNumberTokens(selected: ReadonlySet<string>): NumberFilterTokens {
    const tokens: NumberFilterTokens = {min: null, max: null, exact: []}
    for (const token of selected) {
        const min = boundOf(token, 'min')
        if (min !== null) { tokens.min = min; continue }
        const max = boundOf(token, 'max')
        if (max !== null) { tokens.max = max; continue }
        tokens.exact.push(token)
    }
    return tokens
}

export function joinNumberTokens(min: number | null, max: number | null): Set<string> {
    const set = new Set<string>()
    if (min !== null) set.add('min:' + min)
    if (max !== null) set.add('max:' + max)
    return set
}

function matchesNumber(value: string, tokens: NumberFilterTokens): boolean {
    if (tokens.exact.length > 0 && !tokens.exact.includes(value)) return false
    const number = Number(value)
    if (!Number.isFinite(number)) return false
    if (tokens.min !== null && number < tokens.min) return false
    return !(tokens.max !== null && number > tokens.max)
}

/**
 * Whether one cell passes the filter standing on its column.
 *
 * @param kind         the filter body the column opens
 * @param values       the cell as {@link filterValuesOf} gives it
 * @param selected     what the filter holds; nothing held means nothing narrowed but the empties
 * @param includeEmpty whether an empty cell passes
 */
export function cellPasses(kind: FilterKind, values: readonly string[], selected: ReadonlySet<string>, includeEmpty: boolean): boolean {
    if (values.length === 0) return includeEmpty
    if (selected.size === 0) return !includeEmpty
    if (kind === 'date' || kind === 'birthDate') {
        const tokens = splitDateTokens(selected)
        return values.some(value => matchesDateFilter(value, tokens))
    }
    if (kind === 'number') {
        const tokens = splitNumberTokens(selected)
        return values.some(value => matchesNumber(value, tokens))
    }
    return values.some(value => selected.has(value))
}

function sortScalar<Row>(column: TableColumn<Row>, scalar: CellScalar, yesNo: YesNo): SortValue {
    if (scalar === null || scalar === undefined || scalar === '') return null
    switch (column.type) {
        case ColumnTypes.NUMBER: {
            const number = Number(scalar)
            return Number.isFinite(number) ? number : null
        }
        case ColumnTypes.BOOLEAN:
            return scalar === true || scalar === 'true'
        case ColumnTypes.ENUM: {
            const index = column.options?.findIndex(option => option.value === String(scalar)) ?? -1
            return index >= 0 ? index : wordScalar(column, scalar, yesNo)
        }
        case ColumnTypes.DATE:
        case ColumnTypes.BIRTH_DATE:
            return String(scalar)
        default:
            return wordScalar(column, scalar, yesNo)
    }
}

/**
 * Orders two rows by one column the way its type reads: numbers by size, dates by day, options in
 * their own order, words alphabetically.
 */
export function compareByColumn<Row>(column: TableColumn<Row>, a: Row, b: Row, yesNo: YesNo): number {
    return compareSortValues(sortValueOf(column, a, yesNo), sortValueOf(column, b, yesNo))
}

/** The value a row sorts by in one column, null where its cell is empty. */
export function sortValueOf<Row>(column: TableColumn<Row>, row: Row, yesNo: YesNo): SortValue {
    if (column.sortValue) return column.sortValue(row) ?? null
    return sortScalar(column, scalarsOf(column.value(row))[0], yesNo)
}

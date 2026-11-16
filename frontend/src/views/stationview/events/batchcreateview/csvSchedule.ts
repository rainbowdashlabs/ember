/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {BatchRow} from '@/api/generated/schema'
import {draftProblems, draftToBatchRow, type DraftProblemReason, type DraftRow} from './draftRows'

/** The date formats a file can be read in; `auto` tries each of them on every row. */
export type CsvDateFormat = 'auto' | 'DD.MM.YYYY' | 'YYYY-MM-DD' | 'MM/DD/YYYY'

/** The targets a column can be mapped to beside the fields of the appointment. */
export const CsvTarget = {
    DATE: '__date__',
    START_TIME: '__startTime__',
    END_TIME: '__endTime__',
    NAME: '__name__',
} as const

/**
 * One line of the file below the header.
 *
 * @param line  the line number in the file, counting the header as line one
 * @param cells the cells of the line, trimmed and without surrounding quotes
 */
export interface CsvLine {
    line: number
    cells: string[]
}

/** A file read into its header and its lines. */
export interface CsvTable {
    columns: string[]
    lines: CsvLine[]
}

/** Why a line of the file could not become an appointment: a value that cannot be read, or an end not after the start. */
export type CsvProblemReason = DraftProblemReason

/**
 * A line of the file that could not become an appointment.
 *
 * @param line   the line number in the file
 * @param reason which value of it could not be read
 * @param value  the value as it stands in the file
 */
export interface CsvProblem {
    line: number
    reason: CsvProblemReason
    value: string
}

/** What the lines of a file became: the appointments that could be read and the lines that could not. */
export interface CsvScheduleResult {
    rows: BatchRow[]
    problems: CsvProblem[]
}

/** The settings a file is turned into appointments with. */
export interface CsvScheduleOptions {
    mapping: Record<string, string>
    dateFormat: CsvDateFormat
    fallbackStartTime: string
    fallbackEndTime: string
}

const BYTE_ORDER_MARK = /^\uFEFF/
const START_OF_DAY = '00:00'
const END_OF_DAY = '23:59'

const DATE_NAMES = ['datum', 'date', 'tag']
const DATE_WORD = /(^|[^a-z])date([^a-z]|$)/
const START_NAMES = ['startzeit', 'start_time', 'starttime', 'von', 'beginn', 'start']
const END_NAMES = ['endzeit', 'end_time', 'endtime', 'bis', 'ende', 'end']
const NAME_NAMES = ['name', 'terminname', 'event_name', 'titel', 'title', 'bezeichnung']

const DOTTED = /^(\d{1,2})\.(\d{1,2})\.(\d{2}|\d{4})$/
const ISO = /^(\d{4})-(\d{1,2})-(\d{1,2})$/
const SLASHED = /^(\d{1,2})\/(\d{1,2})\/(\d{2}|\d{4})$/
const TIME = /^(\d{1,2})[:.](\d{2})(?::\d{2})?$/

function unquoted(cell: string): string {
    return cell.trim().replace(/^"|"$/g, '').trim()
}

/**
 * Reads the text of a file into its header and its lines.
 *
 * <p>Spreadsheet software writes a byte order mark in front of a UTF-8 export and often a space in
 * front of a header name; both are dropped so the column is found by its name. It also writes lines
 * of nothing but separators below the data, which are dropped like empty lines. Each line keeps its
 * number in the file, so a problem can be named by the line the reader sees in their editor.
 */
export function readCsvTable(text: string): CsvTable {
    const numbered = text.replace(BYTE_ORDER_MARK, '').split(/\r?\n/)
        .map((content, index) => ({content: content.trim(), line: index + 1}))
        .filter(entry => entry.content)
    const [header, ...rest] = numbered
    if (!header) return {columns: [], lines: []}
    const separator = header.content.includes(';') ? ';' : ','
    return {
        columns: header.content.split(separator).map(unquoted),
        lines: rest
            .map(entry => ({line: entry.line, cells: entry.content.split(separator).map(unquoted)}))
            .filter(entry => entry.cells.some(cell => cell)),
    }
}

function nameMatches(column: string, names: string[]): boolean {
    return names.includes(column.toLowerCase())
}

/** Whether a column name says it holds a date without being one of the plain names, such as "ImprDatum" or "Start Date". */
function namedLikeDate(column: string): boolean {
    const lower = column.toLowerCase()
    return lower.endsWith('datum') || DATE_WORD.test(lower)
}

function mapByExactName(column: string, mapping: Record<string, string>, fieldNames: string[]): string | undefined {
    if (nameMatches(column, DATE_NAMES) && !Object.values(mapping).includes(CsvTarget.DATE)) return CsvTarget.DATE
    if (nameMatches(column, START_NAMES)) return CsvTarget.START_TIME
    if (nameMatches(column, END_NAMES)) return CsvTarget.END_TIME
    if (nameMatches(column, NAME_NAMES)) return CsvTarget.NAME
    return fieldNames.find(name => name.toLowerCase() === column.toLowerCase())
}

function holdsOnlyDates(table: CsvTable, index: number): boolean {
    return table.lines.length > 0 && table.lines.every(line => parseCsvDate(line.cells[index] ?? '', 'auto') !== null)
}

/**
 * Which target each column is mapped to at first, before the reader corrects it.
 *
 * <p>Exact names come first: a plain date name, a time or title name, and a column named like a
 * field of the batch, so a field called "Abgabedatum" keeps its column. Only where no column took
 * the date does a name that merely says date, such as "ImprDatum", or failing that the first column
 * whose every value reads as a date, become the date. One column at most is the date, so an
 * export carrying a creation date beside the appointment date does not get both.
 */
export function guessColumnMapping(table: CsvTable, fieldNames: string[]): Record<string, string> {
    const mapping: Record<string, string> = {}
    for (const column of table.columns) {
        const target = mapByExactName(column, mapping, fieldNames)
        if (target) mapping[column] = target
    }
    if (!Object.values(mapping).includes(CsvTarget.DATE)) {
        const dateColumn = table.columns.find(column => !mapping[column] && namedLikeDate(column))
            ?? table.columns.find((column, index) => !mapping[column] && holdsOnlyDates(table, index))
        if (dateColumn) mapping[dateColumn] = CsvTarget.DATE
    }
    return mapping
}

/** The date format a sample from the date column is written in, or `auto` where none fits. */
export function detectedDateFormat(sample: string): CsvDateFormat {
    const value = sample.trim()
    if (DOTTED.test(value)) return 'DD.MM.YYYY'
    if (ISO.test(value)) return 'YYYY-MM-DD'
    if (SLASHED.test(value)) return 'MM/DD/YYYY'
    return 'auto'
}

function fullYear(year: string): number {
    return year.length === 2 ? 2000 + Number(year) : Number(year)
}

function isoDateOf(year: number, month: number, day: number): string | null {
    const date = new Date(Date.UTC(year, month - 1, day))
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return null
    return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

function readDotted(value: string): string | null {
    const match = DOTTED.exec(value)
    return match ? isoDateOf(fullYear(match[3]!), Number(match[2]), Number(match[1])) : null
}

function readIso(value: string): string | null {
    const match = ISO.exec(value)
    return match ? isoDateOf(Number(match[1]), Number(match[2]), Number(match[3])) : null
}

function readSlashed(value: string): string | null {
    const match = SLASHED.exec(value)
    return match ? isoDateOf(fullYear(match[3]!), Number(match[1]), Number(match[2])) : null
}

/**
 * A day from the file as `YYYY-MM-DD`, or `null` where it names no day of the calendar.
 *
 * <p>A two-digit year is read as a year of this century, as spreadsheet software writes it.
 */
export function parseCsvDate(raw: string, format: CsvDateFormat): string | null {
    const value = raw.trim()
    if (format === 'DD.MM.YYYY') return readDotted(value)
    if (format === 'YYYY-MM-DD') return readIso(value)
    if (format === 'MM/DD/YYYY') return readSlashed(value)
    return readDotted(value) ?? readIso(value) ?? readSlashed(value)
}

/** A time of day from the file as `HH:mm`, or `null` where no clock can show it. */
export function parseCsvTime(raw: string): string | null {
    const match = TIME.exec(raw.trim())
    if (!match) return null
    const hours = Number(match[1])
    const minutes = Number(match[2])
    if (hours > 23 || minutes > 59) return null
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`
}

function columnOf(mapping: Record<string, string>, columns: string[], target: string): number {
    const column = Object.entries(mapping).find(([, value]) => value === target)?.[0]
    return column === undefined ? -1 : columns.indexOf(column)
}

/** The values a line brings for the fields of the batch, by the columns mapped to them. */
function fieldValuesOf(table: CsvTable, mapping: Record<string, string>, cells: string[]): Record<string, string> {
    const fieldValues: Record<string, string> = {}
    for (const [column, target] of Object.entries(mapping)) {
        if (target.startsWith('__')) continue
        const index = table.columns.indexOf(column)
        if (index >= 0) fieldValues[target] = cells[index] ?? ''
    }
    return fieldValues
}

/** Which values of a line could not be read at all, each named for the value as it stands in the file. */
function unreadable(draft: DraftRow): CsvProblemReason[] {
    const reasons: CsvProblemReason[] = []
    if (!draft.date) reasons.push('date')
    if (!draft.startTime) reasons.push('startTime')
    if (!draft.endTime) reasons.push('endTime')
    return reasons
}

/**
 * Turns the lines of a file into appointments, each on the reader's own clock.
 *
 * <p>A line whose day or time cannot be read does not become an appointment: it is returned as a
 * problem naming the line and the value, so the reader can correct the file or the mapping. Such a
 * line used to be placed at the moment of the import, and the batch then created appointments
 * nobody had asked for. A line that ends at or before its start is refused by the rule every row of
 * a batch is judged by. A time column that is not mapped takes the fallback time instead, and an empty
 * cell of a mapped one the start or the end of the day.
 */
export function csvToBatchRows(table: CsvTable, options: CsvScheduleOptions): CsvScheduleResult {
    const {mapping, dateFormat, fallbackStartTime, fallbackEndTime} = options
    const dateIndex = columnOf(mapping, table.columns, CsvTarget.DATE)
    const startIndex = columnOf(mapping, table.columns, CsvTarget.START_TIME)
    const endIndex = columnOf(mapping, table.columns, CsvTarget.END_TIME)
    const nameIndex = columnOf(mapping, table.columns, CsvTarget.NAME)

    const rows: BatchRow[] = []
    const problems: CsvProblem[] = []
    for (const {line, cells} of table.lines) {
        const raw: Record<CsvProblemReason, string> = {
            date: dateIndex >= 0 ? cells[dateIndex] ?? '' : '',
            startTime: startIndex >= 0 ? cells[startIndex] || START_OF_DAY : fallbackStartTime,
            endTime: endIndex >= 0 ? cells[endIndex] || END_OF_DAY : fallbackEndTime,
            order: '',
        }
        raw.order = `${raw.startTime} - ${raw.endTime}`
        const draft: DraftRow = {
            date: parseCsvDate(raw.date, dateFormat) ?? '',
            startTime: parseCsvTime(raw.startTime) ?? '',
            endTime: parseCsvTime(raw.endTime) ?? '',
            name: nameIndex >= 0 ? cells[nameIndex] ?? '' : '',
            fieldValues: fieldValuesOf(table, mapping, cells),
        }
        const reasons = unreadable(draft)
        const lineProblems = reasons.length > 0 ? reasons : draftProblems(draft)
        if (lineProblems.length > 0) problems.push(...lineProblems.map(reason => ({line, reason, value: raw[reason]})))
        else rows.push(draftToBatchRow(draft))
    }
    return {rows, problems}
}

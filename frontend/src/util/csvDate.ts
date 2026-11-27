/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The orders a day can be written in, whatever joins its three numbers: dots, dashes or slashes, as
 * long as both are the same. `auto` leaves the order to {@link detectDateFormat} over the whole column;
 * a single value read with it tries day first, month first and year first in turn.
 *
 * <p>Day and month take one or two digits, the year two or four. A year in front takes two digits only
 * above 31, since `04.12.26` would otherwise read as a day in 2004 as readily as the 4th of December.
 * The member import reads its files on the server with the same orders and rules, so a file reads
 * alike wherever it is imported.
 */
export type CsvDateFormat = 'auto' | 'day-month-year' | 'month-day-year' | 'year-month-day'

/** Every order a picker offers beside `auto`, in the order a tie in {@link detectDateFormat} is settled by. */
export const CSV_DATE_FORMATS = ['day-month-year', 'month-day-year', 'year-month-day'] as const

/**
 * Which century a two-digit year falls in: `century` the current one, for appointments, which mostly
 * lie ahead; `past` the latest year not after the current one, for birth and join dates.
 */
export type TwoDigitYears = 'century' | 'past'

const THREE_PARTS = /^(\d{1,4})([./-])(\d{1,4})\2(\d{1,4})$/
const LAST_DAY = 31

/** Where day, month and year stand among the three numbers of each order. */
const PLACES: Readonly<Record<(typeof CSV_DATE_FORMATS)[number], readonly [number, number, number]>> = {
    'day-month-year': [0, 1, 2],
    'month-day-year': [1, 0, 2],
    'year-month-day': [2, 1, 0],
}

function fullYear(year: string, years: TwoDigitYears, currentYear: number): number {
    const value = Number(year)
    if (year.length === 4) return value
    const century = currentYear - (currentYear % 100)
    if (years === 'century') return century + value
    return century + value > currentYear ? century - 100 + value : century + value
}

function isoDateOf(year: number, month: number, day: number): string | null {
    const date = new Date(Date.UTC(year, month - 1, day))
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return null
    return `${String(year).padStart(4, '0')}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

function inOrder(
    parts: readonly string[],
    format: (typeof CSV_DATE_FORMATS)[number],
    years: TwoDigitYears,
    currentYear: number,
): string | null {
    const [dayAt, monthAt, yearAt] = PLACES[format]
    const day = parts[dayAt]!
    const month = parts[monthAt]!
    const year = parts[yearAt]!
    if (day.length > 2 || month.length > 2 || (year.length !== 2 && year.length !== 4)) return null
    if (format === 'year-month-day' && year.length === 2 && Number(year) <= LAST_DAY) return null
    return isoDateOf(fullYear(year, years, currentYear), Number(month), Number(day))
}

/**
 * A day from a file as `YYYY-MM-DD`, or `null` where it names no day of the calendar.
 *
 * @param raw         the value as it stands in the file
 * @param format      the order the day is written in
 * @param years       which century a two-digit year falls in
 * @param currentYear the year the centuries are counted from
 */
export function parseCsvDate(
    raw: string,
    format: CsvDateFormat,
    years: TwoDigitYears = 'century',
    currentYear: number = new Date().getFullYear(),
): string | null {
    const match = THREE_PARTS.exec(raw.trim())
    if (!match) return null
    const parts = [match[1]!, match[3]!, match[4]!]
    if (format !== 'auto') return inOrder(parts, format, years, currentYear)
    for (const order of CSV_DATE_FORMATS) {
        const day = inOrder(parts, order, years, currentYear)
        if (day) return day
    }
    return null
}

/**
 * The order a column of days is written in, worked out from all of its values.
 *
 * <p>Each order is tried on every value, and the first to read all of them is taken. A day never
 * passes 31 and a month never passes 12, so one value such as `03-16-60` is enough to distinguish month first
 * from day first. Where several orders read everything, day first wins, then month first, then year
 * first. Where none reads everything, the one reading the most is taken, and `auto` where none reads
 * anything.
 */
export function detectDateFormat(values: readonly string[]): CsvDateFormat {
    let best: CsvDateFormat = 'auto'
    let mostRead = 0
    for (const format of CSV_DATE_FORMATS) {
        const read = values.filter(value => parseCsvDate(value, format) !== null).length
        if (read > mostRead) {
            best = format
            mostRead = read
        }
    }
    return best
}

/**
 * How the days of one column are read, as `YYYY-MM-DD` or `null`.
 *
 * <p>A picked order reads every cell. `auto` reads with the order detected over the column, and a
 * cell that order cannot read is tried in every order on its own, so a column mixing `04.12.26` and
 * `2026-12-05` still reads both.
 */
export function columnDateReader(
    values: readonly string[],
    picked: CsvDateFormat,
    years: TwoDigitYears = 'century',
): (raw: string) => string | null {
    if (picked !== 'auto') return raw => parseCsvDate(raw, picked, years)
    const detected = detectDateFormat(values)
    return raw => parseCsvDate(raw, detected, years) ?? parseCsvDate(raw, 'auto', years)
}

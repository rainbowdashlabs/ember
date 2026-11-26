/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/**
 * The ways a day can be written in an imported file. `auto` leaves the format to
 * {@link detectDateFormat} over the whole column; a single value read with it tries the dotted, the
 * dashed, the ISO and the month-first slashed form in turn.
 *
 * <p>The member import reads its files on the server with the same formats and rules, so a file reads
 * alike wherever it is imported.
 */
export type CsvDateFormat = 'auto' | 'DD.MM.YYYY' | 'DD-MM-YYYY' | 'YYYY-MM-DD' | 'MM/DD/YYYY' | 'DD/MM/YYYY'

/** Every format a picker offers beside `auto`, in the order a tie in {@link detectDateFormat} is settled by. */
export const CSV_DATE_FORMATS = ['DD.MM.YYYY', 'DD-MM-YYYY', 'YYYY-MM-DD', 'MM/DD/YYYY', 'DD/MM/YYYY'] as const

/**
 * Which century a two-digit year falls in: `century` the current one, for appointments, which mostly
 * lie ahead; `past` the latest year not after the current one, for birth and join dates.
 */
export type TwoDigitYears = 'century' | 'past'

const DOTTED = /^(\d{1,2})\.(\d{1,2})\.(\d{2}|\d{4})$/
const DASHED = /^(\d{1,2})-(\d{1,2})-(\d{2}|\d{4})$/
const ISO = /^(\d{4})-(\d{1,2})-(\d{1,2})$/
const SLASHED = /^(\d{1,2})\/(\d{1,2})\/(\d{2}|\d{4})$/

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

/**
 * A day from a file as `YYYY-MM-DD`, or `null` where it names no day of the calendar.
 *
 * @param raw         the value as it stands in the file
 * @param format      how the day is written
 * @param years       which century a two-digit year falls in
 * @param currentYear the year the centuries are counted from
 */
export function parseCsvDate(
    raw: string,
    format: CsvDateFormat,
    years: TwoDigitYears = 'century',
    currentYear: number = new Date().getFullYear(),
): string | null {
    const value = raw.trim()
    const dayFirst = (pattern: RegExp) => {
        const match = pattern.exec(value)
        return match ? isoDateOf(fullYear(match[3]!, years, currentYear), Number(match[2]), Number(match[1])) : null
    }
    const monthFirst = () => {
        const match = SLASHED.exec(value)
        return match ? isoDateOf(fullYear(match[3]!, years, currentYear), Number(match[1]), Number(match[2])) : null
    }
    const yearFirst = () => {
        const match = ISO.exec(value)
        return match ? isoDateOf(Number(match[1]), Number(match[2]), Number(match[3])) : null
    }
    if (format === 'DD.MM.YYYY') return dayFirst(DOTTED)
    if (format === 'DD-MM-YYYY') return dayFirst(DASHED)
    if (format === 'YYYY-MM-DD') return yearFirst()
    if (format === 'MM/DD/YYYY') return monthFirst()
    if (format === 'DD/MM/YYYY') return dayFirst(SLASHED)
    return dayFirst(DOTTED) ?? dayFirst(DASHED) ?? yearFirst() ?? monthFirst()
}

/**
 * The format a column of days is written in, worked out from all of its values.
 *
 * <p>Each format is tried on every value, and the first to read all of them is taken. A day never
 * passes 31 and a month never passes 12, so one value such as `13/01/2024` is enough to tell day-first
 * from month-first slashes. Where several formats read everything, the usual one wins: dots and dashes
 * day first, then ISO, then slashes month first. Where none reads everything, the one reading the most
 * is taken, and `auto` where none reads anything.
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

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The calendar day a birth date names, as year, month and day.
 *
 * <p>Read out of the text rather than through a Date, because a date on its own is parsed as
 * midnight UTC while the day, month and year are then read back in whatever zone the browser keeps.
 * West of UTC those two disagree, and the birthday landed a day early: somebody born on the 14th
 * counted as a year older on the 13th. Anything not written as a plain date still goes through a
 * Date, where the parts are at least read in the same zone they were parsed in.
 */
function dayOf(birthIso: string): {year: number; month: number; day: number} | null {
    const written = /^(\d{4})-(\d{2})-(\d{2})/.exec(birthIso.trim())
    if (written) {
        return {year: Number(written[1]), month: Number(written[2]) - 1, day: Number(written[3])}
    }
    const parsed = new Date(birthIso)
    if (isNaN(parsed.getTime())) return null
    return {year: parsed.getFullYear(), month: parsed.getMonth(), day: parsed.getDate()}
}

/**
 * Age in whole years on the target day, or null where the birth date cannot be read.
 */
export function ageOn(birthIso: string, target: Date): number | null {
    if (!birthIso) return null
    const birth = dayOf(birthIso)
    if (!birth) return null
    let age = target.getFullYear() - birth.year
    const m = target.getMonth() - birth.month
    if (m < 0 || (m === 0 && target.getDate() < birth.day)) age--
    return age
}

/** December 31 of the year the given day falls in, which is what the calendar age is taken on. */
export function endOfYear(today: Date = new Date()): Date {
    return new Date(today.getFullYear(), 11, 31)
}

/**
 * The age as a display string: today's for mode 'now', the calendar age (December 31) for mode
 * 'end_of_year'. Empty where there is no readable birth date.
 */
export function computeAge(dateStr: string, mode: string): string {
    const target = mode === 'end_of_year' ? endOfYear() : new Date()
    const age = ageOn(dateStr, target)
    return age == null ? '' : String(age)
}

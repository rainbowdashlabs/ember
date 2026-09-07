/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Age in whole years on the target day, or null where the birth date cannot be read.
 */
export function ageOn(birthIso: string, target: Date): number | null {
    if (!birthIso) return null
    const birth = new Date(birthIso)
    if (isNaN(birth.getTime())) return null
    let age = target.getFullYear() - birth.getFullYear()
    const m = target.getMonth() - birth.getMonth()
    if (m < 0 || (m === 0 && target.getDate() < birth.getDate())) age--
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

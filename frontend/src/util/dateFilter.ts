/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ageOn, endOfYear} from '@/util/age'

/**
 * The token grammar a date column's filter set speaks.
 *
 * <p>Column filters everywhere are a {@code Set<string>}, and saved filters store that set as it
 * is. A date filter therefore encodes itself as tokens in the same set: plain ISO prefixes for the
 * checkmarks ({@code 2026}, {@code 2026-03}, {@code 2026-03-14}), {@code from:}/{@code before:}
 * for the range, and {@code age-*} bounds for birth dates. An old saved filter that stored full
 * dates from the former flat list is a set of day tokens and keeps matching unchanged.
 *
 * <p>Bounds follow one rule: lower bounds include their value, upper bounds exclude it. "From" a
 * day includes that day and "before" a day does not; an age of "at least 10" includes 10 and
 * "below 14" excludes 14.
 */
export interface DateFilterTokens {
    /** Checkmark tokens: a year, a month or a day as an ISO prefix. Any of them matches. */
    prefixes: string[]
    /** First day that matches, inclusive, or null without a lower bound. */
    from: string | null
    /** First day that no longer matches, exclusive, or null without an upper bound. */
    before: string | null
    ageNowMin: number | null
    ageNowBelow: number | null
    ageEoyMin: number | null
    ageEoyBelow: number | null
}

const PREFIX_PATTERN = /^\d{4}(-\d{2}){0,2}$/

function parseBound(token: string, name: string): string | null {
    return token.startsWith(name + ':') ? token.slice(name.length + 1) : null
}

function parseAge(token: string, name: string): number | null {
    if (!token.startsWith(name + ':')) return null
    const value = Number(token.slice(name.length + 1))
    return Number.isFinite(value) ? value : null
}

/** Reads a filter set into its date tokens. Unknown strings count as day-level checkmarks. */
export function splitDateTokens(selected: ReadonlySet<string>): DateFilterTokens {
    const tokens: DateFilterTokens = {
        prefixes: [],
        from: null,
        before: null,
        ageNowMin: null,
        ageNowBelow: null,
        ageEoyMin: null,
        ageEoyBelow: null,
    }
    for (const token of selected) {
        const from = parseBound(token, 'from')
        if (from !== null) { tokens.from = from; continue }
        const before = parseBound(token, 'before')
        if (before !== null) { tokens.before = before; continue }
        const ageNowMin = parseAge(token, 'age-now-min')
        if (ageNowMin !== null) { tokens.ageNowMin = ageNowMin; continue }
        const ageNowBelow = parseAge(token, 'age-now-below')
        if (ageNowBelow !== null) { tokens.ageNowBelow = ageNowBelow; continue }
        const ageEoyMin = parseAge(token, 'age-eoy-min')
        if (ageEoyMin !== null) { tokens.ageEoyMin = ageEoyMin; continue }
        const ageEoyBelow = parseAge(token, 'age-eoy-below')
        if (ageEoyBelow !== null) { tokens.ageEoyBelow = ageEoyBelow; continue }
        tokens.prefixes.push(token)
    }
    return tokens
}

/** Writes the tokens back into one set, the shape the filter state and saved filters keep. */
export function joinDateTokens(tokens: DateFilterTokens): Set<string> {
    const set = new Set<string>(tokens.prefixes)
    if (tokens.from) set.add('from:' + tokens.from)
    if (tokens.before) set.add('before:' + tokens.before)
    if (tokens.ageNowMin !== null) set.add('age-now-min:' + tokens.ageNowMin)
    if (tokens.ageNowBelow !== null) set.add('age-now-below:' + tokens.ageNowBelow)
    if (tokens.ageEoyMin !== null) set.add('age-eoy-min:' + tokens.ageEoyMin)
    if (tokens.ageEoyBelow !== null) set.add('age-eoy-below:' + tokens.ageEoyBelow)
    return set
}

/** The day part of whatever a date column stores, so a timestamp filters like its day. */
function dayOf(value: string): string {
    return value.slice(0, 10)
}

function underPrefix(day: string, prefix: string): boolean {
    return day === prefix || day.startsWith(prefix + '-')
}

/**
 * Whether one date value passes the filter. Checkmarks are OR-ed; the range and the ages are
 * constraints AND-ed on top, and with no checkmarks at all they alone decide.
 */
export function matchesDateFilter(value: string, tokens: DateFilterTokens, today: Date = new Date()): boolean {
    const day = dayOf(value)
    if (tokens.prefixes.length > 0 && !tokens.prefixes.some(p => underPrefix(day, p))) return false
    if (tokens.from && day < tokens.from) return false
    if (tokens.before && day >= tokens.before) return false
    if (tokens.ageNowMin !== null || tokens.ageNowBelow !== null) {
        const age = ageOn(day, today)
        if (age === null) return false
        if (tokens.ageNowMin !== null && age < tokens.ageNowMin) return false
        if (tokens.ageNowBelow !== null && age >= tokens.ageNowBelow) return false
    }
    if (tokens.ageEoyMin !== null || tokens.ageEoyBelow !== null) {
        const age = ageOn(day, endOfYear(today))
        if (age === null) return false
        if (tokens.ageEoyMin !== null && age < tokens.ageEoyMin) return false
        if (tokens.ageEoyBelow !== null && age >= tokens.ageEoyBelow) return false
    }
    return true
}

// -- The year/month/day tree the filter modal renders --

export interface DateTreeMonth {
    /** The month as its own token, e.g. {@code 2026-03}. */
    month: string
    /** The days as their own tokens, ascending. */
    days: string[]
}

export interface DateTreeYear {
    /** The year as its own token, e.g. {@code 2026}. */
    year: string
    months: DateTreeMonth[]
}

/** Folds a column's distinct values into the tree, years ascending. Unreadable values drop out. */
export function buildDateTree(values: readonly string[]): DateTreeYear[] {
    const days = [...new Set(values.map(dayOf).filter(v => PREFIX_PATTERN.test(v)))].sort()
    const years: DateTreeYear[] = []
    for (const day of days) {
        const yearToken = day.slice(0, 4)
        const monthToken = day.slice(0, 7)
        let year = years[years.length - 1]
        if (!year || year.year !== yearToken) {
            year = {year: yearToken, months: []}
            years.push(year)
        }
        let month = year.months[year.months.length - 1]
        if (!month || month.month !== monthToken) {
            month = {month: monthToken, days: []}
            year.months.push(month)
        }
        month.days.push(day)
    }
    return years
}

/** Checked: the token itself is selected, or a whole containing it is. */
export function isTreeChecked(prefixes: readonly string[], token: string): boolean {
    return prefixes.some(p => underPrefix(token, p))
}

/** Indeterminate: not checked itself, but something inside it is. */
export function isTreeIndeterminate(prefixes: readonly string[], token: string): boolean {
    return !isTreeChecked(prefixes, token) && prefixes.some(p => underPrefix(p, token) && p !== token)
}

function childrenOf(tree: readonly DateTreeYear[], token: string): string[] {
    if (token.length === 4) {
        return tree.find(y => y.year === token)?.months.map(m => m.month) ?? []
    }
    if (token.length === 7) {
        for (const year of tree) {
            const month = year.months.find(m => m.month === token)
            if (month) return month.days
        }
    }
    return []
}

function parentOf(token: string): string | null {
    return token.length > 4 ? token.slice(0, token.lastIndexOf('-')) : null
}

/**
 * Flips one tree row and returns the minimal token list saying the same thing.
 *
 * <p>Checking a row stores that row's own token and drops whatever it contains; where that
 * completes a parent's children, the children fold into the parent, so a fully ticked year is one
 * token and not twelve. Unchecking a row covered by an ancestor unfolds the ancestor into the
 * siblings along the path first, so everything else it covered stays selected.
 */
export function toggleTreeToken(
    prefixes: readonly string[],
    token: string,
    tree: readonly DateTreeYear[],
): string[] {
    let result = [...prefixes]
    if (isTreeChecked(result, token)) {
        while (!result.includes(token)) {
            const ancestor = result.find(p => underPrefix(token, p) && p !== token)
            if (!ancestor) break
            result = result.filter(p => p !== ancestor)
            const path = token.slice(0, ancestor.length + 3)
            result.push(...childrenOf(tree, ancestor).filter(c => c !== path), path)
        }
        result = result.filter(p => p !== token)
    } else {
        result = result.filter(p => !underPrefix(p, token))
        result.push(token)
        let current = token
        for (let parent = parentOf(current); parent; parent = parentOf(current)) {
            const children = childrenOf(tree, parent)
            if (children.length === 0 || !children.every(c => result.includes(c))) break
            result = result.filter(p => !children.includes(p))
            result.push(parent)
            current = parent
        }
    }
    return result.sort()
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {
    buildDateTree,
    isTreeChecked,
    isTreeIndeterminate,
    joinDateTokens,
    matchesDateFilter,
    splitDateTokens,
    toggleTreeToken,
} from './dateFilter'

const TODAY = new Date(2026, 8, 7) // 2026-09-07

function tokensOf(...entries: string[]) {
    return splitDateTokens(new Set(entries))
}

describe('splitDateTokens / joinDateTokens', () => {
    it('reads every token kind and survives the round trip', () => {
        const set = new Set([
            '2026', '2025-03', '2024-12-31',
            'from:2024-01-01', 'before:2026-07-01',
            'age-now-min:10', 'age-now-below:14', 'age-eoy-min:6', 'age-eoy-below:18',
        ])
        const tokens = splitDateTokens(set)
        expect(tokens.prefixes.sort()).toEqual(['2024-12-31', '2025-03', '2026'])
        expect(tokens.from).toBe('2024-01-01')
        expect(tokens.before).toBe('2026-07-01')
        expect(tokens.ageNowMin).toBe(10)
        expect(tokens.ageNowBelow).toBe(14)
        expect(tokens.ageEoyMin).toBe(6)
        expect(tokens.ageEoyBelow).toBe(18)
        expect(joinDateTokens(tokens)).toEqual(set)
    })

    it('treats an old flat filter as plain day checkmarks', () => {
        const tokens = tokensOf('2024-05-01', '2024-06-01')
        expect(tokens.prefixes).toHaveLength(2)
        expect(tokens.from).toBeNull()
    })
})

describe('matchesDateFilter', () => {
    it('OR-s the checkmark prefixes over year, month and day', () => {
        const tokens = tokensOf('2024', '2025-03', '2026-01-05')
        expect(matchesDateFilter('2024-11-30', tokens, TODAY)).toBe(true)
        expect(matchesDateFilter('2025-03-14', tokens, TODAY)).toBe(true)
        expect(matchesDateFilter('2026-01-05', tokens, TODAY)).toBe(true)
        expect(matchesDateFilter('2025-04-01', tokens, TODAY)).toBe(false)
        expect(matchesDateFilter('2026-01-06', tokens, TODAY)).toBe(false)
    })

    it('does not let a year prefix swallow a longer year', () => {
        expect(matchesDateFilter('20261-01-01', tokensOf('2026'), TODAY)).toBe(false)
    })

    it('filters a timestamp by its day', () => {
        expect(matchesDateFilter('2025-03-14T18:30:00Z', tokensOf('2025-03'), TODAY)).toBe(true)
    })

    it('takes from inclusive and before exclusive', () => {
        const tokens = tokensOf('from:2025-01-01', 'before:2025-02-01')
        expect(matchesDateFilter('2025-01-01', tokens, TODAY)).toBe(true)
        expect(matchesDateFilter('2025-01-31', tokens, TODAY)).toBe(true)
        expect(matchesDateFilter('2025-02-01', tokens, TODAY)).toBe(false)
        expect(matchesDateFilter('2024-12-31', tokens, TODAY)).toBe(false)
    })

    it('AND-s the range with the checkmarks', () => {
        const tokens = tokensOf('2025', 'before:2025-06-01')
        expect(matchesDateFilter('2025-05-31', tokens, TODAY)).toBe(true)
        expect(matchesDateFilter('2025-06-01', tokens, TODAY)).toBe(false)
    })

    it('bounds the current age with an inclusive floor and an exclusive ceiling', () => {
        const tokens = tokensOf('age-now-min:10', 'age-now-below:14')
        expect(matchesDateFilter('2016-09-07', tokens, TODAY)).toBe(true)  // turns 10 today
        expect(matchesDateFilter('2016-09-08', tokens, TODAY)).toBe(false) // still 9
        expect(matchesDateFilter('2012-09-08', tokens, TODAY)).toBe(true)  // still 13
        expect(matchesDateFilter('2012-09-07', tokens, TODAY)).toBe(false) // turned 14 today
    })

    it('takes the calendar age on December 31', () => {
        const tokens = tokensOf('age-eoy-min:10')
        expect(matchesDateFilter('2016-12-31', tokens, TODAY)).toBe(true)  // 10 at year end
        expect(matchesDateFilter('2017-01-01', tokens, TODAY)).toBe(false) // 9 at year end
    })

    it('rejects an unreadable date where an age bound stands', () => {
        expect(matchesDateFilter('soon', tokensOf('age-now-min:1'), TODAY)).toBe(false)
    })
})

describe('buildDateTree', () => {
    it('folds days into months into years, ascending', () => {
        const tree = buildDateTree(['2025-03-14', '2024-01-01', '2025-03-02', '2025-07-01'])
        expect(tree.map(y => y.year)).toEqual(['2024', '2025'])
        expect(tree[1]!.months.map(m => m.month)).toEqual(['2025-03', '2025-07'])
        expect(tree[1]!.months[0]!.days).toEqual(['2025-03-02', '2025-03-14'])
    })

    it('drops what does not read as a date and deduplicates', () => {
        const tree = buildDateTree(['2024-01-01', '2024-01-01T10:00:00Z', 'soon'])
        expect(tree).toHaveLength(1)
        expect(tree[0]!.months[0]!.days).toEqual(['2024-01-01'])
    })
})

describe('tree check states and toggling', () => {
    const tree = buildDateTree(['2025-03-02', '2025-03-14', '2025-07-01', '2026-01-05'])

    it('marks ancestors indeterminate and descendants checked', () => {
        const prefixes = ['2025-03']
        expect(isTreeChecked(prefixes, '2025-03-02')).toBe(true)
        expect(isTreeChecked(prefixes, '2025-03')).toBe(true)
        expect(isTreeChecked(prefixes, '2025')).toBe(false)
        expect(isTreeIndeterminate(prefixes, '2025')).toBe(true)
        expect(isTreeIndeterminate(prefixes, '2026')).toBe(false)
    })

    it('checking a year stores one token', () => {
        expect(toggleTreeToken([], '2025', tree)).toEqual(['2025'])
    })

    it('unchecking a day under a checked year unfolds into the siblings', () => {
        expect(toggleTreeToken(['2025'], '2025-03-14', tree))
            .toEqual(['2025-03-02', '2025-07'])
    })

    it('completing all children folds them into the parent', () => {
        expect(toggleTreeToken(['2025-03-02'], '2025-03-14', tree)).toEqual(['2025-03'])
        expect(toggleTreeToken(['2025-03'], '2025-07-01', tree)).toEqual(['2025'])
    })

    it('checking an indeterminate parent checks it whole', () => {
        expect(toggleTreeToken(['2025-03'], '2025', tree)).toEqual(['2025'])
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {byMemberName, compareMemberNames} from './memberOrder'

describe('compareMemberNames', () => {
    it('orders by the first name, not the surname', () => {
        const names = ['Zoe Abel', 'Anna Zimmer', 'Ben Müller']
        expect(names.toSorted(compareMemberNames)).toEqual(['Anna Zimmer', 'Ben Müller', 'Zoe Abel'])
    })

    it('reaches the surname only where the first name is shared', () => {
        const names = ['Anna Zimmer', 'Anna Abel']
        expect(names.toSorted(compareMemberNames)).toEqual(['Anna Abel', 'Anna Zimmer'])
    })

    it('ignores case and accents', () => {
        expect(compareMemberNames('anna', 'Anna')).toBe(0)
        expect(compareMemberNames('Müller', 'Muller')).toBe(0)
    })

    it('sorts an umlaut where a German reader looks for it', () => {
        const names = ['Zoe', 'Änne', 'Bea']
        expect(names.toSorted(compareMemberNames)).toEqual(['Änne', 'Bea', 'Zoe'])
    })

    it('puts somebody with no name last', () => {
        const names = ['Ben', '', 'Anna', '   ']
        expect(names.toSorted(compareMemberNames)).toEqual(['Anna', 'Ben', '', '   '])
    })
})

describe('byMemberName', () => {
    it('orders rows by whatever name they carry', () => {
        const rows = [{label: 'Zoe'}, {label: 'Anna'}]
        expect(rows.toSorted(byMemberName(row => row.label))).toEqual([{label: 'Anna'}, {label: 'Zoe'}])
    })
})

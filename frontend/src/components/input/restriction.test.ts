/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {emptyRestriction, namesSomebody, normalizedRestriction, sameRestriction} from './restriction'

/** Selections are compared by whom they name and how they combine, never by the order of their parts. */
describe('restriction', () => {
    it('names somebody by any part', () => {
        expect(namesSomebody(emptyRestriction())).toBe(false)
        expect(namesSomebody({...emptyRestriction(), memberIds: [4]})).toBe(true)
        expect(namesSomebody({...emptyRestriction(), userTypes: ['TEAM']})).toBe(true)
    })

    it('reads a missing selection as one naming nobody, sorted', () => {
        expect(normalizedRestriction(null)).toEqual({userTypes: [], groupIds: [], tagIds: [], memberIds: []})
        expect(normalizedRestriction({groupIds: [3, 1]}).groupIds).toEqual([1, 3])
    })

    it('takes two selections naming the same people in another order as the same', () => {
        const a = {...emptyRestriction(), groupIds: [1, 2], memberIds: [9]}
        const b = {...emptyRestriction(), groupIds: [2, 1], memberIds: [9]}

        expect(sameRestriction(a, b)).toBe(true)
        expect(sameRestriction(a, {...b, mode: 'OR'})).toBe(false)
        expect(sameRestriction(a, {...b, memberIds: []})).toBe(false)
    })
})

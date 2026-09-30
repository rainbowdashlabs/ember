/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {MemberGroup} from '@/api/types'
import {admits, chooseInSet, groupOfSet, groupsOutsideSets, setsWithGroups, toggled} from './groupRules'

const beginners: MemberGroup = {id: 1, name: 'Anfänger', groupSetId: 10, userTypes: ['MEMBER']}
const advanced: MemberGroup = {id: 2, name: 'Fortgeschritten', groupSetId: 10}
const trainers: MemberGroup = {id: 3, name: 'Ausbilder', groupSetId: null, userTypes: ['TEAM', 'MANAGER']}
const swimmers: MemberGroup = {id: 4, name: 'Schwimmer'}
const groups = [beginners, advanced, trainers, swimmers]
const levels = {id: 10, stationId: 's', name: 'Stufen'}
const empty = {id: 11, stationId: 's', name: 'Leer'}

/** Which groups a member can be in, and what choosing one of a set does to the others. */
describe('groupRules', () => {
    it('lets a group bound to no type take everybody and a bound one only its types', () => {
        expect(admits(swimmers, 'GUARDIAN')).toBe(true)
        expect(admits(trainers, 'TEAM')).toBe(true)
        expect(admits(trainers, 'MEMBER')).toBe(false)
    })

    it('offers the groups in no set one by one and every set with groups as one choice', () => {
        expect(groupsOutsideSets(groups)).toEqual([trainers, swimmers])
        expect(setsWithGroups(groups, [levels, empty])).toEqual([{set: levels, groups: [beginners, advanced]}])
    })

    it('moves a member within a set and takes them out of it on none', () => {
        const entry = {set: levels, groups: [beginners, advanced]}
        expect([...chooseInSet(new Set([1, 4]), entry, 2)].sort()).toEqual([2, 4])
        expect([...chooseInSet(new Set([1, 4]), entry, null)]).toEqual([4])
    })

    it('joins and leaves a group outside a set', () => {
        expect([...toggled(new Set([4]), 3)].sort()).toEqual([3, 4])
        expect([...toggled(new Set([3, 4]), 3)]).toEqual([4])
    })

    it('finds the other group of a set a member is in', () => {
        expect(groupOfSet([1, 4], groups, 10, 2)).toEqual(beginners)
        expect(groupOfSet([1, 4], groups, 10, 1)).toBeUndefined()
        expect(groupOfSet([1], groups, null)).toBeUndefined()
    })
})

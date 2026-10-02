/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {StationUserType} from '@/api/generated/schema'
import {canHaveGuardians, looksAfterMembers, relationsTabLabel} from './relationSides'

/**
 * Which side of the guardian link a member is on, which decides what their relations tab shows and
 * what it is called.
 */
describe('relationSides', () => {
    const t = (key: string) => key

    it('gives guardians to members and trial members only', () => {
        expect(canHaveGuardians(StationUserType.MEMBER)).toBe(true)
        expect(canHaveGuardians(StationUserType.TRIAL)).toBe(true)
        expect(canHaveGuardians(StationUserType.GUARDIAN)).toBe(false)
        expect(canHaveGuardians(StationUserType.TEAM)).toBe(false)
        expect(canHaveGuardians(StationUserType.MANAGER)).toBe(false)
    })

    it('puts guardians, team members and managers on the side that looks after others', () => {
        for (const type of [StationUserType.GUARDIAN, StationUserType.TEAM, StationUserType.MANAGER]) {
            expect(looksAfterMembers(type)).toBe(true)
            expect(relationsTabLabel(t, type)).toBe('memberDetail.tabManagedMembers')
        }
    })

    it('puts members and trial members on the side that is looked after', () => {
        for (const type of [StationUserType.MEMBER, StationUserType.TRIAL]) {
            expect(looksAfterMembers(type)).toBe(false)
            expect(relationsTabLabel(t, type)).toBe('memberDetail.tabGuardians')
        }
    })

    it('puts anybody with members in their care on the side that looks after others', () => {
        expect(looksAfterMembers(StationUserType.MEMBER, 1)).toBe(true)
        expect(relationsTabLabel(t, StationUserType.MEMBER, 1)).toBe('memberDetail.tabManagedMembers')
    })

    it('says nothing about a member whose type is not known yet', () => {
        expect(canHaveGuardians('')).toBe(false)
        expect(looksAfterMembers('')).toBe(false)
    })
})

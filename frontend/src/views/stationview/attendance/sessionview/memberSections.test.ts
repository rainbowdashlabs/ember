/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {buildMemberSections, type MemberSectionSources} from './memberSections'
import type {AttendanceEntry, MemberWithName} from '@/api/generated/schema'
import type {MemberGroup, StationUserTypeName} from '@/api/types'
import {createMember} from '@/test/mocks/factories'

/**
 * The blocks a sheet is read in follow whom it expects: its groups, then its user types, then
 * everybody else on it. A sheet started for other people than its template's shows those people.
 */
function member(id: number, name: string, userType: StationUserTypeName, formerAt: string | null = null): MemberWithName {
    return createMember({id, stationId: 's', accountId: id, name, userType, formerAt})
}

function entry(memberId: number): AttendanceEntry {
    return {id: memberId, sessionId: 1, memberId, status: 'UNCONFIRMED', source: 'EXPECTED', checkIn: null, checkOut: null}
}

const YOUTH: MemberGroup = {id: 1, stationId: 's', name: 'Jugend', position: 0}
const ADULTS: MemberGroup = {id: 2, stationId: 's', name: 'Aktive', position: 1}
const JANA = member(1, 'Jana', 'MEMBER')
const TOM = member(2, 'Tom', 'TEAM')
const ANNA = member(3, 'Anna', 'TEAM')
const LEFT = member(4, 'Lea', 'TEAM', '2026-01-01')
const GUEST = member(5, 'Gast', 'GUARDIAN')
const MANAGER = member(6, 'Mara', 'MANAGER')

function sources(overrides: Partial<MemberSectionSources>): MemberSectionSources {
    return {
        audience: {userTypes: [], groupIds: []},
        groups: [YOUTH, ADULTS],
        groupMembers: new Map([[1, [JANA, TOM]], [2, [MANAGER]]]),
        allMembers: [JANA, TOM, ANNA, LEFT, GUEST, MANAGER],
        entries: [],
        locale: 'de',
        ...overrides,
    }
}

describe('buildMemberSections', () => {
    it('lists the groups, then whoever of a type no group took, then everybody else', () => {
        const sections = buildMemberSections(sources({
            audience: {userTypes: ['TEAM'], groupIds: [1]},
            entries: [entry(1), entry(3), entry(5)],
        }))

        expect(sections.map(section => section.title)).toEqual(['Jugend', 'Team', null])
        expect(sections[0]!.members.map(m => m.id)).toEqual([JANA.id, TOM.id])
        expect(sections[1]!.members.map(m => m.id)).toEqual([ANNA.id])
        expect(sections[2]!.members.map(m => m.id)).toEqual([GUEST.id])
    })

    it('leaves out the groups the sheet does not expect', () => {
        const sections = buildMemberSections(sources({
            audience: {userTypes: ['TEAM'], groupIds: []},
            entries: [entry(2)],
        }))

        expect(sections.map(section => section.key)).toEqual(['type-TEAM'])
        expect(sections[0]!.members.map(m => m.id)).toEqual([ANNA.id, TOM.id])
    })

    it('keeps the groups in the order the sheet names them', () => {
        const sections = buildMemberSections(sources({audience: {userTypes: [], groupIds: [2, 1]}}))

        expect(sections.map(section => section.title)).toEqual(['Aktive', 'Jugend'])
    })

    it('lists somebody who left only where they are on the sheet', () => {
        const without = buildMemberSections(sources({audience: {userTypes: ['TEAM'], groupIds: []}}))
        const withEntry = buildMemberSections(sources({
            audience: {userTypes: ['TEAM'], groupIds: []},
            entries: [entry(4)],
        }))

        expect(without[0]!.members.map(m => m.id)).not.toContain(LEFT.id)
        expect(withEntry[0]!.members.map(m => m.id)).toContain(LEFT.id)
    })

    it('shows nothing for a sheet that expects nobody and has nobody on it', () => {
        expect(buildMemberSections(sources({}))).toEqual([])
    })
})

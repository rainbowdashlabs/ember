/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {keepSetsInTheirOrder, keepSetsTogether, regroupForms, reorderedSets} from './profileFields'
import type {ProfileFieldAssignment} from '@/api/generated/schema'
import {setHeadingBefore, type SetField} from '@/components/profilefields/fieldLayout'

const ADDRESS = {id: 1, name: 'Adresse', heading: true}

const ADDRESS_FIELDS = [
    {id: 10, fieldSetId: 1, setPosition: 0},
    {id: 11, fieldSetId: 1, setPosition: 1},
    {id: 12, fieldSetId: 1, setPosition: 2},
    {id: 5},
    {id: 6},
]

function asked(fieldId: number, role: 'MEMBER' | 'TEAM', position: number): ProfileFieldAssignment {
    return {
        id: fieldId * 10 + position, fieldId, targetKind: 'ROLE', role, groupId: null, position,
        widthOverride: null, readonlyOverride: null, requiredOverride: null,
    }
}

/** One role's form out of the assignments, in the order it stands. */
function positionsOf(assignments: ProfileFieldAssignment[], role: 'MEMBER' | 'TEAM'): number[] {
    return assignments.filter(a => a.role === role).sort((a, b) => a.position - b.position).map(a => a.fieldId)
}

/**
 * A group stands together on a form, in the order its fields were dragged into, which becomes the group's
 * own order on every other form, and shows its name once above its first field where it is a heading.
 */
describe('profile field groups', () => {
    it('keeps a field dragged within its group where it was dropped', () => {
        expect(keepSetsTogether([5, 12, 10, 11, 6], ADDRESS_FIELDS)).toEqual([5, 12, 10, 11, 6])
        expect(reorderedSets([5, 12, 10, 11, 6], ADDRESS_FIELDS)).toEqual(new Map([[1, [12, 10, 11]]]))
    })

    it('leaves a group dragged as a whole in its own order', () => {
        expect(keepSetsTogether([10, 11, 12, 5], ADDRESS_FIELDS)).toEqual([10, 11, 12, 5])
        expect(reorderedSets([10, 11, 12, 5], ADDRESS_FIELDS).size).toBe(0)
    })

    it('pulls a group together where its first field stands, in the order its fields arrived', () => {
        expect(keepSetsTogether([5, 11, 6, 10, 12], ADDRESS_FIELDS)).toEqual([5, 11, 10, 12, 6])
        expect(reorderedSets([5, 11, 6, 10, 12], ADDRESS_FIELDS)).toEqual(new Map([[1, [11, 10, 12]]]))
    })

    it('keeps the places of group fields a form does not ask', () => {
        expect(reorderedSets([12, 10], ADDRESS_FIELDS)).toEqual(new Map([[1, [12, 11, 10]]]))
    })

    it('puts every other form back in the group order', () => {
        const moved = ADDRESS_FIELDS.map(f => f.id === 12 ? {...f, setPosition: -1} : f)
        expect(keepSetsInTheirOrder([5, 10, 11, 12], moved)).toEqual([5, 12, 10, 11])

        const forms = regroupForms([asked(10, 'MEMBER', 0), asked(11, 'MEMBER', 1), asked(12, 'MEMBER', 2),
            asked(12, 'TEAM', 0), asked(5, 'TEAM', 1)], moved)
        expect(positionsOf(forms, 'MEMBER')).toEqual([12, 10, 11])
        expect(positionsOf(forms, 'TEAM')).toEqual([12, 5])
    })

    it('puts the group name above the first field of the group only', () => {
        const form: SetField[] = [
            {origin: 'STATION', fieldSet: null},
            {origin: 'STATION', fieldSet: ADDRESS},
            {origin: 'STATION', fieldSet: ADDRESS},
        ]

        expect(setHeadingBefore(form, 0)).toBeNull()
        expect(setHeadingBefore(form, 1)).toBe('Adresse')
        expect(setHeadingBefore(form, 2)).toBeNull()
    })

    it('shows no name where the group is not a heading, and tells the owners apart', () => {
        expect(setHeadingBefore([{fieldSet: {...ADDRESS, heading: false}}], 0)).toBeNull()
        expect(setHeadingBefore([
            {origin: 'STATION', fieldSet: ADDRESS},
            {origin: 'CLUSTER', fieldSet: ADDRESS},
        ], 1)).toBe('Adresse')
    })
})

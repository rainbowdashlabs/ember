/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {keepSetsTogether} from './profileFields'
import {setHeadingBefore} from '@/components/profilefields/fieldLayout'

const ADDRESS = {id: 1, name: 'Adresse', heading: true}

/**
 * A group stands together on a form, in its own order, and shows its name once above its first field
 * where it is a heading.
 */
describe('profile field groups', () => {
    it('pulls a group together where its first field stands, in the group order', () => {
        const fields = [
            {id: 10, fieldSetId: 1, setPosition: 0},
            {id: 11, fieldSetId: 1, setPosition: 1},
            {id: 5},
            {id: 6},
        ]

        expect(keepSetsTogether([5, 11, 6, 10], fields)).toEqual([5, 10, 11, 6])
    })

    it('puts the group name above the first field of the group only', () => {
        const form = [
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

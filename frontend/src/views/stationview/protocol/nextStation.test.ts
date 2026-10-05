/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {TestProtocolSection} from '@/api/generated/schema'
import {nextStation} from './nextStation'

function station(id: number): TestProtocolSection {
    return {
        id,
        protocolId: 1,
        parentId: null,
        name: `Station ${id}`,
        description: '',
        maxPoints: null,
        passThreshold: null,
        position: id,
    }
}

const stations = [station(1), station(2), station(3)]

describe('nextStation', () => {
    it('sends the member on to the next station of the sheet', () => {
        expect(nextStation(stations, new Set([1]), 1)?.id).toBe(2)
    })

    it('passes over stations already checked', () => {
        expect(nextStation(stations, new Set([1, 2]), 1)?.id).toBe(3)
    })

    it('starts over at the first station after the last', () => {
        expect(nextStation(stations, new Set([3]), 3)?.id).toBe(1)
    })

    it('starts over past the first station when that one is checked too', () => {
        expect(nextStation(stations, new Set([1, 3]), 3)?.id).toBe(2)
    })

    it('has nowhere to send the member once every station is checked', () => {
        expect(nextStation(stations, new Set([1, 2, 3]), 2)).toBeNull()
    })
})

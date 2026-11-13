/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {EventGroupPartState, EventGroupSet} from '@/api/generated/schema'
import {asksForAll, choiceMeetsRules, evaluateChoice, isMet, missingOf} from './eventGroupChoice'

const PRACTICE: EventGroupSet = {id: 1, eventGroupId: 9, name: 'Übung', minimum: 2, position: 0}
const TEST: EventGroupSet = {id: 2, eventGroupId: 9, name: 'Prüfung', minimum: 1, position: 1}

function part(eventId: number, setId: number, calledOff = false): EventGroupPartState {
    return {eventId, setId, name: `Tag ${eventId}`, startTime: '2027-03-06T08:00:00Z', endTime: '2027-03-06T16:00:00Z', calledOff, tookPlace: false}
}

const PARTS = [part(11, 1), part(12, 1), part(13, 1), part(21, 2)]

describe('evaluateChoice', () => {
    it('meets the rules with two practice days and the test', () => {
        expect(choiceMeetsRules([PRACTICE, TEST], PARTS, new Set([11, 13, 21]))).toBe(true)
    })

    it('names the set that is short and by how much', () => {
        const results = evaluateChoice([PRACTICE, TEST], PARTS, new Set([11, 21]))
        expect(isMet(results[0]!)).toBe(false)
        expect(missingOf(results[0]!)).toBe(1)
        expect(isMet(results[1]!)).toBe(true)
    })

    it('stops asking for a called-off part nobody chose, and keeps counting one that was chosen', () => {
        const calledOff = [part(11, 1), part(12, 1, true), part(13, 1, true), part(21, 2)]
        expect(choiceMeetsRules([PRACTICE, TEST], calledOff, new Set([11, 21]))).toBe(true)
        expect(choiceMeetsRules([PRACTICE, TEST], calledOff, new Set([11, 12, 21]))).toBe(true)
    })

    it('refuses an empty choice', () => {
        expect(choiceMeetsRules([{...PRACTICE, minimum: 0}], PARTS, new Set())).toBe(false)
    })

    it('reads a set asking for all its parts as mandatory', () => {
        expect(asksForAll(TEST, PARTS)).toBe(true)
        expect(asksForAll(PRACTICE, PARTS)).toBe(false)
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {TimeRule} from '@/api/generated/schema'
import {draftOf, emptyDraft, inputOf, minutesFit, namesSomebody, savable} from './timeRuleDraft'

const RULE: TimeRule = {
    id: 3,
    position: 0,
    label: 'Jugendwart',
    preparationMinutes: 30,
    followUpMinutes: 15,
    audience: {userTypes: [], groupIds: [7], tagIds: [], memberIds: [], mode: 'AND'},
    intact: true,
}

/** How a rule travels between the server and the editor, and what the editor lets through. */
describe('timeRuleDraft', () => {
    it('reads a rule and writes it back as it was', () => {
        const draft = draftOf(RULE)

        expect(inputOf(draft)).toEqual({
            label: 'Jugendwart',
            preparationMinutes: 30,
            followUpMinutes: 15,
            audience: RULE.audience,
        })
    })

    it('gives every draft a key of its own', () => {
        expect(draftOf(RULE).key).not.toBe(draftOf(RULE).key)
    })

    it('writes a blank label as no label', () => {
        const draft = {...draftOf(RULE), label: '   '}

        expect(inputOf(draft).label).toBeNull()
    })

    it('does not share its audience with the rule it was read from', () => {
        const draft = draftOf(RULE)
        draft.audience.groupIds.push(9)

        expect(RULE.audience.groupIds).toEqual([7])
    })

    it('holds back a rule naming nobody', () => {
        const fresh = emptyDraft()

        expect(namesSomebody(fresh)).toBe(false)
        expect(savable([draftOf(RULE), fresh])).toBe(false)
        expect(savable([draftOf(RULE)])).toBe(true)
    })

    it('keeps minutes within a day and whole', () => {
        expect(minutesFit(0)).toBe(true)
        expect(minutesFit(1440)).toBe(true)
        expect(minutesFit(1441)).toBe(false)
        expect(minutesFit(-1)).toBe(false)
        expect(minutesFit(1.5)).toBe(false)
        expect(minutesFit(undefined)).toBe(false)
    })
})

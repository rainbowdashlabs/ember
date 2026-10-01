/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {fieldValueBody, fieldValueOfText, fieldValueText, isBoardFieldType} from './boards'

/**
 * What a ticket's field holds, read out of and written back into the answer box every field shares.
 */
describe('board field values', () => {
    it('keeps a zero, which a number field used to throw away', () => {
        expect(fieldValueOfText('NUMBER', '0')).toBe(0)
        expect(fieldValueBody('NUMBER', 0)).toEqual({value: 0})
    })

    it('reads an empty box as nothing held', () => {
        for (const type of ['TEXT', 'NUMBER', 'DATE', 'CHOICE', 'LANE_ASSIGNEE'] as const) {
            expect(fieldValueOfText(type, '')).toBeNull()
        }
    })

    it('holds a yes as a yes and a member as their id', () => {
        expect(fieldValueOfText('BOOLEAN', 'true')).toBe(true)
        expect(fieldValueOfText('BOOLEAN', 'false')).toBe(false)
        expect(fieldValueOfText('LANE_ASSIGNEE', '12')).toBe(12)
        expect(fieldValueBody('LANE_ASSIGNEE', 12)).toEqual({memberId: 12})
    })

    it('writes what a ticket holds as the text the box reads', () => {
        expect(fieldValueText(0)).toBe('0')
        expect(fieldValueText(true)).toBe('true')
        expect(fieldValueText(null)).toBe('')
    })

    it('knows only the types a board offers', () => {
        expect(isBoardFieldType('CHOICE')).toBe(true)
        expect(isBoardFieldType('ENUM')).toBe(false)
        expect(isBoardFieldType('MEMBER')).toBe(false)
    })
})

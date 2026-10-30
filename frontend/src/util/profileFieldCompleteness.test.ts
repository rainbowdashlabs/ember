/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {incompleteFields, type ExpectedField} from './profileFields'
import {FieldType} from '@/api/generated/schema'

function field(id: number, fieldType: string, extra: Partial<ExpectedField> = {}): ExpectedField {
    return {id, fieldType, required: true, ...extra}
}

const blank = () => ''

/**
 * The profile says what is left to fill in, and it has to say the same as the server, which only counts
 * a question the member can actually write.
 */
describe('incompleteFields', () => {
    it('counts a required question left blank', () => {
        expect(incompleteFields([field(1, FieldType.NUMBER)], blank).map(f => f.id)).toEqual([1])
    })

    /** A field that was a required number before it became an age still says required. */
    it('never counts an age, a heading or a gap, which nobody can fill in', () => {
        const fields = [field(1, FieldType.AGE), field(2, FieldType.SECTION), field(3, FieldType.SPACER)]

        expect(incompleteFields(fields, blank)).toEqual([])
    })

    it('never counts a question the member may only read, whoever locked it', () => {
        const fields = [field(1, FieldType.TEXT, {readonly: true}), field(2, FieldType.TEXT, {readonlyAtStation: true})]

        expect(incompleteFields(fields, blank)).toEqual([])
    })

    it('reads an empty string and a null left by a selection as blank', () => {
        const fields = [field(1, FieldType.TEXT), field(2, FieldType.CHOICE), field(3, FieldType.TEXT)]
        const values: Record<number, string> = {1: '""', 2: 'null', 3: 'Feuerwehr'}

        expect(incompleteFields(fields, f => values[f.id] ?? '').map(f => f.id)).toEqual([1, 2])
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {FieldType, type WaitingListField} from '@/api/generated/schema'
import {blankDraft, draftOf, requestOf} from './waitingListFieldDraft'

function field(fieldType: FieldType, options?: string[], required = false): WaitingListField {
    return {id: 5, listId: 1, name: ' Größe ', fieldType, position: 2, required, isPublic: false, config: {options}}
}

describe('waitingListFieldDraft', () => {
    it('keeps the usable answers of a choice and whether it is required', () => {
        const request = requestOf(draftOf(field(FieldType.CHOICE, ['S', ' ', 'M'], true)), 1)
        expect(request).toEqual({
            name: 'Größe',
            fieldType: FieldType.CHOICE,
            config: {options: ['S', 'M']},
            position: 1,
            required: true,
            isPublic: false,
        })
    })

    it('keeps no answers for a type that offers none, however they were typed', () => {
        const draft = {...draftOf(field(FieldType.CHOICE, ['S', 'M'])), fieldType: FieldType.TEXT}
        expect(requestOf(draft, 0).config).toEqual({})
    })

    it('starts a new field as text asked on the public form', () => {
        expect(requestOf(blankDraft(), 4)).toEqual({
            name: '', fieldType: FieldType.TEXT, config: {}, position: 4, required: false, isPublic: true,
        })
    })
})

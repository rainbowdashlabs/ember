/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {FieldType, type AttendanceFieldConfig, type AttendanceTemplateField} from '@/api/generated/schema'
import {blankDraft, draftOf, previewOf, requestOf} from './attendanceFieldDraft'

const EMPTY: AttendanceFieldConfig = {
    autoAttend: false, defaultValue: null, groupId: null, options: null, required: false, width: null,
}

function field(fieldType: FieldType, config: Partial<AttendanceFieldConfig>): AttendanceTemplateField {
    return {id: 1, templateId: 1, name: 'Feld', fieldType, position: 0, config: {...EMPTY, ...config}}
}

/**
 * A sheet's field writes back the settings it was opened with, in the shape the sheet keeps them:
 * a yes as a yes, and nothing a type has no use for.
 */
function saved(edited: AttendanceTemplateField) {
    return requestOf(draftOf(edited), 3)
}

describe('attendanceFieldDraft', () => {
    it('keeps a yes as the starting value of a yes or no field', () => {
        const request = saved(field(FieldType.BOOLEAN, {defaultValue: true, required: true}))
        expect(request.fieldType).toBe(FieldType.BOOLEAN)
        expect(request.config?.defaultValue).toBe(true)
        expect(request.config?.required).toBe(true)
    })

    it('keeps a number field it no longer offers, with its number', () => {
        const request = saved(field(FieldType.NUMBER, {defaultValue: 4}))
        expect(request.fieldType).toBe(FieldType.NUMBER)
        expect(request.config?.defaultValue).toBe(4)
    })

    it('keeps the group of a member field and gives it no starting value', () => {
        const request = saved(field(FieldType.MEMBER_OF_GROUP, {groupId: 7, defaultValue: '3', autoAttend: true}))
        expect(request.config?.groupId).toBe(7)
        expect(request.config?.defaultValue).toBeNull()
        expect(request.config?.autoAttend).toBe(true)
    })

    it('drops a group a type does not narrow by', () => {
        const request = saved(field(FieldType.TEXT, {groupId: 7, options: ['a'], autoAttend: true}))
        expect(request.config?.groupId).toBeNull()
        expect(request.config?.options).toBeNull()
        expect(request.config?.autoAttend).toBe(false)
    })

    it('writes the place it is drawn at and a width only where it is not the whole row', () => {
        expect(saved(field(FieldType.TEXT, {width: 'half'})).config?.width).toBe('half')
        expect(saved(field(FieldType.TEXT, {})).config?.width).toBeNull()
        expect(saved(field(FieldType.TEXT, {})).position).toBe(3)
        expect(requestOf(blankDraft(), 0).config?.width).toBeNull()
    })

    it('draws the field as wide as it is set and marks what must be answered', () => {
        const shown = previewOf(draftOf(field(FieldType.TEXT, {width: 'third', required: true})))
        expect(shown).toEqual({name: 'Feld', fieldType: FieldType.TEXT, required: true, width: 'third'})
        expect(previewOf(blankDraft()).width).toBeNull()
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {nextTick} from 'vue'
import {describe, expect, it} from 'vitest'
import FieldModal from './FieldModal.vue'
import {FieldTypes, type FieldTypeName} from '@/api/fieldTypes'
import type {AttendanceFieldConfig, AttendanceTemplateField, TemplateFieldRequest} from '@/api/generated/schema'

const EMPTY: AttendanceFieldConfig = {
    autoAttend: false, defaultValue: null, groupId: null, options: null, required: false, width: null,
}

function field(fieldType: FieldTypeName, config: Partial<AttendanceFieldConfig>): AttendanceTemplateField {
    return {id: 1, templateId: 1, name: 'Feld', fieldType, position: 0, config: {...EMPTY, ...config}}
}

/**
 * A sheet's field writes back the settings it was opened with, in the shape the sheet keeps them:
 * a yes as a yes, and nothing a type has no use for.
 */
async function saved(edited: AttendanceTemplateField): Promise<Required<TemplateFieldRequest>> {
    const wrapper = mount(FieldModal, {
        props: {field: null, availableGroups: [], saving: false, fieldCount: 0, modelValue: false},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}, MemberSelectInput: true, DragList: true}},
    })
    await wrapper.setProps({field: edited, modelValue: true})
    await nextTick()
    await wrapper.findAll('button').at(-1)!.trigger('click')
    return wrapper.emitted('save')![0]![0] as Required<TemplateFieldRequest>
}

describe('FieldModal', () => {
    it('keeps a yes as the starting value of a yes or no field', async () => {
        const request = await saved(field(FieldTypes.BOOLEAN, {defaultValue: true, required: true}))
        expect(request.fieldType).toBe(FieldTypes.BOOLEAN)
        expect(request.config.defaultValue).toBe(true)
        expect(request.config.required).toBe(true)
    })

    it('keeps a number field it no longer offers, with its number', async () => {
        const request = await saved(field(FieldTypes.NUMBER, {defaultValue: 4}))
        expect(request.fieldType).toBe(FieldTypes.NUMBER)
        expect(request.config.defaultValue).toBe(4)
    })

    it('keeps the group of a member field and gives it no starting value', async () => {
        const request = await saved(field(FieldTypes.MEMBER_OF_GROUP, {groupId: 7, defaultValue: '3', autoAttend: true}))
        expect(request.config.groupId).toBe(7)
        expect(request.config.defaultValue).toBeNull()
        expect(request.config.autoAttend).toBe(true)
    })

    it('drops a group a type does not narrow by', async () => {
        const request = await saved(field(FieldTypes.TEXT, {groupId: 7, options: ['a']}))
        expect(request.config.groupId).toBeNull()
        expect(request.config.options).toBeNull()
    })
})

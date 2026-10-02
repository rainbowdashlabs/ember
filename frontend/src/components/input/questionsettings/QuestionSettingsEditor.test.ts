/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import QuestionSettingsEditor from './QuestionSettingsEditor.vue'
import {FieldTypes} from '@/api/fieldTypes'
import type {MemberGroup} from '@/api/generated/schema'
import {TODAY, typedDefault, type QuestionSetting, type QuestionSettingsModel} from './questionSettings'

const ALL: QuestionSetting[] = ['required', 'default', 'options', 'bounds', 'step', 'members']

const YOUTH: MemberGroup = {
    id: 3, name: 'Jugend', color: null, groupSetId: null, position: 0, stationId: 'station', userTypes: [],
}

/**
 * The shared part of every settings screen: each setting shows for the types it means something for
 * and only where the feature offers it.
 */
describe('QuestionSettingsEditor', () => {
    function edit(fieldType: string, offers: QuestionSetting[] = ALL, settings: QuestionSettingsModel = {}) {
        return mount(QuestionSettingsEditor, {
            props: {fieldType, offers, modelValue: settings, groups: [YOUTH]},
            global: {stubs: {MemberSelectInput: true, DragList: true}},
        })
    }

    function shows(wrapper: ReturnType<typeof edit>, testid: string): boolean {
        return wrapper.find(`[data-testid="${testid}"]`).exists()
    }

    it('offers the answers of a choice only for a choice', () => {
        expect(edit(FieldTypes.CHOICE).find('[data-testid="question-option-add"]').exists()).toBe(true)
        expect(edit(FieldTypes.TEXT).find('[data-testid="question-option-add"]').exists()).toBe(false)
    })

    it('offers bounds only for a number, and the step only where the feature offers it', () => {
        expect(shows(edit(FieldTypes.NUMBER), 'question-min')).toBe(true)
        expect(shows(edit(FieldTypes.NUMBER), 'question-step')).toBe(true)
        expect(shows(edit(FieldTypes.NUMBER, ['bounds']), 'question-step')).toBe(false)
        expect(shows(edit(FieldTypes.DATE), 'question-min')).toBe(false)
    })

    it('offers the group only for a member field narrowed to one', () => {
        expect(shows(edit(FieldTypes.MEMBER_OF_GROUP), 'question-group')).toBe(true)
        expect(shows(edit(FieldTypes.MEMBER), 'question-group')).toBe(false)
        expect(shows(edit(FieldTypes.MEMBER_LIST_OF_TAG), 'question-tag')).toBe(true)
    })

    it('leaves out what the feature does not offer', () => {
        const wrapper = edit(FieldTypes.CHOICE, ['required'])
        expect(wrapper.find('[data-testid="question-option-add"]').exists()).toBe(false)
        expect(shows(wrapper, 'question-has-default')).toBe(false)
        expect(shows(wrapper, 'question-required')).toBe(true)
    })

    it('offers no starting value for a type that holds none', () => {
        expect(shows(edit(FieldTypes.AGE), 'question-has-default')).toBe(false)
        expect(shows(edit(FieldTypes.TEXT), 'question-has-default')).toBe(true)
    })

    it('starts a date from today where the feature says so', async () => {
        const wrapper = edit(FieldTypes.DATE, ['default', 'todayDefault'])
        await wrapper.find('[data-testid="question-has-default"]').trigger('click')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{defaultValue: TODAY}])
        expect(shows(wrapper, 'question-default')).toBe(false)
    })

    it('hands a group back as a number and no group as none', async () => {
        const wrapper = edit(FieldTypes.MEMBER_OF_GROUP)
        await wrapper.find('[data-testid="question-group"]').setValue('3')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{groupId: 3}])
        await wrapper.find('[data-testid="question-group"]').setValue('')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([{groupId: null}])
    })
})

describe('typedDefault', () => {
    it('keeps a yes as a yes and a number as a number', () => {
        expect(typedDefault(FieldTypes.BOOLEAN, 'true')).toBe(true)
        expect(typedDefault(FieldTypes.NUMBER, '4')).toBe(4)
        expect(typedDefault(FieldTypes.TEXT, ' Halle ')).toBe('Halle')
    })

    it('leaves out a starting value there is none of', () => {
        expect(typedDefault(FieldTypes.TEXT, null)).toBeUndefined()
        expect(typedDefault(FieldTypes.NUMBER, '')).toBeUndefined()
    })
})

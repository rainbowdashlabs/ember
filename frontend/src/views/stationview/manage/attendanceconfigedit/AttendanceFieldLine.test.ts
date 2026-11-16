/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import {FieldType} from '@/api/generated/schema'
import AttendanceFieldLine from './AttendanceFieldLine.vue'
import {blankDraft, type AttendanceFieldDraft} from './attendanceFieldDraft'

function line(draft: Partial<AttendanceFieldDraft>) {
    return mount(AttendanceFieldLine, {
        props: {modelValue: {...blankDraft(), name: 'Fahrzeug', ...draft}, availableGroups: []},
        global: {stubs: {'font-awesome-icon': true, FieldEditor: true}},
    })
}

/**
 * A field of an attendance sheet takes one line: its name, its type and the marks that matter for a
 * sheet. Its card opens on request.
 */
describe('AttendanceFieldLine', () => {
    it('shows the name on one line with the card closed', () => {
        const wrapper = line({})

        expect((wrapper.get('[data-testid="question-row-name"]').element as HTMLInputElement).value).toBe('Fahrzeug')
        expect(wrapper.find('field-editor-stub').exists()).toBe(false)
    })

    it('switches whether the field must be answered', async () => {
        const wrapper = line({fieldType: FieldType.TEXT})

        await wrapper.get('[data-testid="attendance-field-required"]').trigger('click')

        const handed = wrapper.emitted('update:modelValue')?.at(-1)?.[0] as AttendanceFieldDraft
        expect(handed.settings.required).toBe(true)
    })

    it('offers counting as present only for a field that names members', () => {
        expect(line({fieldType: FieldType.TEXT}).find('[data-testid="attendance-field-auto-attend"]').exists()).toBe(false)
        expect(line({fieldType: FieldType.MEMBER}).find('[data-testid="attendance-field-auto-attend"]').exists()).toBe(true)
    })

    it('opens the card on request and straight away for a field without a name', async () => {
        const named = line({})
        await named.get('[data-testid="question-row-open"]').trigger('click')
        expect(named.find('field-editor-stub').exists()).toBe(true)

        expect(line({name: ''}).find('field-editor-stub').exists()).toBe(true)
    })
})

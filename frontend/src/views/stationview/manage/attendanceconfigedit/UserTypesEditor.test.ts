/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import UserTypesEditor from './UserTypesEditor.vue'
import type {StationUserTypeName} from '@/api/types'

/**
 * The user types a template enters besides its groups. Ticking or unticking one hands the whole
 * new choice on at once, the way the groups beside it are saved.
 */
function editor(modelValue: StationUserTypeName[]) {
    return mount(UserTypesEditor, {props: {modelValue}, global: {stubs: {'font-awesome-icon': true}}})
}

describe('UserTypesEditor', () => {
    it('shows the template\'s user types ticked', () => {
        const wrapper = editor(['TEAM'])

        const team = wrapper.find('[data-testid="attendance-template-type-TEAM"]').element as HTMLInputElement
        const trial = wrapper.find('[data-testid="attendance-template-type-TRIAL"]').element as HTMLInputElement
        expect(team.checked).toBe(true)
        expect(trial.checked).toBe(false)
    })

    it('hands on a type that is ticked', async () => {
        const wrapper = editor(['TEAM'])

        await wrapper.find('[data-testid="attendance-template-type-MANAGER"]').setValue(true)

        expect(wrapper.emitted('update:modelValue')).toEqual([[['TEAM', 'MANAGER']]])
    })

    it('hands on a type that is unticked, down to none at all', async () => {
        const wrapper = editor(['TEAM'])

        await wrapper.find('[data-testid="attendance-template-type-TEAM"]').setValue(false)

        expect(wrapper.emitted('update:modelValue')).toEqual([[[]]])
    })
})

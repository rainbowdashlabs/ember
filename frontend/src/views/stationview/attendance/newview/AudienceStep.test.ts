/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import AudienceStep from './AudienceStep.vue'
import type {TemplateDetail} from '@/api/attendance'
import type {MemberGroup} from '@/api/types'

/**
 * The step that says whom a sheet nobody kept a template for expects.
 *
 * <p>The two answers add up, and a sheet still has to borrow its questions from somewhere, so the
 * step cannot be walked past having answered neither.
 */
const harness = {global: {stubs: {'font-awesome-icon': true}}}

const templates: TemplateDetail[] = [
    {id: 4, stationId: 's', name: 'Dienstabend', fields: [], groups: []},
    {id: 9, stationId: 's', name: 'Übung', fields: [], groups: []},
]

const groups: MemberGroup[] = [
    {id: 11, name: 'Jugend'} as MemberGroup,
    {id: 12, name: 'Aktive'} as MemberGroup,
]

function step() {
    return mount(AudienceStep, {props: {templates, groups}, ...harness})
}

describe('AudienceStep', () => {
    it('refuses to go on while it has been told nobody', () => {
        const wrapper = step()

        expect(wrapper.find('[data-testid="attendance-audience-confirm"]').attributes('disabled')).toBeDefined()
    })

    it('hands over the types and the groups together', async () => {
        const wrapper = step()

        await wrapper.find('[data-testid="attendance-type-TEAM"]').setValue(true)
        await wrapper.find('[data-testid="attendance-group-11"]').setValue(true)
        await wrapper.find('[data-testid="attendance-audience-confirm"]').trigger('click')

        expect(wrapper.emitted('confirm')).toEqual([[4, {userTypes: ['TEAM'], groupIds: [11]}]])
    })

    /** A sheet carries questions, and the first template is the one a station usually means. */
    it('borrows the fields of the template that was chosen', async () => {
        const wrapper = step()

        await wrapper.find('[data-testid="attendance-fields-from"]').setValue('9')
        await wrapper.find('[data-testid="attendance-group-12"]').setValue(true)
        await wrapper.find('[data-testid="attendance-audience-confirm"]').trigger('click')

        expect(wrapper.emitted('confirm')![0]![0]).toBe(9)
    })

    it('keeps the groups in the order they were chosen', async () => {
        const wrapper = step()

        await wrapper.find('[data-testid="attendance-group-12"]').setValue(true)
        await wrapper.find('[data-testid="attendance-group-11"]').setValue(true)
        await wrapper.find('[data-testid="attendance-audience-confirm"]').trigger('click')

        expect(wrapper.emitted('confirm')![0]![1]).toEqual({userTypes: [], groupIds: [12, 11]})
    })
})

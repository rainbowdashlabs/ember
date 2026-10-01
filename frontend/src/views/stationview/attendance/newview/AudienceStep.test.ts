/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import AudienceStep from './AudienceStep.vue'
import type {MemberGroup, TemplateDetail} from '@/api/generated/schema'

/**
 * The step that says whom a sheet nobody kept a template for expects.
 *
 * <p>The two answers add up, and a sheet still has to borrow its questions from somewhere, so the
 * step cannot be walked past having answered neither.
 */
const harness = {global: {stubs: {'font-awesome-icon': true}}}

const templates: TemplateDetail[] = [
    {id: 4, stationId: 's', name: 'Dienstabend', fields: [], groups: [], userTypes: []},
    {id: 9, stationId: 's', name: 'Übung', fields: [], groups: [], userTypes: []},
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

    /** Started from a template, the step arrives with whom the template enters, and its questions. */
    it('arrives filled in with the template it was started from', async () => {
        const template: TemplateDetail = {
            id: 9,
            stationId: 's',
            name: 'Übung',
            fields: [],
            groups: [{groupId: 12, position: 1}, {groupId: 11, position: 0}],
            userTypes: ['MANAGER'],
        }
        const wrapper = mount(AudienceStep, {props: {templates, groups, template}, ...harness})

        expect((wrapper.find('[data-testid="attendance-type-MANAGER"]').element as HTMLInputElement).checked).toBe(true)
        expect((wrapper.find('[data-testid="attendance-type-TEAM"]').element as HTMLInputElement).checked).toBe(false)
        expect((wrapper.find('[data-testid="attendance-group-11"]').element as HTMLInputElement).checked).toBe(true)
        expect((wrapper.find('[data-testid="attendance-fields-from"]').element as HTMLSelectElement).value).toBe('9')

        await wrapper.find('[data-testid="attendance-audience-confirm"]').trigger('click')

        expect(wrapper.emitted('confirm')).toEqual([[9, {userTypes: ['MANAGER'], groupIds: [11, 12]}]])
    })

    /** A template that enters nobody still starts a sheet, to which people are then added by hand. */
    it('lets a template that enters nobody through', async () => {
        const wrapper = mount(AudienceStep, {props: {templates, groups, template: templates[1]}, ...harness})

        await wrapper.find('[data-testid="attendance-audience-confirm"]').trigger('click')

        expect(wrapper.emitted('confirm')).toEqual([[9, {userTypes: [], groupIds: []}]])
    })

    it('keeps the groups in the order they were chosen', async () => {
        const wrapper = step()

        await wrapper.find('[data-testid="attendance-group-12"]').setValue(true)
        await wrapper.find('[data-testid="attendance-group-11"]').setValue(true)
        await wrapper.find('[data-testid="attendance-audience-confirm"]').trigger('click')

        expect(wrapper.emitted('confirm')![0]![1]).toEqual({userTypes: [], groupIds: [12, 11]})
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import TimeRulesCard from './TimeRulesCard.vue'
import type {TimeRule} from '@/api/generated/schema'

const listTemplateTimeRules = vi.fn<(templateId: number) => Promise<TimeRule[]>>()
const setTemplateTimeRules = vi.fn()

vi.mock('@/api', () => ({
    attendance: {
        listTemplateTimeRules: (templateId: number) => listTemplateTimeRules(templateId),
        setTemplateTimeRules: (...args: unknown[]) => setTemplateTimeRules(...args),
    },
    stationMembers: {listMembers: async () => []},
}))

vi.mock('@/components/documents/generation', () => ({
    audienceLists: async () => ({groups: [], tags: []}),
}))

const harness = {global: {stubs: {'font-awesome-icon': true, RestrictionPicker: true}}}

const BROKEN: TimeRule = {
    id: 1,
    position: 0,
    label: 'Ausbilder',
    preparationMinutes: 30,
    followUpMinutes: 0,
    audience: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    intact: false,
}

/** The rules card of a template, where a failed read must never turn into an empty save. */
describe('TimeRulesCard', () => {
    beforeEach(() => {
        listTemplateTimeRules.mockReset()
        setTemplateTimeRules.mockReset()
    })

    it('offers nothing to save when the rules could not be read', async () => {
        listTemplateTimeRules.mockRejectedValue(new Error('offline'))

        const wrapper = mount(TimeRulesCard, {props: {templateId: 4}, ...harness})
        await flushPromises()

        expect(wrapper.find('[data-testid="time-rules-editor"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="time-rules-save"]').exists()).toBe(false)
    })

    it('holds back saving while a broken rule names nobody', async () => {
        listTemplateTimeRules.mockResolvedValue([BROKEN])

        const wrapper = mount(TimeRulesCard, {props: {templateId: 4}, ...harness})
        await flushPromises()

        const save = wrapper.find('[data-testid="time-rules-save"]')
        expect(save.exists()).toBe(true)
        expect(save.attributes('disabled')).toBeDefined()
        expect(wrapper.text()).toContain('Eine Gruppe oder ein Tag dieser Regel wurde gelöscht.')
    })
})

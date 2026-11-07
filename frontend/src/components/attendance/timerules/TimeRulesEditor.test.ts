/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import {defineComponent, h, ref} from 'vue'
import TimeRulesEditor from './TimeRulesEditor.vue'
import {draftOf, type TimeRuleDraft} from './timeRuleDraft'

const harness = {global: {stubs: {'font-awesome-icon': true, RestrictionPicker: true, DragList: false}}}

function editor(initial: TimeRuleDraft[]) {
    const rules = ref(initial)
    const wrapper = mount(defineComponent({
        setup: () => () => h(TimeRulesEditor, {
            modelValue: rules.value,
            'onUpdate:modelValue': (value: TimeRuleDraft[]) => {
                rules.value = value
            },
            groups: [],
            tags: [],
            members: [],
        }),
    }), harness)
    return {wrapper, rules}
}

const BROKEN = draftOf({
    id: 1,
    position: 0,
    label: 'Ausbilder',
    preparationMinutes: 30,
    followUpMinutes: 0,
    audience: {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'},
    intact: false,
})

/** The list of preparation and follow-up rules as somebody edits it. */
describe('TimeRulesEditor', () => {
    it('adds a rule that still has to be told whom it is for', async () => {
        const {wrapper, rules} = editor([])

        await wrapper.find('[data-testid="time-rule-add"]').trigger('click')

        expect(rules.value).toHaveLength(1)
        expect(wrapper.text()).toContain('Lege fest, für wen die Regel gilt.')
    })

    it('says that a rule lost part of whom it named', () => {
        const {wrapper} = editor([BROKEN])

        expect(wrapper.text()).toContain('Eine Gruppe oder ein Tag dieser Regel wurde gelöscht.')
    })

    it('removes a rule', async () => {
        const {wrapper, rules} = editor([BROKEN])

        await wrapper.find('[aria-label="Regel entfernen"]').trigger('click')

        expect(rules.value).toHaveLength(0)
    })
})

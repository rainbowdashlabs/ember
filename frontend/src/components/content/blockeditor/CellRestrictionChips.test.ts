/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import CellRestrictionChips from './CellRestrictionChips.vue'
import de from '@/i18n/de-DE'
import {GuardianCondition} from '@/api/generated/schema'

/** The chips on a block say who it is printed for, the condition on a second guardian included. */
describe('CellRestrictionChips', () => {
    const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': de, en: {}}})
    const choices = {groups: [], tags: []}

    it('shows the condition on a second guardian on its own', () => {
        const wrapper = mount(CellRestrictionChips, {
            props: {restriction: null, guardianCondition: GuardianCondition.NO_SECOND_GUARDIAN, choices},
            global: {plugins: [i18n]},
        })

        expect(wrapper.text()).toContain(de.stationPages.editor.guardianConditionOf.NO_SECOND_GUARDIAN)
    })

    it('draws nothing for a block printed for everybody', () => {
        const wrapper = mount(CellRestrictionChips, {
            props: {restriction: null, guardianCondition: null, choices},
            global: {plugins: [i18n]},
        })

        expect(wrapper.find('[data-testid="cell-restriction"]').exists()).toBe(false)
    })
})

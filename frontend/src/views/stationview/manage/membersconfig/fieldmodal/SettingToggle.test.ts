/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import SettingToggle from './SettingToggle.vue'

/** A switch of a field's settings keeps what it does behind a hint, which the switch names as its description. */
describe('SettingToggle', () => {
    it('shows what the switch does as a hint, not as a line under it', () => {
        const view = mount(SettingToggle, {props: {modelValue: false, label: 'Pflichtfeld', hint: 'Gilt für alle.'}})

        const icon = view.get('[data-testid="hint-icon"]')
        expect(icon.attributes('aria-label')).toBe('Gilt für alle.')
        expect(view.find('p').exists()).toBe(false)

        const tooltip = view.get('[role="tooltip"]')
        expect(tooltip.text()).toBe('Gilt für alle.')
        expect(view.get('[role="switch"]').attributes('aria-describedby')).toBe(tooltip.attributes('id'))
    })

    it('stands without a hint where its name says enough', () => {
        const view = mount(SettingToggle, {props: {modelValue: true, label: 'In der Übersicht'}})

        expect(view.find('[data-testid="hint-icon"]').exists()).toBe(false)
        expect(view.get('[role="switch"]').attributes('aria-describedby')).toBeUndefined()
    })
})

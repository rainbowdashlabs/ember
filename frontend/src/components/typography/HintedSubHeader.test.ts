/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import HintedSubHeader from './HintedSubHeader.vue'

/** A heading whose explanation waits behind an icon instead of standing under it. */
describe('HintedSubHeader', () => {
    it('shows the heading and keeps the explanation in the icon', () => {
        const wrapper = mount(HintedSubHeader, {
            props: {hint: 'Was der Abschnitt tut'},
            slots: {default: 'Dateien'},
            global: {stubs: {'font-awesome-icon': true}},
        })

        expect(wrapper.text()).toContain('Dateien')
        expect(wrapper.get('[data-testid="hint-icon"]').attributes('aria-label')).toBe('Was der Abschnitt tut')
    })
})

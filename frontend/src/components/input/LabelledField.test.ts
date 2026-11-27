/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {h} from 'vue'
import {mount} from '@vue/test-utils'
import LabelledField from './LabelledField.vue'

/**
 * A labelled input shows what it is for under the field, or behind a hint beside the label where a dense
 * screen asks for that. What is wrong with the input is always shown under it.
 */
describe('LabelledField', () => {
    function field(props: Record<string, unknown>) {
        return mount(LabelledField, {
            props: {label: 'Warnen ab', help: 'Tage vor dem Datum.', ...props},
            slots: {default: (slot: {describedBy?: string}) => h('input', {'aria-describedby': slot.describedBy})},
        })
    }

    it('writes the help under the field by default', () => {
        const view = field({})

        expect(view.find('[data-testid="hint-icon"]').exists()).toBe(false)
        expect(view.text()).toContain('Tage vor dem Datum.')
        expect(view.get('input').attributes('aria-describedby')).toBeUndefined()
    })

    it('puts the help behind a hint the input is described by, where asked', () => {
        const view = field({helpAsHint: true})

        const tooltip = view.get('[role="tooltip"]')
        expect(tooltip.text()).toBe('Tage vor dem Datum.')
        expect(view.findAll('p')).toHaveLength(0)
        expect(view.get('input').attributes('aria-describedby')).toBe(tooltip.attributes('id'))
    })

    it('keeps an error under the field', () => {
        const view = field({helpAsHint: true, error: 'Keine Zahl.'})

        expect(view.get('[role="alert"]').text()).toBe('Keine Zahl.')
    })
})

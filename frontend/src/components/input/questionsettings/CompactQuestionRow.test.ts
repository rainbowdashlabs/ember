/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import CompactQuestionRow from './CompactQuestionRow.vue'

function row(open: boolean) {
    return mount(CompactQuestionRow, {
        props: {open, name: 'Ort', nameLabel: 'Name', typeLabel: 'Ort', 'onUpdate:open': () => undefined},
        global: {plugins: [createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}})]},
    })
}

/**
 * The arrow that opens and closes a question stands at the far end of its line, open or closed, so a double click
 * on it toggles the card twice and never reaches the delete button.
 *
 * @vitest-environment happy-dom
 */
describe('CompactQuestionRow', () => {
    it('puts the arrow last and the delete button before it on a closed line', () => {
        const buttons = row(false).findAll('button')
        expect(buttons.at(-1)!.attributes('data-testid')).toBe('question-row-open')
        expect(buttons.at(-2)!.attributes('data-testid')).toBe('question-row-remove')
    })

    it('opens, closes and opens again on three presses of the arrow without asking to delete', async () => {
        const wrapper = row(false)
        await wrapper.get('[data-testid="question-row-open"]').trigger('click')
        await wrapper.setProps({open: true})
        await wrapper.get('[data-testid="question-row-close"]').trigger('click')
        await wrapper.setProps({open: false})
        await wrapper.findAll('button').at(-1)!.trigger('click')

        expect(wrapper.emitted('remove')).toBeUndefined()
        expect(wrapper.emitted('update:open')).toEqual([[true], [false], [true]])
    })
})

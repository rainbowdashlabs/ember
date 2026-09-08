/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import QuestionOptionsEditor from './QuestionOptionsEditor.vue'

/**
 * The choices a question offers, which every feature used to write for itself out of one box of
 * text. One of them split that box on line breaks while asking for commas, so a station could only
 * ever have one choice: a row per choice is what makes that unspellable.
 */
function mountEditor(options: string[]) {
    // Typed loosely on purpose: the editor is generic over what an option is, and a mount of a
    // generic component carries that generic into every assertion below.
    const wrapper: any = mount(QuestionOptionsEditor, {
        props: {
            modelValue: options,
            'onUpdate:modelValue': (next: unknown[]) => wrapper.setProps({modelValue: next}),
        },
        global: {
            stubs: {
                DragList: {
                    props: ['items'],
                    template: '<div><template v-for="(item, index) in items"><slot :item="item" :index="index"/></template></div>',
                },
            },
        },
    })
    return wrapper
}

describe('QuestionOptionsEditor', () => {
    it('holds one row per choice', () => {
        const wrapper = mountEditor(['S', 'M', 'L'])
        expect(wrapper.findAll('[data-testid^="question-option-"]').length).toBeGreaterThanOrEqual(3)
        expect((wrapper.find('[data-testid="question-option-0"]').element as HTMLInputElement).value).toBe('S')
    })

    it('adds a choice as an empty row, so nothing is split or guessed', async () => {
        const wrapper = mountEditor(['S'])
        await wrapper.find('[data-testid="question-option-add"]').trigger('click')
        expect(wrapper.props('modelValue')).toEqual(['S', ''])
    })

    it('writes what is typed into the row it was typed in', async () => {
        const wrapper = mountEditor(['S', 'M'])
        await wrapper.find('[data-testid="question-option-1"]').setValue('L')
        expect(wrapper.props('modelValue')).toEqual(['S', 'L'])
    })

    it('takes one away without touching the rest', async () => {
        const wrapper = mountEditor(['S', 'M', 'L'])
        await wrapper.find('[data-testid="question-option-remove-1"]').trigger('click')
        expect(wrapper.props('modelValue')).toEqual(['S', 'L'])
    })

    /**
     * A quiz option carries whether it is the right answer and an equipment option carries the value
     * stored beside the label. Both are the same list with something extra in the row, which is why
     * the list takes the option apart through the feature rather than knowing its shape.
     */
    it('edits an option that is more than its words', async () => {
        type Option = {text: string; correct: boolean}
        const wrapper: any = mount(QuestionOptionsEditor, {
            props: {
                modelValue: [{text: 'Berlin', correct: true}] as Option[],
                textOf: (option: unknown) => (option as Option).text,
                withText: (option: unknown, text: string) => ({...(option as Option), text}),
                blank: () => ({text: '', correct: false}),
                'onUpdate:modelValue': (next: unknown[]) => wrapper.setProps({modelValue: next}),
            },
            global: {
                stubs: {
                    DragList: {
                        props: ['items'],
                        template: '<div><template v-for="(item, index) in items"><slot :item="item" :index="index"/></template></div>',
                    },
                },
            },
        })

        expect((wrapper.find('[data-testid="question-option-0"]').element as HTMLInputElement).value).toBe('Berlin')

        await wrapper.find('[data-testid="question-option-0"]').setValue('Hamburg')
        expect(wrapper.props('modelValue')).toEqual([{text: 'Hamburg', correct: true}])

        await wrapper.find('[data-testid="question-option-add"]').trigger('click')
        expect(wrapper.props('modelValue')).toEqual([{text: 'Hamburg', correct: true}, {text: '', correct: false}])
    })

    /** A comma is a character in an answer, not a separator, which is what the old box got wrong. */
    it('keeps a comma inside the choice it was typed into', async () => {
        const wrapper = mountEditor([''])
        await wrapper.find('[data-testid="question-option-0"]').setValue('Ja, mit Begleitung')
        expect(wrapper.props('modelValue')).toEqual(['Ja, mit Begleitung'])
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import QuestionValueInput from './QuestionValueInput.vue'
import {QuestionKinds, type QuestionKindName} from '@/util/questions'

/**
 * The one box every question is answered in. What each kind renders matters, and so does the one
 * language it speaks back: text, the same the server measures an answer in.
 */
function mountInput(kind: QuestionKindName, value = '', props: Record<string, unknown> = {}) {
    const wrapper = mount(QuestionValueInput, {
        props: {
            kind,
            modelValue: value,
            'onUpdate:modelValue': (next: string) => wrapper.setProps({modelValue: next}),
            ...props,
        },
        global: {
            stubs: {
                SingleSelectDropdown: {props: ['modelValue', 'options'], template: '<div class="single"/>'},
                MultiSelectDropdown: {props: ['modelValue', 'options'], template: '<div class="multi"/>'},
            },
        },
    })
    return wrapper
}

describe('QuestionValueInput', () => {
    it('answers a date in a date box and a time in a time box', () => {
        expect(mountInput(QuestionKinds.DATE).find('input[type="date"]').exists()).toBe(true)
        expect(mountInput(QuestionKinds.TIME).find('input[type="time"]').exists()).toBe(true)
    })

    it('answers a long question in a box with room for it', () => {
        expect(mountInput(QuestionKinds.LONG_TEXT).find('textarea').exists()).toBe(true)
        expect(mountInput(QuestionKinds.TEXT).find('input').exists()).toBe(true)
    })

    it('offers the choices, and an empty entry that cannot be chosen where an answer is expected', () => {
        const wrapper = mountInput(QuestionKinds.CHOICE, '', {options: ['S', 'M'], required: true})
        const options = wrapper.findAll('option')
        expect(options.map(option => option.text())).toEqual(['Bitte wählen', 'S', 'M'])
        expect(options[0]!.attributes('disabled')).toBeDefined()
    })

    /** The equipment fields keep a value and a label apart, and the label is what a reader picks. */
    it('shows the label of a choice and answers with its value', async () => {
        const wrapper = mountInput(QuestionKinds.CHOICE, '', {
            options: [{value: 'hose', label: 'Hose'}],
        })
        expect(wrapper.findAll('option').at(1)!.text()).toBe('Hose')
        await wrapper.find('select').setValue('hose')
        expect(wrapper.props('modelValue')).toBe('hose')
    })

    it('answers a number as text, which is what everything downstream reads', async () => {
        const wrapper = mountInput(QuestionKinds.NUMBER, '')
        await wrapper.find('input').setValue('4')
        expect(wrapper.props('modelValue')).toBe('4')
    })

    it('reads yes and no in both spellings a feature stores them in', () => {
        const checked = (value: string) =>
            mountInput(QuestionKinds.BOOLEAN, value).find('[role="switch"]').attributes('aria-checked')
        expect(checked('true')).toBe('true')
        expect(checked('1')).toBe('true')
        expect(checked('')).toBe('false')
    })

    /** Answering yes writes the word, because text is the language every answer is stored in. */
    it('writes a yes as text', async () => {
        const wrapper = mountInput(QuestionKinds.BOOLEAN, '')
        await wrapper.find('[role="switch"]').trigger('click')
        expect(wrapper.props('modelValue')).toBe('true')
    })

    it('picks one member with a picker and several with the other', () => {
        const members = [{value: '1', label: 'Anna'}]
        expect(mountInput(QuestionKinds.MEMBER, '', {members}).find('.single').exists()).toBe(true)
        expect(mountInput(QuestionKinds.MEMBER_LIST, '', {members}).find('.multi').exists()).toBe(true)
    })
})

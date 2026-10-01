/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import FormOptionsEditor from './FormOptionsEditor.vue'
import type {Option} from '@/api/generated/schema'

const DRAG_LIST_STUB = {
    props: ['items'],
    emits: ['reorder'],
    template: `<div>
        <button data-testid="move-first-down" @click="$emit('reorder', 0, 1)"/>
        <template v-for="(item, index) in items"><slot :item="item" :index="index"/></template>
    </div>`,
}

function mountEditor(options: Option[]) {
    const wrapper = mount(FormOptionsEditor, {
        props: {
            modelValue: options,
            'onUpdate:modelValue': (next: Option[]) => wrapper.setProps({modelValue: next}),
        },
        global: {stubs: {DragList: DRAG_LIST_STUB}},
    })
    return wrapper
}

function current(wrapper: ReturnType<typeof mountEditor>): Option[] {
    return wrapper.props('modelValue') as Option[]
}

/**
 * Answers name an option by its key, so the editor has to keep a key through everything a reader
 * does to the option and give a new option a key nothing else in the question has.
 *
 * @vitest-environment happy-dom
 */
describe('FormOptionsEditor', () => {
    const yes = {key: 'k-yes', label: 'Ja'}
    const no = {key: 'k-no', label: 'Nein'}

    it('keeps the key when the words of an option change', async () => {
        const wrapper = mountEditor([yes, no])
        await wrapper.find('[data-testid="question-option-1"]').setValue('Nein, danke')
        expect(current(wrapper)).toEqual([yes, {key: 'k-no', label: 'Nein, danke'}])
    })

    it('moves options with their keys', async () => {
        const wrapper = mountEditor([yes, no])
        await wrapper.find('[data-testid="move-first-down"]').trigger('click')
        expect(current(wrapper)).toEqual([no, yes])
    })

    it('gives an added option a fresh key of its own', async () => {
        const wrapper = mountEditor([yes, no])
        await wrapper.find('[data-testid="question-option-add"]').trigger('click')
        const added = current(wrapper)[2]!
        expect(added.label).toBe('')
        expect(added.key).toMatch(/^[0-9a-z]{8}$/)
        expect([yes.key, no.key]).not.toContain(added.key)
    })

    it('removes one option and leaves the keys of the rest alone', async () => {
        const wrapper = mountEditor([yes, no])
        await wrapper.find('[data-testid="question-option-remove-0"]').trigger('click')
        expect(current(wrapper)).toEqual([no])
    })
})

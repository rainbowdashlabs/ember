/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {computed, nextTick, ref} from 'vue'
import {mount} from '@vue/test-utils'
import ReaderPersonTabs from './ReaderPersonTabs.vue'
import {useReaderPersonChoice} from './readerPersonChoice'
import type {ReaderPerson} from '@/composables/useReaderPeople'

const ME: ReaderPerson = {id: 1, name: 'Gina Grün', own: true}
const LEO: ReaderPerson = {id: 2, name: 'Leo Grün', own: false}

/** A guardian picks whose documents they look at; a reader looking after nobody is offered no choice. */
describe('ReaderPersonTabs', () => {
    it('offers one tab per person, the reader first and by a word of their own', () => {
        const wrapper = mount(ReaderPersonTabs, {props: {modelValue: 1, people: [ME, LEO]}})

        const tabs = wrapper.findAll('[role="tab"]')
        expect(tabs.map(tab => tab.text())).toEqual(['Meine', 'Leo Grün'])
    })

    it('chooses a child by their tab', async () => {
        const wrapper = mount(ReaderPersonTabs, {props: {modelValue: 1, people: [ME, LEO]}})

        await wrapper.findAll('[role="tab"]')[1]!.trigger('click')

        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([2])
    })

    it('shows no tabs for a reader on their own', () => {
        const wrapper = mount(ReaderPersonTabs, {props: {modelValue: 1, people: [ME]}})

        expect(wrapper.find('[data-testid="reader-person-tabs"]').exists()).toBe(false)
    })
})

describe('useReaderPersonChoice', () => {
    it('starts with the reader and keeps a choice while that person is there', async () => {
        const people = ref<ReaderPerson[]>([])
        const {selected} = useReaderPersonChoice(computed(() => people.value))
        expect(selected.value).toBeNull()

        people.value = [ME, LEO]
        await nextTick()
        expect(selected.value).toBe(1)

        selected.value = 2
        people.value = [ME, LEO]
        await nextTick()
        expect(selected.value).toBe(2)

        people.value = [ME]
        await nextTick()
        expect(selected.value).toBe(1)
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import PersonalFeedCategories from './PersonalFeedCategories.vue'

const setHiddenCategories = vi.fn()
vi.mock('@/api', () => ({
    events: {listCategories: () => Promise.resolve([{id: 3, name: 'Jugend'}, {id: 4, name: 'Übung'}])},
    feedToken: {
        getHiddenCategories: () => Promise.resolve([3]),
        setHiddenCategories: (ids: number[]) => setHiddenCategories(ids),
    },
}))

async function view() {
    const wrapper = shallowMount(PersonalFeedCategories)
    await flushPromises()
    return wrapper
}

/** The member's own feed keeps the categories it leaves out, saved on every change. */
describe('PersonalFeedCategories', () => {
    beforeEach(() => setHiddenCategories.mockReset())

    it('shows the stored choice and saves a change at once', async () => {
        setHiddenCategories.mockResolvedValue([3, 4])
        const wrapper = await view()
        const choice = wrapper.findComponent({name: 'FeedCategoryChoice'})
        expect(choice.props('modelValue')).toEqual([3])

        choice.vm.$emit('update:modelValue', [3, 4])
        await flushPromises()

        expect(setHiddenCategories).toHaveBeenCalledWith([3, 4])
        expect(wrapper.findComponent({name: 'FeedCategoryChoice'}).props('modelValue')).toEqual([3, 4])
    })

    it('puts the choice back when the save is refused', async () => {
        setHiddenCategories.mockRejectedValueOnce({isAxiosError: true, response: {status: 404, data: {code: 'FD-012'}}})
        const wrapper = await view()

        wrapper.findComponent({name: 'FeedCategoryChoice'}).vm.$emit('update:modelValue', [])
        await flushPromises()

        expect(wrapper.findComponent({name: 'FeedCategoryChoice'}).props('modelValue')).toEqual([3])
        expect(wrapper.findComponent({name: 'FailureAlert'}).props('failure')).not.toBeNull()
    })
})

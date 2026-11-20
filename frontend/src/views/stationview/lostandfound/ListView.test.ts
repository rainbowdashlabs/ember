/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {ref} from 'vue'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import {followModifierKeys} from '@/util/modifierKeys'
import ListView from './ListView.vue'
import LostItemCard from './listview/LostItemCard.vue'
import LostItemConfirmModal from './listview/LostItemConfirmModal.vue'

const releaseItem = vi.fn()
const markProvided = vi.fn()
const listItems = vi.fn()
vi.mock('@/api', () => ({
    lostAndFound: {
        listItems: () => listItems(),
        releaseItem: (id: number) => releaseItem(id),
        markProvided: (id: number) => markProvided(id),
    },
    managedMembers: {listManaged: async () => []},
}))
vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: (permission: string) => permission !== 'MEMBER_GUARDIAN', sessionInfo: ref(null)}),
}))
vi.mock('@/composables/useSidebarCounts', () => ({useSidebarCounts: () => ({refresh: () => {}})}))

const ITEM = {id: 5, description: 'Handschuh', hasImage: false}

async function list() {
    const wrapper = mount(ListView, {
        global: {
            stubs: {
                ViewContent: {template: '<div><slot/></div>'},
                AsyncSection: {template: '<div><slot/></div>'},
                LostItemCard: {name: 'LostItemCard', template: '<div/>', emits: ['claim', 'release', 'provided', 'delete', 'add-image']},
                LostItemClaimModal: true,
                LostItemCreateModal: true,
            },
        },
    })
    await flushPromises()
    return wrapper
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Releasing a found piece and handing it over ask first, and shift skips the question. */
describe('lost and found ListView', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        listItems.mockReset().mockResolvedValue([ITEM])
        releaseItem.mockReset().mockResolvedValue({})
        markProvided.mockReset().mockResolvedValue(undefined)
    })
    afterEach(() => holdShift(false))

    it('asks before releasing a piece', async () => {
        const wrapper = await list()

        wrapper.findComponent(LostItemCard).vm.$emit('release', 5)
        await wrapper.vm.$nextTick()

        expect(wrapper.findAllComponents(LostItemConfirmModal)[0]!.props('modelValue')).toBe(true)
        expect(releaseItem).not.toHaveBeenCalled()

        wrapper.findAllComponents(LostItemConfirmModal)[0]!.vm.$emit('confirm')
        await vi.waitFor(() => expect(releaseItem).toHaveBeenCalledWith(5))
    })

    it('hands a piece over at once while shift is held and fetches the list again', async () => {
        const wrapper = await list()

        holdShift(true)
        wrapper.findComponent(LostItemCard).vm.$emit('provided', 5)

        await vi.waitFor(() => expect(markProvided).toHaveBeenCalledWith(5))
        await vi.waitFor(() => expect(listItems).toHaveBeenCalledTimes(2))
        expect(wrapper.findAllComponents(LostItemConfirmModal)[1]!.props('modelValue')).toBe(false)
    })

    it('holds the button while a release is on its way', async () => {
        let answer: () => void = () => {}
        releaseItem.mockReturnValue(new Promise<void>(resolve => { answer = resolve }))
        const wrapper = await list()

        wrapper.findComponent(LostItemCard).vm.$emit('release', 5)
        await wrapper.vm.$nextTick()
        wrapper.findAllComponents(LostItemConfirmModal)[0]!.vm.$emit('confirm')
        await wrapper.vm.$nextTick()

        expect(wrapper.findAllComponents(LostItemConfirmModal)[0]!.props('loading')).toBe(true)
        answer()
        await flushPromises()
        expect(wrapper.findAllComponents(LostItemConfirmModal)[0]!.props('loading')).toBe(false)
        expect(releaseItem).toHaveBeenCalledOnce()
    })

    it('lets another piece go ahead while one is on its way', async () => {
        let answer: () => void = () => {}
        releaseItem.mockReturnValue(new Promise<void>(resolve => { answer = resolve }))
        const wrapper = await list()

        holdShift(true)
        wrapper.findComponent(LostItemCard).vm.$emit('release', 5)
        wrapper.findComponent(LostItemCard).vm.$emit('release', 5)
        wrapper.findComponent(LostItemCard).vm.$emit('provided', 6)

        await vi.waitFor(() => expect(markProvided).toHaveBeenCalledWith(6))
        expect(releaseItem).toHaveBeenCalledOnce()
        answer()
        await flushPromises()
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {ref} from 'vue'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import CalendarFeedManageView from './CalendarFeedManageView.vue'

const listCalendarFeeds = vi.fn()
const createCalendarFeed = vi.fn()
const updateCalendarFeed = vi.fn()
const deleteCalendarFeed = vi.fn()
const regenerateCalendarFeed = vi.fn()
vi.mock('@/api', () => ({
    calendarFeeds: {
        listCalendarFeeds: () => listCalendarFeeds(),
        createCalendarFeed: (body: unknown) => createCalendarFeed(body),
        updateCalendarFeed: (id: number, body: unknown) => updateCalendarFeed(id, body),
        deleteCalendarFeed: (id: number) => deleteCalendarFeed(id),
        regenerateCalendarFeed: (id: number) => regenerateCalendarFeed(id),
        stationFeedUrl: (token: string) => `https://ember.example/public/station-feed/${token}/events.ics`,
    },
    events: {listCategories: () => Promise.resolve([{id: 3, name: 'Jugend'}, {id: 4, name: 'Übung'}])},
}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({loaded: ref(true), hasPermission: () => true})}))

const feed = {
    id: 7, name: 'Leinwand', level: 'MEMBERS', token: 'tok', hiddenCategories: [3],
    createdAt: '2026-11-27T10:00:00Z', polledAt: null,
}

async function view() {
    const wrapper = shallowMount(CalendarFeedManageView, {global: {renderStubDefaultSlot: true}})
    await flushPromises()
    return wrapper
}

function saveButton(wrapper: Awaited<ReturnType<typeof view>>) {
    return wrapper.findComponent({name: 'SaveButton'})
}

/** Station calendar feeds are listed, created and changed; reaching restricted appointments asks first. */
describe('CalendarFeedManageView', () => {
    beforeEach(() => {
        listCalendarFeeds.mockReset().mockResolvedValue([feed])
        createCalendarFeed.mockReset().mockResolvedValue(feed)
        updateCalendarFeed.mockReset().mockResolvedValue(feed)
        deleteCalendarFeed.mockReset().mockResolvedValue(undefined)
        regenerateCalendarFeed.mockReset().mockResolvedValue(feed)
    })

    it('lists each feed with the categories it leaves out', async () => {
        const wrapper = await view()
        const row = wrapper.find('[data-testid="calendar-feed-7"]')
        expect(row.text()).toContain('Leinwand')
        expect(row.text()).toContain('Jugend')
        expect(row.text()).not.toContain('Übung')
    })

    it('creates a feed with its name, level and hidden categories', async () => {
        const wrapper = await view()
        wrapper.findComponent({name: 'PrimaryButton'}).vm.$emit('click')
        await wrapper.vm.$nextTick()
        wrapper.findComponent({name: 'TextInput'}).vm.$emit('update:modelValue', 'Führung')
        wrapper.findComponent({name: 'FeedCategoryChoice'}).vm.$emit('update:modelValue', [4])
        await wrapper.vm.$nextTick()

        await saveButton(wrapper).props('action')()

        expect(createCalendarFeed).toHaveBeenCalledWith({name: 'Führung', level: 'MEMBERS', hiddenCategories: [4]})
    })

    it('asks before a feed reaches restricted appointments, and saves only once confirmed', async () => {
        const wrapper = await view()
        wrapper.findComponent({name: 'PrimaryButton'}).vm.$emit('click')
        await wrapper.vm.$nextTick()
        wrapper.findComponent({name: 'TextInput'}).vm.$emit('update:modelValue', 'Alles')
        wrapper.findAllComponents({name: 'SelectionToggleButton'})
            .find(button => button.attributes('data-testid') === 'calendar-feed-level-ALL')!
            .vm.$emit('toggle')
        await wrapper.vm.$nextTick()

        await saveButton(wrapper).props('action')()

        expect(createCalendarFeed).not.toHaveBeenCalled()
        const question = wrapper.findAllComponents({name: 'ConfirmDeleteModal'})
            .find(modal => modal.props('confirmTestId') === 'calendar-feed-everything-confirm')!
        expect(question.props('modelValue')).toBe(true)

        question.vm.$emit('confirm')
        await vi.waitFor(() => expect(createCalendarFeed)
            .toHaveBeenCalledWith({name: 'Alles', level: 'ALL', hiddenCategories: []}))
    })

    it('does not ask again for a feed that already reaches everything', async () => {
        listCalendarFeeds.mockResolvedValue([{...feed, level: 'ALL'}])
        const wrapper = await view()
        wrapper.findAllComponents({name: 'IconButton'})
            .find(button => button.props('icon')[1] === 'pen')!
            .vm.$emit('click')
        await wrapper.vm.$nextTick()

        await saveButton(wrapper).props('action')()

        expect(updateCalendarFeed).toHaveBeenCalledWith(7, {name: 'Leinwand', level: 'ALL', hiddenCategories: [3]})
    })

    async function createAtAll(wrapper: Awaited<ReturnType<typeof view>>) {
        wrapper.findComponent({name: 'PrimaryButton'}).vm.$emit('click')
        await wrapper.vm.$nextTick()
        wrapper.findComponent({name: 'TextInput'}).vm.$emit('update:modelValue', 'Alles')
        wrapper.findAllComponents({name: 'SelectionToggleButton'})
            .find(button => button.attributes('data-testid') === 'calendar-feed-level-ALL')!
            .vm.$emit('toggle')
        await wrapper.vm.$nextTick()
        await saveButton(wrapper).props('action')()
        return wrapper.findAllComponents({name: 'ConfirmDeleteModal'})
            .find(modal => modal.props('confirmTestId') === 'calendar-feed-everything-confirm')!
    }

    it('saves nothing when the question about restricted appointments is turned down', async () => {
        const wrapper = await view()
        const question = await createAtAll(wrapper)

        question.vm.$emit('update:modelValue', false)
        await flushPromises()

        expect(createCalendarFeed).not.toHaveBeenCalled()
    })

    it('does not ask when a feed goes down from everything to members', async () => {
        listCalendarFeeds.mockResolvedValue([{...feed, level: 'ALL'}])
        const wrapper = await view()
        wrapper.findAllComponents({name: 'IconButton'})
            .find(button => button.props('icon')[1] === 'pen')!
            .vm.$emit('click')
        await wrapper.vm.$nextTick()
        wrapper.findAllComponents({name: 'SelectionToggleButton'})
            .find(button => button.attributes('data-testid') === 'calendar-feed-level-MEMBERS')!
            .vm.$emit('toggle')
        await wrapper.vm.$nextTick()

        await saveButton(wrapper).props('action')()

        expect(updateCalendarFeed).toHaveBeenCalledWith(7, {name: 'Leinwand', level: 'MEMBERS', hiddenCategories: [3]})
    })

    it('shows a refused save in the dialog, which stays open', async () => {
        createCalendarFeed.mockRejectedValueOnce({isAxiosError: true, response: {status: 400, data: {code: 'FD-009'}}})
        const wrapper = await view()
        const question = await createAtAll(wrapper)

        question.vm.$emit('confirm')
        await flushPromises()

        const failures = wrapper.findAllComponents({name: 'FailureAlert'})
        expect(failures.some(alert => alert.props('failure') !== null)).toBe(true)
        expect(wrapper.findComponent({name: 'Modal'}).props('modelValue')).toBe(true)
    })

    it('deletes and renews the feed asked about', async () => {
        const wrapper = await view()

        wrapper.findComponent({name: 'DeleteButton'}).vm.$emit('click')
        await wrapper.vm.$nextTick()
        wrapper.findAllComponents({name: 'ConfirmDeleteModal'})
            .find(modal => modal.props('message').includes('Leinwand'))!
            .vm.$emit('confirm')
        await vi.waitFor(() => expect(deleteCalendarFeed).toHaveBeenCalledWith(7))

        wrapper.findAllComponents({name: 'IconButton'})
            .find(button => button.props('icon')[1] === 'rotate')!
            .vm.$emit('click')
        await wrapper.vm.$nextTick()
        wrapper.findAllComponents({name: 'ConfirmDeleteModal'})
            .find(modal => modal.props('confirmIcon')?.[1] === 'rotate')!
            .vm.$emit('confirm')
        await vi.waitFor(() => expect(regenerateCalendarFeed).toHaveBeenCalledWith(7))
    })
})

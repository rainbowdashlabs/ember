/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {StationPermission, WaitingListEntryStatus, type WaitingListEntryWithScore} from '@/api/generated/schema'
import EntryDetailView from './EntryDetailView.vue'

const listEntries = vi.fn()
const listFields = vi.fn()
const deleteEntry = vi.fn()
const refreshSidebarCounts = vi.fn()
const push = vi.fn()
const granted = new Set<string>()

vi.mock('vue-router', async (original) => ({
    ...await original<typeof import('vue-router')>(),
    useRoute: () => ({params: {id: '3', entryId: '7'}}),
    useRouter: () => ({push}),
}))

vi.mock('@/api', () => ({
    waitingList: {
        listEntries: (...args: unknown[]) => listEntries(...args),
        listFields: (...args: unknown[]) => listFields(...args),
        deleteEntry: (...args: unknown[]) => deleteEntry(...args),
    },
}))

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: (permission: string) => granted.has(permission), loaded: ref(true)}),
}))

vi.mock('@/composables/useSidebarCounts', () => ({
    useSidebarCounts: () => ({refresh: refreshSidebarCounts}),
}))

function entryIn(status: WaitingListEntryStatus): WaitingListEntryWithScore {
    return {
        age: null,
        belowJoinAge: false,
        score: 0,
        values: [],
        guardians: [],
        entry: {
            id: 7,
            listId: 3,
            firstname: 'Mia',
            lastname: 'Muster',
            email: 'mia@example.test',
            notes: '',
            parentName: '',
            status,
            accessToken: 'token',
            answer: null,
            attendanceCount: 0,
            invitation: null,
            memberId: status === WaitingListEntryStatus.JOINED ? 12 : null,
            createdAt: '2026-01-01T08:00:00Z',
            confirmedAt: '2026-01-01T08:00:00Z',
            invitedAt: null,
            testingAt: null,
            joinedAt: status === WaitingListEntryStatus.JOINED ? '2026-03-01T08:00:00Z' : null,
            withdrawnAt: null,
            reminderSentAt: null,
        },
    }
}

async function openEntry(status: WaitingListEntryStatus) {
    listEntries.mockResolvedValue([entryIn(status)])
    const view = await mountSuspended(EntryDetailView)
    await flushPromises()
    return view
}

/** The entry's own page offers deleting it to those allowed to edit the list, whatever its status. */
describe('EntryDetailView', () => {
    beforeEach(() => {
        granted.clear()
        listEntries.mockReset()
        listFields.mockReset().mockResolvedValue([])
        deleteEntry.mockReset().mockResolvedValue(undefined)
        refreshSidebarCounts.mockReset()
        push.mockReset()
    })

    it('offers no deletion without the right to edit the list', async () => {
        const view = await openEntry(WaitingListEntryStatus.WAITING)

        expect(view.text()).toContain('Mia Muster')
        expect(view.find('[data-testid="delete-entry"]').exists()).toBe(false)
    })

    it.each([WaitingListEntryStatus.PENDING, WaitingListEntryStatus.TESTING, WaitingListEntryStatus.JOINED])(
        'deletes an entry in status %s after asking, then returns to the list',
        async status => {
            granted.add(StationPermission.WAITLIST_EDIT)
            const view = await openEntry(status)

            await view.find('[data-testid="delete-entry"]').trigger('click')
            const modal = view.findComponent(ConfirmDeleteModal)
            expect(modal.props('modelValue')).toBe(true)
            expect(modal.props('message')).toContain('Mia Muster')
            expect(deleteEntry).not.toHaveBeenCalled()

            modal.vm.$emit('confirm')
            await flushPromises()

            expect(deleteEntry).toHaveBeenCalledWith(3, 7)
            expect(refreshSidebarCounts).toHaveBeenCalled()
            expect(push).toHaveBeenCalledWith({name: 'waiting-list-detail', params: {id: 3}})
        },
    )
})

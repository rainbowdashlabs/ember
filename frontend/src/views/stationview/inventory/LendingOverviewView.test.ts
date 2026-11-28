/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {LendingRequestResponse, LendingStatus} from '@/api/generated/schema'
import BorrowColumn from './lendingoverviewview/BorrowColumn.vue'
import LendColumn from './lendingoverviewview/LendColumn.vue'
import RequestsTable from './lendingoverviewview/RequestsTable.vue'
import DeclineRequestModal from './lendingrequestview/DeclineRequestModal.vue'
import LendingOverviewView from './LendingOverviewView.vue'

const listRequests = vi.fn()
const approveRequest = vi.fn()
const declineRequest = vi.fn()
const remind = vi.fn()
vi.mock('@/api/lending', () => ({
    getOffer: () => Promise.resolve([]),
    remind: (id: number) => remind(id),
    listRequests: () => listRequests(),
    approveRequest: (id: number) => approveRequest(id),
    declineRequest: (id: number, reason: string) => declineRequest(id, reason),
}))
const listBorrowed = vi.fn()
vi.mock('@/api', () => ({inventory: {listBorrowed: () => listBorrowed()}}))

const permissions = ref<string[]>([])
vi.mock('@/composables/useSession', () => ({
    useSession: () => ({loaded: ref(true), hasPermission: (permission: string) => permissions.value.includes(permission)}),
}))
vi.mock('@/composables/useSidebarCounts', () => ({useSidebarCounts: () => ({refresh: vi.fn()})}))

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

function entry(id: number, isOwner: boolean, status: LendingStatus): LendingRequestResponse {
    return {
        allowedSteps: isOwner && status === 'REQUESTED' ? ['APPROVED', 'DECLINED'] : [],
        isOwner, overdue: false, itemSummary: '', owningStationName: 'Nord', requestingStationName: 'Süd',
        request: {
            id, status, requestedDateFrom: '2099-01-01', requestedDateTo: null, createdAt: '2030-01-01T00:00:00Z',
            createdBy: null, eventDate: null, eventId: null, occasion: '', owningStationUid: 'a', requestingStationUid: 'b', uid: String(id),
        },
    } as LendingRequestResponse
}

async function overview() {
    const wrapper = shallowMount(LendingOverviewView, {global: {plugins: [i18n], renderStubDefaultSlot: true}})
    await flushPromises()
    return wrapper
}

/** The overview shows each side to whoever may see it and answers a request where it stands. */
describe('LendingOverviewView', () => {
    beforeEach(() => {
        permissions.value = ['INVENTORY_LENDING_MANAGER', 'INVENTORY_READ']
        listRequests.mockReset().mockResolvedValue([entry(1, true, 'REQUESTED'), entry(2, false, 'REQUESTED')])
        listBorrowed.mockReset().mockResolvedValue([])
        approveRequest.mockReset().mockResolvedValue({})
        declineRequest.mockReset().mockResolvedValue({})
    })

    it('shows both sides and the table to a lending manager', async () => {
        const wrapper = await overview()

        expect(wrapper.findComponent(LendColumn).props('blocks').toAnswer.map((e: LendingRequestResponse) => e.request.id)).toEqual([1])
        expect(wrapper.findComponent(BorrowColumn).props('blocks')!.waiting.map((e: LendingRequestResponse) => e.request.id)).toEqual([2])
        expect(wrapper.findComponent(RequestsTable).exists()).toBe(true)
    })

    it('shows only the gear at the station to whoever may only read the inventory', async () => {
        permissions.value = ['INVENTORY_READ']
        const wrapper = await overview()

        expect(listRequests).not.toHaveBeenCalled()
        expect(wrapper.findComponent(LendColumn).exists()).toBe(false)
        expect(wrapper.findComponent(BorrowColumn).props('blocks')).toBeNull()
        expect(wrapper.findComponent(RequestsTable).exists()).toBe(false)
    })

    it('shows the borrow side and the table to whoever may ask for gear, and no lend side', async () => {
        permissions.value = ['INVENTORY_LENDING_REQUEST', 'INVENTORY_READ']
        const wrapper = await overview()

        expect(wrapper.findComponent(LendColumn).exists()).toBe(false)
        expect(wrapper.findComponent(BorrowColumn).props('blocks')).not.toBeNull()
        expect(wrapper.findComponent(RequestsTable).exists()).toBe(true)
    })

    it('leaves the gear at the station out for whoever may ask but not read the inventory', async () => {
        permissions.value = ['INVENTORY_LENDING_REQUEST']
        const wrapper = await overview()

        expect(listBorrowed).not.toHaveBeenCalled()
        expect(wrapper.findComponent(BorrowColumn).props('reads')).toBe(false)
    })

    it('approves and declines right on the overview', async () => {
        const wrapper = await overview()

        wrapper.findComponent(LendColumn).vm.$emit('approve', 1)
        await flushPromises()
        expect(approveRequest).toHaveBeenCalledWith(1)
        expect(listRequests).toHaveBeenCalledTimes(2)

        wrapper.findComponent(LendColumn).vm.$emit('decline', 1)
        await flushPromises()
        expect(wrapper.findComponent(DeclineRequestModal).props('modelValue')).toBe(true)
        wrapper.findComponent(DeclineRequestModal).vm.$emit('decline', 'Kein Platz')
        await flushPromises()
        expect(declineRequest).toHaveBeenCalledWith(1, 'Kein Platz')
        expect(wrapper.findComponent(DeclineRequestModal).props('modelValue')).toBe(false)
    })

    it('reminds of overdue gear right on the overview', async () => {
        remind.mockReset().mockResolvedValue(undefined)
        const wrapper = await overview()

        wrapper.findComponent(LendColumn).vm.$emit('remind', 1)
        await flushPromises()
        expect(remind).toHaveBeenCalledWith(1)
    })

    it('keeps the decline dialog open with its failure where declining fails', async () => {
        declineRequest.mockRejectedValue(new Error('nope'))
        const wrapper = await overview()

        wrapper.findComponent(LendColumn).vm.$emit('decline', 1)
        await flushPromises()
        wrapper.findComponent(DeclineRequestModal).vm.$emit('decline', '')
        await flushPromises()
        const dialog = wrapper.findComponent(DeclineRequestModal)
        expect(dialog.props('modelValue')).toBe(true)
        expect(dialog.props('failure')).not.toBeNull()
    })
})

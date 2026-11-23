/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import {followModifierKeys} from '@/util/modifierKeys'
import AdminProblemReportsView from './AdminProblemReportsView.vue'
import ReportCard from './adminproblemreportsview/ReportCard.vue'

const deleteReport = vi.fn()
vi.mock('@/api/problemReports', () => ({
    listReports: async () => [{id: 4, acknowledged: false, screenshotFileId: null, forwardedAt: null}],
    deleteReport: (id: number) => deleteReport(id),
    acknowledgeReport: vi.fn(),
    acknowledgeAllReports: vi.fn(),
}))
vi.mock('@/api', () => ({beacon: {getStatus: async () => ({enabled: false})}}))
vi.mock('@/composables/useMonitoringCounts', () => ({useMonitoringCounts: () => ({refresh: async () => {}})}))

async function view() {
    const wrapper = shallowMount(AdminProblemReportsView, {global: {renderStubDefaultSlot: true}})
    await flushPromises()
    return wrapper
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Deleting a problem report asks first, and shift skips the question. */
describe('AdminProblemReportsView', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        deleteReport.mockReset().mockResolvedValue(undefined)
    })
    afterEach(() => holdShift(false))

    it('asks before deleting a report', async () => {
        const wrapper = await view()

        wrapper.findComponent(ReportCard).vm.$emit('remove', 4)
        await wrapper.vm.$nextTick()

        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(true)
        expect(deleteReport).not.toHaveBeenCalled()

        wrapper.findComponent({name: 'ConfirmDeleteModal'}).vm.$emit('confirm')
        await vi.waitFor(() => expect(deleteReport).toHaveBeenCalledWith(4))
    })

    it('deletes at once while shift is held and drops the row', async () => {
        const wrapper = await view()

        holdShift(true)
        wrapper.findComponent(ReportCard).vm.$emit('remove', 4)

        await vi.waitFor(() => expect(wrapper.findComponent(ReportCard).exists()).toBe(false))
        expect(deleteReport).toHaveBeenCalledWith(4)
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })
})

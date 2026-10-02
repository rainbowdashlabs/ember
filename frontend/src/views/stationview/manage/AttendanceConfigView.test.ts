/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import AttendanceConfigView from './AttendanceConfigView.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'

const listTemplates = vi.fn()
const deleteTemplate = vi.fn()

vi.mock('@/api', () => ({
    attendance: {
        listTemplates: (...args: unknown[]) => listTemplates(...args),
        deleteTemplate: (...args: unknown[]) => deleteTemplate(...args),
    },
}))

/**
 * Deleting a template once took every sheet made from it, with everybody's attendance. The question
 * asked before it is the last place a reader learns what deleting does, so it has to say that the
 * sheets stay.
 */
describe('AttendanceConfigView', () => {
    beforeEach(() => {
        listTemplates.mockReset()
        deleteTemplate.mockReset()
        listTemplates.mockResolvedValue([{id: 3, stationId: '7f3c2a10-0000-4000-8000-000000000001', name: 'Sommerlager'}])
        deleteTemplate.mockResolvedValue(undefined)
    })

    it('says before deleting that the template is archived and its sheets stay', async () => {
        const wrapper = await mountSuspended(AttendanceConfigView)
        await new Promise(resolve => setTimeout(resolve, 0))
        await wrapper.vm.$nextTick()

        await wrapper.findComponent(DeleteButton).vm.$emit('click', new MouseEvent('click'))
        await wrapper.vm.$nextTick()

        const message = wrapper.findComponent(ConfirmDeleteModal).props('message') as string
        expect(message).toContain('Sommerlager')
        expect(message).toContain('archiviert')
        expect(message).toContain('bleiben')
        expect(message).not.toContain('ebenfalls gelöscht')
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {shallowMount} from '@vue/test-utils'
import {ref} from 'vue'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import type {BoardTicketAttachment} from '@/api/generated/schema'
import IconButton from '@/components/button/IconButton.vue'
import {followModifierKeys} from '@/util/modifierKeys'
import TicketLinksSection from './TicketLinksSection.vue'

const deleteAttachment = vi.fn()
vi.mock('@/api', () => ({
    boards: {deleteAttachment: (key: string, ticket: number, id: number) => deleteAttachment(key, ticket, id)},
}))
vi.mock('@/composables/useBoardApi', () => ({useBoardApi: () => ({})}))
vi.mock('@/views/stationview/boards/boardview/useAttachmentPreview', () => ({
    useAttachmentPreview: () => ({
        srcFor: () => null, url: ref(null), name: ref(''), csv: ref(null), shown: ref(false), index: ref(0),
        isImage: () => false, canPreview: () => false, fileIcon: () => ['fas', 'file'],
        open: () => {}, previous: () => {}, next: () => {}, download: () => {}, downloadCurrent: () => {},
    }),
}))

const ATTACHMENT = {id: 6, originalName: 'Plan.pdf', sizeBytes: 10} as unknown as BoardTicketAttachment

function section() {
    return shallowMount(TicketLinksSection, {
        props: {
            boardKey: 'FW', ticketNumber: 3, shortKey: 'FW', links: [], weblinks: [], attachments: [ATTACHMENT],
            allTickets: [], lanes: [], members: [], priorityOptions: [], showAddLink: false, showAddWeblink: false,
        },
        global: {renderStubDefaultSlot: true},
    })
}

function trash(wrapper: ReturnType<typeof section>) {
    return wrapper.findAllComponents(IconButton).find(button => (button.props('icon') as string[])[1] === 'trash')!
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Deleting an attachment of a ticket asks first, and shift skips the question. */
describe('TicketLinksSection', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        deleteAttachment.mockReset().mockResolvedValue(undefined)
    })
    afterEach(() => holdShift(false))

    it('asks before deleting an attachment, naming it', async () => {
        const wrapper = section()

        trash(wrapper).vm.$emit('click')
        await wrapper.vm.$nextTick()

        const question = wrapper.findComponent({name: 'ConfirmDeleteModal'})
        expect(question.props('modelValue')).toBe(true)
        expect(question.props('message')).toContain('Plan.pdf')
        expect(deleteAttachment).not.toHaveBeenCalled()

        question.vm.$emit('confirm')
        await vi.waitFor(() => expect(wrapper.emitted('reload')).toHaveLength(1))
        expect(deleteAttachment).toHaveBeenCalledWith('FW', 3, 6)
    })

    it('deletes at once while shift is held', async () => {
        const wrapper = section()

        holdShift(true)
        trash(wrapper).vm.$emit('click')

        await vi.waitFor(() => expect(wrapper.emitted('reload')).toHaveLength(1))
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })

    it('says why an attachment could not be deleted', async () => {
        deleteAttachment.mockRejectedValue(new Error('refused'))
        const wrapper = section()

        holdShift(true)
        trash(wrapper).vm.$emit('click')

        await vi.waitFor(() => expect(wrapper.findComponent({name: 'FailureAlert'}).props('failure')).not.toBeNull())
        expect(wrapper.emitted('reload')).toBeUndefined()
    })
})

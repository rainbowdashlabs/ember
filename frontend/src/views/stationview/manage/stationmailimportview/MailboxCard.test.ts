/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {shallowMount} from '@vue/test-utils'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import type {MailboxResponse} from '@/api/generated/schema'
import {followModifierKeys} from '@/util/modifierKeys'
import MailboxCard from './MailboxCard.vue'
import MailboxHeader from './MailboxHeader.vue'

const deleteMailbox = vi.fn()
vi.mock('@/api', () => ({mailImport: {deleteMailbox: (id: number) => deleteMailbox(id)}}))

const MAILBOX = {id: 3, folder: 'INBOX', lastError: null} as unknown as MailboxResponse

function card() {
    return shallowMount(MailboxCard, {props: {mailbox: MAILBOX, supportedTypes: []}, global: {renderStubDefaultSlot: true}})
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Deleting a mailbox asks first, and shift skips the question. */
describe('MailboxCard', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        deleteMailbox.mockReset().mockResolvedValue(undefined)
    })
    afterEach(() => holdShift(false))

    it('asks before deleting the mailbox', async () => {
        const wrapper = card()

        wrapper.findComponent(MailboxHeader).vm.$emit('remove')
        await wrapper.vm.$nextTick()

        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(true)
        expect(deleteMailbox).not.toHaveBeenCalled()

        wrapper.findComponent({name: 'ConfirmDeleteModal'}).vm.$emit('confirm')
        await vi.waitFor(() => expect(wrapper.emitted('changed')).toHaveLength(1))
        expect(deleteMailbox).toHaveBeenCalledWith(3)
    })

    it('deletes at once while shift is held', async () => {
        const wrapper = card()

        holdShift(true)
        wrapper.findComponent(MailboxHeader).vm.$emit('remove')

        await vi.waitFor(() => expect(wrapper.emitted('changed')).toHaveLength(1))
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })

    it('passes a refusal on to the page', async () => {
        deleteMailbox.mockRejectedValue(new Error('refused'))
        const wrapper = card()

        holdShift(true)
        wrapper.findComponent(MailboxHeader).vm.$emit('remove')

        await vi.waitFor(() => expect(wrapper.emitted('error')).toHaveLength(1))
        expect(wrapper.emitted('changed')).toBeUndefined()
    })
})

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {followModifierKeys} from '@/util/modifierKeys'
import AbsencesTab from './AbsencesTab.vue'

const listMemberAbsences = vi.fn()
const deleteMemberAbsence = vi.fn()
vi.mock('@/api/absences', () => ({
    listMemberAbsences: (id: number) => listMemberAbsences(id),
    deleteMemberAbsence: (id: number) => deleteMemberAbsence(id),
    createMemberAbsence: vi.fn(),
}))

const ABSENCE = {id: 8, memberId: 2, absentFrom: '2026-12-01', absentUntil: '2026-12-10', reason: null}

async function tab() {
    const wrapper = shallowMount(AbsencesTab, {props: {memberId: 2}, global: {renderStubDefaultSlot: true}})
    await flushPromises()
    return wrapper
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Deleting a member's absence asks first, and shift skips the question. */
describe('AbsencesTab', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        listMemberAbsences.mockReset().mockResolvedValue([ABSENCE])
        deleteMemberAbsence.mockReset().mockResolvedValue(undefined)
    })
    afterEach(() => holdShift(false))

    it('asks before deleting an absence', async () => {
        const wrapper = await tab()

        wrapper.findComponent(DeleteButton).vm.$emit('click')
        await wrapper.vm.$nextTick()

        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(true)
        expect(deleteMemberAbsence).not.toHaveBeenCalled()

        wrapper.findComponent({name: 'ConfirmDeleteModal'}).vm.$emit('confirm')
        await vi.waitFor(() => expect(deleteMemberAbsence).toHaveBeenCalledWith(8))
    })

    it('deletes at once while shift is held and reads the list again', async () => {
        const wrapper = await tab()

        holdShift(true)
        wrapper.findComponent(DeleteButton).vm.$emit('click')

        await vi.waitFor(() => expect(listMemberAbsences).toHaveBeenCalledTimes(2))
        expect(deleteMemberAbsence).toHaveBeenCalledWith(8)
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })
})

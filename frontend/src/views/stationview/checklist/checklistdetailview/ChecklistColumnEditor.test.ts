/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import type {ColumnResponse} from '@/api/generated/schema'
import {followModifierKeys} from '@/util/modifierKeys'
import ChecklistColumnEditor from './ChecklistColumnEditor.vue'

const getChecklist = vi.fn()
const deleteColumn = vi.fn()
vi.mock('@/api', () => ({
    checklists: {
        getChecklist: (id: number) => getChecklist(id),
        deleteColumn: (id: number, columnId: number) => deleteColumn(id, columnId),
    },
}))

const COLUMN = {id: 4, label: 'Helm', description: '', position: 0} as unknown as ColumnResponse

/** The editor's own handle on the question, as its delete button calls it. */
interface Editor {
    askDelete: () => Promise<void>
}

function editor() {
    return shallowMount(ChecklistColumnEditor, {props: {checklistId: 2, column: COLUMN, totalColumns: 1}})
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Deleting a column says how many ticks go with it first, and shift skips the question. */
describe('ChecklistColumnEditor', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        getChecklist.mockReset().mockResolvedValue({cells: [{columnId: 4, checked: true}, {columnId: 4, checked: false}]})
        deleteColumn.mockReset().mockResolvedValue(undefined)
    })
    afterEach(() => holdShift(false))

    it('asks with the count of ticks and deletes once agreed', async () => {
        const wrapper = editor()

        await (wrapper.vm as unknown as Editor).askDelete()
        await flushPromises()

        const question = wrapper.findComponent({name: 'ConfirmDeleteModal'})
        expect(question.props('modelValue')).toBe(true)
        expect(getChecklist).toHaveBeenCalledWith(2)
        expect(question.props('message')).toContain('1')
        expect(deleteColumn).not.toHaveBeenCalled()

        question.vm.$emit('confirm')
        await vi.waitFor(() => expect(wrapper.emitted('changed')).toHaveLength(1))
        expect(deleteColumn).toHaveBeenCalledWith(2, 4)
    })

    it('deletes at once while shift is held, without counting', async () => {
        const wrapper = editor()

        holdShift(true)
        await (wrapper.vm as unknown as Editor).askDelete()

        await vi.waitFor(() => expect(wrapper.emitted('changed')).toHaveLength(1))
        expect(deleteColumn).toHaveBeenCalledWith(2, 4)
        expect(getChecklist).not.toHaveBeenCalled()
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })
})

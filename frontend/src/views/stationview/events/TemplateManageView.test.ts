/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {ref} from 'vue'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {followModifierKeys} from '@/util/modifierKeys'
import TemplateManageView from './TemplateManageView.vue'

const listTemplates = vi.fn()
const deleteTemplate = vi.fn()
vi.mock('@/api', () => ({
    events: {
        listTemplates: () => listTemplates(),
        deleteTemplate: (id: number) => deleteTemplate(id),
    },
}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({loaded: ref(true), hasPermission: () => true})}))
vi.mock('vue-router', () => ({useRouter: () => ({push: vi.fn()})}))

async function view() {
    const wrapper = shallowMount(TemplateManageView, {global: {renderStubDefaultSlot: true}})
    await flushPromises()
    return wrapper
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Deleting an appointment template asks first, and shift skips the question. */
describe('TemplateManageView', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        listTemplates.mockReset().mockResolvedValue([{id: 5, name: 'Übung'}])
        deleteTemplate.mockReset().mockResolvedValue(undefined)
    })
    afterEach(() => holdShift(false))

    it('asks before deleting a template, naming it', async () => {
        const wrapper = await view()

        wrapper.findComponent(DeleteButton).vm.$emit('click')
        await wrapper.vm.$nextTick()

        const question = wrapper.findComponent({name: 'ConfirmDeleteModal'})
        expect(question.props('modelValue')).toBe(true)
        expect(question.props('message')).toContain('Übung')
        expect(deleteTemplate).not.toHaveBeenCalled()

        question.vm.$emit('confirm')
        await vi.waitFor(() => expect(deleteTemplate).toHaveBeenCalledWith(5))
    })

    it('deletes at once while shift is held and reads the list again', async () => {
        const wrapper = await view()

        holdShift(true)
        wrapper.findComponent(DeleteButton).vm.$emit('click')

        await vi.waitFor(() => expect(listTemplates).toHaveBeenCalledTimes(2))
        expect(deleteTemplate).toHaveBeenCalledWith(5)
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })
})

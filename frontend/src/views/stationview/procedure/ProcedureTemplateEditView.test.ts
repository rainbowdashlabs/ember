/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {ref} from 'vue'
import {afterEach, beforeAll, beforeEach, describe, expect, it, vi} from 'vitest'
import {followModifierKeys} from '@/util/modifierKeys'
import ProcedureTemplateEditView from './ProcedureTemplateEditView.vue'
import TemplateItemCard from './proceduretemplateeditview/TemplateItemCard.vue'

const getTemplate = vi.fn()
const deleteTemplateItem = vi.fn()
vi.mock('@/api', () => ({
    procedures: {
        getTemplate: (id: number) => getTemplate(id),
        deleteTemplateItem: (templateId: number, itemId: number) => deleteTemplateItem(templateId, itemId),
        dependencyEntries: () => [],
    },
}))
vi.mock('@/components/procedures/partyChoices', () => ({
    loadPartyChoices: async () => ({}),
    noPartyChoices: () => ({}),
}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({loaded: ref(true), hasPermission: () => true})}))
vi.mock('vue-router', () => ({useRoute: () => ({params: {id: '2'}}), useRouter: () => ({push: vi.fn()})}))

const DETAIL = {
    template: {id: 2, name: 'Neuaufnahme', description: null},
    items: [{id: 9, title: 'Spind zuteilen'}],
    dependencies: [],
    documents: [],
    party: null,
}

async function view() {
    const wrapper = shallowMount(ProcedureTemplateEditView, {global: {renderStubDefaultSlot: true}})
    await flushPromises()
    return wrapper
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

/** Deleting a step of a procedure template asks first, and shift skips the question. */
describe('ProcedureTemplateEditView', () => {
    beforeAll(() => followModifierKeys())
    beforeEach(() => {
        getTemplate.mockReset().mockResolvedValue(DETAIL)
        deleteTemplateItem.mockReset().mockResolvedValue(undefined)
    })
    afterEach(() => holdShift(false))

    it('asks before deleting a step, naming it', async () => {
        const wrapper = await view()

        wrapper.findComponent(TemplateItemCard).vm.$emit('delete', 9)
        await wrapper.vm.$nextTick()

        const question = wrapper.findComponent({name: 'ConfirmDeleteModal'})
        expect(question.props('modelValue')).toBe(true)
        expect(question.props('message')).toContain('Spind zuteilen')
        expect(deleteTemplateItem).not.toHaveBeenCalled()

        question.vm.$emit('confirm')
        await vi.waitFor(() => expect(deleteTemplateItem).toHaveBeenCalledWith(2, 9))
    })

    it('deletes at once while shift is held and reads the template again', async () => {
        const wrapper = await view()

        holdShift(true)
        wrapper.findComponent(TemplateItemCard).vm.$emit('delete', 9)

        await vi.waitFor(() => expect(getTemplate).toHaveBeenCalledTimes(2))
        expect(deleteTemplateItem).toHaveBeenCalledWith(2, 9)
        expect(wrapper.findComponent({name: 'ConfirmDeleteModal'}).props('modelValue')).toBe(false)
    })
})

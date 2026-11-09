/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import type {MemberCompletion, ProcedureTemplate} from '@/api/generated/schema'
import {fromCompletion} from '@/components/input/select/memberOption'
import {procedures} from '@/api'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ProcedureFromTemplateModal from './ProcedureFromTemplateModal.vue'

const push = vi.fn()

vi.mock('vue-router', async (original) => ({
    ...await original<typeof import('vue-router')>(),
    useRouter: () => ({push}),
}))

vi.mock('@/api', () => ({
    procedures: {createProceduresForMembers: vi.fn()},
}))

const TEMPLATE: ProcedureTemplate = {
    id: 4,
    stationId: '00000000-0000-4000-a000-000000000001',
    name: 'Neues Mitglied aufnehmen',
    description: 'Aufnahme mit Antrag.',
    archived: false,
    createdBy: 1,
    createdAt: '2026-11-01T10:00:00Z',
}

const LEON: MemberCompletion = {
    id: 21, name: 'Leon Lang', stationUid: 'demo', memberUid: 'm-21', stationName: null, nameColor: null, displayTag: null,
}

async function mountModal() {
    return mountSuspended(ProcedureFromTemplateModal, {
        props: {modelValue: true, template: TEMPLATE, members: [fromCompletion(LEON)]},
        global: {stubs: {
            Modal: {template: '<div><slot/></div>'},
            MemberSelectInput: {
                props: ['modelValue'],
                emits: ['update:modelValue'],
                template: `<button type="button" @click="$emit('update:modelValue', '21')"/>`,
            },
        }},
    })
}

/**
 * A procedure from a template asks only whom it is about and when it is due. The server names it after the
 * template and the person; the dialog previews that name.
 */
describe('ProcedureFromTemplateModal', () => {
    beforeEach(() => {
        push.mockReset()
        vi.mocked(procedures.createProceduresForMembers).mockReset()
    })

    it('starts nothing before somebody is chosen', async () => {
        const view = await mountModal()

        expect(view.find('[data-testid="procedure-from-template-submit"]').attributes('disabled')).toBeDefined()
        await view.find('form').trigger('submit')
        expect(procedures.createProceduresForMembers).not.toHaveBeenCalled()
    })

    it('starts the procedure from the template for the person, previews its name, and opens it', async () => {
        vi.mocked(procedures.createProceduresForMembers).mockResolvedValue([{id: 9}] as never)
        const view = await mountModal()

        await view.find('[data-testid="procedure-from-template-about"]').trigger('click')
        expect(view.find('[data-testid="procedure-from-template-name"]').text()).toContain('Neues Mitglied aufnehmen: Leon Lang')
        await view.find('form').trigger('submit')
        await flushPromises()

        expect(procedures.createProceduresForMembers).toHaveBeenCalledWith({
            templateId: 4,
            memberIds: [21],
            name: 'Neues Mitglied aufnehmen',
            description: 'Aufnahme mit Antrag.',
            dueAt: undefined,
            isPublic: true,
        })
        expect(push).toHaveBeenCalledWith({name: 'procedure-detail', params: {id: 9}})
    })

    it('shows a refusal and stays open', async () => {
        vi.mocked(procedures.createProceduresForMembers).mockRejectedValue(new Error('Nicht erlaubt'))
        const view = await mountModal()

        await view.find('[data-testid="procedure-from-template-about"]').trigger('click')
        await view.find('form').trigger('submit')
        await flushPromises()

        expect(view.findComponent(FailureAlert).props('failure')).not.toBeNull()
        expect(push).not.toHaveBeenCalled()
    })
})

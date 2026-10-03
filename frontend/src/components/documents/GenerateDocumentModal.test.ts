/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, type VueWrapper} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import GenerateDocumentModal from './GenerateDocumentModal.vue'

const previewForMember = vi.fn()
const generateForMember = vi.fn()

vi.mock('@/api', () => ({
    documentTemplates: {
        usableTemplates: vi.fn(async () => [
            {id: 1, name: 'Bescheinigung', ofAssociation: false, lastUsedAt: null, forAppointments: false},
            {id: 2, name: 'Einverständnis', ofAssociation: true, lastUsedAt: '2026-10-01T09:00:00Z', forAppointments: false},
            {id: 3, name: 'Ausweis', ofAssociation: false, lastUsedAt: '2026-10-02T09:00:00Z', forAppointments: false},
            {id: 4, name: 'Zeltlager', ofAssociation: false, lastUsedAt: '2026-10-03T09:00:00Z', forAppointments: true},
        ]),
        previewForMember: (...args: unknown[]) => previewForMember(...args),
        generateForMember: (...args: unknown[]) => generateForMember(...args),
    },
}))

async function mountDialog(props: Record<string, unknown>) {
    const dialog = await mountSuspended(GenerateDocumentModal, {
        props: {modelValue: true, ...props},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}, GeneratedPreview: true}},
    })
    await flushPromises()
    return dialog
}

async function templateChoices() {
    const dialog = await mountDialog({memberId: 4})
    return dialog.findAll('[data-testid="generate-template"] option:not([disabled])').map(option => option.text())
}

async function chooseTemplate(dialog: VueWrapper, id: number) {
    await dialog.find('[data-testid="generate-template"]').setValue(String(id))
    await flushPromises()
}

/**
 * A station generates from its own templates and from those of its association, which only the
 * association changes; the choice says which is which, and offers what was used last first. A member's
 * page names the member; the document store asks for one, and draws nothing until somebody is chosen.
 */
describe('GenerateDocumentModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        previewForMember.mockResolvedValue({missing: [], pdfBase64: '', unprintable: []})
        generateForMember.mockResolvedValue({documentId: 42, generationId: 7, missing: [], title: 'Ausweis'})
    })

    it('marks the templates of the association', async () => {
        const options = await templateChoices()

        expect(options).toContain('Bescheinigung')
        expect(options).toContain('Einverständnis (Vom Verband)')
    })

    it('lists the templates used most recently first and leaves out those for appointments', async () => {
        expect(await templateChoices()).toEqual(['Ausweis', 'Einverständnis (Vom Verband)', 'Bescheinigung'])
    })

    it('generates for the member handed in without asking for one', async () => {
        const dialog = await mountDialog({memberId: 4})
        expect(dialog.findComponent(MemberSelectInput).exists()).toBe(false)

        await chooseTemplate(dialog, 3)
        expect(previewForMember).toHaveBeenCalledWith(3, 4)

        await dialog.find('[data-testid="generate-file"]').trigger('click')
        await flushPromises()

        expect(generateForMember).toHaveBeenCalledWith(3, 4)
        expect(dialog.emitted('filed')).toEqual([[42]])
    })

    it('generates for the member chosen in the dialog', async () => {
        const dialog = await mountDialog({members: [{value: '9', name: 'Lena Berg'}]})
        await chooseTemplate(dialog, 1)
        expect(previewForMember).not.toHaveBeenCalled()
        expect(dialog.find<HTMLButtonElement>('[data-testid="generate-file"]').element.disabled).toBe(true)

        dialog.findComponent(MemberSelectInput).vm.$emit('update:modelValue', '9')
        await flushPromises()
        expect(previewForMember).toHaveBeenCalledWith(1, 9)

        await dialog.find('[data-testid="generate-file"]').trigger('click')
        await flushPromises()

        expect(generateForMember).toHaveBeenCalledWith(1, 9)
        expect(dialog.emitted('filed')).toEqual([[42]])
    })
})

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
import IssuerOverride from './IssuerOverride.vue'
import TemplateChoice from './TemplateChoice.vue'

const previewForMember = vi.fn()
const generateForMember = vi.fn()

vi.mock('@/api', () => ({
    documentTemplates: {
        previewForMember: (...args: unknown[]) => previewForMember(...args),
        generateForMember: (...args: unknown[]) => generateForMember(...args),
    },
    stationMembers: {
        listCompletions: vi.fn(async () => []),
    },
}))

async function mountDialog(props: Record<string, unknown>) {
    const dialog = await mountSuspended(GenerateDocumentModal, {
        props: {modelValue: true, ...props},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}, GeneratedPreview: true, TemplateChoice: true}},
    })
    await flushPromises()
    return dialog
}

async function chooseTemplate(dialog: VueWrapper, id: number) {
    dialog.findComponent(TemplateChoice).vm.$emit('update:modelValue', {id, name: `Vorlage ${id}`})
    await flushPromises()
}

/**
 * The template is chosen in the template picker, which leaves out templates for appointments. A
 * member's page names the member; the document store asks for one, and draws nothing until somebody is
 * chosen.
 */
describe('GenerateDocumentModal', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        previewForMember.mockResolvedValue({missing: [], pdfBase64: '', unprintable: []})
        generateForMember.mockResolvedValue({documentId: 42, generationId: 7, missing: [], title: 'Ausweis'})
    })

    it('asks the picker to leave out the templates for appointments', async () => {
        const dialog = await mountDialog({memberId: 4})

        expect(dialog.findComponent(TemplateChoice).props('fixed')).toEqual({forAppointments: false})
    })

    it('generates for the member handed in without asking for one', async () => {
        const dialog = await mountDialog({memberId: 4})
        expect(dialog.findComponent(MemberSelectInput).exists()).toBe(false)

        await chooseTemplate(dialog, 3)
        expect(previewForMember).toHaveBeenCalledWith(3, 4, null)

        await dialog.find('[data-testid="generate-file"]').trigger('click')
        await flushPromises()

        expect(generateForMember).toHaveBeenCalledWith(3, 4, null)
        expect(dialog.emitted('filed')).toEqual([[42]])
    })

    it('generates for the member chosen in the dialog', async () => {
        const dialog = await mountDialog({members: [{value: '9', name: 'Lena Berg'}]})
        await chooseTemplate(dialog, 1)
        expect(previewForMember).not.toHaveBeenCalled()
        expect(dialog.find<HTMLButtonElement>('[data-testid="generate-file"]').element.disabled).toBe(true)

        dialog.findComponent(MemberSelectInput).vm.$emit('update:modelValue', '9')
        await flushPromises()
        expect(previewForMember).toHaveBeenCalledWith(1, 9, null)

        await dialog.find('[data-testid="generate-file"]').trigger('click')
        await flushPromises()

        expect(generateForMember).toHaveBeenCalledWith(1, 9, null)
        expect(dialog.emitted('filed')).toEqual([[42]])
    })

    it('shows the template\'s issuer and files with the one picked instead', async () => {
        previewForMember.mockResolvedValue({
            missing: [],
            pdfBase64: '',
            unprintable: [],
            issuer: {memberId: 5, name: 'Erika Wehr', function: 'Jugendwartin', fixed: true},
        })
        const dialog = await mountDialog({memberId: 4})

        await chooseTemplate(dialog, 1)
        expect(previewForMember).toHaveBeenLastCalledWith(1, 4, null)
        const override = dialog.findComponent(IssuerOverride)
        expect(override.text()).toContain('Erika Wehr, Jugendwartin')

        override.vm.$emit('update:modelValue', {memberId: 7, function: 'Kassenwart'})
        await flushPromises()
        await dialog.find('[data-testid="generate-file"]').trigger('click')
        await flushPromises()

        expect(generateForMember).toHaveBeenCalledWith(1, 4, {memberId: 7, function: 'Kassenwart'})
    })
})

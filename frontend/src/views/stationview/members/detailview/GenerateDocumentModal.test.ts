/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {documentTemplates} from '@/api'
import IssuerOverride from '@/components/documents/IssuerOverride.vue'
import GenerateDocumentModal from './GenerateDocumentModal.vue'

vi.mock('@/api', () => ({
    documentTemplates: {
        usableTemplates: vi.fn(async () => [
            {id: 1, name: 'Bescheinigung', ofAssociation: false, lastUsedAt: null},
            {id: 2, name: 'Einverständnis', ofAssociation: true, lastUsedAt: '2026-10-01T09:00:00Z'},
            {id: 3, name: 'Ausweis', ofAssociation: false, lastUsedAt: '2026-10-02T09:00:00Z'},
        ]),
        previewForMember: vi.fn(async () => ({
            pdfBase64: 'JVBER',
            missing: [],
            unprintable: [],
            issuer: {memberId: 5, name: 'Erika Wehr', function: 'Jugendwartin', fixed: true},
        })),
        generateForMember: vi.fn(),
        pdfOf: vi.fn(() => new Blob()),
    },
    stationMembers: {
        listCompletions: vi.fn(async () => []),
    },
}))

async function dialog() {
    const mounted = await mountSuspended(GenerateDocumentModal, {
        props: {modelValue: true, memberId: 4},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}, GeneratedPreview: true}},
    })
    await flushPromises()
    return mounted
}

async function templateChoices() {
    const mounted = await dialog()
    return mounted.findAll('[data-testid="generate-template"] option:not([disabled])').map(option => option.text())
}

/**
 * A station generates from its own templates and from those of its association, which only the
 * association changes; the choice says which is which, and offers what was used last first.
 */
describe('GenerateDocumentModal', () => {
    it('marks the templates of the association', async () => {
        const options = await templateChoices()

        expect(options).toContain('Bescheinigung')
        expect(options).toContain('Einverständnis (Vom Verband)')
    })

    it('lists the templates used most recently first', async () => {
        expect(await templateChoices()).toEqual(['Ausweis', 'Einverständnis (Vom Verband)', 'Bescheinigung'])
    })

    it('shows the template\'s issuer and files with the one picked instead', async () => {
        const mounted = await dialog()

        await mounted.find('[data-testid="generate-template"]').setValue('1')
        await flushPromises()
        expect(documentTemplates.previewForMember).toHaveBeenLastCalledWith(1, 4, null)
        const override = mounted.findComponent(IssuerOverride)
        expect(override.text()).toContain('Erika Wehr, Jugendwartin')

        override.vm.$emit('update:modelValue', {memberId: 7, function: 'Kassenwart'})
        await flushPromises()
        await mounted.find('[data-testid="generate-file"]').trigger('click')
        await flushPromises()

        expect(documentTemplates.generateForMember).toHaveBeenCalledWith(1, 4, {memberId: 7, function: 'Kassenwart'})
    })
})

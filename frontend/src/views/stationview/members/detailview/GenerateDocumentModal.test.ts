/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import GenerateDocumentModal from './GenerateDocumentModal.vue'

vi.mock('@/api', () => ({
    documentTemplates: {
        usableTemplates: vi.fn(async () => [
            {id: 1, name: 'Bescheinigung', ofAssociation: false},
            {id: 2, name: 'Einverständnis', ofAssociation: true},
        ]),
        previewForMember: vi.fn(),
        generateForMember: vi.fn(),
    },
}))

/**
 * A station generates from its own templates and from those of its association, which only the
 * association changes; the choice says which is which.
 */
describe('GenerateDocumentModal', () => {
    it('marks the templates of the association', async () => {
        const dialog = await mountSuspended(GenerateDocumentModal, {
            props: {modelValue: true, memberId: 4},
            global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
        })
        await flushPromises()

        const options = dialog.findAll('[data-testid="generate-template"] option').map(option => option.text())
        expect(options).toContain('Bescheinigung')
        expect(options).toContain('Einverständnis (Vom Verband)')
    })
})

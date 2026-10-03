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
            {id: 1, name: 'Bescheinigung', ofAssociation: false, lastUsedAt: null},
            {id: 2, name: 'Einverständnis', ofAssociation: true, lastUsedAt: '2026-10-01T09:00:00Z'},
            {id: 3, name: 'Ausweis', ofAssociation: false, lastUsedAt: '2026-10-02T09:00:00Z'},
        ]),
        previewForMember: vi.fn(),
        generateForMember: vi.fn(),
    },
}))

async function templateChoices() {
    const dialog = await mountSuspended(GenerateDocumentModal, {
        props: {modelValue: true, memberId: 4},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
    })
    await flushPromises()
    return dialog.findAll('[data-testid="generate-template"] option:not([disabled])').map(option => option.text())
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
})

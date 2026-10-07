/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {DocumentTemplateKind, RequirementStatus, type AppointmentDocuments, type ParticipantDocuments} from '@/api/generated/schema'
import {appointmentDocuments} from '@/api'
import DocumentsToBringPanel from './DocumentsToBringPanel.vue'

vi.mock('@/api', () => ({
    appointmentDocuments: {documentsToBring: vi.fn()},
}))

const CONSENT = {templateId: 8, name: 'Einverständnis', kind: DocumentTemplateKind.PDF, version: 1, archived: false, lastUsedAt: null}

const lena: ParticipantDocuments = {
    memberId: 11,
    name: 'Lena Schmidt',
    documents: [{templateId: 8, name: 'Einverständnis', status: RequirementStatus.NOT_GENERATED, documentId: null, generatedAt: null, outdated: false}],
}

async function mountPanel(documents: AppointmentDocuments) {
    vi.mocked(appointmentDocuments.documentsToBring).mockResolvedValue(documents)
    const panel = await mountSuspended(DocumentsToBringPanel, {
        props: {eventId: 3, date: '2026-10-08'},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}, FileThumbnail: true}},
    })
    await flushPromises()
    return panel
}

/**
 * Everyone who sees the appointment sees what it asks for, participants get their copies, and
 * whoever manages the registrations sees where every participant stands.
 */
describe('DocumentsToBringPanel', () => {
    beforeEach(() => {
        vi.mocked(appointmentDocuments.documentsToBring).mockReset()
    })

    it('shows a reader who takes no part what the appointment asks for, without copies', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [], participants: null})

        const tiles = panel.findAll('[data-testid="document-to-bring-tile"]')
        expect(tiles).toHaveLength(1)
        expect(tiles[0]!.text()).toContain('Einverständnis')
        expect(panel.findAll('[data-testid="document-to-bring-download"]')).toHaveLength(0)
        expect(panel.find('[data-testid="document-to-bring-status"]').exists()).toBe(false)
    })

    it('gives a participant their copy to download on the tile', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [lena], participants: null})

        expect(panel.findAll('[data-testid="document-to-bring-download"]')).toHaveLength(1)
    })

    it('opens where every participant stands for an event manager', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [], participants: [lena]})
        expect(panel.find('[data-testid="documents-to-bring-overview"]').exists()).toBe(false)

        await panel.find('[data-testid="document-to-bring-status"]').trigger('click')

        const rows = panel.findAll('[data-testid="documents-to-bring-participant"]')
        expect(rows).toHaveLength(1)
        expect(rows[0]!.text()).toContain('Lena Schmidt')
    })

    it('shows nothing where the appointment asks for nothing', async () => {
        const panel = await mountPanel({required: [], own: [], participants: []})

        expect(panel.find('[data-testid="documents-to-bring"]').exists()).toBe(false)
    })
})

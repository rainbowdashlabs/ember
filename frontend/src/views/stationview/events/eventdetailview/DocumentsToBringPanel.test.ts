/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {
    DocumentTemplateKind,
    PaperState,
    RequirementStatus,
    type AppointmentDocuments,
    type PaperSubmission,
    type ParticipantDocuments,
} from '@/api/generated/schema'
import {appointmentDocuments} from '@/api'
import DocumentsToBringPanel from './DocumentsToBringPanel.vue'

vi.mock('@/api', () => ({
    appointmentDocuments: {
        documentsToBring: vi.fn(),
        submitScan: vi.fn(),
        confirmScan: vi.fn(),
        rejectScan: vi.fn(),
        scanContentUrl: (eventId: number, id: number) => `/events/${eventId}/document-scans/${id}/content`,
    },
}))

const CONSENT = {templateId: 8, name: 'Einverständnis', kind: DocumentTemplateKind.PDF, version: 1, archived: false, lastUsedAt: null}

function scan(state: PaperState, rejectReason: string | null = null): PaperSubmission {
    return {
        id: 20,
        eventId: 3,
        eventDate: '2026-10-08',
        templateId: 8,
        memberId: 11,
        documentId: 40,
        state,
        submittedAt: '2026-10-07T10:00:00Z',
        reviewedAt: state === PaperState.SUBMITTED ? null : '2026-10-07T12:00:00Z',
        rejectReason,
    }
}

function lena(paper: PaperSubmission | null = null): ParticipantDocuments {
    return {
        memberId: 11,
        name: 'Lena Schmidt',
        documents: [{
            templateId: 8,
            name: 'Einverständnis',
            status: RequirementStatus.NOT_GENERATED,
            documentId: null,
            generatedAt: null,
            outdated: false,
            paper,
        }],
    }
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

async function pick(panel: Awaited<ReturnType<typeof mountPanel>>, testId: string) {
    const input = panel.find(`[data-testid="${testId}"] input[type="file"]`)
    const file = new File(['%PDF-1.4'], 'scan.pdf', {type: 'application/pdf'})
    Object.defineProperty(input.element, 'files', {value: [file]})
    await input.trigger('change')
    await flushPromises()
    return file
}

/**
 * Everyone who sees the appointment sees what it asks for, participants get their copies and hand in
 * their signed scans, and whoever manages the registrations sees where every participant stands and
 * decides on the scans.
 */
describe('DocumentsToBringPanel', () => {
    beforeEach(() => {
        vi.mocked(appointmentDocuments.documentsToBring).mockReset()
        vi.mocked(appointmentDocuments.submitScan).mockReset()
        vi.mocked(appointmentDocuments.confirmScan).mockReset()
        vi.mocked(appointmentDocuments.rejectScan).mockReset()
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
        const panel = await mountPanel({required: [CONSENT], own: [lena()], participants: null})

        expect(panel.findAll('[data-testid="document-to-bring-download"]')).toHaveLength(1)
    })

    it('hands a participant\'s signed scan in and shows it waiting', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [lena()], participants: null})
        vi.mocked(appointmentDocuments.submitScan).mockResolvedValue(scan(PaperState.SUBMITTED))
        vi.mocked(appointmentDocuments.documentsToBring).mockResolvedValue(
            {required: [CONSENT], own: [lena(scan(PaperState.SUBMITTED))], participants: null})

        const file = await pick(panel, 'document-to-bring-scan')

        expect(appointmentDocuments.submitScan).toHaveBeenCalledWith(
            {eventId: 3, date: '2026-10-08', templateId: 8, memberId: 11}, file, 'Einverständnis, unterschrieben')
        expect(panel.text()).toContain('Eingereicht, wartet auf Bestätigung')
    })

    it('offers no further scan once one is confirmed, and says why one was turned down', async () => {
        const confirmed = await mountPanel({required: [CONSENT], own: [lena(scan(PaperState.CONFIRMED))], participants: null})
        expect(confirmed.text()).toContain('Auf Papier bestätigt')
        expect(confirmed.find('[data-testid="document-to-bring-scan"]').exists()).toBe(false)

        const rejected = await mountPanel(
            {required: [CONSENT], own: [lena(scan(PaperState.REJECTED, 'Unterschrift fehlt'))], participants: null})
        expect(rejected.find('[data-testid="document-scan-rejection"]').text()).toBe('Abgelehnt: Unterschrift fehlt')
        expect(rejected.find('[data-testid="document-to-bring-scan"]').exists()).toBe(true)
    })

    it('opens where every participant stands for an event manager', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [], participants: [lena()]})
        expect(panel.find('[data-testid="documents-to-bring-overview"]').exists()).toBe(false)

        await panel.find('[data-testid="document-to-bring-status"]').trigger('click')

        const rows = panel.findAll('[data-testid="documents-to-bring-participant"]')
        expect(rows).toHaveLength(1)
        expect(rows[0]!.text()).toContain('Lena Schmidt')
        expect(panel.find('[data-testid="document-scan-hand-in"]').exists()).toBe(true)
    })

    it('lets an event manager confirm a waiting scan', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [], participants: [lena(scan(PaperState.SUBMITTED))]})
        vi.mocked(appointmentDocuments.confirmScan).mockResolvedValue(scan(PaperState.CONFIRMED))
        await panel.find('[data-testid="document-to-bring-status"]').trigger('click')

        await panel.find('[data-testid="document-scan-confirm"]').trigger('click')
        await flushPromises()

        expect(appointmentDocuments.confirmScan).toHaveBeenCalledWith(3, 20)
        expect(appointmentDocuments.documentsToBring).toHaveBeenCalledTimes(2)
    })

    it('lets an event manager turn a waiting scan down with a reason', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [], participants: [lena(scan(PaperState.SUBMITTED))]})
        vi.mocked(appointmentDocuments.rejectScan).mockResolvedValue(scan(PaperState.REJECTED, 'Unterschrift fehlt'))
        await panel.find('[data-testid="document-to-bring-status"]').trigger('click')

        await panel.find('[data-testid="document-scan-reject"]').trigger('click')
        await panel.find('[data-testid="document-scan-reject-form"] textarea').setValue('Unterschrift fehlt')
        await panel.find('[data-testid="document-scan-reject-form"]').trigger('submit')
        await flushPromises()

        expect(appointmentDocuments.rejectScan).toHaveBeenCalledWith(3, 20, 'Unterschrift fehlt')
        expect(panel.find('[data-testid="document-scan-reject-form"]').exists()).toBe(false)
    })

    it('shows nothing where the appointment asks for nothing', async () => {
        const panel = await mountPanel({required: [], own: [], participants: []})

        expect(panel.find('[data-testid="documents-to-bring"]').exists()).toBe(false)
    })
})

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
    PartnerAgreementState,
    RequirementSignatureState,
    RequirementStatus,
    type AppointmentDocuments,
    type PaperSubmission,
    type ParticipantDocuments,
    type PartnerSigner,
    type RequirementSignature,
} from '@/api/generated/schema'
import {appointmentDocuments, partnerAgreements} from '@/api'
import DocumentsToBringPanel from './DocumentsToBringPanel.vue'

vi.mock('@/api', () => ({
    appointmentDocuments: {
        documentsToBring: vi.fn(),
        submitScan: vi.fn(),
        confirmScan: vi.fn(),
        rejectScan: vi.fn(),
        scanContentUrl: (eventId: number, id: number) => `/events/${eventId}/document-scans/${id}/content`,
    },
    partnerAgreements: {
        listSigners: vi.fn(),
        confirmPaper: vi.fn(),
        copyUrl: (eventId: number, id: number) => `/events/${eventId}/partner-agreements/${id}/copy`,
    },
}))

/** A member of a partner station, unnamed, standing with the consent as given. */
function partnerSigner(state: PartnerAgreementState): PartnerSigner {
    return {
        registrationId: 90,
        member: null,
        documents: [{
            templateId: 8,
            name: 'Einverständnis',
            state,
            complete: state === PartnerAgreementState.SIGNED,
            agreementId: state === PartnerAgreementState.MISSING ? null : 5,
            copies: state === PartnerAgreementState.SIGNED ? 1 : 0,
            confirmedByName: null,
        }],
    }
}

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

function lena(paper: PaperSubmission | null = null, signature: RequirementSignature | null = null): ParticipantDocuments {
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
            signature,
        }],
    }
}

/** Lena's copy asks her and her first guardian; the reader may sign the participant field where `yours`. */
function asked(
    participant: RequirementSignatureState,
    guardian: RequirementSignatureState,
    yours = false,
): RequirementSignature {
    const fields = [
        {id: 70, name: 'participant', signerName: 'Lena Schmidt', state: participant, yours},
        {id: 71, name: 'guardian1', signerName: 'Anna Schmidt', state: guardian, yours: false},
    ]
    const open = fields.some(field => field.state === RequirementSignatureState.OPEN)
    return {
        templateId: 8,
        memberId: 11,
        requestUid: '0b9f5c1e-8f6d-4a39-9d55-2c1b7f3d4e10',
        state: open ? RequirementSignatureState.OPEN : RequirementSignatureState.SIGNED,
        fields,
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
        vi.mocked(partnerAgreements.listSigners).mockReset()
        vi.mocked(partnerAgreements.listSigners).mockResolvedValue([])
        vi.mocked(partnerAgreements.confirmPaper).mockReset()
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

    it('shows a participant each signature of their copy and offers the ones they sign online', async () => {
        const signature = asked(RequirementSignatureState.OPEN, RequirementSignatureState.OPEN, true)
        const panel = await mountPanel({required: [CONSENT], own: [lena(null, signature)], participants: null})

        const fields = panel.findAll('[data-testid="signature-field"]')
        expect(fields).toHaveLength(2)
        expect(fields[0]!.text()).toContain('Teilnehmende Person: Lena Schmidt')
        expect(fields[0]!.text()).toContain('Unterschrift offen')
        expect(fields[1]!.text()).toContain('Erziehungsberechtigte Person 1: Anna Schmidt')
        expect(fields[0]!.find('[data-testid="signature-field-sign"]').exists()).toBe(true)
        expect(fields[1]!.find('[data-testid="signature-field-sign"]').exists()).toBe(false)
        expect(panel.find('[data-testid="document-to-bring"]').text()).toContain('Unterschrift offen')
    })

    it('shows an event manager signed, open, paper confirmed and waived fields without signing them', async () => {
        const signed = {
            ...asked(RequirementSignatureState.SIGNED, RequirementSignatureState.PAPER_CONFIRMED, true),
            state: RequirementSignatureState.PAPER_CONFIRMED,
        }
        const panel = await mountPanel({required: [CONSENT], own: [], participants: [lena(null, signed)]})
        await panel.find('[data-testid="document-to-bring-status"]').trigger('click')

        const row = panel.find('[data-testid="documents-to-bring-participant"]')
        const fields = row.findAll('[data-testid="signature-field"]')
        expect(fields[0]!.text()).toContain('Unterschrieben')
        expect(fields[1]!.text()).toContain('Auf Papier bestätigt')
        expect(row.find('[data-testid="signature-field-sign"]').exists()).toBe(false)

        const waived = await mountPanel({
            required: [CONSENT],
            own: [],
            participants: [lena(null, {
                ...asked(RequirementSignatureState.WAIVED, RequirementSignatureState.WAIVED),
                state: RequirementSignatureState.WAIVED,
            })],
        })
        await waived.find('[data-testid="document-to-bring-status"]').trigger('click')
        expect(waived.find('[data-testid="documents-to-bring-participant"]').text()).toContain('Erlassen')
    })

    it('shows an event manager a partner\'s member whose signature is missing and confirms their paper copy', async () => {
        vi.mocked(partnerAgreements.listSigners).mockResolvedValue([partnerSigner(PartnerAgreementState.MISSING)])
        vi.mocked(partnerAgreements.confirmPaper).mockResolvedValue({
            ...partnerSigner(PartnerAgreementState.PAPER_CONFIRMED).documents[0]!,
            confirmedByName: 'Maria Leitung',
        })
        const panel = await mountPanel({required: [CONSENT], own: [], participants: []})
        expect(partnerAgreements.listSigners).toHaveBeenCalledWith(3, '2026-10-08')
        await panel.find('[data-testid="document-to-bring-status"]').trigger('click')

        const row = panel.find('[data-testid="partner-signer"]')
        expect(row.text()).toContain('Mitglied einer Partnerwache')
        expect(row.text()).toContain('Unterschrift fehlt')
        expect(row.find('[data-testid="partner-signer-copy"]').exists()).toBe(false)

        await row.find('[data-testid="partner-signer-paper"]').trigger('click')
        await flushPromises()

        expect(partnerAgreements.confirmPaper).toHaveBeenCalledWith(3, 90, 8)
        expect(appointmentDocuments.documentsToBring).toHaveBeenCalledTimes(2)
    })

    it('offers an event manager the copy a partner sent back and asks partners nothing for a reader', async () => {
        vi.mocked(partnerAgreements.listSigners).mockResolvedValue([partnerSigner(PartnerAgreementState.SIGNED)])
        const panel = await mountPanel({required: [CONSENT], own: [], participants: []})
        await panel.find('[data-testid="document-to-bring-status"]').trigger('click')

        const row = panel.find('[data-testid="partner-signer"]')
        expect(row.text()).toContain('Unterschrieben')
        expect(row.find('[data-testid="partner-signer-copy"]').exists()).toBe(true)
        expect(row.find('[data-testid="partner-signer-paper"]').exists()).toBe(false)

        vi.mocked(partnerAgreements.listSigners).mockClear()
        await mountPanel({required: [CONSENT], own: [], participants: null})
        expect(partnerAgreements.listSigners).not.toHaveBeenCalled()
    })

    it('shows nothing where the appointment asks for nothing', async () => {
        const panel = await mountPanel({required: [], own: [], participants: []})

        expect(panel.find('[data-testid="documents-to-bring"]').exists()).toBe(false)
    })
})

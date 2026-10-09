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
import {appointmentDocuments, partnerAgreements, signing} from '@/api'
import DocumentsToBringPanel from './DocumentsToBringPanel.vue'

const push = vi.hoisted(() => vi.fn())

vi.mock('vue-router', async (importOriginal) => ({
    ...(await importOriginal<typeof import('vue-router')>()),
    useRouter: () => ({push}),
}))

vi.mock('@/api', () => ({
    signing: {withdrawAgreement: vi.fn()},
    appointmentDocuments: {
        documentsToBring: vi.fn(),
        offerAgreement: vi.fn(),
        submitScan: vi.fn(),
        withdrawScan: vi.fn(),
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

function lena(
    paper: PaperSubmission | null = null,
    signature: RequirementSignature | null = null,
    agreementOffered = false,
): ParticipantDocuments {
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
            agreementOffered,
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
        withdrawable: false,
        withdrawnAt: null,
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
        vi.mocked(appointmentDocuments.withdrawScan).mockReset()
        vi.mocked(appointmentDocuments.confirmScan).mockReset()
        vi.mocked(appointmentDocuments.rejectScan).mockReset()
        vi.mocked(partnerAgreements.listSigners).mockReset()
        vi.mocked(partnerAgreements.listSigners).mockResolvedValue([])
        vi.mocked(partnerAgreements.confirmPaper).mockReset()
        vi.mocked(appointmentDocuments.offerAgreement).mockReset()
        vi.mocked(signing.withdrawAgreement).mockReset()
    })

    it('shows a reader who takes no part what the appointment asks for, without copies', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [], participants: null})

        const tiles = panel.findAll('[data-testid="document-to-bring-tile"]')
        expect(tiles).toHaveLength(1)
        expect(tiles[0]!.text()).toContain('Einverständnis')
        expect(panel.findAll('[data-testid="document-to-bring-download"]')).toHaveLength(0)
        expect(panel.find('[data-testid="document-to-bring-status"]').exists()).toBe(false)
    })

    it('gives each person a tile of their own, with their documents to download and upload', async () => {
        const tim = {...lena(), memberId: 12, name: 'Tim Schmidt'}
        const panel = await mountPanel({required: [CONSENT], own: [lena(), tim], participants: null})

        const people = panel.findAll('[data-testid="person-documents"]')
        expect(people).toHaveLength(2)
        expect(people[0]!.text()).toContain('Lena Schmidt')
        expect(people[0]!.text()).toContain('Einverständnis')
        expect(people[1]!.text()).toContain('Tim Schmidt')
        expect(people[0]!.find('[data-testid="document-to-bring-download"]').text()).toBe('Herunterladen')
        expect(people[0]!.find('[data-testid="document-to-bring-scan"]').text()).toBe('Hochladen')
        expect(panel.find('[data-testid="document-to-bring-tile"]').exists()).toBe(false)
        expect(panel.text()).toContain('Herunterladen, ausdrucken, unterschreiben')
        expect(panel.text()).not.toContain('Online unterschreiben')
    })

    it('signs a person\'s fields of a document online in one go, and leads the hint with it', async () => {
        const signature = asked(RequirementSignatureState.OPEN, RequirementSignatureState.OPEN, true)
        const panel = await mountPanel({required: [CONSENT], own: [lena(null, signature)], participants: null})

        expect(panel.text()).toContain('Am einfachsten unterschreibst du direkt hier')
        await panel.find('[data-testid="document-to-bring-sign"]').trigger('click')

        expect(push).toHaveBeenCalledWith({name: 'station-signing-all', query: {fields: '70'}})
    })

    it('hands a participant\'s signed scan in and shows it waiting', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [lena()], participants: null})
        vi.mocked(appointmentDocuments.submitScan).mockResolvedValue(scan(PaperState.SUBMITTED))
        vi.mocked(appointmentDocuments.documentsToBring).mockResolvedValue(
            {required: [CONSENT], own: [lena(scan(PaperState.SUBMITTED))], participants: null})

        const file = await pick(panel, 'document-to-bring-scan')

        expect(appointmentDocuments.submitScan).toHaveBeenCalledWith(
            {eventId: 3, date: '2026-10-08', templateId: 8, memberId: 11}, file, 'Einverständnis, unterschrieben')
        expect(panel.text()).toContain('Scan eingereicht, wartet auf Bestätigung')
        expect(panel.find('[data-testid="document-scan-received"]').text())
            .toBe('Scan hochgeladen. Die Terminverwaltung prüft ihn jetzt.')
        expect(panel.find('[data-testid="document-to-bring-scan"]').text()).toBe('Ersetzen')
    })

    it('shows the scan waiting at once, before the documents are read again, and offers no signing meanwhile', async () => {
        const signature = asked(RequirementSignatureState.OPEN, RequirementSignatureState.OPEN, true)
        const panel = await mountPanel({required: [CONSENT], own: [lena(null, signature)], participants: null})
        vi.mocked(appointmentDocuments.submitScan).mockResolvedValue(scan(PaperState.SUBMITTED))
        vi.mocked(appointmentDocuments.documentsToBring).mockReturnValue(new Promise(() => {}))
        expect(panel.find('[data-testid="document-to-bring-sign"]').exists()).toBe(true)

        await pick(panel, 'document-to-bring-scan')

        expect(panel.find('[data-testid="document-to-bring"]').text()).toContain('Scan eingereicht, wartet auf Bestätigung')
        expect(panel.find('[data-testid="document-to-bring-sign"]').exists()).toBe(false)
        expect(panel.find('[data-testid="document-scan-received"]').exists()).toBe(true)
    })

    it('takes a waiting scan back and reads the copies again', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [lena(scan(PaperState.SUBMITTED))], participants: null})
        vi.mocked(appointmentDocuments.withdrawScan).mockResolvedValue()
        vi.mocked(appointmentDocuments.documentsToBring).mockResolvedValue(
            {required: [CONSENT], own: [lena()], participants: null})

        await panel.find('[data-testid="document-to-bring-scan-withdraw"]').trigger('click')
        await flushPromises()

        expect(appointmentDocuments.withdrawScan).toHaveBeenCalledWith(3, 20)
        expect(panel.find('[data-testid="document-to-bring-scan-withdraw"]').exists()).toBe(false)
        expect(panel.find('[data-testid="document-to-bring-scan"]').text()).toBe('Hochladen')
    })

    it('offers a withdrawn paper copy for a new scan', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [lena(scan(PaperState.WITHDRAWN))], participants: null})

        expect(panel.find('[data-testid="document-to-bring-scan"]').text()).toBe('Hochladen')
        expect(panel.find('[data-testid="document-to-bring-scan-withdraw"]').exists()).toBe(false)
        expect(panel.text()).not.toContain('Auf Papier bestätigt')
    })

    it('keeps a failed upload in the failure handling and says nothing was received', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [lena()], participants: null})
        vi.mocked(appointmentDocuments.submitScan).mockRejectedValue(new Error('offline'))

        await pick(panel, 'document-to-bring-scan')

        expect(panel.find('[data-testid="document-scan-received"]').exists()).toBe(false)
        expect(panel.text()).not.toContain('Scan eingereicht')
    })

    it('tells an event manager that the scan they handed in is confirmed at once', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [], participants: [lena()]})
        vi.mocked(appointmentDocuments.submitScan).mockResolvedValue(scan(PaperState.CONFIRMED))
        await panel.find('[data-testid="document-to-bring-status"]').trigger('click')

        await pick(panel, 'document-scan-hand-in')

        expect(panel.find('[data-testid="documents-to-bring-overview"] [data-testid="document-scan-received"]').text())
            .toBe('Scan hochgeladen und bestätigt. Das Dokument gilt als auf Papier unterschrieben.')
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

    it('shows a participant each signature of their copy below the buttons', async () => {
        const signature = asked(RequirementSignatureState.OPEN, RequirementSignatureState.OPEN, true)
        const panel = await mountPanel({required: [CONSENT], own: [lena(null, signature)], participants: null})

        const fields = panel.findAll('[data-testid="signature-field"]')
        expect(fields).toHaveLength(2)
        expect(fields[0]!.text()).toContain('Teilnehmende Person: Lena Schmidt')
        expect(fields[0]!.text()).toContain('Unterschrift offen')
        expect(fields[1]!.text()).toContain('Erziehungsberechtigte Person 1: Anna Schmidt')
        expect(panel.find('[data-testid="signature-field-sign"]').exists()).toBe(false)
        expect(panel.find('[data-testid="document-to-bring"]').text()).toContain('Unterschrift offen')
    })

    it('offers no online signing where the reader signs none of the fields', async () => {
        const signature = asked(RequirementSignatureState.OPEN, RequirementSignatureState.OPEN, false)
        const panel = await mountPanel({required: [CONSENT], own: [lena(null, signature)], participants: null})

        expect(panel.find('[data-testid="document-to-bring-sign"]').exists()).toBe(false)
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

    it('offers the agreement of an appointment without registrations and asks for its signatures', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [lena(null, null, true)], participants: null})
        vi.mocked(appointmentDocuments.offerAgreement).mockResolvedValue(
            asked(RequirementSignatureState.OPEN, RequirementSignatureState.OPEN, false))

        expect(panel.find('[data-testid="agreement-actions"]').text()).toContain('Mit der Unterschrift sagst du')
        await panel.find('[data-testid="agreement-sign"]').trigger('click')
        await flushPromises()

        expect(appointmentDocuments.offerAgreement).toHaveBeenCalledWith(3, '2026-10-08', 8, 11)
        expect(appointmentDocuments.documentsToBring).toHaveBeenCalledTimes(2)
    })

    it('withdraws a signed agreement with a reason and reads the copies again', async () => {
        const signed = {
            ...asked(RequirementSignatureState.SIGNED, RequirementSignatureState.SIGNED),
            withdrawable: true,
        }
        const panel = await mountPanel({required: [CONSENT], own: [lena(null, signed)], participants: null})
        vi.mocked(signing.withdrawAgreement).mockResolvedValue({
            requestUid: signed.requestUid,
            withdrawnAt: '2026-10-08T09:00:00Z',
        })

        await panel.find('[data-testid="agreement-withdraw"]').trigger('click')
        await panel.find('[data-testid="agreement-withdraw-form"] textarea').setValue('Doch krank')
        await panel.find('[data-testid="agreement-withdraw-form"]').trigger('submit')
        await flushPromises()

        expect(signing.withdrawAgreement).toHaveBeenCalledWith(signed.requestUid, 'Doch krank')
        expect(appointmentDocuments.documentsToBring).toHaveBeenCalledTimes(2)
    })

    it('shows a withdrawn agreement as withdrawn, with when', async () => {
        const withdrawn = {
            ...asked(RequirementSignatureState.SIGNED, RequirementSignatureState.WAIVED),
            state: RequirementSignatureState.REVOKED,
            withdrawnAt: '2026-10-07T12:00:00Z',
        }
        const panel = await mountPanel({required: [CONSENT], own: [lena(null, withdrawn, true)], participants: null})

        expect(panel.find('[data-testid="document-to-bring"]').text()).toContain('Widerrufen')
        expect(panel.find('[data-testid="agreement-withdrawn-at"]').exists()).toBe(true)
        expect(panel.find('[data-testid="agreement-withdraw"]').exists()).toBe(false)
        expect(panel.find('[data-testid="agreement-sign"]').exists()).toBe(true)
    })

    it('shows nothing where the appointment asks for nothing', async () => {
        const panel = await mountPanel({required: [], own: [], participants: []})

        expect(panel.find('[data-testid="documents-to-bring"]').exists()).toBe(false)
    })
})

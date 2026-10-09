/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {PaperState, PartnerAgreementState, RequirementSignatureState, type AppointmentDocuments} from '@/api/generated/schema'
import {appointmentDocuments, partnerAgreements, signing} from '@/api'
import {CONSENT, asked, lena, partnerSigner, scan} from '@/test/mocks/documentsToBring'
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
    },
    partnerAgreements: {listSigners: vi.fn()},
}))

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
 * Everyone who sees the appointment sees what it asks for, and participants get their copies and hand in
 * their signed scans. Whoever manages the registrations gets a section per document below, which its own
 * test covers.
 */
describe('DocumentsToBringPanel', () => {
    beforeEach(() => {
        vi.mocked(appointmentDocuments.documentsToBring).mockReset()
        vi.mocked(appointmentDocuments.submitScan).mockReset()
        vi.mocked(appointmentDocuments.withdrawScan).mockReset()
        vi.mocked(partnerAgreements.listSigners).mockReset()
        vi.mocked(partnerAgreements.listSigners).mockResolvedValue([])
        vi.mocked(appointmentDocuments.offerAgreement).mockReset()
        vi.mocked(signing.withdrawAgreement).mockReset()
    })

    it('shows a reader who takes no part what the appointment asks for, without copies', async () => {
        const panel = await mountPanel({required: [CONSENT], own: [], participants: null})

        const tiles = panel.findAll('[data-testid="document-to-bring-tile"]')
        expect(tiles).toHaveLength(1)
        expect(tiles[0]!.text()).toContain('Einverständnis')
        expect(panel.findAll('[data-testid="document-to-bring-download"]')).toHaveLength(0)
        expect(panel.find('[data-testid="participant-documents"]').exists()).toBe(false)
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

    it('offers no further scan once one is confirmed, and says why one was turned down', async () => {
        const confirmed = await mountPanel({required: [CONSENT], own: [lena(scan(PaperState.CONFIRMED))], participants: null})
        expect(confirmed.text()).toContain('Auf Papier bestätigt')
        expect(confirmed.find('[data-testid="document-to-bring-scan"]').exists()).toBe(false)

        const rejected = await mountPanel(
            {required: [CONSENT], own: [lena(scan(PaperState.REJECTED, 'Unterschrift fehlt'))], participants: null})
        expect(rejected.find('[data-testid="document-scan-rejection"]').text()).toBe('Abgelehnt: Unterschrift fehlt')
        expect(rejected.find('[data-testid="document-to-bring-scan"]').exists()).toBe(true)
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

    it('gives an event manager a section per document with every participant and the partners\' members', async () => {
        vi.mocked(partnerAgreements.listSigners).mockResolvedValue([partnerSigner(PartnerAgreementState.MISSING)])
        const second = {...CONSENT, templateId: 9, name: 'Packliste'}
        const panel = await mountPanel({required: [CONSENT, second], own: [], participants: [lena()]})

        expect(partnerAgreements.listSigners).toHaveBeenCalledWith(3, '2026-10-08')
        const sections = panel.findAll('[data-testid="participant-documents"]')
        expect(sections).toHaveLength(2)
        expect(sections[0]!.text()).toContain('Dokumente der Teilnehmenden: Einverständnis')
        expect(sections[1]!.text()).toContain('Dokumente der Teilnehmenden: Packliste')
        expect(panel.findAll('[data-testid="document-to-bring-tile"]')).toHaveLength(2)
    })

    it('asks partners nothing for a reader who manages no registrations', async () => {
        await mountPanel({required: [CONSENT], own: [lena()], participants: null})

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
        expect(panel.find('[data-testid="participant-documents"]').exists()).toBe(false)
    })
})

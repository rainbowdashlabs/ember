/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {
    PaperState,
    PartnerAgreementState,
    RequirementSignatureState,
    RequirementStatus,
    type ParticipantDocuments,
    type PartnerSigner,
} from '@/api/generated/schema'
import {appointmentDocuments, partnerAgreements, signing} from '@/api'
import {getItem, setItem} from '@/api/storage'
import {downloadAuthed} from '@/util/downloadAuthed'
import {CONSENT, asked, lena, partnerSigner, scan} from '@/test/mocks/documentsToBring'
import {createIdentity} from '@/test/mocks/factories'
import ParticipantDocumentsSection from './ParticipantDocumentsSection.vue'

const mayEditDocuments = vi.hoisted(() => ({value: true}))

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: () => mayEditDocuments.value}),
}))

vi.mock('@/api/storage', () => ({getItem: vi.fn(), setItem: vi.fn()}))

vi.mock('@/util/downloadAuthed', () => ({downloadAuthed: vi.fn()}))

vi.mock('@/api', () => ({
    signing: {settleSignatureField: vi.fn(), withdrawAgreement: vi.fn()},
    appointmentDocuments: {
        submitScan: vi.fn(),
        confirmScan: vi.fn(),
        rejectScan: vi.fn(),
        offerAgreement: vi.fn(),
        scanContentUrl: (eventId: number, id: number) => `/events/${eventId}/document-scans/${id}/content`,
        participantCopyUrl: (target: {templateId: number, memberId: number}) =>
            `/copy/${target.templateId}/${target.memberId}`,
    },
    partnerAgreements: {
        confirmPaper: vi.fn(),
        copyUrl: (eventId: number, id: number) => `/events/${eventId}/partner-agreements/${id}/copy`,
    },
}))

const signed = asked(RequirementSignatureState.SIGNED, RequirementSignatureState.SIGNED)

function person(memberId: number, name: string, participant: ParticipantDocuments): ParticipantDocuments {
    return {...participant, memberId, name}
}

/** Lena's scan waits, Tim still owes his signatures, Mia signed and her copy is filed as document 41. */
function participants(): ParticipantDocuments[] {
    const mia = person(13, 'Mia Weber', lena(null, signed))
    mia.documents = [{...mia.documents[0]!, status: RequirementStatus.GENERATED, documentId: 41}]
    return [
        lena(scan(PaperState.SUBMITTED)),
        person(12, 'Tim Schmidt', lena(null, asked(RequirementSignatureState.OPEN, RequirementSignatureState.OPEN))),
        mia,
    ]
}

async function mountSection(people: ParticipantDocuments[], partners: PartnerSigner[] = []) {
    const onChanged = vi.fn()
    const section = await mountSuspended(ParticipantDocumentsSection, {
        props: {eventId: 3, date: '2026-10-08', template: CONSENT, participants: people, partnerSigners: partners, onChanged},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
    })
    await flushPromises()
    return {section, onChanged}
}

function group(section: Awaited<ReturnType<typeof mountSection>>['section'], name: string) {
    return section.find(`[data-testid="document-group-${name}"]`)
}

/**
 * Whoever manages the registrations sees per document every participant and every partner's member, sorted
 * into what needs doing, what is missing and what is done, and acts on each tile.
 */
describe('ParticipantDocumentsSection', () => {
    beforeEach(() => {
        mayEditDocuments.value = true
        vi.mocked(getItem).mockReset()
        vi.mocked(getItem).mockReturnValue(null)
        vi.mocked(setItem).mockReset()
        vi.mocked(downloadAuthed).mockReset()
        vi.mocked(appointmentDocuments.confirmScan).mockReset()
        vi.mocked(appointmentDocuments.rejectScan).mockReset()
        vi.mocked(appointmentDocuments.submitScan).mockReset()
        vi.mocked(partnerAgreements.confirmPaper).mockReset()
        vi.mocked(signing.settleSignatureField).mockReset()
    })

    it('counts every group in its header, partners\' members included', async () => {
        const partners = [
            partnerSigner(PartnerAgreementState.MISSING),
            {...partnerSigner(PartnerAgreementState.SIGNED), registrationId: 91},
        ]
        const {section} = await mountSection(participants(), partners)

        expect(section.text()).toContain('Dokumente der Teilnehmenden: Einverständnis')
        expect(group(section, 'todo').text()).toContain('Zu erledigen (2)')
        expect(group(section, 'missing').text()).toContain('Fehlt noch (1)')
        expect(group(section, 'done').text()).toContain('Erledigt (2)')
    })

    it('opens only the group that needs the manager, and keeps the headers of empty groups', async () => {
        const {section} = await mountSection(participants())

        expect(group(section, 'todo').findAll('[data-testid="person-documents"]')).toHaveLength(1)
        expect(group(section, 'missing').find('[data-testid="person-documents"]').exists()).toBe(false)
        expect(group(section, 'done').find('[data-testid="person-documents"]').exists()).toBe(false)

        const {section: calm} = await mountSection([person(12, 'Tim Schmidt', lena())])
        expect(group(calm, 'todo').text()).toContain('Zu erledigen (0)')
        expect(group(calm, 'todo').find('[data-testid="document-group-todo-toggle"]').attributes('aria-expanded'))
            .toBe('false')
    })

    it('remembers which groups the reader opened and opens them again', async () => {
        const {section} = await mountSection(participants())

        await section.find('[data-testid="document-group-done-toggle"]').trigger('click')

        expect(group(section, 'done').findAll('[data-testid="person-documents"]')).toHaveLength(1)
        expect(setItem).toHaveBeenCalledWith('document_groups', JSON.stringify({done: true}))

        vi.mocked(getItem).mockReturnValue(JSON.stringify({todo: false, done: true, other: true}))
        const {section: again} = await mountSection(participants())
        expect(group(again, 'todo').find('[data-testid="person-documents"]').exists()).toBe(false)
        expect(group(again, 'done').find('[data-testid="person-documents"]').exists()).toBe(true)
    })

    it('folds the whole section', async () => {
        const {section} = await mountSection(participants())

        await section.find('[data-testid="participant-documents-toggle"]').trigger('click')

        expect(group(section, 'todo').exists()).toBe(false)
        expect(setItem).toHaveBeenCalledWith('document_groups', JSON.stringify({section: false}))
    })

    it('confirms a waiting scan and reads the documents again', async () => {
        vi.mocked(appointmentDocuments.confirmScan).mockResolvedValue(scan(PaperState.CONFIRMED))
        const {section, onChanged} = await mountSection(participants())

        await group(section, 'todo').find('[data-testid="document-scan-confirm"]').trigger('click')
        await flushPromises()

        expect(appointmentDocuments.confirmScan).toHaveBeenCalledWith(3, 20)
        expect(onChanged).toHaveBeenCalled()
    })

    it('turns a waiting scan down with a reason', async () => {
        vi.mocked(appointmentDocuments.rejectScan).mockResolvedValue(scan(PaperState.REJECTED, 'Unscharf'))
        const {section, onChanged} = await mountSection(participants())

        await section.find('[data-testid="document-scan-reject"]').trigger('click')
        await section.find('[data-testid="document-scan-reject-form"] textarea').setValue('Unscharf')
        await section.find('[data-testid="document-scan-reject-form"]').trigger('submit')
        await flushPromises()

        expect(appointmentDocuments.rejectScan).toHaveBeenCalledWith(3, 20, 'Unscharf')
        expect(section.find('[data-testid="document-scan-reject-form"]').exists()).toBe(false)
        expect(onChanged).toHaveBeenCalled()
    })

    it('hands a scan in for a participant who still owes the document', async () => {
        vi.mocked(appointmentDocuments.submitScan).mockResolvedValue(scan(PaperState.CONFIRMED))
        const {section, onChanged} = await mountSection(participants())
        await section.find('[data-testid="document-group-missing-toggle"]').trigger('click')

        const input = group(section, 'missing').find('[data-testid="document-scan-hand-in"] input[type="file"]')
        const file = new File(['%PDF-1.4'], 'scan.pdf', {type: 'application/pdf'})
        Object.defineProperty(input.element, 'files', {value: [file]})
        await input.trigger('change')
        await flushPromises()

        expect(appointmentDocuments.submitScan).toHaveBeenCalledWith(
            {eventId: 3, date: '2026-10-08', templateId: 8, memberId: 12}, file, 'Einverständnis, unterschrieben')
        expect(onChanged).toHaveBeenCalled()
    })

    it('downloads a participant\'s copy and the scan handed in for it', async () => {
        const {section} = await mountSection(participants())
        expect(group(section, 'todo').find('[data-testid="copy-review-download"]').exists()).toBe(false)

        await group(section, 'todo').find('[data-testid="copy-review-scan"]').trigger('click')
        await section.find('[data-testid="document-group-done-toggle"]').trigger('click')
        await group(section, 'done').find('[data-testid="copy-review-download"]').trigger('click')
        await flushPromises()

        expect(downloadAuthed).toHaveBeenCalledWith('/events/3/document-scans/20/content')
        expect(downloadAuthed).toHaveBeenCalledWith('/copy/8/13')
        expect(group(section, 'done').find('[data-testid="document-scan-hand-in"]').exists()).toBe(false)
    })

    it('waives a field nobody can sign, asked once more', async () => {
        const open = asked(RequirementSignatureState.SIGNED, RequirementSignatureState.OPEN)
        const nobody = {...open, fields: [open.fields[0]!, {...open.fields[1]!, nobodyCanSign: true}]}
        vi.mocked(signing.settleSignatureField).mockResolvedValue({} as never)
        const {section, onChanged} = await mountSection([lena(null, nobody)])

        const field = group(section, 'todo').find('[data-testid="unsignable-field"]')
        expect(field.text()).toContain('Erziehungsberechtigte Person 1: Anna Schmidt: niemand kann unterschreiben')
        await field.find('[data-testid="unsignable-field-waive"]').trigger('click')
        await section.find('[data-testid="unsignable-field-confirm"]').trigger('click')
        await flushPromises()

        expect(signing.settleSignatureField).toHaveBeenCalledWith(nobody.requestUid, 'guardian1', 'waive')
        expect(onChanged).toHaveBeenCalled()
    })

    it('tells a manager without the right to change member documents who settles a field nobody can sign', async () => {
        mayEditDocuments.value = false
        const open = asked(RequirementSignatureState.SIGNED, RequirementSignatureState.OPEN)
        const nobody = {...open, fields: [open.fields[0]!, {...open.fields[1]!, nobodyCanSign: true}]}
        const {section} = await mountSection([lena(null, nobody)])

        expect(section.find('[data-testid="unsignable-field-waive"]').exists()).toBe(false)
        expect(section.find('[data-testid="unsignable-field"]').text()).toContain('Mitgliederdokumente bearbeiten')
    })

    it('flags a registration whose agreement was withdrawn as something to look at', async () => {
        const withdrawn = {
            ...lena(null, asked(RequirementSignatureState.OPEN, RequirementSignatureState.OPEN)),
            agreementWithdrawnAt: '2026-10-07T12:00:00Z',
        }
        const {section} = await mountSection([withdrawn])

        expect(group(section, 'todo').find('[data-testid="person-documents-withdrawn"]').text())
            .toContain('Vereinbarung widerrufen am')
    })

    it('names a partner\'s member with their station, downloads their copy and confirms a paper copy', async () => {
        vi.mocked(partnerAgreements.confirmPaper).mockResolvedValue(
            partnerSigner(PartnerAgreementState.PAPER_CONFIRMED).documents[0]!)
        const member = createIdentity({name: 'Jonas Berg', stationName: 'Wache Nord'})
        const missing = partnerSigner(PartnerAgreementState.MISSING, member)
        const returned = {...partnerSigner(PartnerAgreementState.SIGNED, member), registrationId: 91}
        const {section, onChanged} = await mountSection([], [missing, returned])

        const tile = group(section, 'todo').find('[data-testid="person-documents"]')
        expect(tile.text()).toContain('Jonas Berg')
        expect(tile.find('[data-testid="person-documents-station"]').text()).toBe('Partnerwache: Wache Nord')
        expect(tile.text()).toContain('Unterschrift fehlt')
        await tile.find('[data-testid="partner-signer-paper"]').trigger('click')
        await section.find('[data-testid="document-group-done-toggle"]').trigger('click')
        await group(section, 'done').find('[data-testid="partner-signer-copy"]').trigger('click')
        await flushPromises()

        expect(partnerAgreements.confirmPaper).toHaveBeenCalledWith(3, 90, 8)
        expect(onChanged).toHaveBeenCalled()
        expect(downloadAuthed).toHaveBeenCalledWith('/events/3/partner-agreements/5/copy')
    })
})

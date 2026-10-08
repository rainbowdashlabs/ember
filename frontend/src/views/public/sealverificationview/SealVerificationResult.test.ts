/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {
    PadesLevel,
    RevocationReason,
    RevocationStatus,
    SealLevel,
    ValidationIndication,
    ValidationSubIndication,
    type SealVerification,
} from '@/api/generated/schema'
import {createSealCheck, createSealVerification, createTimestampCheck} from '@/test/mocks/sealVerification'
import SealVerificationResult from './SealVerificationResult.vue'

/**
 * What a reader sees once a file is checked: one plain verdict per seal first, the facts below it and
 * the technical side folded away, then whether this installation keeps the file.
 */
function show(result: SealVerification) {
    return mount(SealVerificationResult, {props: {result, fileName: 'urkunde.pdf'}})
}

function verdicts(view: ReturnType<typeof show>): (string | undefined)[] {
    return view.findAll('[data-testid="seal-check"]').map(card => card.attributes('data-verdict'))
}

describe('SealVerificationResult', () => {
    it('says a passed seal of this installation is sealed here and unchanged, with who and when', () => {
        const view = show(createSealVerification({signatures: [createSealCheck()]}))

        expect(verdicts(view)).toEqual(['sealedHere'])
        const card = view.get('[data-testid="seal-check"]').text()
        expect(card).toContain('Von dieser Installation versiegelt und unverändert')
        expect(view.get('[data-testid="seal-sealer"]').text()).toBe('Jugendfeuerwehr Musterstadt')
        expect(card).toContain('laut Uhr des versiegelnden Servers')
        expect(card).toContain('von Test Timestamp Responder')
        expect(view.get('[data-testid="seal-revocation"]').text()).toBe('Nicht gesperrt.')
    })

    it('says a passed seal of a federation partner is the partner\'s, by name, with its key', () => {
        const view = show(createSealVerification({
            signatures: [createSealCheck({
                issuedHere: false,
                partner: {stationUid: '5d1c2b7e-8a4f-4e61-b3d0-9c2a7f6e1b84', name: 'Jugendfeuerwehr Nordstadt'},
            })],
        }))

        expect(verdicts(view)).toEqual(['sealedByPartner'])
        const card = view.get('[data-testid="seal-check"]').text()
        expect(card).toContain('Von Jugendfeuerwehr Nordstadt versiegelt und unverändert')
        expect(card).toContain('nicht diese Installation')
        expect(card).not.toContain('Von dieser Installation versiegelt')
        expect(view.get('[data-testid="seal-revocation"]').text()).toBe('Nicht gesperrt.')
    })

    it('says a seal whose bytes changed is altered', () => {
        const view = show(createSealVerification({
            signatures: [createSealCheck({
                intact: false,
                indication: ValidationIndication.TOTAL_FAILED,
                subIndication: ValidationSubIndication.HASH_FAILURE,
            })],
        }))

        expect(verdicts(view)).toEqual(['altered'])
        expect(view.text()).toContain('Verändert seit dem Versiegeln')
    })

    it('says a stranger\'s seal is not from here and says nothing about its key', () => {
        const view = show(createSealVerification({
            signatures: [createSealCheck({
                issuedHere: false,
                indication: ValidationIndication.INDETERMINATE,
                subIndication: ValidationSubIndication.NOT_ISSUED_HERE,
                revocation: {status: RevocationStatus.UNKNOWN, revokedAt: null, reason: null},
            })],
        }))

        expect(verdicts(view)).toEqual(['notIssuedHere'])
        expect(view.text()).toContain('Nicht von dieser Installation versiegelt')
        expect(view.find('[data-testid="seal-revocation"]').exists()).toBe(false)
    })

    it('says a key revoked after the timestamp leaves the seal standing', () => {
        const view = show(createSealVerification({
            signatures: [createSealCheck({
                revocation: {
                    status: RevocationStatus.REVOKED,
                    revokedAt: '2026-10-07T12:00:00Z',
                    reason: RevocationReason.KEY_COMPROMISE,
                },
            })],
        }))

        expect(verdicts(view)).toEqual(['sealedHere'])
        const revocation = view.get('[data-testid="seal-revocation"]').text()
        expect(revocation).toContain('Schlüssel später gesperrt am')
        expect(revocation).toContain('das Siegel stammt von davor')
        expect(revocation).toContain('nicht mehr sicher')
    })

    it('says a key revoked with nothing proving the seal older is not settled', () => {
        const view = show(createSealVerification({
            signatures: [createSealCheck({
                indication: ValidationIndication.INDETERMINATE,
                subIndication: ValidationSubIndication.REVOKED_NO_POE,
                timestamps: [],
                revocation: {status: RevocationStatus.REVOKED, revokedAt: '2026-10-07T12:00:00Z', reason: null},
            })],
        }))

        expect(verdicts(view)).toEqual(['unclear'])
        expect(view.text()).toContain('Nicht eindeutig prüfbar')
        expect(view.text()).toContain('Kein Zeitstempel.')
        expect(view.get('[data-testid="seal-revocation"]').text()).toContain('Kein Zeitstempel belegt')
    })

    it('says plainly when the file carries no seal at all', () => {
        const view = show(createSealVerification())

        expect(view.get('[data-testid="seal-none"]').text()).toContain('Dieses PDF trägt kein Siegel.')
        expect(view.findAll('[data-testid="seal-check"]')).toHaveLength(0)
    })

    it('says whether this installation keeps exactly this file, neutrally either way', () => {
        const held = show(createSealVerification({
            document: {held: true, sealedAt: '2026-10-05T09:12:03Z', sealLevel: SealLevel.BASELINE_LT},
            signatures: [createSealCheck()],
        }))
        const notHeld = show(createSealVerification({signatures: [createSealCheck()]}))

        const heldText = held.get('[data-testid="seal-held-copy"]').text()
        expect(heldText).toContain('bewahrt genau diese Datei auf, versiegelt am')
        expect(heldText).toContain('Siegel mit Zeitstempel, offline prüfbar')
        const notHeldText = notHeld.get('[data-testid="seal-held-copy"]').text()
        expect(notHeldText).toContain('bewahrt keine Datei mit genau diesem Inhalt auf')
        expect(notHeldText).toContain('Das sagt nichts über das Siegel')
    })

    it('keeps fingerprints, serials and the standard verdicts in closed details', () => {
        const view = show(createSealVerification({signatures: [createSealCheck()]}))

        const details = view.get('[data-testid="seal-check-details"]')
        expect(details.attributes('open')).toBeUndefined()
        expect(details.text()).toContain('TOTAL_PASSED')
        expect(details.text()).toContain('5c1e0a7f3b9d2e41')
        expect(details.text()).toContain('3A:7F:12:C4:9B:E0:55:D1')
        expect(details.text()).toContain('B-LT')
        expect(details.text()).toContain('Deckt die ganze Datei ab')
    })

    it('says a file changed after sealing is not what was sealed, though the seal itself holds', () => {
        const view = show(createSealVerification({signatures: [createSealCheck({modifiedAfterSealing: true})]}))

        expect(verdicts(view)).toEqual(['modifiedAfterSealing'])
        expect(view.text()).toContain('Nach dem Versiegeln geändert')
        expect(view.get('[data-testid="seal-modified-after-sealing"]').text()).toBe('Ja')
    })

    it('says a seal that failed for another reason than a change is invalid', () => {
        const view = show(createSealVerification({
            signatures: [createSealCheck({
                indication: ValidationIndication.TOTAL_FAILED,
                subIndication: ValidationSubIndication.REVOKED,
            })],
        }))

        expect(verdicts(view)).toEqual(['invalid'])
        expect(view.text()).toContain('Siegel ungültig')
        expect(view.text()).not.toContain('Verändert seit dem Versiegeln')
    })

    it('explains a seal that does not reach the end of the file only for the long-term levels', () => {
        const longTerm = show(createSealVerification({signatures: [createSealCheck({level: PadesLevel.BASELINE_LT})]}))
        const archived = show(createSealVerification({signatures: [createSealCheck({level: PadesLevel.BASELINE_LTA})]}))
        const timestamped = show(createSealVerification({signatures: [createSealCheck({level: PadesLevel.BASELINE_T})]}))

        expect(longTerm.find('[data-testid="seal-long-term-hint"]').exists()).toBe(true)
        expect(archived.find('[data-testid="seal-long-term-hint"]').exists()).toBe(true)
        expect(timestamped.find('[data-testid="seal-long-term-hint"]').exists()).toBe(false)
    })

    it('lists timestamps on the file as a whole apart from the seals', () => {
        const view = show(createSealVerification({
            signatures: [createSealCheck()],
            documentTimestamps: [{coversWholeFile: true, timestamp: createTimestampCheck()}],
        }))

        expect(view.get('[data-testid="seal-document-timestamps"]').text()).toContain('Zeitstempel auf der ganzen Datei')
    })
})

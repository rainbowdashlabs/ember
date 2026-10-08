/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {
    PadesLevel,
    RevocationReason,
    RevocationStatus,
    ValidationIndication,
    ValidationSubIndication,
    type TimestampCheck,
} from '@/api/generated/schema'
import {createSealCheck, createTimestampCheck} from '@/test/mocks/sealVerification'
import {
    certificateName,
    isLongTerm,
    partnerName,
    provingTimestamp,
    revokedAfterTimestamp,
    subjectAttribute,
    verdictOf,
} from './sealVerdict'

/**
 * The one answer a reader gets about a seal before any detail, and the facts read out of the
 * certificate's subject for it.
 */
describe('verdictOf', () => {
    it('calls a passed seal of this installation sealed here', () => {
        expect(verdictOf(createSealCheck())).toBe('sealedHere')
    })

    it('calls a passed seal of a federation partner sealed by the partner, never sealed here', () => {
        const partnerSeal = createSealCheck({
            issuedHere: false,
            partner: {stationUid: '5d1c2b7e-8a4f-4e61-b3d0-9c2a7f6e1b84', name: 'Jugendfeuerwehr Nordstadt'},
        })

        expect(verdictOf(partnerSeal)).toBe('sealedByPartner')
        expect(partnerName(partnerSeal)).toBe('Jugendfeuerwehr Nordstadt')
    })

    it('names a partner without a known name by its certificate, and no partner for any other seal', () => {
        const unnamed = createSealCheck({
            issuedHere: false,
            partner: {stationUid: '5d1c2b7e-8a4f-4e61-b3d0-9c2a7f6e1b84', name: null},
        })

        expect(partnerName(unnamed)).toBe('Jugendfeuerwehr Musterstadt')
        expect(partnerName(createSealCheck())).toBeNull()
        expect(verdictOf({...unnamed, indication: ValidationIndication.INDETERMINATE})).toBe('unclear')
    })

    it('calls a seal whose bytes changed altered, whoever made it', () => {
        expect(verdictOf(createSealCheck({intact: false}))).toBe('altered')
        expect(verdictOf(createSealCheck({
            issuedHere: false,
            indication: ValidationIndication.TOTAL_FAILED,
            subIndication: ValidationSubIndication.HASH_FAILURE,
        }))).toBe('altered')
    })

    it('calls a stranger\'s seal not issued here, even where the validator passed it', () => {
        expect(verdictOf(createSealCheck({
            issuedHere: false,
            indication: ValidationIndication.INDETERMINATE,
            subIndication: ValidationSubIndication.NOT_ISSUED_HERE,
        }))).toBe('notIssuedHere')
    })

    it('calls only a broken content, signature value or file structure altered', () => {
        const failedWith = (subIndication: ValidationSubIndication) => verdictOf(createSealCheck({
            indication: ValidationIndication.TOTAL_FAILED,
            subIndication,
        }))

        expect(failedWith(ValidationSubIndication.HASH_FAILURE)).toBe('altered')
        expect(failedWith(ValidationSubIndication.SIG_CRYPTO_FAILURE)).toBe('altered')
        expect(failedWith(ValidationSubIndication.FORMAT_FAILURE)).toBe('altered')
    })

    it('calls every other failure invalid, never altered', () => {
        const failedWith = (subIndication: ValidationSubIndication | null) => verdictOf(createSealCheck({
            indication: ValidationIndication.TOTAL_FAILED,
            subIndication,
        }))

        expect(failedWith(ValidationSubIndication.REVOKED)).toBe('invalid')
        expect(failedWith(ValidationSubIndication.NOT_YET_VALID)).toBe('invalid')
        expect(failedWith(ValidationSubIndication.CHAIN_CONSTRAINTS_FAILURE)).toBe('invalid')
        expect(failedWith(null)).toBe('invalid')
        expect(verdictOf(createSealCheck({
            indication: ValidationIndication.FAILED,
            subIndication: ValidationSubIndication.SIG_CONSTRAINTS_FAILURE,
        }))).toBe('invalid')
    })

    it('calls an intact seal of a file changed afterwards modified after sealing, before anything else', () => {
        expect(verdictOf(createSealCheck({modifiedAfterSealing: true}))).toBe('modifiedAfterSealing')
        expect(verdictOf(createSealCheck({
            modifiedAfterSealing: true,
            indication: ValidationIndication.TOTAL_FAILED,
            subIndication: ValidationSubIndication.FORMAT_FAILURE,
        }))).toBe('modifiedAfterSealing')
        expect(verdictOf(createSealCheck({
            modifiedAfterSealing: true,
            issuedHere: false,
            indication: ValidationIndication.INDETERMINATE,
            subIndication: ValidationSubIndication.NOT_ISSUED_HERE,
        }))).toBe('modifiedAfterSealing')
        expect(verdictOf(createSealCheck({modifiedAfterSealing: true, intact: false}))).toBe('altered')
    })

    it('calls anything else undecided unclear', () => {
        expect(verdictOf(createSealCheck({
            indication: ValidationIndication.INDETERMINATE,
            subIndication: ValidationSubIndication.REVOKED_NO_POE,
        }))).toBe('unclear')
    })
})

describe('subjectAttribute', () => {
    it('reads an attribute and undoes the escaping of a comma', () => {
        const subject = 'CN=Wache Nord\\, Zug 2,UID=abc,O=ember.example.org'

        expect(subjectAttribute(subject, 'CN')).toBe('Wache Nord, Zug 2')
        expect(subjectAttribute(subject, 'O')).toBe('ember.example.org')
        expect(subjectAttribute(subject, 'OU')).toBeNull()
    })

    it('names a certificate by its organisation where it has no common name', () => {
        expect(certificateName({subject: 'O=Test Trust,C=DE', serialNumber: '1', sha256Fingerprint: 'AA'})).toBe('Test Trust')
        expect(certificateName(null)).toBeNull()
    })
})

describe('revokedAfterTimestamp', () => {
    const revoked = (revokedAt: string) => createSealCheck({
        revocation: {status: RevocationStatus.REVOKED, revokedAt, reason: RevocationReason.KEY_COMPROMISE},
    })

    it('holds when the key was revoked after the earliest intact timestamp', () => {
        expect(revokedAfterTimestamp(revoked('2026-10-06T00:00:00Z'))).toBe(true)
    })

    it('does not hold when the key was revoked before it, or when nothing proves the time', () => {
        expect(revokedAfterTimestamp(revoked('2026-10-01T00:00:00Z'))).toBe(false)
        expect(revokedAfterTimestamp({...revoked('2026-10-06T00:00:00Z'), timestamps: []})).toBe(false)
    })

    it('takes the earliest intact timestamp as the proving one', () => {
        const check = createSealCheck({
            timestamps: [
                createTimestampCheck({time: '2026-10-07T00:00:00Z'}),
                createTimestampCheck({time: '2026-10-01T00:00:00Z', intact: false}),
                createTimestampCheck({time: '2026-10-05T00:00:00Z'}),
            ],
        })

        expect(provingTimestamp(check)?.time).toBe('2026-10-05T00:00:00Z')
    })

    it('takes only an intact timestamp of a pinned service that passed as proof of the time', () => {
        const proving = (stamp: Partial<TimestampCheck>) => provingTimestamp(createSealCheck({
            timestamps: [createTimestampCheck(stamp)],
        }))

        expect(proving({})).not.toBeNull()
        expect(proving({intact: false})).toBeNull()
        expect(proving({pinnedAuthority: false})).toBeNull()
        expect(proving({indication: ValidationIndication.INDETERMINATE})).toBeNull()
        expect(proving({indication: ValidationIndication.FAILED})).toBeNull()
    })

    it('does not leave a revoked key standing on a timestamp that proves nothing', () => {
        const check = createSealCheck({
            revocation: {status: RevocationStatus.REVOKED, revokedAt: '2026-10-06T00:00:00Z', reason: null},
            timestamps: [createTimestampCheck({time: '2026-10-05T00:00:00Z', pinnedAuthority: false})],
        })

        expect(revokedAfterTimestamp(check)).toBe(false)
    })
})

describe('isLongTerm', () => {
    it('holds for the levels that add validation material after the seal', () => {
        expect(isLongTerm(createSealCheck({level: PadesLevel.BASELINE_LT}))).toBe(true)
        expect(isLongTerm(createSealCheck({level: PadesLevel.BASELINE_LTA}))).toBe(true)
        expect(isLongTerm(createSealCheck({level: PadesLevel.BASELINE_T}))).toBe(false)
        expect(isLongTerm(createSealCheck({level: PadesLevel.BASELINE_B}))).toBe(false)
        expect(isLongTerm(createSealCheck({level: PadesLevel.NOT_BASELINE}))).toBe(false)
    })
})

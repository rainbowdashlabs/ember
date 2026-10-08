/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    PadesLevel,
    RevocationStatus,
    ValidationIndication,
    type SealCheck,
    type SealVerification,
    type TimestampCheck,
} from '@/api/generated/schema'

/** A timestamp by a pinned service that passed, for a test to change. */
export function createTimestampCheck(overrides: Partial<TimestampCheck> = {}): TimestampCheck {
    return {
        time: '2026-10-05T09:12:04Z',
        authority: {
            subject: 'CN=Test Timestamp Responder,O=Test Trust\\, Inc.,C=DE',
            serialNumber: '0a6b2f4e',
            sha256Fingerprint: '9C:2D:51:7E:A3:08:F6:4B:1D:C0:3A:92:E7:5F:18:6C:04:BB:D9:21:7A:E5:30:C8:6F:12:9D:A4:57:0E:B3:61',
        },
        pinnedAuthority: true,
        indication: ValidationIndication.PASSED,
        subIndication: null,
        intact: true,
        ...overrides,
    }
}

/** A station seal of this installation at LT that passed, for a test to change. */
export function createSealCheck(overrides: Partial<SealCheck> = {}): SealCheck {
    return {
        signer: {
            subject: 'CN=Jugendfeuerwehr Musterstadt,UID=0b6f6a52-3a4e-4d1c-9a27-5f8e2c1d7b90,O=ember.example.org',
            serialNumber: '7d20c1e94a',
            sha256Fingerprint: 'B1:0E:6C:2A:94:F7:13:5D:E8:42:7B:C9:06:3F:A1:D4:58:2E:9B:70:C3:1A:E6:84:2F:D5:0C:B7:69:13:4E:A8',
        },
        issuer: {
            subject: 'CN=Ember signing authority ember.example.org,O=ember.example.org',
            serialNumber: '5c1e0a7f3b9d2e41',
            sha256Fingerprint: '3A:7F:12:C4:9B:E0:55:D1:08:6A:F3:2C:91:BE:47:0D:E2:19:84:5B:C7:3F:A0:6E:D8:21:9C:44:F5:0B:7A:13',
        },
        issuedHere: true,
        indication: ValidationIndication.TOTAL_PASSED,
        subIndication: null,
        validatorIndication: ValidationIndication.TOTAL_PASSED,
        validatorSubIndication: null,
        level: PadesLevel.BASELINE_LT,
        signingTime: '2026-10-05T09:12:03Z',
        intact: true,
        coversWholeFile: false,
        modifiedAfterSealing: false,
        revocation: {status: RevocationStatus.GOOD, revokedAt: null, reason: null},
        timestamps: [createTimestampCheck()],
        ...overrides,
    }
}

/** The answer for a file this installation does not hold, with the seals given. */
export function createSealVerification(overrides: Partial<SealVerification> = {}): SealVerification {
    return {
        document: {held: false, sealedAt: null, sealLevel: null},
        signatures: [],
        documentTimestamps: [],
        ...overrides,
    }
}

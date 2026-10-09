/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    PadesLevel,
    RevocationStatus,
    ValidationIndication,
    ValidationSubIndication,
    type CertificateFacts,
    type SealCheck,
    type TimestampCheck,
} from '@/api/generated/schema'

/**
 * The one answer a reader gets about a seal before any detail.
 *
 * - `sealedHere`: sealed by a station of this installation, and the sealed content is unchanged
 * - `sealedByPartner`: sealed by a federation partner of a station here, whose authority this
 *   installation pinned, and the sealed content is unchanged
 * - `altered`: the sealed content no longer matches its seal
 * - `modifiedAfterSealing`: the sealed content is unchanged, but the file was changed after sealing
 * - `notIssuedHere`: somebody else's seal, which this installation cannot vouch for
 * - `invalid`: the seal failed for another reason than a change, such as its certificate
 * - `unclear`: none of the above could be settled
 */
export type SealVerdict =
    | 'sealedHere'
    | 'sealedByPartner'
    | 'altered'
    | 'modifiedAfterSealing'
    | 'notIssuedHere'
    | 'invalid'
    | 'unclear'

const FAILED: ReadonlySet<ValidationIndication> = new Set([ValidationIndication.TOTAL_FAILED, ValidationIndication.FAILED])
const PASSED: ReadonlySet<ValidationIndication> = new Set([ValidationIndication.TOTAL_PASSED, ValidationIndication.PASSED])

/** The failures that mean the signed content, the signature value or the file's structure is broken. */
const CHANGED: ReadonlySet<ValidationSubIndication> = new Set([
    ValidationSubIndication.HASH_FAILURE,
    ValidationSubIndication.SIG_CRYPTO_FAILURE,
    ValidationSubIndication.FORMAT_FAILURE,
])

/** The levels that carry validation material in a revision of their own after the seal. */
const LONG_TERM: ReadonlySet<PadesLevel> = new Set([PadesLevel.BASELINE_LT, PadesLevel.BASELINE_LTA])

/**
 * What a seal amounts to for the reader.
 *
 * <p>A change to the sealed content is said first, whoever sealed it: it is the one answer that holds
 * whatever else is unclear. A change made to the file after sealing comes next, since the sealed part may
 * still be fine while what the reader sees is not what was sealed. Only a broken content, signature value
 * or file structure reads as altered; a seal that fails for any other reason reads as invalid. A
 * stranger's seal is never called valid here, since the server already refuses to pass one. Only a
 * passed seal of this installation reads as sealed here, and a passed seal of a federation partner says
 * so in its own words, never as sealed here.
 *
 * @param check the server's answer for one signature
 */
export function verdictOf(check: SealCheck): SealVerdict {
    if (!check.intact) return 'altered'
    if (check.modifiedAfterSealing) return 'modifiedAfterSealing'
    const failed = FAILED.has(check.indication)
    if (failed && check.subIndication && CHANGED.has(check.subIndication)) return 'altered'
    if (check.subIndication === ValidationSubIndication.NOT_ISSUED_HERE) return 'notIssuedHere'
    if (failed) return 'invalid'
    if (check.issuedHere && PASSED.has(check.indication)) return 'sealedHere'
    if (check.partner && PASSED.has(check.indication)) return 'sealedByPartner'
    return 'unclear'
}

/**
 * The name of the partner station whose seal it is: the name its partnership knows it by, else the
 * name its certificate carries.
 *
 * @param check the server's answer for one signature
 */
export function partnerName(check: SealCheck): string | null {
    if (!check.partner) return null
    return check.partner.name ?? certificateName(check.signer)
}

/**
 * Whether a seal's level carries validation material after the seal, which is why such a seal never
 * covers the whole file.
 *
 * @param check the server's answer for one signature
 */
export function isLongTerm(check: SealCheck): boolean {
    return LONG_TERM.has(check.level)
}

/**
 * One attribute of a certificate's subject, read from its RFC 2253 form.
 *
 * <p>The attributes are separated by commas that are not escaped, and an escaped character stands
 * for itself. Multi-valued attributes and hex-encoded values are not taken apart, since the station
 * name and the timestamp service's name are never written that way.
 *
 * @param subject the subject as the server wrote it
 * @param name    the attribute's short name, such as `CN`
 * @return the value, or null where the subject has no such attribute
 */
export function subjectAttribute(subject: string, name: string): string | null {
    for (const part of subject.split(/(?<!\\),/)) {
        const separator = part.indexOf('=')
        if (separator < 0) continue
        if (part.slice(0, separator).trim().toUpperCase() !== name) continue
        return part.slice(separator + 1).trim().replace(/\\(.)/g, '$1')
    }
    return null
}

/**
 * The name a certificate goes by: its common name, else its organisation, else the whole subject.
 *
 * @param certificate the certificate, where there is one
 */
export function certificateName(certificate: CertificateFacts | null | undefined): string | null {
    if (!certificate) return null
    return subjectAttribute(certificate.subject, 'CN')
        ?? subjectAttribute(certificate.subject, 'O')
        ?? certificate.subject
}

/**
 * The earliest timestamp inside a seal that proves the seal existed by then: one that is intact, comes
 * from a timestamp service this installation pins, passed the check and says when. Any other timestamp
 * proves nothing about the time.
 *
 * @param check the server's answer for one signature
 */
export function provingTimestamp(check: SealCheck): TimestampCheck | null {
    const dated = check.timestamps
        .filter(stamp => stamp.intact && stamp.pinnedAuthority && PASSED.has(stamp.indication))
        .map(stamp => ({stamp, at: Date.parse(stamp.time ?? '')}))
        .filter(({at}) => Number.isFinite(at))
        .sort((a, b) => a.at - b.at)
    return dated[0]?.stamp ?? null
}

/**
 * Whether the key was revoked only after a timestamp proved the seal, which leaves the seal standing:
 * it was made while the key was still good.
 *
 * @param check the server's answer for one signature
 */
export function revokedAfterTimestamp(check: SealCheck): boolean {
    const revokedAt = check.revocation.revokedAt
    const stamp = provingTimestamp(check)
    if (check.revocation.status !== RevocationStatus.REVOKED || !revokedAt || !stamp?.time) return false
    return Date.parse(revokedAt) > Date.parse(stamp.time)
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
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
 * - `altered`: the file no longer matches its seal
 * - `notIssuedHere`: somebody else's seal, which this installation cannot vouch for
 * - `unclear`: none of the above could be settled
 */
export type SealVerdict = 'sealedHere' | 'altered' | 'notIssuedHere' | 'unclear'

const FAILED: ReadonlySet<ValidationIndication> = new Set([ValidationIndication.TOTAL_FAILED, ValidationIndication.FAILED])
const PASSED: ReadonlySet<ValidationIndication> = new Set([ValidationIndication.TOTAL_PASSED, ValidationIndication.PASSED])

/**
 * What a seal amounts to for the reader.
 *
 * <p>A change to the sealed bytes is said first, whoever sealed them: it is the one answer that holds
 * whatever else is unclear. A stranger's seal is never called valid here, since the server already
 * refuses to pass one, and only a passed seal of this installation reads as sealed here.
 *
 * @param check the server's answer for one signature
 */
export function verdictOf(check: SealCheck): SealVerdict {
    if (!check.intact || FAILED.has(check.indication)) return 'altered'
    if (check.subIndication === ValidationSubIndication.NOT_ISSUED_HERE) return 'notIssuedHere'
    if (check.issuedHere && PASSED.has(check.indication)) return 'sealedHere'
    return 'unclear'
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
 * The earliest timestamp inside a seal that is intact and says when, which is the one that proves
 * the seal existed by then.
 *
 * @param check the server's answer for one signature
 */
export function provingTimestamp(check: SealCheck): TimestampCheck | null {
    const dated = check.timestamps
        .filter(stamp => stamp.intact)
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

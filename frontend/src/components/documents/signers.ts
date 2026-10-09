/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {SignatureRole} from '@/api/generated/schema'

/**
 * The signers a signature field or a signature line of a letter offers, in the order they are listed
 * and a new PDF signature field takes the first free one.
 */
export const SIGNERS: readonly SignatureRole[] = [
    SignatureRole.PARTICIPANT,
    SignatureRole.GUARDIAN_1,
    SignatureRole.GUARDIAN_2,
    SignatureRole.ISSUER,
    SignatureRole.ANY_GUARDIAN,
    SignatureRole.EACH_GUARDIAN,
]

/** The signers a field to fill in may ask: everybody but the issuer, whose letters are signed unattended. */
export const FILL_IN_SIGNERS: readonly SignatureRole[] = SIGNERS.filter(role => role !== SignatureRole.ISSUER)

/** The signers that ask the same guardian to sign as another one, so the two never stand together. */
const CLASHES: Readonly<Partial<Record<SignatureRole, readonly SignatureRole[]>>> = {
    [SignatureRole.EACH_GUARDIAN]: [SignatureRole.GUARDIAN_1, SignatureRole.GUARDIAN_2],
    [SignatureRole.GUARDIAN_1]: [SignatureRole.EACH_GUARDIAN],
    [SignatureRole.GUARDIAN_2]: [SignatureRole.EACH_GUARDIAN],
}

/**
 * @param taken the signers already asked for
 * @returns the first signer that asks nobody already asked to sign, or null where none is left
 */
export function freeSignerOf(taken: readonly (SignatureRole | null | undefined)[]): SignatureRole | null {
    const used = new Set(taken)
    return SIGNERS.find(role => !used.has(role) && !(CLASHES[role] ?? []).some(other => used.has(other))) ?? null
}

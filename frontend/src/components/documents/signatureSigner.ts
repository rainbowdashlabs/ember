/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldRole, SignerCapacity, type AskedFieldResponse} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'

/** What naming the signer of a field takes: who it asks for, the person it names and how they sign. */
export type SignerOfField = Pick<AskedFieldResponse, 'role' | 'signerName' | 'capacity'>

/**
 * Who signs a field, in words: any of the member's guardians, the person it names, that person through a
 * guardian's account, or nobody where the field names nobody.
 *
 * @param field the field
 * @param t     translates the words
 */
export function signerOf(field: SignerOfField, t: Translate): string {
    if (field.role === FieldRole.ANY_GUARDIAN) return t('signing.ask.anyGuardian')
    if (!field.signerName) return t('signing.ask.nobody')
    if (field.capacity === SignerCapacity.MEMBER_THROUGH_ACCOUNT) {
        return t('signing.ask.throughAccount', {name: field.signerName})
    }
    return field.signerName
}

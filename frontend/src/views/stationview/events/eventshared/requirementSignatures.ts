/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    RequirementSignatureState,
    type RequirementSignature,
    type RequirementSignatureField,
} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'

const GUARDIAN_PLACE = /^guardian(\d+)$/

/**
 * Who signs a field, read from the field's name: the participant, a guardian by place, any one guardian or
 * the issuer, followed by the name of whoever is asked where somebody in particular is.
 *
 * @param field the field
 * @param t     the translator
 * @returns the label to show beside the field's state
 */
export function signerLabel(field: Pick<RequirementSignatureField, 'name' | 'signerName'>, t: Translate): string {
    const role = roleOf(field.name, t)
    return field.signerName ? t('events.documents.signerNamed', {role, name: field.signerName}) : role
}

function roleOf(name: string, t: Translate): string {
    if (name === 'participant') return t('events.documents.signer.participant')
    if (name === 'anyGuardian') return t('events.documents.signer.anyGuardian')
    if (name === 'issuer') return t('events.documents.signer.issuer')
    const place = GUARDIAN_PLACE.exec(name)?.[1]
    return place ? t('events.documents.signer.guardian', {place}) : name
}

/**
 * The fields of a copy the reader can sign now, for themselves or for a member in their care.
 *
 * @param signature the signatures asked for on the copy, or null where none were
 * @returns the fields, in the order the copy carries them
 */
export function fieldsToSign(signature: RequirementSignature | null | undefined): RequirementSignatureField[] {
    return signature?.fields.filter(field => field.yours && field.state === RequirementSignatureState.OPEN) ?? []
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {SignerCapacity} from '@/api/generated/schema'
import type {BatchFieldChoice, FillInResponse, OpenSignatureResponse} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'
import {fillInEntries, fillInsComplete, type FillInValues} from './fillIns'

/** The fields of one document the reader may sign, in the order the server lists them. */
export interface SigningDocumentGroup {
    requestUid: string
    title: string
    fields: OpenSignatureResponse[]
}

/** A member who signs their own field through the reader's account and draws their own picture. */
export interface ThroughAccountSigner {
    memberId: number
    name: string
}

/** One screen of the flow, in the order the reader walks through them. */
export type FlowStep =
    | {kind: 'overview'}
    | {kind: 'document'; group: SigningDocumentGroup; index: number; count: number}
    | {kind: 'holderPicture'}
    | {kind: 'memberPicture'; signer: ThroughAccountSigner}
    | {kind: 'check'}
    | {kind: 'confirm'}

/**
 * The fields grouped by their document, documents in the order they first appear, the one holding the
 * given field first.
 *
 * @param fields       every field waiting for the reader
 * @param untitled     the title of a document that has none any more
 * @param firstFieldId the field the reader came for, whose document comes first
 */
export function groupByDocument(
    fields: readonly OpenSignatureResponse[],
    untitled: string,
    firstFieldId: number | null = null,
): SigningDocumentGroup[] {
    const groups: SigningDocumentGroup[] = []
    for (const field of fields) {
        const group = groups.find(candidate => candidate.requestUid === field.requestUid)
        if (group) group.fields.push(field)
        else groups.push({requestUid: field.requestUid, title: field.documentTitle ?? untitled, fields: [field]})
    }
    const first = groups.findIndex(group => group.fields.some(field => field.fieldId === firstFieldId))
    if (first > 0) groups.unshift(...groups.splice(first, 1))
    return groups
}

/**
 * Which fields start ticked: all of them, or only those the address names where it names any it holds.
 *
 * @param fields the fields waiting for the reader
 * @param named  the field ids the address names, empty for none
 */
export function preselected(fields: readonly OpenSignatureResponse[], named: readonly number[]): number[] {
    const ids = fields.map(field => field.fieldId)
    const known = named.filter(id => ids.includes(id))
    return known.length > 0 ? known : ids
}

/**
 * The field ids a query value names, as `fields=1,2,3` writes them.
 *
 * @param value the query value
 */
export function namedFields(value: unknown): number[] {
    if (typeof value !== 'string') return []
    return value.split(',').map(Number).filter(id => Number.isInteger(id) && id > 0)
}

/** The person whose own signature a field asks for, by name, where it is somebody in the reader's care. */
function cared(field: OpenSignatureResponse): string {
    return field.signerName ?? field.memberName
}

/**
 * Who signs a field, said short, as the overview and the check list it: the reader themselves, the reader
 * for a child, or the child in person.
 *
 * @param field the field
 * @param t     the translator
 */
export function signerShort(field: OpenSignatureResponse, t: Translate): string {
    switch (field.capacity) {
        case SignerCapacity.GUARDIAN: return t('signing.flow.who.asGuardian', {name: field.memberName})
        case SignerCapacity.MEMBER_THROUGH_ACCOUNT: return t('signing.flow.who.themselves', {name: cared(field)})
        default: return t('signing.flow.who.you')
    }
}

/**
 * Who signs a field, as one plain sentence on the document's screen.
 *
 * @param field the field
 * @param t     the translator
 */
export function signerSentence(field: OpenSignatureResponse, t: Translate): string {
    switch (field.capacity) {
        case SignerCapacity.GUARDIAN: return t('signing.flow.line.asGuardian', {name: field.memberName})
        case SignerCapacity.MEMBER_THROUGH_ACCOUNT: return t('signing.flow.line.themselves', {name: cared(field)})
        default: return t('signing.flow.line.you')
    }
}

/**
 * The members who sign their own field through the reader's account among the chosen fields, once each.
 *
 * @param fields the chosen fields
 */
export function throughAccountSigners(fields: readonly OpenSignatureResponse[]): ThroughAccountSigner[] {
    const signers: ThroughAccountSigner[] = []
    for (const field of fields) {
        if (field.capacity !== SignerCapacity.MEMBER_THROUGH_ACCOUNT || field.memberId === null) continue
        if (signers.some(signer => signer.memberId === field.memberId)) continue
        signers.push({memberId: field.memberId, name: cared(field)})
    }
    return signers
}

/**
 * Whether the reader signs any of the chosen fields with their own picture: for themselves or as a guardian.
 *
 * @param fields the chosen fields
 */
export function holderSigns(fields: readonly OpenSignatureResponse[]): boolean {
    return fields.some(field => field.capacity !== SignerCapacity.MEMBER_THROUGH_ACCOUNT)
}

/**
 * The official name of the reader as the chosen fields they sign with their own picture print it, or null
 * where none of those fields names them (any guardian may sign).
 *
 * @param fields the chosen fields
 */
export function holderName(fields: readonly OpenSignatureResponse[]): string | null {
    return fields.find(field => field.capacity !== SignerCapacity.MEMBER_THROUGH_ACCOUNT && field.signerName)
        ?.signerName ?? null
}

/**
 * The screens of the flow for the chosen fields: the overview, one per document, the reader's picture
 * where they sign themselves, one per member drawing their own, the check and the confirmation.
 *
 * @param groups the documents with the chosen fields only, documents without any left out
 */
export function flowSteps(groups: readonly SigningDocumentGroup[]): FlowStep[] {
    const fields = groups.flatMap(group => group.fields)
    const steps: FlowStep[] = [{kind: 'overview'}]
    groups.forEach((group, index) => steps.push({kind: 'document', group, index, count: groups.length}))
    if (holderSigns(fields)) steps.push({kind: 'holderPicture'})
    throughAccountSigners(fields).forEach(signer => steps.push({kind: 'memberPicture', signer}))
    steps.push({kind: 'check'}, {kind: 'confirm'})
    return steps
}

/**
 * The documents with only the chosen fields left in them, in the overview's order.
 *
 * @param groups the documents with every field
 * @param chosen the ids of the chosen fields
 */
export function chosenGroups(groups: readonly SigningDocumentGroup[], chosen: readonly number[]): SigningDocumentGroup[] {
    return groups
        .map(group => ({...group, fields: group.fields.filter(field => chosen.includes(field.fieldId))}))
        .filter(group => group.fields.length > 0)
}

/**
 * Whether a document's screen is done: every statement confirmed and every field to fill in as it must be.
 *
 * @param group     the document with the chosen fields
 * @param confirmed the confirmed statements, by field id
 * @param fillIns   the fields to fill in, by signature field id
 * @param values    what was typed, by signature field id
 */
export function documentReady(
    group: SigningDocumentGroup,
    confirmed: Readonly<Record<number, boolean>>,
    fillIns: Readonly<Record<number, readonly FillInResponse[]>>,
    values: Readonly<Record<number, FillInValues>>,
): boolean {
    return group.fields.every(field => confirmed[field.fieldId] === true
        && fillInsComplete(fillIns[field.fieldId] ?? [], values[field.fieldId] ?? {}))
}

/**
 * What the start binds: every chosen field in the overview's order, each with what was typed for it.
 *
 * @param groups  the documents with the chosen fields
 * @param fillIns the fields to fill in, by signature field id
 * @param values  what was typed, by signature field id
 */
export function batchChoices(
    groups: readonly SigningDocumentGroup[],
    fillIns: Readonly<Record<number, readonly FillInResponse[]>>,
    values: Readonly<Record<number, FillInValues>>,
): BatchFieldChoice[] {
    return groups.flatMap(group => group.fields).map(field => ({
        fieldId: field.fieldId,
        entries: fillInEntries(fillIns[field.fieldId] ?? [], values[field.fieldId] ?? {}),
    }))
}

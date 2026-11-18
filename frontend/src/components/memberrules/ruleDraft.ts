/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {InjectionKey, Ref} from 'vue'
import {
    FieldOrigin,
    RuleNeed,
    RuleNodeKind,
    RuleOperator,
    RuleSubject,
    type FieldType,
    type RuleField,
    type RuleNode,
    type RuleOperatorOption,
    type RuleValue,
} from '@/api/generated/schema'

/** The operators that count from a reference day, besides every check on an age or a membership length. */
const COUNTS_FROM_REFERENCE: readonly RuleOperator[] = [
    RuleOperator.WITHIN_NEXT,
    RuleOperator.WITHIN_LAST,
    RuleOperator.EXPIRES_WITHIN,
    RuleOperator.EXPIRED,
    RuleOperator.STATE_IN,
]

/** Whether a subject reads a field, and so needs one picked before it offers operators. */
export function readsField(subject: RuleSubject): boolean {
    return subject === RuleSubject.AGE || subject === RuleSubject.FIELD || subject === RuleSubject.CLUSTER_FIELD
}

/** A value carrying nothing, for an operator that needs nothing. */
export function emptyValue(): RuleValue {
    return {
        amount: null,
        amountTo: null,
        answers: null,
        date: null,
        number: null,
        numberTo: null,
        states: null,
        text: null,
        unit: null,
        userTypes: null,
    }
}

/** A block of the given kind with no children yet. */
export function emptyBlock(kind: RuleNodeKind = RuleNodeKind.ALL): RuleNode {
    return {
        kind,
        children: [],
        subject: null,
        operator: null,
        fieldId: null,
        clusterFieldId: null,
        groupId: null,
        tagId: null,
        value: null,
        referenceDate: null,
    }
}

/** A check on a group, which is what a new check starts as until another subject is picked. */
export function emptyCheck(): RuleNode {
    return {...emptyBlock(), kind: RuleNodeKind.CHECK, subject: RuleSubject.GROUP, operator: RuleOperator.IN}
}

/** The field a check reads, among the readable ones, or undefined where it names none or one that is gone. */
export function fieldOf(check: RuleNode, fields: readonly RuleField[]): RuleField | undefined {
    if (check.clusterFieldId !== null) {
        return fields.find(field => field.origin === FieldOrigin.CLUSTER && field.id === check.clusterFieldId)
    }
    if (check.fieldId !== null) return fields.find(field => field.origin === FieldOrigin.STATION && field.id === check.fieldId)
    return undefined
}

/** The fields a subject can read: date fields for an age, the station's or the association's for a field check. */
export function fieldsFor(subject: RuleSubject, fields: readonly RuleField[], options: readonly RuleOperatorOption[]) {
    return fields.filter(field => {
        if (subject === RuleSubject.FIELD && field.origin !== FieldOrigin.STATION) return false
        if (subject === RuleSubject.CLUSTER_FIELD && field.origin !== FieldOrigin.CLUSTER) return false
        return operatorsFor(options, subject, field.fieldType).length > 0
    })
}

/** The operators a check offers, in the order the server lists them. */
export function operatorsFor(
    options: readonly RuleOperatorOption[],
    subject: RuleSubject,
    fieldType: FieldType | null | undefined,
): RuleOperatorOption[] {
    const wanted = readsField(subject) ? (fieldType ?? null) : null
    if (readsField(subject) && wanted === null) return []
    return options.filter(option => option.subject === subject && option.fieldType === wanted)
}

/** What the check's operator needs as its value, or undefined where the operator is not offered. */
export function needOf(
    check: RuleNode,
    options: readonly RuleOperatorOption[],
    fields: readonly RuleField[],
): RuleNeed | undefined {
    const subject = check.subject
    if (!subject) return undefined
    const fieldType = fieldOf(check, fields)?.fieldType
    return operatorsFor(options, subject, fieldType).find(option => option.operator === check.operator)?.need
}

/** Whether a check counts from a reference day, which is today unless it names a fixed one. */
export function countsFromReference(check: RuleNode): boolean {
    return check.subject === RuleSubject.AGE
        || check.subject === RuleSubject.MEMBER_SINCE
        || (check.operator !== null && COUNTS_FROM_REFERENCE.includes(check.operator))
}

/**
 * The check with another subject: its target, operator and value start over, since none of them carries
 * across subjects. A group or tag check takes the first operator its subject offers.
 */
export function withSubject(check: RuleNode, subject: RuleSubject, options: readonly RuleOperatorOption[]): RuleNode {
    const first = readsField(subject) ? null : operatorsFor(options, subject, null)[0]?.operator ?? null
    return {
        ...check,
        subject,
        operator: first,
        fieldId: null,
        clusterFieldId: null,
        groupId: null,
        tagId: null,
        value: null,
        referenceDate: null,
    }
}

/** The check reading another field: the operator starts over with the first one the field offers. */
export function withField(check: RuleNode, field: RuleField, options: readonly RuleOperatorOption[]): RuleNode {
    const subject = check.subject ?? RuleSubject.FIELD
    return {
        ...check,
        fieldId: field.origin === FieldOrigin.STATION ? field.id : null,
        clusterFieldId: field.origin === FieldOrigin.CLUSTER ? field.id : null,
        operator: operatorsFor(options, subject, field.fieldType)[0]?.operator ?? null,
        value: null,
        referenceDate: null,
    }
}

/** The check with another operator: the value starts over when the new one needs something else. */
export function withOperator(
    check: RuleNode,
    operator: RuleOperator,
    options: readonly RuleOperatorOption[],
    fields: readonly RuleField[],
): RuleNode {
    const before = needOf(check, options, fields)
    const next = {...check, operator}
    const after = needOf(next, options, fields)
    const value = before === after ? check.value : null
    return {...next, value, referenceDate: countsFromReference(next) ? check.referenceDate : null}
}

/** The value of a check with one part changed, starting from an empty value where it had none. */
export function withValue(check: RuleNode, part: Partial<RuleValue>): RuleNode {
    return {...check, value: {...(check.value ?? emptyValue()), ...part}}
}

/** How many checks a tree holds, which the server caps. */
export function countChecks(node: RuleNode): number {
    if (node.kind === RuleNodeKind.CHECK) return 1
    return node.children.reduce((sum, child) => sum + countChecks(child), 0)
}

/** Hands the checks of the stored rule that lost what they asked about to every row of the editor. */
export const LOST_CHECKS: InjectionKey<Ref<ReadonlySet<RuleNode>>> = Symbol('lostChecks')

/**
 * The checks of a stored rule that name nothing any more, because the field, group or tag they asked about was
 * deleted underneath them, the same test the server makes before it runs a rule. Such a rule rests.
 */
export function lostChecks(node: RuleNode): RuleNode[] {
    if (node.kind !== RuleNodeKind.CHECK) return node.children.flatMap(lostChecks)
    switch (node.subject) {
        case RuleSubject.GROUP: return node.groupId === null ? [node] : []
        case RuleSubject.TAG: return node.tagId === null ? [node] : []
        case RuleSubject.CLUSTER_FIELD: return node.clusterFieldId === null ? [node] : []
        case RuleSubject.FIELD:
        case RuleSubject.AGE: return node.fieldId === null && node.clusterFieldId === null ? [node] : []
        default: return []
    }
}

/** How many blocks of a tree hold nothing, which the server refuses: an empty block would ask nothing at all. */
export function countEmptyBlocks(node: RuleNode): number {
    if (node.kind === RuleNodeKind.CHECK) return 0
    const own = node.children.length === 0 ? 1 : 0
    return node.children.reduce((sum, child) => sum + countEmptyBlocks(child), own)
}

/** How many levels a tree has, one for a single node, as the server counts them. */
export function depthOf(node: RuleNode): number {
    return 1 + node.children.reduce((deepest, child) => Math.max(deepest, depthOf(child)), 0)
}

/** How deep a rule may go and how many checks it may hold, the same limits the server keeps. */
export const RULE_LIMITS = {depth: 6, checks: 50} as const

/** Whether a value carries what the need asks for, the same way the server measures it. */
export function satisfies(value: RuleValue | null, need: RuleNeed): boolean {
    const v = value ?? emptyValue()
    switch (need) {
        case RuleNeed.NOTHING:
            return true
        case RuleNeed.ANSWERS:
            return (v.answers ?? []).some(answer => answer.trim() !== '')
        case RuleNeed.USER_TYPES:
            return (v.userTypes ?? []).length > 0
        case RuleNeed.STATES:
            return (v.states ?? []).length > 0
        case RuleNeed.NUMBER:
            return v.number !== null
        case RuleNeed.NUMBER_RANGE:
            return v.number !== null && v.numberTo !== null && v.number <= v.numberTo
        case RuleNeed.TEXT:
            return (v.text ?? '').trim() !== ''
        case RuleNeed.DATE:
            return v.date !== null && v.date !== ''
        case RuleNeed.DURATION:
            return v.amount !== null && v.amount >= 0 && v.unit !== null
        case RuleNeed.YEARS:
            return v.amount !== null && v.amount >= 0
        case RuleNeed.YEARS_RANGE:
            return v.amount !== null && v.amountTo !== null && v.amount >= 0 && v.amount <= v.amountTo
    }
}

/**
 * Whether a check names everything the server asks of it: the group or tag it asks about, or a field it can
 * read, an operator offered there, and a value that carries what that operator needs.
 */
export function complete(
    check: RuleNode,
    options: readonly RuleOperatorOption[],
    fields: readonly RuleField[],
): boolean {
    if (check.subject === RuleSubject.GROUP && check.groupId === null) return false
    if (check.subject === RuleSubject.TAG && check.tagId === null) return false
    const need = needOf(check, options, fields)
    return need !== undefined && satisfies(check.value, need)
}

/** How many checks of a tree are still incomplete. */
export function incompleteChecks(
    node: RuleNode,
    options: readonly RuleOperatorOption[],
    fields: readonly RuleField[],
): number {
    if (node.kind === RuleNodeKind.CHECK) return complete(node, options, fields) ? 0 : 1
    return node.children.reduce((sum, child) => sum + incompleteChecks(child, options, fields), 0)
}

/** Whether a check reads member data: a field, an age or how long somebody has been a member. */
export function readsMemberData(check: RuleNode): boolean {
    return check.subject !== null && (readsField(check.subject) || check.subject === RuleSubject.MEMBER_SINCE)
}

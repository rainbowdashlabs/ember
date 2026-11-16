/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Ref} from 'vue'
import {ageSourceOf, parseFieldConfig, type FieldSettings} from '@/api/profileFields'
import {
    FieldType, type FieldOrigin, type MergedValue, type ProfileFieldAssignment, type ProfileFieldScope,
} from '@/api/generated/schema'
import {computeAge} from '@/util/age'
import {isWrittenType} from '@/util/questions'

/**
 * The answer to a question that is worked out rather than given, or null where this question is
 * not one of those.
 *
 * <p>Every screen that shows such an answer has to work it out, because none is stored: the field
 * can hold one, and a question that was a number before it became a calculated age still carries
 * whatever was written back then. A screen that read the stored answer showed that old number and
 * went on showing it, beside a list that had already worked out the real one.
 *
 * @param field      the question being shown
 * @param fields     the questions it may count from, which are the ones of its own owner
 * @param rawValueOf reads the answer to another question of the same member
 */
export function calculatedAnswer(
    field: {fieldType: string; config?: FieldSettings | null},
    fields: readonly {id: number; name: string}[],
    rawValueOf: (fieldId: number) => unknown,
): string | null {
    if (field.fieldType !== FieldType.AGE) return null
    const config = parseFieldConfig(field.config)
    const source = ageSourceOf(config, fields)
    if (!source) return ''
    return computeAge(String(rawValueOf(source.id) ?? ''), config.ageMode ?? 'now')
}

/**
 * Reads a profile field value from a {@link Map}-backed {@link Ref}, returning
 * the empty string when the field is not yet set.
 */
export function getFieldValue(values: Ref<Map<number, string>>, fieldId: number): string {
    return values.value.get(fieldId) ?? ''
}

/**
 * Writes a profile field value into a {@link Map}-backed {@link Ref}.
 *
 * Replaces the underlying {@link Map} instance so Vue treats the assignment
 * as a reactive change.
 */
export function setFieldValue(values: Ref<Map<number, string>>, fieldId: number, value: string): void {
    const next = new Map(values.value)
    next.set(fieldId, value)
    values.value = next
}

/** One stored answer, as every list of answers the backend sends carries it. */
export type ProfileFieldValueEntry = Pick<MergedValue, 'fieldId'> & {value?: string | null}

/**
 * The question as one audience meets it: the definition, with whatever their assignment says instead.
 *
 * <p>Width and whether an answer is expected are the definition's until an audience overrides them, so
 * the override standing at null is what keeps the definition's answer free to change underneath.
 */
export function asAsked<T extends {required?: boolean; readonly?: boolean; width?: string | null}>(
    field: T,
    assignment: ProfileFieldAssignment,
): T & {required: boolean; width: string | null; readonly: boolean; position: number} {
    return {
        ...field,
        required: assignment.requiredOverride ?? field.required ?? false,
        width: assignment.widthOverride ?? field.width ?? null,
        readonly: assignment.readonlyOverride ?? field.readonly ?? false,
        position: assignment.position,
    }
}

/** A question as far as the group it belongs to goes. */
export interface GroupedField {
    id: number
    fieldSetId?: number | null
    setPosition?: number
}

/**
 * Puts a form's questions in order with every group kept together, the way the server stores it: a group
 * takes the place of whichever of its questions comes first, and its questions follow in the order they
 * were dragged into, which becomes the group's own order.
 *
 * @param ordered the questions in the order they were dragged into
 * @param fields  every question of the owner, which says which group each is in
 */
export function keepSetsTogether(ordered: readonly number[], fields: readonly GroupedField[]): number[] {
    return groupTogether(ordered, fields, (setId, setOf) => ordered.filter(other => setOf(other) === setId))
}

/**
 * Puts questions in order with every group standing where its first question stands.
 *
 * @param membersOf the questions a group lists at its place
 */
function groupTogether(
    ordered: readonly number[],
    fields: readonly GroupedField[],
    membersOf: (setId: number, setOf: (id: number) => number | null) => number[],
): number[] {
    const setOf = setLookup(fields)
    const placed = new Set<number>()
    const result: number[] = []
    for (const id of ordered) {
        const setId = setOf(id)
        if (setId === null) {
            result.push(id)
            continue
        }
        if (placed.has(setId)) continue
        placed.add(setId)
        result.push(...membersOf(setId, setOf))
    }
    return result
}

/** Which group a question is in, or null for a question in none. */
function setLookup(fields: readonly GroupedField[]): (id: number) => number | null {
    const byId = new Map(fields.map(field => [field.id, field]))
    return id => byId.get(id)?.fieldSetId ?? null
}

/**
 * The groups whose own order a form's new order changes, each with all its questions in the new order, the
 * same way the server works it out.
 *
 * <p>The questions of a group on the form take the places those questions held in the group, in the order
 * they were dragged into. Questions of the group the form does not ask keep their places.
 *
 * @param ordered the questions in the order they were dragged into
 * @param fields  every question of the owner, which says which group each is in and where
 * @return the full new order of every group that changes, by group
 */
export function reorderedSets(ordered: readonly number[], fields: readonly GroupedField[]): Map<number, number[]> {
    const setOf = setLookup(fields)
    const arrived = new Map<number, number[]>()
    for (const id of new Set(ordered)) {
        const setId = setOf(id)
        if (setId !== null) arrived.set(setId, [...arrived.get(setId) ?? [], id])
    }
    const reordered = new Map<number, number[]>()
    for (const [setId, moved] of arrived) {
        const stored = membersOfSet(fields, setId).map(field => field.id)
        let arrival = 0
        const next = stored.map(id => moved.includes(id) ? moved[arrival++] ?? id : id)
        if (next.some((id, slot) => id !== stored[slot])) reordered.set(setId, next)
    }
    return reordered
}

/**
 * Puts a form's questions in order with every group kept together in the group's own order, the way the
 * server puts every other form back once a group's order has changed.
 *
 * @param ordered the questions in the order the form had them
 * @param fields  every question of the owner, which says which group each is in and where
 */
export function keepSetsInTheirOrder(ordered: readonly number[], fields: readonly GroupedField[]): number[] {
    return groupTogether(ordered, fields, setId =>
        membersOfSet(fields, setId).map(field => field.id).filter(other => ordered.includes(other)))
}

/** The questions with every group of the new orders given its new place for each of its questions. */
export function withSetOrders<T extends GroupedField>(
    fields: readonly T[],
    orders: ReadonlyMap<number, readonly number[]>,
): T[] {
    return fields.map(field => {
        const order = field.fieldSetId == null ? undefined : orders.get(field.fieldSetId)
        const index = order?.indexOf(field.id) ?? -1
        return index < 0 ? field : {...field, setPosition: index + 1}
    })
}

/** The assignments with one role's form put in the given order, counted from one as the server does. */
export function placeForm(
    assignments: readonly ProfileFieldAssignment[],
    role: ProfileFieldScope,
    ordered: readonly number[],
): ProfileFieldAssignment[] {
    return assignments.map(assignment =>
        assignment.targetKind === 'ROLE' && assignment.role === role && ordered.includes(assignment.fieldId)
            ? {...assignment, position: ordered.indexOf(assignment.fieldId) + 1}
            : assignment)
}

/**
 * The assignments with every role's form put back so each group stands together in its own order, which
 * is what the server does to every form once a group's order has changed.
 */
export function regroupForms(
    assignments: readonly ProfileFieldAssignment[],
    fields: readonly GroupedField[],
): ProfileFieldAssignment[] {
    const roles = new Set(assignments.flatMap(a => a.targetKind === 'ROLE' && a.role ? [a.role] : []))
    let result = [...assignments]
    for (const role of roles) {
        const ordered = result
            .filter(a => a.targetKind === 'ROLE' && a.role === role)
            .sort((a, b) => a.position - b.position)
            .map(a => a.fieldId)
        result = placeForm(result, role, keepSetsInTheirOrder(ordered, fields))
    }
    return result
}

/** The questions of one group, in the group's own order. */
export function membersOfSet<T extends GroupedField>(fields: readonly T[], setId: number): T[] {
    return fields
        .filter(field => field.fieldSetId === setId)
        .sort((a, b) => (a.setPosition ?? 0) - (b.setPosition ?? 0))
}

/** Who a question belongs to; one that does not say is the station's. */
export function originOf(field: {origin?: FieldOrigin | null}): FieldOrigin {
    return field.origin ?? 'STATION'
}

/** The kinds of question another one can be shown behind, as the server allows them. */
const CONDITION_TYPES: readonly string[] = ['BOOLEAN', 'CHOICE', 'TEXT', 'LONG_TEXT']

/** Whether another question can be shown behind a question of this kind. */
export function isConditionType(fieldType: string | null | undefined): boolean {
    return !!fieldType && CONDITION_TYPES.includes(fieldType)
}

/** A question as far as depending on another question goes. */
export interface DependentField {
    id: number
    origin?: FieldOrigin
    shownIfFieldId?: number | null
    /** What kind of answer it takes; a question without one is read as yes/no. */
    fieldType?: string
}

/**
 * Whether a question is hidden because the question it depends on does not let it in: a yes/no question
 * not answered yes, or a choice or text question left blank.
 *
 * <p>Only where that yes/no question is on the same form: a member asked only the dependent question sees
 * it as usual. The yes/no question is one of the same owner, since a station and its association number
 * their questions apart.
 *
 * @param field   the question
 * @param fields  every question on the form
 * @param valueOf the answer to a question, as text or as the value itself
 */
export function hiddenByDependency<T extends DependentField>(
    field: T,
    fields: readonly T[],
    valueOf: (field: T) => unknown,
): boolean {
    if (field.shownIfFieldId == null) return false
    const controller = fields.find(other => other.id === field.shownIfFieldId && originOf(other) === originOf(field))
    if (!controller) return false
    return !admits(controller, valueOf(controller))
}

/** Whether an answer to the question another depends on lets that one in. */
function admits(controller: DependentField, answer: unknown): boolean {
    if (!controller.fieldType || controller.fieldType === 'BOOLEAN') return answer === true || answer === 'true'
    if (answer == null) return false
    const text = String(answer)
    return /\S/.test(text) && text !== 'null' && !/^"\s*"$/.test(text)
}

/** A question as far as whether a member still has to answer it goes. */
export interface ExpectedField extends DependentField {
    fieldType: string
    required?: boolean
    readonly?: boolean
    readonlyAtStation?: boolean
}

/**
 * The questions a member has to answer and has left blank, judged the way the server judges a profile
 * complete.
 *
 * <p>Only a question somebody writes counts: a heading, a gap and an age hold no answer to give, and
 * a question the member may only read is not theirs to fill in, whether the station or its association
 * locked it. A question hidden behind a question that does not let it in does not count either.
 *
 * @param fields  every question on the form
 * @param valueOf the answer to a question, as text
 */
export function incompleteFields<T extends ExpectedField>(fields: readonly T[], valueOf: (field: T) => string): T[] {
    return fields.filter(field => {
        if (!field.required || field.readonly || field.readonlyAtStation) return false
        if (!isWrittenType(field.fieldType)) return false
        if (hiddenByDependency(field, fields, valueOf)) return false
        const value = valueOf(field)
        return !value || value === '""' || value === 'null'
    })
}

/** The questions of a form that are shown, the ones hidden behind another question left out. */
export function shownFields<T extends DependentField>(fields: readonly T[], valueOf: (field: T) => unknown): T[] {
    return fields.filter(field => !hiddenByDependency(field, fields, valueOf))
}

/**
 * The key an answer is held under on the profile, which is the pair and not the id.
 *
 * <p>A station numbers its own fields and an association numbers its own, in separate tables, so the
 * id alone does not name a question on a profile that shows both.
 */
export function profileKey(fieldId: number, origin: FieldOrigin): string {
    return `${origin}:${fieldId}`
}

/**
 * Decodes profile answers of both origins into a map keyed by {@link profileKey}, since two questions on
 * one profile can carry the same number.
 *
 * Values arrive JSON-encoded; decoding falls back to the raw string when parsing fails so legacy or
 * hand-edited rows still load.
 */
export function decodeMergedValues(
    entries: ReadonlyArray<ProfileFieldValueEntry & {origin?: FieldOrigin}>,
): Map<string, string> {
    const map = new Map<string, string>()
    for (const entry of entries) {
        let val: unknown = entry.value ?? ''
        try {
            val = JSON.parse(val as string)
        } catch {
            void 0
        }
        map.set(profileKey(entry.fieldId, entry.origin ?? 'STATION'),
            typeof val === 'string' ? val : String(val))
    }
    return map
}

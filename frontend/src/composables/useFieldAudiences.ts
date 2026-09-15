/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {listAssignments} from '@/api/profileFields'
import type {ProfileFieldAssignment} from '@/util/profileFields'

/**
 * Reading a list of assignments as the answer to "is this question put to that kind of member".
 *
 * <p>A screen that shows a form, a column or an import target needs to know which of the station's
 * questions belong to the member in front of it. That used to be one value on the question, so a
 * question two kinds of member were asked had to be written twice. It is a list of its own now, and
 * this is the one place that reads it for a screen that is not the editor.
 */
export function fieldAudiences(assignments: Ref<ProfileFieldAssignment[]>) {
    /** Whether one question is put to this kind of member. A group is a separate audience and no role. */
    function isAskedOf(fieldId: number, role: string): boolean {
        return assignments.value.some(a => a.fieldId === fieldId && a.targetKind === 'ROLE' && a.role === role)
    }

    /** Whether one question is put to any of these kinds of member. */
    function isAskedOfAny(fieldId: number, roles: readonly string[]): boolean {
        return roles.some(role => isAskedOf(fieldId, role))
    }

    /** The questions of one kind of member, in the order that audience sees them. */
    function fieldsFor<T extends {id: number}>(fields: readonly T[], role: string): T[] {
        const positions = new Map(assignments.value
            .filter(a => a.targetKind === 'ROLE' && a.role === role)
            .map(a => [a.fieldId, a.position]))
        return fields
            .filter(field => positions.has(field.id))
            .sort((a, b) => (positions.get(a.id) ?? 0) - (positions.get(b.id) ?? 0))
    }

    /** Every kind of member one question is put to, which is how a question names its own audiences. */
    function rolesOf(fieldId: number): string[] {
        return assignments.value
            .filter(a => a.fieldId === fieldId && a.targetKind === 'ROLE' && a.role)
            .map(a => a.role as string)
    }

    return {isAskedOf, isAskedOfAny, fieldsFor, rolesOf}
}

/** The same, for a screen that has to fetch the station's assignments itself. */
export function useFieldAudiences() {
    const assignments: Ref<ProfileFieldAssignment[]> = ref([])

    async function load(): Promise<void> {
        assignments.value = await listAssignments()
    }

    return {assignments, load, ...fieldAudiences(assignments)}
}

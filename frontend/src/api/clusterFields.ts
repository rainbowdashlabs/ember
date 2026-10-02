/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {AssignmentTarget, EditableFieldRequest} from './profileFields'
import type {
    AssignmentRequest,
    ClusterFieldResponse,
    ClusterProfileFieldAssignment,
    ProfileFieldAssignment,
    ProfileFieldScope,
} from './generated/schema'

/**
 * The kinds of member an association may ask.
 *
 * <p>A trial member is missing on purpose, because they belong to one station and are the station's own
 * business, and so is a group, because a group belongs to one station and an association cannot see it.
 */
export const CLUSTER_FIELD_ROLES = ['MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER'] as const

export async function listFields(): Promise<ClusterFieldResponse[]> {
    const res = await client.get<ClusterFieldResponse[]>('/cluster/fields')
    return res.data
}

/**
 * Adds a question. It is written the way the shared field editor writes a station's, with the
 * association's two settings beside it.
 */
export async function createField(data: EditableFieldRequest): Promise<ClusterFieldResponse> {
    const res = await client.post<ClusterFieldResponse>('/cluster/fields', data)
    return res.data
}

export async function updateField(fieldId: number, data: EditableFieldRequest): Promise<void> {
    await client.put(`/cluster/fields/${fieldId}`, data)
}

export async function deleteField(fieldId: number): Promise<void> {
    await client.delete(`/cluster/fields/${fieldId}`)
}

/** Puts one audience's questions in a given order in one request, named by role as a station's order is. */
export async function reorderFields(role: ProfileFieldScope, fieldIds: number[]): Promise<void> {
    await client.put('/cluster/fields/order', {role, fieldIds})
}

/**
 * Every assignment of this cluster's questions, which is what each audience's form is built from.
 *
 * <p>An association can only ever name a kind of member, so its rows carry no kind of target and no
 * group. Both are filled in here, because everything that reads an assignment reads a station's
 * and an association's through the same code and has no business knowing which it got.
 */
export async function listAssignments(): Promise<ProfileFieldAssignment[]> {
    const res = await client.get<ClusterProfileFieldAssignment[]>('/cluster/fields/assignments')
    return res.data.map(assignment => ({...assignment, targetKind: 'ROLE', groupId: null}))
}

/** Asks a kind of member this question, or changes how it is put to them. */
export async function assignField(fieldId: number, assignment: AssignmentRequest): Promise<void> {
    await client.put(`/cluster/fields/${fieldId}/assignments`, assignment)
}

/** Stops asking a kind of member this question. The definition and its answers stay. */
export async function unassignField(fieldId: number, target: AssignmentTarget): Promise<void> {
    await client.delete(`/cluster/fields/${fieldId}/assignments`, {data: target})
}

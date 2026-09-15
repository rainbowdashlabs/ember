/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {AssignmentRequest, AssignmentTarget, ProfileField, ProfileFieldRequest} from './profileFields'
import type {ProfileFieldAssignment} from '@/util/profileFields'

/**
 * A question an association asks, which is a station's question kept in another table.
 *
 * <p>The same shape as a station's on purpose. The settings ride in the same {@code config}, so a
 * width set here lays the field out beside a station's own in the one grid, and anything the station
 * fields gain is gained here without a second declaration to keep in step.
 */
export type ClusterField = ProfileField

export type ClusterFieldRequest = ProfileFieldRequest

/**
 * What an association may ask for.
 *
 * <p>Everything except a date of birth: a station declares its own, and a second one would collide.
 * A section holds no answer and is allowed, so an association can head its block of questions rather
 * than having them run into the station's.
 */
export const CLUSTER_FIELD_TYPES
    = ['TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'ENUM', 'AGE', 'SECTION'] as const

/**
 * The kinds of member an association may ask.
 *
 * <p>A trial member is missing on purpose, because they belong to one station and are the station's own
 * business, and so is a group, because a group belongs to one station and an association cannot see it.
 */
export const CLUSTER_FIELD_ROLES = ['MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER'] as const

export async function listFields(): Promise<ClusterField[]> {
    const res = await client.get<ClusterField[]>('/cluster/fields')
    return res.data
}

export async function createField(data: ClusterFieldRequest): Promise<ClusterField> {
    const res = await client.post<ClusterField>('/cluster/fields', data)
    return res.data
}

export async function updateField(fieldId: number, data: ClusterFieldRequest): Promise<void> {
    await client.put(`/cluster/fields/${fieldId}`, data)
}

export async function deleteField(fieldId: number): Promise<void> {
    await client.delete(`/cluster/fields/${fieldId}`)
}

/** Field id to answer, in the same JSON shape a station field's answer has. */
export interface ClusterFieldValues {
    values: Record<number, string>
}

export async function getMemberValues(memberId: number): Promise<ClusterFieldValues> {
    const res = await client.get<ClusterFieldValues>(`/cluster/fields/member/${memberId}`)
    return res.data
}

export async function setMemberValues(memberId: number, values: Record<number, string>): Promise<void> {
    await client.put(`/cluster/fields/member/${memberId}`, {values})
}

/** Puts one audience's questions in a given order in one request. */
export async function reorderFields(role: string, fieldIds: number[]): Promise<void> {
    await client.put('/cluster/fields/order', {scope: role, fieldIds})
}

/**
 * Every assignment of this cluster's questions, which is what each audience's form is built from.
 *
 * <p>An association can only ever name a kind of member, so its rows carry no kind of target. The one
 * they would carry is filled in here, because everything that reads an assignment reads a station's
 * and an association's through the same code and has no business knowing which it got.
 */
export async function listAssignments(): Promise<ProfileFieldAssignment[]> {
    const res = await client.get<Omit<ProfileFieldAssignment, 'targetKind'>[]>('/cluster/fields/assignments')
    return res.data.map(assignment => ({...assignment, targetKind: 'ROLE'}))
}

/** Asks a kind of member this question, or changes how it is put to them. */
export async function assignField(fieldId: number, assignment: AssignmentRequest): Promise<void> {
    await client.put(`/cluster/fields/${fieldId}/assignments`, assignment)
}

/** Stops asking a kind of member this question. The definition and its answers stay. */
export async function unassignField(fieldId: number, target: AssignmentTarget): Promise<void> {
    await client.delete(`/cluster/fields/${fieldId}/assignments`, {data: target})
}

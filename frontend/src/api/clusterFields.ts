/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {ProfileField, ProfileFieldRequest} from './profileFields'

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

/** Group scope is missing on purpose: a group belongs to one station and a cluster cannot see it. */
export const CLUSTER_FIELD_SCOPES = ['MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER'] as const

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

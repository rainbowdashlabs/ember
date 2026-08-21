/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/**
 * The settings a field carries, shared with the station's own fields so the two lay out together.
 * Only the parts a cluster screen edits are named here.
 */
export interface ClusterFieldConfig {
    required?: boolean
    readonly?: boolean
    notifyOnChange?: boolean
    overview?: boolean
    options?: string[] | null
    defaultValue?: unknown
}

export interface ClusterField {
    id: number
    name: string
    fieldType: string
    config: ClusterFieldConfig
    position: number
    scope: string
    /** Whether the people at the station may read the answer but not write it. */
    stationReadonly: boolean
    keepOnArchive: boolean
}

export type ClusterFieldRequest = Omit<ClusterField, 'id'>

/** A cluster may not ask for a date of birth: a station declares its own and the two would collide. */
export const CLUSTER_FIELD_TYPES = ['TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'ENUM', 'AGE'] as const

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

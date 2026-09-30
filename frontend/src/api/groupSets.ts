/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** A set of groups a member can be in only one of, such as the levels of a training. */
export interface MemberGroupSet {
    id: number
    stationId: string
    name: string
}

/** The sets of groups of the current station, by name. */
export async function listSets(): Promise<MemberGroupSet[]> {
    const res = await client.get<MemberGroupSet[]>('/group-sets')
    return res.data
}

/** Creates a set of groups. */
export async function createSet(name: string): Promise<MemberGroupSet> {
    const res = await client.post<MemberGroupSet>('/group-sets', {name})
    return res.data
}

/** Renames a set of groups. */
export async function renameSet(id: number, name: string): Promise<MemberGroupSet> {
    const res = await client.put<MemberGroupSet>(`/group-sets/${id}`, {name})
    return res.data
}

/** Deletes a set of groups. Its groups stay, with their members, in no set. */
export async function deleteSet(id: number): Promise<void> {
    await client.delete(`/group-sets/${id}`)
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { StationMember, UserTag } from './types'

export async function listTags(): Promise<UserTag[]> {
    const res = await client.get<UserTag[]>('/tags')
    return res.data
}

export async function createTag(data: { name: string }): Promise<UserTag> {
    const res = await client.post<UserTag>('/tags', data)
    return res.data
}

export async function updateTag(id: number, data: { name: string }): Promise<void> {
    await client.put(`/tags/${id}`, data)
}

export async function deleteTag(id: number): Promise<void> {
    await client.delete(`/tags/${id}`)
}

export async function getTagMembers(tagId: number): Promise<StationMember[]> {
    const res = await client.get<StationMember[]>(`/tags/${tagId}/members`)
    return res.data
}

export async function setTagMembers(tagId: number, memberIds: number[]): Promise<void> {
    await client.put(`/tags/${tagId}/members`, { memberIds })
}

export async function getMemberTags(memberId: number): Promise<UserTag[]> {
    const res = await client.get<UserTag[]>(`/station-members/${memberId}/tags`)
    return res.data
}

export async function convertToGroup(tagId: number): Promise<void> {
    await client.post(`/tags/${tagId}/convert-to-group`)
}

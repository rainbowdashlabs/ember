/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource } from './crud'
import type { StationMember, UserTag } from './types'

interface TagRequest {
    name: string
    color?: string | null
    visible?: boolean
    position?: number
}

const tags = createCrudResource<UserTag, TagRequest, TagRequest, UserTag, UserTag, void>('/tags')

export const listTags = tags.list
export const createTag = tags.create
export const updateTag = tags.update
export const deleteTag = tags.remove

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

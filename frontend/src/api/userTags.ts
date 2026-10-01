/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, type NoContent} from './crud'
import type {MemberWithName, TagRequest, UserTag} from './generated/schema'

const tags = createCrudResource<UserTag, TagRequest, TagRequest, UserTag, UserTag, NoContent>('/tags')

export const listTags = tags.list
export const createTag = tags.create
export const updateTag = tags.update
export const deleteTag = tags.remove

export async function getTagMembers(tagId: number): Promise<MemberWithName[]> {
    const res = await client.get<MemberWithName[]>(`/tags/${tagId}/members`)
    return res.data
}

export async function setTagMembers(tagId: number, memberIds: number[]): Promise<void> {
    await client.put(`/tags/${tagId}/members`, {memberIds})
}

export async function getMemberTags(memberId: number): Promise<UserTag[]> {
    const res = await client.get<UserTag[]>(`/station-members/${memberId}/tags`)
    return res.data
}

export async function convertToGroup(tagId: number): Promise<void> {
    await client.post(`/tags/${tagId}/convert-to-group`)
}

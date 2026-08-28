/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { pageParams } from './crud'
import type { ContentModeName } from './news'
import type { MemberIdentity } from './types'
import type { PageRow, SaveRowRequest } from './pageManage'

/**
 * An entry the instance published to every station at once.
 *
 * <p>It is one row belonging to no station, which is why there is no station on it to show and no
 * author to resolve: every station reads the same entry, and it is shown as coming from Ember.
 */
export interface SystemNewsEntry {
    id: number
    title: string
    contentMarkdown: string
    contentHtml: string
    publishedAt?: string
    createdAt?: string
    /** The user types that may read it. Empty means everyone. */
    userTypes: string[]
    commentCount: number
    contentMode: ContentModeName
    /** The blocks of a rich entry. Empty for a plain one, and on list responses. */
    rows: PageRow[]
}

export interface SystemNewsRequest {
    title: string
    contentMarkdown: string
    userTypes: string[]
    publish?: boolean
    /**
     * Whether members are told about it. Off unless asked for: most of what an instance says is a
     * notice people meet when they next look, and waking everyone for it teaches them to ignore the
     * ones that matter.
     */
    notifyMembers?: boolean
    contentMode?: ContentModeName
}

/** A comment under a system entry, as the instance reads it: from every station at once. */
export interface SystemNewsComment {
    id: number
    newsId: number
    parentId: number | null
    author: MemberIdentity | null
    authorName: string
    content: string
    deleted: boolean
    createdAt: string
}

export async function listSystemNews(offset = 0, limit = 50): Promise<SystemNewsEntry[]> {
    const res = await client.get<SystemNewsEntry[]>('/admin/news', {params: pageParams({offset, limit})})
    return res.data
}

export async function getSystemNews(id: number): Promise<SystemNewsEntry> {
    const res = await client.get<SystemNewsEntry>(`/admin/news/${id}`)
    return res.data
}

export async function createSystemNews(data: SystemNewsRequest): Promise<SystemNewsEntry> {
    const res = await client.post<SystemNewsEntry>('/admin/news', data)
    return res.data
}

export async function updateSystemNews(id: number, data: SystemNewsRequest): Promise<SystemNewsEntry> {
    const res = await client.put<SystemNewsEntry>(`/admin/news/${id}`, data)
    return res.data
}

/** Withdraws an entry from every station it was published to. */
export async function retractSystemNews(id: number): Promise<void> {
    await client.delete(`/admin/news/${id}`)
}

export async function saveSystemNewsBlocks(id: number, rows: SaveRowRequest[]): Promise<SystemNewsEntry> {
    const res = await client.put<SystemNewsEntry>(`/admin/news/${id}/blocks`, {rows})
    return res.data
}

export async function enableSystemNewsBlocks(id: number): Promise<SystemNewsEntry> {
    const res = await client.post<SystemNewsEntry>(`/admin/news/${id}/blocks/enable`, {})
    return res.data
}

export async function listSystemNewsComments(id: number): Promise<SystemNewsComment[]> {
    const res = await client.get<SystemNewsComment[]>(`/admin/news/${id}/comments`)
    return res.data
}

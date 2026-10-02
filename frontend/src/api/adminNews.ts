/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { pageParams } from './crud'
import type {
    BlockRowRequest,
    SystemCommentResponse,
    SystemNewsRequest,
    SystemNewsResponse,
} from '@/api/generated/schema'

export async function listSystemNews(offset = 0, limit = 50): Promise<SystemNewsResponse[]> {
    const res = await client.get<SystemNewsResponse[]>('/admin/news', {params: pageParams({offset, limit})})
    return res.data
}

export async function getSystemNews(id: number): Promise<SystemNewsResponse> {
    const res = await client.get<SystemNewsResponse>(`/admin/news/${id}`)
    return res.data
}

export async function createSystemNews(data: SystemNewsRequest): Promise<SystemNewsResponse> {
    const res = await client.post<SystemNewsResponse>('/admin/news', data)
    return res.data
}

export async function updateSystemNews(id: number, data: SystemNewsRequest): Promise<SystemNewsResponse> {
    const res = await client.put<SystemNewsResponse>(`/admin/news/${id}`, data)
    return res.data
}

/** Withdraws an entry from every station it was published to. */
export async function retractSystemNews(id: number): Promise<void> {
    await client.delete(`/admin/news/${id}`)
}

export async function saveSystemNewsBlocks(id: number, rows: BlockRowRequest[]): Promise<SystemNewsResponse> {
    const res = await client.put<SystemNewsResponse>(`/admin/news/${id}/blocks`, {rows})
    return res.data
}

export async function enableSystemNewsBlocks(id: number): Promise<SystemNewsResponse> {
    const res = await client.post<SystemNewsResponse>(`/admin/news/${id}/blocks/enable`, {})
    return res.data
}

export async function listSystemNewsComments(id: number): Promise<SystemCommentResponse[]> {
    const res = await client.get<SystemCommentResponse[]>(`/admin/news/${id}/comments`)
    return res.data
}

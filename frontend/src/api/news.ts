/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { CommentRequest, NewsComment, NewsEntry, NewsRequest } from './types'

export async function listNews(offset = 0, limit = 20): Promise<NewsEntry[]> {
    const res = await client.get<NewsEntry[]>('/news', { params: { offset, limit } })
    return res.data
}

export async function getNews(id: number): Promise<NewsEntry> {
    const res = await client.get<NewsEntry>(`/news/${id}`)
    return res.data
}

export async function createNews(data: NewsRequest): Promise<NewsEntry> {
    const res = await client.post<NewsEntry>('/news', data)
    return res.data
}

export async function updateNews(id: number, data: NewsRequest): Promise<NewsEntry> {
    const res = await client.put<NewsEntry>(`/news/${id}`, data)
    return res.data
}

export async function deleteNews(id: number): Promise<void> {
    await client.delete(`/news/${id}`)
}

export async function listComments(newsId: number): Promise<NewsComment[]> {
    const res = await client.get<NewsComment[]>(`/news/${newsId}/comments`)
    return res.data
}

export async function createComment(newsId: number, data: CommentRequest): Promise<NewsComment> {
    const res = await client.post<NewsComment>(`/news/${newsId}/comments`, data)
    return res.data
}

export async function updateComment(commentId: number, data: CommentRequest): Promise<NewsComment> {
    const res = await client.put<NewsComment>(`/news/comments/${commentId}`, data)
    return res.data
}

export async function deleteComment(commentId: number): Promise<void> {
    await client.delete(`/news/comments/${commentId}`)
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { CommentRequest, NewsComment, NewsEntry, NewsRequest } from './types'

// -- Federation share management --

export interface NewsFederationShareInfo {
    shared: boolean
    scope?: string
    visibilityRole?: string
    partnerIds?: number[]
}

export async function getFederationShare(newsId: number): Promise<NewsFederationShareInfo> {
    const res = await client.get<NewsFederationShareInfo>(`/news/${newsId}/federation`)
    return res.data
}

export async function setFederationShare(newsId: number, scope: string, visibilityRole: string, partnerIds?: number[]): Promise<void> {
    await client.put(`/news/${newsId}/federation`, { scope, visibilityRole, partnerIds: partnerIds ?? [] })
}

export async function removeFederationShare(newsId: number): Promise<void> {
    await client.delete(`/news/${newsId}/federation`)
}

// -- Federated news browse/detail --

export interface FederatedNewsItem {
    id: number
    title: string
    publishedAt: string
    commentCount: number
    visibilityRole: string
    stationName: string
    stationId: string
}

export async function listFederatedNews(): Promise<FederatedNewsItem[]> {
    const res = await client.get<FederatedNewsItem[]>('/federated/news')
    return res.data
}

export interface FederatedNewsDetail {
    id: number
    title: string
    contentHtml: string
    authorName: string
    publishedAt: string
    commentCount: number
}

export async function getFederatedNews(stationUid: string, newsId: number): Promise<FederatedNewsDetail> {
    const res = await client.get<FederatedNewsDetail>(`/federated/${stationUid}/news/${newsId}`)
    return res.data
}

// -- Federated news comments --

export async function listFederatedNewsComments(stationUid: string, newsId: number): Promise<NewsComment[]> {
    const res = await client.get<NewsComment[]>(`/federated/${stationUid}/news/${newsId}/comments`)
    return res.data
}

export async function createFederatedNewsComment(stationUid: string, newsId: number, data: CommentRequest): Promise<NewsComment> {
    const res = await client.post<NewsComment>(`/federated/${stationUid}/news/${newsId}/comments`, data)
    return res.data
}

export async function updateFederatedNewsComment(stationUid: string, commentId: number, data: CommentRequest): Promise<void> {
    await client.put(`/federated/${stationUid}/news/comments/${commentId}`, data)
}

export async function deleteFederatedNewsComment(stationUid: string, commentId: number): Promise<void> {
    await client.delete(`/federated/${stationUid}/news/comments/${commentId}`)
}

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

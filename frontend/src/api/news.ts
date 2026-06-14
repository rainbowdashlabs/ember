/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { CommentRequest, NewsComment, NewsEntry, NewsRequest, PublicBlogEntry } from './types'

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
    contentHtml?: string
    authorName?: string
    publishedAt: string
    commentCount: number
    visibilityRole: string
    stationName: string
    stationId: string
}

interface RawFederatedNewsItem {
    partnerId: number
    partnerStationName: string
    partnerStationUid?: string
    news: {
        id: number
        title: string
        contentHtml?: string
        authorName?: string
        publishedAt: string
        commentCount: number
        visibilityRole: string
    }
}

export async function listFederatedNews(): Promise<FederatedNewsItem[]> {
    const res = await client.get<RawFederatedNewsItem[]>('/federated/news')
    return res.data.map(item => ({
        id: item.news.id,
        title: item.news.title,
        contentHtml: item.news.contentHtml ?? '',
        authorName: item.news.authorName ?? '',
        publishedAt: item.news.publishedAt,
        commentCount: item.news.commentCount,
        visibilityRole: item.news.visibilityRole,
        stationName: item.partnerStationName,
        stationId: item.partnerStationUid ?? String(item.partnerId),
    }))
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

// --- View tracking ---

export interface NewsViewerEntry {
    member: import('./types').MemberIdentity
    seenAt: string | null
}

export interface NewsViewsResponse {
    seen: NewsViewerEntry[]
    unseen: NewsViewerEntry[]
}

export async function recordNewsView(newsId: number): Promise<void> {
    await client.post(`/news/${newsId}/view`)
}

export async function listNewsViewers(newsId: number): Promise<NewsViewsResponse> {
    const res = await client.get<NewsViewsResponse>(`/news/${newsId}/views`)
    return res.data
}

export async function getNewsViewCount(newsId: number): Promise<number> {
    const res = await client.get<{count: number}>(`/news/${newsId}/view-count`)
    return res.data.count
}

// --- Public Blog ---

export async function listPublicBlog(stationUid: string, offset = 0, limit = 20): Promise<PublicBlogEntry[]> {
    const res = await client.get<PublicBlogEntry[]>(`/public/station/${stationUid}/blog`, {params: {offset, limit}})
    return res.data
}

export async function getPublicBlogEntry(stationUid: string, blogId: number): Promise<PublicBlogEntry> {
    const res = await client.get<PublicBlogEntry>(`/public/station/${stationUid}/blog/${blogId}`)
    return res.data
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource, createScopedCrudResource, pageParams } from './crud'
import type { MemberIdentity } from './types'
import type { PageRow, SaveRowRequest } from './pageManage'

/**
 * A file a news entry hands over. It points at the station media library rather than holding
 * bytes of its own, so the same flyer attached to three entries is stored once.
 */
export interface NewsAttachment {
    id: number
    newsId: number
    fileId: number
    label: string | null
    sortOrder: number
    createdAt: string
    fileName: string
    mimeType: string
    fileSize: number
    contentHash: string
}

/**
 * How an entry was written. A plain entry is one markdown field, which is the right tool for a
 * short notice; a rich entry is built from blocks with the page editor. The switch only goes one
 * way, because the stored text of a rich entry is derived from its blocks.
 */
export const ContentMode = {
    SIMPLE: 'SIMPLE',
    RICH: 'RICH',
} as const

export type ContentModeName = (typeof ContentMode)[keyof typeof ContentMode]

export interface NewsEntry {
    id: number
    stationId: string
    title: string
    contentMarkdown: string
    contentHtml: string
    author: MemberIdentity | null
    authorName: string
    publishedAt?: string
    createdAt?: string
    userTypes: string[]
    groupIds: number[]
    tagIds: number[]
    memberIds: number[]
    commentCount: number
    restricted?: boolean
    publicBlog?: boolean
    viewCount: number
    viewedByMe: boolean
    attachments: NewsAttachment[]
    contentMode: ContentModeName
    /** The blocks of a rich entry. Empty for a plain one, and on list responses. */
    rows: PageRow[]
}

export interface NewsRequest {
    title: string
    contentMarkdown: string
    contentHtml: string
    userTypes: string[]
    groupIds: number[]
    tagIds: number[]
    memberIds: number[]
    publicBlog?: boolean
    /** Set when creating an entry that is built from blocks, which brings no written markdown. */
    contentMode?: ContentModeName
}

export interface PublicBlogEntry {
    id: number
    /** Stable opaque public identifier - referenced by NEWS_TEASER cells. */
    publicUid: string
    title: string
    contentHtml: string
    authorName: string
    publishedAt: string
    attachments: NewsAttachment[]
    contentMode: ContentModeName
    rows: PageRow[]
}

export interface NewsComment {
    id: number
    newsId: number
    parentId: number | null
    author: MemberIdentity | null
    authorName: string
    content: string
    deleted?: boolean
    createdAt: string
    updatedAt?: string | null
}

export interface CommentRequest {
    parentId?: number | null
    content: string
}

const news = createCrudResource<NewsEntry, NewsRequest>('/news')

const newsComments = createScopedCrudResource<NewsComment, CommentRequest>(
    (newsId: number) => `/news/${newsId}/comments`,
)

const comments = createCrudResource<NewsComment, CommentRequest>('/news/comments')

// -- Page-editor picker. PAGE_EDIT-gated. --

export interface NewsSearchResult {
    publicUid: string
    title: string
    summary: string
    publishedAt: string | null
}

export async function searchNews(query?: string, limit = 5): Promise<NewsSearchResult[]> {
    const params: Record<string, string | number> = {limit}
    if (query) params.q = query
    const res = await client.get<NewsSearchResult[]>('/news/search', {params})
    return res.data
}

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
    return news.list(pageParams({offset, limit}))
}

export const getNews = news.get
export const createNews = news.create
export const updateNews = news.update
export const deleteNews = news.remove

export const listComments = newsComments.list
export const createComment = newsComments.create
export const updateComment = comments.update
export const deleteComment = comments.remove

// --- View tracking ---

export interface NewsViewerEntry {
    member: import('./types').MemberIdentity
    seenAt: string | null
}

export interface NewsViewsResponse {
    seen: NewsViewerEntry[]
    unseen: NewsViewerEntry[]
}

// -- Blocks --

/**
 * Turns a plain entry into one built from blocks. What the author already wrote becomes a single
 * markdown block, which they then split up as they like.
 */
export async function enableNewsBlocks(newsId: number): Promise<NewsEntry> {
    const res = await client.post<NewsEntry>(`/news/${newsId}/blocks/enable`)
    return res.data
}

/**
 * Saves the blocks of a rich entry. The stored text is rewritten from them on every save, which is
 * what keeps the search summary, the feed and the federation payload current.
 */
export async function saveNewsBlocks(newsId: number, rows: SaveRowRequest[]): Promise<NewsEntry> {
    const res = await client.put<NewsEntry>(`/news/${newsId}/blocks`, {rows})
    return res.data
}

// -- Attachments --

export async function attachNewsFile(newsId: number, fileId: number, label?: string | null): Promise<NewsAttachment> {
    const res = await client.post<NewsAttachment>(`/news/${newsId}/attachments`, {fileId, label: label ?? null})
    return res.data
}

export async function relabelNewsAttachment(attachmentId: number, label: string | null): Promise<void> {
    await client.post(`/news/attachments/${attachmentId}/label`, {label})
}

export async function reorderNewsAttachments(newsId: number, attachmentIds: number[]): Promise<void> {
    await client.put(`/news/${newsId}/attachments/order`, {attachmentIds})
}

export async function detachNewsAttachment(attachmentId: number): Promise<void> {
    await client.delete(`/news/attachments/${attachmentId}`)
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
    const res = await client.get<PublicBlogEntry[]>(`/public/station/${stationUid}/blog`, {
        params: pageParams({offset, limit}),
    })
    return res.data
}

export async function getPublicBlogEntry(stationUid: string, blogId: number): Promise<PublicBlogEntry> {
    const res = await client.get<PublicBlogEntry>(`/public/station/${stationUid}/blog/${blogId}`)
    return res.data
}

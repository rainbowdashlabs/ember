/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource, createScopedCrudResource, pageParams } from './crud'
import type { MemberIdentity } from './types'
import type { BlockAudience } from './pageManage'
import type {
    BlockRowRequest,
    FederatedNewsData,
    FederatedNewsItem,
    NewsAttachment,
    NewsFederationShareResponse,
    NewsRequest,
    NewsResponse,
    NewsSearchPage,
    NewsTeaser,
    NewsViewCountResponse,
    NewsViewsResponse,
    PublicBlogEntry,
    components,
} from '@/api/generated/schema'

type Schemas = components['schemas']

export type ContentModeName = Schemas['ContentMode']

/**
 * How an entry was written. A plain entry is one markdown field, which is the right tool for a
 * short notice; a rich entry is built from blocks with the page editor. The switch only goes one
 * way, because the stored text of a rich entry is derived from its blocks.
 */
export const ContentMode = {
    SIMPLE: 'SIMPLE',
    RICH: 'RICH',
} as const satisfies Record<ContentModeName, ContentModeName>

export type NewsVisibilityRoleName = Schemas['NewsVisibilityRole']

/** Who at a partner station may read a shared entry. */
export const NewsVisibilityRole = {
    MEMBER: 'MEMBER',
    TEAM: 'TEAM',
    MANAGER: 'MANAGER',
} as const satisfies Record<NewsVisibilityRoleName, NewsVisibilityRoleName>

/** The role a select hands back, read as one the server knows; anything else is every member. */
export function visibilityRoleOf(value: string): NewsVisibilityRoleName {
    return Object.values(NewsVisibilityRole).find(role => role === value) ?? NewsVisibilityRole.MEMBER
}
/**
 * A comment under a news entry.
 *
 * TODO: take the generated comment record once the comment system is reworked to say which fields it fills.
 */
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

/** What is sent when a comment is written or corrected. */
export interface CommentRequest {
    parentId?: number | null
    content: string
}

const news = createCrudResource<NewsResponse, NewsRequest>('/news')

const newsComments = createScopedCrudResource<NewsComment, CommentRequest>(
    (newsId: number) => `/news/${newsId}/comments`,
)

const comments = createCrudResource<NewsComment, CommentRequest>('/news/comments')

/**
 * Searches the station's news a block may name for its readers by title, newest first: on a page
 * (`PUBLIC`) the entries on the public blog, in a news or wiki article (`MEMBERS`) every entry every
 * member may read.
 */
export async function searchNews(query: string, limit: number, scope: BlockAudience): Promise<NewsSearchPage> {
    const params: Record<string, string | number> = {limit, scope}
    if (query) params.q = query
    const res = await client.get<NewsSearchPage>('/news/search', {params})
    return res.data
}

/**
 * The entry a news block on a public page names, read the same way during a server render and in
 * the browser. It answers only an entry on the station's public blog; anything else rejects with a
 * 404.
 *
 * @param apiBase the API root from `apiUrl('')`, resolved by the caller while it still has the Nuxt
 *                instance
 */
export function getPublicNewsTeaser(apiBase: string, stationUid: string, newsUid: string): Promise<NewsTeaser> {
    return $fetch<NewsTeaser>(
        `${apiBase}/public/station/${encodeURIComponent(stationUid)}/news-teaser/${encodeURIComponent(newsUid)}`,
    )
}

/**
 * The entry a news block in a news or wiki article names, for a member of the station that owns it.
 * It answers every entry every member may read, internal ones included; anything else rejects with a
 * 404.
 */
export async function getMemberNewsTeaser(newsUid: string): Promise<NewsTeaser> {
    const res = await client.get<NewsTeaser>(`/news/embed/${encodeURIComponent(newsUid)}`)
    return res.data
}

export async function getFederationShare(newsId: number): Promise<NewsFederationShareResponse> {
    const res = await client.get<NewsFederationShareResponse>(`/news/${newsId}/federation`)
    return res.data
}

export async function setFederationShare(
    newsId: number,
    scope: Schemas['ShareScope'],
    visibilityRole: NewsVisibilityRoleName,
    partnerIds?: number[],
): Promise<void> {
    await client.put(`/news/${newsId}/federation`, { scope, visibilityRole, partnerIds: partnerIds ?? [] })
}

export async function removeFederationShare(newsId: number): Promise<void> {
    await client.delete(`/news/${newsId}/federation`)
}

/** A partner's news entry as the station's news list shows it next to its own. */
export interface FederatedNewsListing {
    id: number
    title: string
    contentHtml: string
    authorName: string
    publishedAt: string
    commentCount: number
    visibilityRole: NewsVisibilityRoleName
    stationName: string
    stationId: string
}

export async function listFederatedNews(): Promise<FederatedNewsListing[]> {
    const res = await client.get<FederatedNewsItem[]>('/federated/news')
    return res.data.map(item => ({
        id: item.news.id,
        title: item.news.title,
        contentHtml: item.news.contentHtml,
        authorName: item.news.authorName,
        publishedAt: item.news.publishedAt,
        commentCount: item.news.commentCount,
        visibilityRole: item.news.visibilityRole,
        stationName: item.partnerStationName,
        stationId: item.partnerStationUid,
    }))
}

export async function getFederatedNews(stationUid: string, newsId: number): Promise<FederatedNewsData> {
    const res = await client.get<FederatedNewsData>(`/federated/${stationUid}/news/${newsId}`)
    return res.data
}

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

export async function listNews(offset = 0, limit = 20): Promise<NewsResponse[]> {
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

/**
 * Turns a plain entry into one built from blocks. What the author already wrote becomes a single
 * markdown block, which they then split up as they like.
 */
export async function enableNewsBlocks(newsId: number): Promise<NewsResponse> {
    const res = await client.post<NewsResponse>(`/news/${newsId}/blocks/enable`)
    return res.data
}

/**
 * Saves the blocks of a rich entry. The stored text is rewritten from them on every save, which is
 * what keeps the search summary, the feed and the federation payload current.
 */
export async function saveNewsBlocks(newsId: number, rows: BlockRowRequest[]): Promise<NewsResponse> {
    const res = await client.put<NewsResponse>(`/news/${newsId}/blocks`, {rows})
    return res.data
}

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
    const res = await client.get<NewsViewCountResponse>(`/news/${newsId}/view-count`)
    return res.data.count
}

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

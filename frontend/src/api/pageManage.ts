/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import type {
    CellConfig,
    CellConfigByType,
    CreatePageRequest,
    PageShareLinkResponse,
    PagesListResponse,
    PickerPage,
    ResolvedMember,
    SavePageRequest,
    StationPage,
    CellContentType,
    MemberListSortBy,
    PageVisibility,
} from '@/api/generated/schema'

export const LAYOUT_KINDS = [
    'CALLOUT', 'QUOTE', 'DIVIDER', 'SPACER', 'ACCORDION', 'PDF', 'FILE_DOWNLOAD',
    'COUNTDOWN', 'FEATURED_EVENT', 'UPCOMING_EVENTS', 'KB_ARTICLE', 'NEWS_TEASER', 'PAGE_LINK',
    'MAP', 'ADDRESS_CARD', 'PARTNER_STATIONS', 'MEMBER_SPOTLIGHT',
    'MEMBER_LIST_SPOTLIGHT', 'STATS_COUNTER', 'IMAGE_GALLERY',
    'HERO_BANNER', 'PAST_EVENT_RECAP', 'TABS', 'ACHIEVEMENTS', 'EXTERNAL_LINK_CARD',
    'BLOG_SIGNUP', 'AUDIO_EMBED', 'POLL_EMBED', 'QUIZ_TEASER', 'FORMS_CTA', 'CODE_BLOCK', 'SIGNATURE',
    'FILL_IN',
] as const satisfies readonly CellContentType[]
export type LayoutKindName = (typeof LAYOUT_KINDS)[number]
export function isLayoutKind(t: string): t is LayoutKindName {
    return (LAYOUT_KINDS as readonly string[]).includes(t)
}

/**
 * The settings of a cell as the record its content type names.
 *
 * <p>The server binds a cell's settings by the content type standing next to them, so every cell it
 * sends pairs the two; the generated union of every settings record cannot say which one a cell
 * holds. This is the one place that reads the pairing, and it answers null for a cell of another
 * kind rather than pretending.
 */
export function configOf<K extends CellContentType>(
    cell: {contentType: CellContentType; config: CellConfig},
    kind: K,
): CellConfigByType[K] | null {
    return cell.contentType === kind ? (cell.config as CellConfigByType[K]) : null
}

/**
 * Where a member list takes its members from. The server keeps this part of the settings as written,
 * so its shape is the editor's to define.
 */
export type MemberListSource =
    | { kind: 'group';  groupId?: number | null }
    | { kind: 'tag';    tagId?: number | null }
    | { kind: 'manual'; memberUids?: string[] }

/** Whether settings read back from the server hold a member list source the editor wrote. */
export function isMemberListSource(value: unknown): value is MemberListSource {
    if (typeof value !== 'object' || value === null) return false
    const kind = (value as {kind?: unknown}).kind
    return kind === 'group' || kind === 'tag' || kind === 'manual'
}

const pages = createCrudResource<
    StationPage,
    CreatePageRequest,
    SavePageRequest
>('/pages')

export async function listPages(): Promise<PagesListResponse> {
    const res = await client.get<PagesListResponse>('/pages')
    return res.data
}

export async function searchPages(query?: string, limit = 5): Promise<PickerPage[]> {
    const params: Record<string, string | number> = {limit}
    if (query) params.q = query
    const res = await client.get<PickerPage[]>('/pages/search', {params})
    return res.data
}

export async function resolveMemberListSource(
    source: MemberListSource,
    sortBy?: MemberListSortBy | null,
    memberDescriptions?: Record<string, string> | null,
    memberOrder?: string[] | null,
): Promise<ResolvedMember[]> {
    const res = await client.post<ResolvedMember[]>('/pages/member-list/resolve', {
        source,
        sortBy: sortBy ?? null,
        memberDescriptions: memberDescriptions ?? null,
        memberOrder: memberOrder ?? null,
    })
    return res.data
}

export async function createPage(title: string, parentId?: number | null): Promise<StationPage> {
    return pages.create({title, parentId})
}

export const getPage = pages.get
export const savePage = pages.update
export const deletePage = pages.remove

export async function duplicatePage(id: number): Promise<StationPage> {
    const res = await client.post<StationPage>(`/pages/${id}/duplicate`)
    return res.data
}

/**
 * Sets who reaches a page. A page reached by its link alone stands outside the page tree: no parent,
 * no children, and its slug path answers nothing.
 */
export async function setVisibility(id: number, visibility: PageVisibility): Promise<StationPage> {
    const res = await client.put<StationPage>(`/pages/${id}/visibility`, {visibility})
    return res.data
}

/**
 * The link an unlisted page is reached at, kept off the page itself so it never travels to a reader, and
 * whether it opens anything today: a link opens nothing while the station keeps its public pages
 * switched off, which is where a new station starts, so whoever hands one out is told alongside it.
 */
export async function getPageShareLink(id: number): Promise<PageShareLinkResponse> {
    const res = await client.get<PageShareLinkResponse>(`/pages/${id}/share-link`)
    return res.data
}

export async function replacePageShareLink(id: number, currentToken: string | null): Promise<PageShareLinkResponse> {
    const res = await client.post<PageShareLinkResponse>(`/pages/${id}/share-link`, {currentToken})
    return res.data
}

export async function setLandingPage(pageId: number | null): Promise<void> {
    await client.put('/pages/landing', {pageId})
}

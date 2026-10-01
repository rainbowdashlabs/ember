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
    components,
} from '@/api/generated/schema'

type Schemas = components['schemas']

export type CellContentTypeName = Schemas['CellContentType']

export const CellContentType = {
    EMPTY: 'EMPTY',
    MARKDOWN: 'MARKDOWN',
    IMAGE: 'IMAGE',
    VIDEO: 'VIDEO',
    CALLOUT: 'CALLOUT',
    QUOTE: 'QUOTE',
    DIVIDER: 'DIVIDER',
    SPACER: 'SPACER',
    ACCORDION: 'ACCORDION',
    PDF: 'PDF',
    FILE_DOWNLOAD: 'FILE_DOWNLOAD',
    COUNTDOWN: 'COUNTDOWN',
    FEATURED_EVENT: 'FEATURED_EVENT',
    UPCOMING_EVENTS: 'UPCOMING_EVENTS',
    KB_ARTICLE: 'KB_ARTICLE',
    NEWS_TEASER: 'NEWS_TEASER',
    PAGE_LINK: 'PAGE_LINK',
    MAP: 'MAP',
    ADDRESS_CARD: 'ADDRESS_CARD',
    PARTNER_STATIONS: 'PARTNER_STATIONS',
    MEMBER_SPOTLIGHT: 'MEMBER_SPOTLIGHT',
    MEMBER_LIST_SPOTLIGHT: 'MEMBER_LIST_SPOTLIGHT',
    STATS_COUNTER: 'STATS_COUNTER',
    IMAGE_GALLERY: 'IMAGE_GALLERY',
    HERO_BANNER: 'HERO_BANNER',
    PAST_EVENT_RECAP: 'PAST_EVENT_RECAP',
    TABS: 'TABS',
    ACHIEVEMENTS: 'ACHIEVEMENTS',
    EXTERNAL_LINK_CARD: 'EXTERNAL_LINK_CARD',
    BLOG_SIGNUP: 'BLOG_SIGNUP',
    AUDIO_EMBED: 'AUDIO_EMBED',
    POLL_EMBED: 'POLL_EMBED',
    QUIZ_TEASER: 'QUIZ_TEASER',
    FORMS_CTA: 'FORMS_CTA',
    CODE_BLOCK: 'CODE_BLOCK',
    NESTED_ROWS: 'NESTED_ROWS',
} as const satisfies Record<CellContentTypeName, CellContentTypeName>

export const LAYOUT_KINDS = [
    'CALLOUT', 'QUOTE', 'DIVIDER', 'SPACER', 'ACCORDION', 'PDF', 'FILE_DOWNLOAD',
    'COUNTDOWN', 'FEATURED_EVENT', 'UPCOMING_EVENTS', 'KB_ARTICLE', 'NEWS_TEASER', 'PAGE_LINK',
    'MAP', 'ADDRESS_CARD', 'PARTNER_STATIONS', 'MEMBER_SPOTLIGHT',
    'MEMBER_LIST_SPOTLIGHT', 'STATS_COUNTER', 'IMAGE_GALLERY',
    'HERO_BANNER', 'PAST_EVENT_RECAP', 'TABS', 'ACHIEVEMENTS', 'EXTERNAL_LINK_CARD',
    'BLOG_SIGNUP', 'AUDIO_EMBED', 'POLL_EMBED', 'QUIZ_TEASER', 'FORMS_CTA', 'CODE_BLOCK',
] as const satisfies readonly CellContentTypeName[]
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
export function configOf<K extends CellContentTypeName>(
    cell: {contentType: CellContentTypeName; config: CellConfig},
    kind: K,
): CellConfigByType[K] | null {
    return cell.contentType === kind ? (cell.config as CellConfigByType[K]) : null
}

export type CalloutVariantName = Schemas['CalloutVariant']

export const CalloutVariant = {
    INFO: 'INFO',
    WARNING: 'WARNING',
    SUCCESS: 'SUCCESS',
    TIP: 'TIP',
} as const satisfies Record<CalloutVariantName, CalloutVariantName>

export type ImageFitName = Schemas['ImageFit']

export const ImageFit = {
    COVER: 'COVER',
    CONTAIN: 'CONTAIN',
    FILL: 'FILL',
} as const satisfies Record<ImageFitName, ImageFitName>

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

export type MemberListSortByName = Schemas['MemberListSortBy']

export const MemberListSortBy = {
    ORDER: 'ORDER',
    NAME: 'NAME',
    ROLE: 'ROLE',
    JOIN_DATE: 'JOIN_DATE',
} as const satisfies Record<MemberListSortByName, MemberListSortByName>

export type GalleryAspectModeName = Schemas['GalleryAspectMode']

export const GalleryAspectMode = {
    SQUARE: 'SQUARE',
    PRESERVE: 'PRESERVE',
} as const satisfies Record<GalleryAspectModeName, GalleryAspectModeName>

export type ExternalLinkImageDisplayName = Schemas['ExternalLinkImageDisplay']

export const ExternalLinkImageDisplay = {
    BANNER: 'BANNER',
    ICON: 'ICON',
} as const satisfies Record<ExternalLinkImageDisplayName, ExternalLinkImageDisplayName>

export type PageVisibilityName = Schemas['PageVisibility']

/**
 * Who reaches a page. A page reached by its link alone stands outside the page tree: no parent, no
 * children, and its slug path answers nothing.
 */
export const PageVisibility = {
    DRAFT: 'DRAFT',
    UNLISTED: 'UNLISTED',
    PUBLIC: 'PUBLIC',
} as const satisfies Record<PageVisibilityName, PageVisibilityName>

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
    sortBy?: MemberListSortByName | null,
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
    return pages.create({title, parentId: parentId ?? undefined})
}

export const getPage = pages.get
export const savePage = pages.update
export const deletePage = pages.remove

export async function duplicatePage(id: number): Promise<StationPage> {
    const res = await client.post<StationPage>(`/pages/${id}/duplicate`)
    return res.data
}

export async function setVisibility(id: number, visibility: PageVisibilityName): Promise<StationPage> {
    const res = await client.put<StationPage>(`/pages/${id}/visibility`, {visibility})
    return res.data
}

/** The link an unlisted page is reached at, kept off the page itself so it never travels to a reader. */
export async function getPageShareLink(id: number): Promise<string | null> {
    const res = await client.get<PageShareLinkResponse>(`/pages/${id}/share-link`)
    return res.data.token
}

export async function replacePageShareLink(id: number, currentToken: string | null): Promise<string | null> {
    const res = await client.post<PageShareLinkResponse>(`/pages/${id}/share-link`, {currentToken})
    return res.data.token
}

export async function setLandingPage(pageId: number | null): Promise<void> {
    await client.put('/pages/landing', {pageId})
}

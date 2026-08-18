/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import {uploadFile} from './upload'

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
} as const
export type CellContentTypeName = (typeof CellContentType)[keyof typeof CellContentType]

export const LAYOUT_KINDS = [
    'CALLOUT', 'QUOTE', 'DIVIDER', 'SPACER', 'ACCORDION', 'PDF', 'FILE_DOWNLOAD',
    'COUNTDOWN', 'FEATURED_EVENT', 'UPCOMING_EVENTS', 'KB_ARTICLE', 'NEWS_TEASER', 'PAGE_LINK',
    'MAP', 'ADDRESS_CARD', 'PARTNER_STATIONS', 'MEMBER_SPOTLIGHT',
    'MEMBER_LIST_SPOTLIGHT', 'STATS_COUNTER', 'IMAGE_GALLERY',
    'HERO_BANNER', 'PAST_EVENT_RECAP', 'TABS', 'ACHIEVEMENTS', 'EXTERNAL_LINK_CARD',
    'BLOG_SIGNUP', 'AUDIO_EMBED', 'POLL_EMBED', 'QUIZ_TEASER', 'FORMS_CTA', 'CODE_BLOCK',
] as const
export type LayoutKindName = (typeof LAYOUT_KINDS)[number]
export function isLayoutKind(t: string): t is LayoutKindName {
    return (LAYOUT_KINDS as readonly string[]).includes(t)
}

export const CalloutVariant = {
    INFO: 'INFO',
    WARNING: 'WARNING',
    SUCCESS: 'SUCCESS',
    TIP: 'TIP',
} as const
export type CalloutVariantName = (typeof CalloutVariant)[keyof typeof CalloutVariant]

export const ImageFit = {
    COVER: 'COVER',
    CONTAIN: 'CONTAIN',
    FILL: 'FILL',
} as const
export type ImageFitName = (typeof ImageFit)[keyof typeof ImageFit]

export interface MarkdownConfig {}

export interface ImageConfig {
    imageFit?: ImageFitName | null
    altText?: string | null
    maxHeight?: number | null
    description?: string | null
    cropTop?: number | null
    cropRight?: number | null
    cropBottom?: number | null
    cropLeft?: number | null
    borderRadiusPercent?: number | null
    borderWidthPx?: number | null
    borderColor?: string | null
}

export interface VideoConfig {
    autoplay?: boolean | null
    loop?: boolean | null
}

export interface CalloutConfig {
    variant?: CalloutVariantName | null
    title?: string | null
}

export interface QuoteConfig {
    author?: string | null
    attributionUrl?: string | null
}

export interface DividerConfig {
    label?: string | null
}

export interface SpacerConfig {
    heightPx?: number | null
}

export interface AccordionConfig {
    title?: string | null
    openByDefault?: boolean | null
}

export interface PdfConfig {
    url?: string | null
    heightPx?: number | null
}

export interface FileDownloadConfig {
    url?: string | null
    label?: string | null
    description?: string | null
}

export interface CountdownConfig {
    targetDate?: string | null
    label?: string | null
    sublabel?: string | null
}

export interface FeaturedEventConfig {
    /** Public UUID of the referenced event. All other display fields come live. */
    eventUid?: string | null
    /** Optional editor-supplied teaser blurb above the auto-rendered location row. */
    descriptionOverride?: string | null
}

export interface UpcomingEventsConfig {
    title?: string | null
    /** Empty / unset = "all categories". Event categories are referenced by their numeric id -
     *  no public_uid exists for them today. */
    categoryIds?: number[] | null
    /** Max items to render. Default 5, max 20. */
    limit?: number | null
    /** Include federated public events from partner stations. Default true. */
    includeFederated?: boolean | null
}

export interface KbArticleConfig {
    /** Internal KB file id. KB doesn't expose a public_uid yet;
     *  the renderer + picker both speak the integer id directly. */
    articleId?: number | null
    /** Optional editor-supplied fallback shown until the live KB lookup resolves the real name. */
    fallbackTitle?: string | null
}

export interface NewsTeaserConfig {
    /** Public UUID of the referenced news entry. All other fields come live from the entity. */
    newsUid?: string | null
}

export interface PageLinkConfig {
    /** Public UUID of the referenced station page. Live title sourced from the entity. */
    pageUid?: string | null
}

export interface MapConfig {
    latitude?: number | null
    longitude?: number | null
    zoom?: number | null
    heightPx?: number | null
    label?: string | null
}

export interface AddressCardConfig {
    addressLine?: string | null
    postalCode?: string | null
    city?: string | null
    country?: string | null
    mapUrl?: string | null
    label?: string | null
}

export interface PartnerStationsConfig {
    title?: string | null
    /** Explicit list of partner station UUIDs to render. */
    stationUids?: string[] | null
    /** When true, the cell ignores {@link stationUids} and pulls every federated partner. */
    autoFillFromPartners?: boolean | null
}

export interface MemberSpotlightConfig {
    /** UUID of the spotlighted member. All display fields come live. */
    memberUid?: string | null
    /** Editor-supplied free-form blurb shown next to the live member card. */
    blurb?: string | null
    /** When true (default), the renderer shows the member's user type next to the name. */
    showUserType?: boolean | null
    /** When true, the renderer additionally shows the member's primary visible tag as a badge. */
    showTag?: boolean | null
}

export type MemberListSource =
    | { kind: 'group';  groupId?: number | null }
    | { kind: 'tag';    tagId?: number | null }
    | { kind: 'manual'; memberUids?: string[] }

export const MemberListSortBy = {
    ORDER: 'ORDER',
    NAME: 'NAME',
    ROLE: 'ROLE',
    JOIN_DATE: 'JOIN_DATE',
} as const

export type MemberListSortByName = (typeof MemberListSortBy)[keyof typeof MemberListSortBy]

export interface ResolvedMember {
    memberUid: string
    displayName: string
    userType: string | null
    displayTag: string | null
    displayTagColor: string | null
    avatarUrl: string | null
    description: string | null
}

export interface MemberListConfig {
    title?: string | null
    /** Source of the member list - group / tag / manual override. */
    source?: MemberListSource | null
    /** Default {@link MemberListSortBy.ORDER}; falls back to natural source order or memberOrder. */
    sortBy?: MemberListSortByName | null
    /** When true (default), each card shows the member's user type. */
    showUserType?: boolean | null
    /** When true, each card additionally shows the member's primary visible tag as a badge. */
    showTag?: boolean | null
    /** Per-entry description shown under the member name, keyed by member UID. */
    memberDescriptions?: Record<string, string> | null
    /** Persistent display order applied when sortBy === ORDER (overrides natural source order). */
    memberOrder?: string[] | null
    /**
     * Render-time injection populated by the public render path so unauthenticated visitors
     * can display the cell without hitting the auth-gated avatar endpoint. Avatar is inlined
     * as a base64 {@code data:} URL when present. {@code null} on the editor path - that
     * surface calls {@link resolveMemberListSource} live with auth in scope.
     */
    resolvedMembers?: ResolvedMember[] | null
}

export interface StatItem {
    label?: string
    value?: string
    suffix?: string
}

export interface StatsCounterConfig {
    items?: StatItem[] | null
}

export interface GalleryItem {
    imageHash: string
    altText?: string | null
    subtext?: string | null
}

export const GalleryAspectMode = {
    SQUARE: 'SQUARE',
    PRESERVE: 'PRESERVE',
} as const
export type GalleryAspectModeName = (typeof GalleryAspectMode)[keyof typeof GalleryAspectMode]

export interface ImageGalleryConfig {
    items?: GalleryItem[] | null
    columns?: number | null
    aspectMode?: GalleryAspectModeName | null
    maxItemHeightPx?: number | null
}

export interface HeroBannerConfig {
    imageHash?: string | null
    headline?: string | null
    subtitle?: string | null
    ctaText?: string | null
    ctaUrl?: string | null
}

export interface PastEventRecapConfig {
    /** Public UUID of the referenced past event. */
    eventUid?: string | null
    /** Editor-supplied recap text shown alongside the event's own description. */
    recapDescription?: string | null
}

export interface TabItem {
    title?: string
    body?: string
}

export interface TabsConfig {
    items?: TabItem[] | null
}

export interface AchievementItem {
    title?: string
    description?: string
    year?: string
}

export interface AchievementsConfig {
    title?: string | null
    items?: AchievementItem[] | null
}

export const ExternalLinkImageDisplay = {
    BANNER: 'BANNER',
    ICON: 'ICON',
} as const

export type ExternalLinkImageDisplayName = (typeof ExternalLinkImageDisplay)[keyof typeof ExternalLinkImageDisplay]

export interface ExternalLinkCardConfig {
    url?: string | null
    title?: string | null
    description?: string | null
    imageUrl?: string | null
    imageDisplay?: ExternalLinkImageDisplayName | null
}

export interface BlogSignupConfig {
    title?: string | null
    description?: string | null
}

export interface AudioEmbedConfig {
    url?: string | null
    title?: string | null
}

export interface PollEmbedConfig {
    formPublicUid?: string | null
    showResultsAfterVote?: boolean | null
}

export interface QuizTeaserConfig {
    title?: string | null
    description?: string | null
    /** Ids of public quiz catalogs. At least one required to render. */
    catalogIds?: number[] | null
}

export interface FormsCtaConfig {
    formPublicUid?: string | null
    headlineOverride?: string | null
    bodyOverride?: string | null
}

export interface CodeBlockConfig {
    language?: string | null
}

/** Stored as the recursive RowEditData[] (forward declaration; defined where used). */
export interface NestedRowsConfig {
    rows?: unknown[] | null
}

export type CellConfig =
    | MarkdownConfig
    | ImageConfig
    | VideoConfig
    | CalloutConfig
    | QuoteConfig
    | DividerConfig
    | SpacerConfig
    | AccordionConfig
    | PdfConfig
    | FileDownloadConfig

export interface PageCell {
    id: number
    rowId: number
    sortOrder: number
    widthPercent: number
    contentType: CellContentTypeName
    content: string
    config: CellConfig
}

export interface PageRow {
    id: number
    pageId: number
    sortOrder: number
    cells: PageCell[]
}

export interface StationPage {
    id: number
    stationId: number
    parentId: number | null
    title: string
    slug: string
    published: boolean
    sortOrder: number
    metaDescription: string | null
    ogImageId: number | null
    /** Content hash of the social preview image. Page files are served by hash, not by id. */
    ogImageHash: string | null
    createdBy: number
    createdAt: string
    updatedAt: string
    rows: PageRow[]
}

export interface PageImage {
    id: number
    pageId: number | null
    stationId: string
    contentHash: string | null
    fileName: string
    mimeType: string
    fileSize: number
    uploadedAt: string
    defaultAltText?: string | null
    defaultDescription?: string | null
    folderId?: number | null
}

/** Alias for clarity: page-files cover images and (eventually) PDFs, audio, etc. */
export type PageFile = PageImage

/** Listing entry returned by GET /pages/files - file + whether it's referenced anywhere + tag ids. */
export interface PageFileListing {
    file: PageFile
    inUse: boolean
    tagIds: number[]
}

export interface PageFileFolder {
    id: number
    stationId: string
    parentId: number | null
    name: string
    sortOrder: number
    createdAt: string
}

export interface PageFileTag {
    id: number
    stationId: string
    name: string
    color: string | null
}

export interface SaveCellRequest {
    sortOrder: number
    widthPercent: number
    contentType: CellContentTypeName
    content: string
    config: CellConfig
}

export interface SaveRowRequest {
    sortOrder: number
    cells: SaveCellRequest[]
}

export interface SavePageRequest {
    title: string
    slug: string
    parentId: number | null
    metaDescription: string | null
    ogImageId: number | null
    rows: SaveRowRequest[]
}

export interface PagesListResponse {
    pages: StationPage[]
    landingPageId: number | null
}

interface PageCreateRequest {
    title: string
    parentId: number | null
}

interface FolderRequest {
    name: string
    parentId: number | null
    sortOrder: number
}

interface TagRequest {
    name: string
    color: string | null
}

interface FileMetaRequest {
    altText: string | null
    description: string | null
}

const pages = createCrudResource<
    StationPage,
    PageCreateRequest,
    SavePageRequest
>('/pages')

const files = createCrudResource<
    PageFileListing,
    FileMetaRequest,
    FileMetaRequest,
    PageFileListing,
    PageFileListing,
    void
>('/pages/files')

const folders = createCrudResource<
    PageFileFolder,
    FolderRequest,
    FolderRequest,
    PageFileFolder,
    PageFileFolder,
    void
>('/pages/folders')

const tags = createCrudResource<
    PageFileTag,
    TagRequest,
    TagRequest,
    PageFileTag,
    PageFileTag,
    void
>('/pages/tags')

export async function listPages(): Promise<PagesListResponse> {
    const res = await client.get<PagesListResponse>('/pages')
    return res.data
}

// -- Page-editor PAGE_LINK picker. PAGE_EDIT-gated. --

export interface PageSearchResult {
    pageUid: string
    title: string
    slug: string
    updatedAt: string
}

export async function searchPages(query?: string, limit = 5): Promise<PageSearchResult[]> {
    const params: Record<string, string | number> = {limit}
    if (query) params.q = query
    const res = await client.get<PageSearchResult[]>('/pages/search', {params})
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
    return pages.create({title, parentId: parentId ?? null})
}

export const getPage = pages.get
export const savePage = pages.update
export const deletePage = pages.remove

export async function duplicatePage(id: number): Promise<StationPage> {
    const res = await client.post<StationPage>(`/pages/${id}/duplicate`)
    return res.data
}

export async function setPublished(id: number, published: boolean): Promise<StationPage> {
    const res = await client.put<StationPage>(`/pages/${id}/publish`, {published})
    return res.data
}

export async function setLandingPage(pageId: number | null): Promise<void> {
    await client.put('/pages/landing', {pageId})
}

export async function uploadPageFile(pageId: number, file: File): Promise<PageFile> {
    return uploadFile<PageFile>(`/pages/${pageId}/files`, {file})
}

export const uploadPageImage = uploadPageFile

export async function deletePageFile(pageId: number, fileId: number): Promise<void> {
    await client.delete(`/pages/${pageId}/files/${fileId}`)
}

/**
 * Deletes a station-scoped page file directly (no page context). Used by the
 * file browser at /station/pages/files for single and bulk delete.
 */
export const deleteStationPageFile = files.remove

/** Public URL for a page-file, addressed by its content hash. */
export function pageFileUrl(stationUid: string, contentHash: string): string {
    return `/api/v1/public/pages/${stationUid}/files/${contentHash}`
}

/**
 * Public URL for a page-image at a requested CSS-pixel width. The backend picks the
 * smallest pre-generated variant ≥ {@code width} and, when the client's {@code Accept}
 * header advertises WebP, prefers the WebP encoding.
 */
export function pageImageUrlAt(stationUid: string, contentHash: string, width: number): string {
    return `${pageFileUrl(stationUid, contentHash)}?w=${width}`
}

/**
 * Builds a 1x/2x {@code srcset} string for a page image at the supplied 1x CSS width.
 * Renderers should set this on every {@code <img>} so the browser can pick the right
 * resolution on Retina displays.
 */
export function pageImageSrcset(stationUid: string, contentHash: string, width1x: number): string {
    return `${pageImageUrlAt(stationUid, contentHash, width1x)} 1x, ${pageImageUrlAt(stationUid, contentHash, width1x * 2)} 2x`
}

export const pageImageUrl = pageFileUrl

/** Lists every page-file with an `inUse` flag (true when still referenced from some cell). */
export const listStationPageFiles = files.list

export async function updatePageFileMeta(
    fileId: number, altText: string | null, description: string | null): Promise<void> {
    return files.update(fileId, {altText, description})
}

export async function prunePageFiles(): Promise<{removed: number}> {
    const res = await client.post<{removed: number}>('/pages/files/prune')
    return res.data
}

export async function moveFileToFolder(fileId: number, folderId: number | null): Promise<void> {
    await client.put(`/pages/files/${fileId}/folder`, {folderId})
}

export const listPageFolders = folders.list

export async function createPageFolder(
    name: string, parentId: number | null = null, sortOrder = 0): Promise<PageFileFolder> {
    return folders.create({name, parentId, sortOrder})
}

export async function updatePageFolder(
    id: number, name: string, parentId: number | null, sortOrder: number): Promise<void> {
    return folders.update(id, {name, parentId, sortOrder})
}

export const deletePageFolder = folders.remove

export const listPageTags = tags.list

export async function createPageTag(name: string, color: string | null = null): Promise<PageFileTag> {
    return tags.create({name, color})
}

export async function updatePageTag(id: number, name: string, color: string | null): Promise<void> {
    return tags.update(id, {name, color})
}

export const deletePageTag = tags.remove

export async function assignPageTag(fileId: number, tagId: number): Promise<void> {
    await client.post(`/pages/files/${fileId}/tags/${tagId}`)
}

export async function unassignPageTag(fileId: number, tagId: number): Promise<void> {
    await client.delete(`/pages/files/${fileId}/tags/${tagId}`)
}

/** Uploads a file scoped to the current station (no specific owning page). */
export async function uploadStationPageFile(file: File): Promise<PageFile> {
    return uploadFile<PageFile>('/pages/files', {file})
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

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
    FEDERATED_EVENT: 'FEDERATED_EVENT',
    MEMBER_SPOTLIGHT: 'MEMBER_SPOTLIGHT',
    OFFICERS_ROW: 'OFFICERS_ROW',
    STATS_COUNTER: 'STATS_COUNTER',
    IMAGE_GALLERY: 'IMAGE_GALLERY',
    HERO_BANNER: 'HERO_BANNER',
    PAST_EVENT_RECAP: 'PAST_EVENT_RECAP',
    TABS: 'TABS',
    ACHIEVEMENTS: 'ACHIEVEMENTS',
    EXTERNAL_LINK_CARD: 'EXTERNAL_LINK_CARD',
    NEWSLETTER_SIGNUP: 'NEWSLETTER_SIGNUP',
    AUDIO_EMBED: 'AUDIO_EMBED',
    POLL_EMBED: 'POLL_EMBED',
    QUIZ_TEASER: 'QUIZ_TEASER',
    APPLICATION_CTA: 'APPLICATION_CTA',
    CODE_BLOCK: 'CODE_BLOCK',
    NESTED_ROWS: 'NESTED_ROWS',
} as const
export type CellContentTypeName = (typeof CellContentType)[keyof typeof CellContentType]

export const LAYOUT_KINDS = [
    'CALLOUT', 'QUOTE', 'DIVIDER', 'SPACER', 'ACCORDION', 'PDF', 'FILE_DOWNLOAD',
    'COUNTDOWN', 'FEATURED_EVENT', 'UPCOMING_EVENTS', 'KB_ARTICLE', 'NEWS_TEASER', 'PAGE_LINK',
    'MAP', 'ADDRESS_CARD', 'PARTNER_STATIONS', 'FEDERATED_EVENT', 'MEMBER_SPOTLIGHT',
    'OFFICERS_ROW', 'STATS_COUNTER', 'IMAGE_GALLERY',
    'HERO_BANNER', 'PAST_EVENT_RECAP', 'TABS', 'ACHIEVEMENTS', 'EXTERNAL_LINK_CARD',
    'NEWSLETTER_SIGNUP', 'AUDIO_EMBED', 'POLL_EMBED', 'QUIZ_TEASER', 'APPLICATION_CTA', 'CODE_BLOCK',
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
    borderRadiusPx?: number | null
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
    title?: string | null
    date?: string | null
    location?: string | null
    description?: string | null
    ctaText?: string | null
    ctaUrl?: string | null
}

export interface EventItem {
    title?: string
    date?: string
    location?: string
    url?: string
}

export interface UpcomingEventsConfig {
    title?: string | null
    items?: EventItem[] | null
}

export interface KbArticleConfig {
    articleId?: number | null
    fallbackTitle?: string | null
}

export interface NewsTeaserConfig {
    title?: string | null
    date?: string | null
    summary?: string | null
    url?: string | null
    imageUrl?: string | null
}

export interface PageLinkConfig {
    pageId?: number | null
    fallbackTitle?: string | null
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

export interface PartnerStationItem {
    name?: string
    url?: string
    distanceKm?: number
}

export interface PartnerStationsConfig {
    title?: string | null
    items?: PartnerStationItem[] | null
}

export interface FederatedEventConfig {
    title?: string | null
    date?: string | null
    partnerName?: string | null
    url?: string | null
    description?: string | null
}

export interface MemberSpotlightConfig {
    name?: string | null
    role?: string | null
    imageUrl?: string | null
    blurb?: string | null
}

export interface OfficerItem {
    name?: string
    role?: string
    imageUrl?: string
}

export interface OfficersRowConfig {
    title?: string | null
    items?: OfficerItem[] | null
}

export interface StatItem {
    label?: string
    value?: string
    suffix?: string
}

export interface StatsCounterConfig {
    items?: StatItem[] | null
}

export interface ImageGalleryConfig {
    imageIds?: number[] | null
    columns?: number | null
}

export interface HeroBannerConfig {
    imageId?: number | null
    headline?: string | null
    subtitle?: string | null
    ctaText?: string | null
    ctaUrl?: string | null
}

export interface PastEventRecapConfig {
    title?: string | null
    date?: string | null
    imageId?: number | null
    summary?: string | null
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

export interface ExternalLinkCardConfig {
    url?: string | null
    title?: string | null
    description?: string | null
    imageUrl?: string | null
}

export interface NewsletterSignupConfig {
    title?: string | null
    description?: string | null
    feedUrl?: string | null
}

export interface AudioEmbedConfig {
    url?: string | null
    title?: string | null
}

export interface PollEmbedConfig {
    title?: string | null
    description?: string | null
    url?: string | null
}

export interface QuizTeaserConfig {
    title?: string | null
    description?: string | null
    ctaText?: string | null
    url?: string | null
}

export interface ApplicationCtaConfig {
    headline?: string | null
    body?: string | null
    ctaText?: string | null
    ctaUrl?: string | null
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
}

/** Alias for clarity: page-files cover images and (eventually) PDFs, audio, etc. */
export type PageFile = PageImage

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

export async function listPages(): Promise<PagesListResponse> {
    const res = await client.get<PagesListResponse>('/pages')
    return res.data
}

export async function createPage(title: string, parentId?: number | null): Promise<StationPage> {
    const res = await client.post<StationPage>('/pages', {title, parentId: parentId ?? null})
    return res.data
}

export async function getPage(id: number): Promise<StationPage> {
    const res = await client.get<StationPage>(`/pages/${id}`)
    return res.data
}

export async function savePage(id: number, data: SavePageRequest): Promise<StationPage> {
    const res = await client.put<StationPage>(`/pages/${id}`, data)
    return res.data
}

export async function duplicatePage(id: number): Promise<StationPage> {
    const res = await client.post<StationPage>(`/pages/${id}/duplicate`)
    return res.data
}

export async function deletePage(id: number): Promise<void> {
    await client.delete(`/pages/${id}`)
}

export async function setPublished(id: number, published: boolean): Promise<StationPage> {
    const res = await client.put<StationPage>(`/pages/${id}/publish`, {published})
    return res.data
}

export async function setLandingPage(pageId: number | null): Promise<void> {
    await client.put('/pages/landing', {pageId})
}

export async function uploadPageImage(pageId: number, file: File): Promise<PageImage> {
    const formData = new FormData()
    formData.append('file', file)
    const res = await client.post<PageImage>(`/pages/${pageId}/images`, formData, {
        headers: {'Content-Type': 'multipart/form-data'},
    })
    return res.data
}

export async function deletePageImage(pageId: number, imageId: number): Promise<void> {
    await client.delete(`/pages/${pageId}/images/${imageId}`)
}

export function pageImageUrl(stationUid: string, imageId: number): string {
    return `/api/v1/public/pages/${stationUid}/images/${imageId}`
}

/** Lists every page-file (image / etc.) that belongs to the caller's station. */
export async function listStationPageFiles(): Promise<PageFile[]> {
    const res = await client.get<PageFile[]>('/pages/files')
    return res.data
}

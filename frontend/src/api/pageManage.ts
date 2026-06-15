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
} as const
export type CellContentTypeName = (typeof CellContentType)[keyof typeof CellContentType]

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
    pageId: number
    fileName: string
    mimeType: string
    fileSize: number
    uploadedAt: string
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

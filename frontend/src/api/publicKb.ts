/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import axios from 'axios'
import type {KbFile, KbFolder, KbTag, MarkdownHtmlResponse} from './knowledgeBase'

const publicClient = axios.create({
    baseURL: '/api/v1/public/kb',
    headers: {'Content-Type': 'application/json'},
})

export interface PublicStationInfo {
    stationName: string
    stationUid: string
}

export interface PublicBrowseResponse {
    currentFolder: KbFolder | null
    folders: KbFolder[]
    files: KbFile[]
}

export interface PublicSearchResult {
    file: KbFile
    snippet: string
}

export async function getStationInfo(stationUid: string): Promise<PublicStationInfo> {
    const res = await publicClient.get<PublicStationInfo>(`/${stationUid}/info`)
    return res.data
}

export async function browse(stationUid: string, folderId?: number | null): Promise<PublicBrowseResponse> {
    const params = folderId != null ? {folderId} : {}
    const res = await publicClient.get<PublicBrowseResponse>(`/${stationUid}/browse`, {params})
    return res.data
}

export async function getFile(stationUid: string, fileId: number): Promise<KbFile> {
    const res = await publicClient.get<KbFile>(`/${stationUid}/files/${fileId}`)
    return res.data
}

export async function getMarkdownHtml(stationUid: string, fileId: number): Promise<MarkdownHtmlResponse> {
    const res = await publicClient.get<MarkdownHtmlResponse>(`/${stationUid}/files/${fileId}/html`)
    return res.data
}

export function fileContentUrl(stationUid: string, fileId: number): string {
    return `/api/v1/public/kb/${stationUid}/files/${fileId}/content`
}

/**
 * Returns the public URL of the PDF rendering of a page. Public pages need no authentication, so
 * this is a plain link rather than an authenticated download.
 */
export function pdfExportUrl(stationUid: string, fileId: number): string {
    return `/api/v1/public/kb/${stationUid}/files/${fileId}/pdf`
}

export async function search(stationUid: string, query: string): Promise<PublicSearchResult[]> {
    const res = await publicClient.get<PublicSearchResult[]>(`/${stationUid}/search`, {params: {q: query}})
    return res.data
}

export function folderIconUrl(stationUid: string, folderId: number, size = 128): string {
    return `/api/v1/public/kb/${stationUid}/folders/${folderId}/icon?size=${size}`
}

export function kbImageUrl(stationUid: string, imageId: string, size = 1024): string {
    return `/api/v1/public/kb/${stationUid}/images/${imageId}?size=${size}`
}

export async function listTags(stationUid: string): Promise<KbTag[]> {
    const res = await publicClient.get<KbTag[]>(`/${stationUid}/tags`)
    return res.data
}

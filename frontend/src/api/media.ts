/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource} from './crud'
import {uploadFile} from './upload'

/**
 * A file in the station media library. Files are deduplicated per station by content hash, so
 * uploading the same picture twice gives back the file that is already there.
 */
export interface StationFile {
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

/**
 * A file as the browser shows it. `inUse` is false when nothing in the station points at it,
 * `uploadedBy` names the member who first brought it in (null for files that predate uploader
 * tracking).
 */
export interface StationFileListing {
    file: StationFile
    inUse: boolean
    tagIds: number[]
    uploadedBy: number | null
}

export interface StationFileFolder {
    id: number
    stationId: string
    parentId: number | null
    name: string
    sortOrder: number
    createdAt: string
}

export interface StationFileTag {
    id: number
    stationId: string
    name: string
    color: string | null
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

const files = createCrudResource<
    StationFileListing,
    FileMetaRequest,
    FileMetaRequest,
    StationFileListing,
    StationFileListing,
    void
>('/media/files')

const folders = createCrudResource<
    StationFileFolder,
    FolderRequest,
    FolderRequest,
    StationFileFolder,
    StationFileFolder,
    void
>('/media/folders')

const tags = createCrudResource<
    StationFileTag,
    TagRequest,
    TagRequest,
    StationFileTag,
    StationFileTag,
    void
>('/media/tags')

/**
 * Lists the library. Members who author content get everything the station has; everyone else
 * gets only what they uploaded themselves. The backend decides which set comes back.
 */
export const listMediaFiles = files.list

/**
 * Removes a file. A manager takes it away outright; anyone else only withdraws their own upload,
 * which takes the file with it once nobody claims it and nothing points at it.
 */
export const removeMediaFile = files.remove

/** Uploads a file into the station library. Any member may do this. */
export async function uploadMediaFile(file: File): Promise<StationFile> {
    return uploadFile<StationFile>('/media/files', {file})
}

/** Uploads a file from the page editor, recording the page it first came from. */
export async function uploadPageMediaFile(pageId: number, file: File): Promise<StationFile> {
    return uploadFile<StationFile>(`/pages/${pageId}/files`, {file})
}

export async function updateMediaFileMeta(
    fileId: number, altText: string | null, description: string | null): Promise<void> {
    return files.update(fileId, {altText, description})
}

export async function pruneMediaFiles(): Promise<{removed: number}> {
    const res = await client.post<{removed: number}>('/media/files/prune')
    return res.data
}

export async function moveMediaFileToFolder(fileId: number, folderId: number | null): Promise<void> {
    await client.put(`/media/files/${fileId}/folder`, {folderId})
}

export const listMediaFolders = folders.list

export async function createMediaFolder(
    name: string, parentId: number | null = null, sortOrder = 0): Promise<StationFileFolder> {
    return folders.create({name, parentId, sortOrder})
}

export async function updateMediaFolder(
    id: number, name: string, parentId: number | null, sortOrder: number): Promise<void> {
    return folders.update(id, {name, parentId, sortOrder})
}

export const deleteMediaFolder = folders.remove

export const listMediaTags = tags.list

export async function createMediaTag(name: string, color: string | null = null): Promise<StationFileTag> {
    return tags.create({name, color})
}

export async function updateMediaTag(id: number, name: string, color: string | null): Promise<void> {
    return tags.update(id, {name, color})
}

export const deleteMediaTag = tags.remove

export async function assignMediaTag(fileId: number, tagId: number): Promise<void> {
    await client.post(`/media/files/${fileId}/tags/${tagId}`)
}

export async function unassignMediaTag(fileId: number, tagId: number): Promise<void> {
    await client.delete(`/media/files/${fileId}/tags/${tagId}`)
}

/** Public URL for a media file, addressed by the hash of its bytes. */
export function mediaFileUrl(stationUid: string, contentHash: string): string {
    return `/api/v1/public/media/${stationUid}/${contentHash}`
}

/**
 * Public URL for a media image at a requested CSS-pixel width. The backend picks the smallest
 * pre-generated variant at or above `width` and, when the client's `Accept` header advertises
 * WebP, prefers the WebP encoding.
 */
export function mediaImageUrlAt(stationUid: string, contentHash: string, width: number): string {
    return `${mediaFileUrl(stationUid, contentHash)}?w=${width}`
}

/**
 * Builds a 1x/2x `srcset` for a media image at the supplied 1x CSS width. Renderers should set
 * this on every `<img>` so the browser can pick the right resolution on Retina displays.
 */
export function mediaImageSrcset(stationUid: string, contentHash: string, width1x: number): string {
    return `${mediaImageUrlAt(stationUid, contentHash, width1x)} 1x, ${mediaImageUrlAt(stationUid, contentHash, width1x * 2)} 2x`
}

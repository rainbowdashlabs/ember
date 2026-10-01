/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, type NoContent} from './crud'
import {uploadFile} from './upload'
import type {
    FileListing,
    MediaFileMetaRequest,
    MediaFolderRequest,
    MediaPruneResult,
    MediaTagRequest,
    StationFile,
    StationFileFolder,
    StationFileTag,
} from '@/api/generated/schema'

const files = createCrudResource<
    FileListing,
    MediaFileMetaRequest,
    MediaFileMetaRequest,
    FileListing,
    FileListing,
    NoContent
>('/media/files')

const folders = createCrudResource<
    StationFileFolder,
    MediaFolderRequest,
    MediaFolderRequest,
    StationFileFolder,
    StationFileFolder,
    NoContent
>('/media/folders')

const tags = createCrudResource<
    StationFileTag,
    MediaTagRequest,
    MediaTagRequest,
    StationFileTag,
    StationFileTag,
    NoContent
>('/media/tags')

/**
 * Lists the library. Members who author content get everything the station has; everyone else
 * gets only what they uploaded themselves. The backend decides which set comes back.
 */
export const listMediaFiles = files.list

/**
 * Lists the library the instance holds: files with no station, which every station can be served
 * and which a system notice draws on. Only an instance administrator may read it.
 */
export async function listInstanceMediaFiles(): Promise<FileListing[]> {
    const res = await client.get<FileListing[]>('/admin/media/files')
    return res.data
}

/** Takes a file into the library the instance holds. */
export async function uploadInstanceMediaFile(file: File): Promise<StationFile> {
    return uploadFile<StationFile>('/admin/media/files', {file})
}

/**
 * What stands in for a station identifier when a file belongs to the instance. The delivery route
 * takes it literally, so a picture from the instance library is addressed the same way a station's
 * is and nothing downstream needs to know the difference.
 */
export const INSTANCE_MEDIA_SCOPE = 'instance'

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
    await files.update(fileId, {altText: altText ?? undefined, description: description ?? undefined})
}

export async function pruneMediaFiles(): Promise<MediaPruneResult> {
    const res = await client.post<MediaPruneResult>('/media/files/prune')
    return res.data
}

export async function moveMediaFileToFolder(fileId: number, folderId: number | null): Promise<void> {
    await client.put(`/media/files/${fileId}/folder`, {folderId})
}

export const listMediaFolders = folders.list

export async function createMediaFolder(
    name: string, parentId: number | null = null, sortOrder = 0): Promise<StationFileFolder> {
    return folders.create({name, parentId: parentId ?? undefined, sortOrder})
}

export async function updateMediaFolder(
    id: number, name: string, parentId: number | null, sortOrder: number): Promise<void> {
    await folders.update(id, {name, parentId: parentId ?? undefined, sortOrder})
}

export const deleteMediaFolder = folders.remove

export const listMediaTags = tags.list

export async function createMediaTag(name: string, color: string | null = null): Promise<StationFileTag> {
    return tags.create({name, color: color ?? undefined})
}

export async function updateMediaTag(id: number, name: string, color: string | null): Promise<void> {
    await tags.update(id, {name, color: color ?? undefined})
}

export const deleteMediaTag = tags.remove

export async function assignMediaTag(fileId: number, tagId: number): Promise<void> {
    await client.post(`/media/files/${fileId}/tags/${tagId}`)
}

export async function unassignMediaTag(fileId: number, tagId: number): Promise<void> {
    await client.delete(`/media/files/${fileId}/tags/${tagId}`)
}

/**
 * Where the picture of a library file is fetched from, at the width the tile wants.
 *
 * <p>The session's own address rather than the public one. A picture is the file in miniature, and
 * the public route asks nothing beyond knowing the hash, which is right for a page anybody may read
 * and wrong for a sheet an appointment keeps back from the room.
 *
 * <p>Refused where the file has no picture, which is what tells a tile to draw its kind instead.
 */
export function mediaPictureUrl(contentHash: string, width?: number): string {
    const base = `/media/picture/${contentHash}`
    return width ? `${base}?w=${width}` : base
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

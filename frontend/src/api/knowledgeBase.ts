/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
import {uploadFile as uploadMultipart} from './upload'
import type {MemberIdentity} from './types'

export interface KbFolder {
    id: number
    stationId: string
    parentId: number | null
    name: string
    description: string
    iconUrl: string | null
    position: number
    createdBy: number
    createdAt: string
    updatedAt: string
    restricted?: boolean
}

export interface KbFile {
    id: number
    stationId: string
    folderId: number | null
    name: string
    description: string
    fileType: string
    mimeType: string | null
    fileSize: number
    iconUrl: string | null
    youtubeUrl: string | null
    linkUrl: string | null
    position: number
    createdBy: number
    createdAt: string
    updatedAt: string
    sourceFileId: number | null
    sourceStationId: string | null
    restricted?: boolean
    conversionStatus: string | null
}

export interface KbFileVersion {
    id: number
    fileId: number
    patch: string
    isFull: boolean
    version: number
    createdBy: number
    createdByName?: string
    createdAt: string
}

/**
 * The file facts a federated listing carries. Partner stations only publish the
 * identity, the name and the description; the remaining metadata stays on the
 * owning station, so everything past those three is optional.
 */
export interface SharedKbFile {
    id: number
    name: string
    description: string
    fileType?: string
}

export interface SharedFileEntry {
    file: SharedKbFile
    stationName: string
    sourceStationUid: string | null
}

export interface BrowseResponse {
    currentFolder: KbFolder | null
    folders: KbFolder[]
    files: KbFile[]
    sharedFiles: SharedFileEntry[]
    favourites: KbFile[]
    /** What the reader may do in the folder being browsed, which decides what may be created in it. */
    currentLevel?: KbAccessLevelName
    /** What the reader may do with each folder, keyed by folder id. */
    folderLevels?: Record<number, KbAccessLevelName>
    /** What the reader may do with each file, keyed by file id. */
    fileLevels?: Record<number, KbAccessLevelName>
}

/**
 * Tells whether a level is enough for an action, using the same order the server checks.
 */
export function levelCovers(level: KbAccessLevelName | undefined, required: KbAccessLevelName): boolean {
    const order = [KbAccessLevel.NONE, KbAccessLevel.READ, KbAccessLevel.WRITE, KbAccessLevel.MANAGE]
    if (!level) return true
    return order.indexOf(level) >= order.indexOf(required)
}

export interface MarkdownHtmlResponse {
    html: string
    markdown: string
}

export const KbFileType = {
    MARKDOWN: 'MARKDOWN',
    PDF: 'PDF',
    TEXT: 'TEXT',
    IMAGE: 'IMAGE',
    YOUTUBE: 'YOUTUBE',
    LINK: 'LINK',
    PRESENTATION: 'PRESENTATION',
    OTHER: 'OTHER',
} as const

export type KbFileTypeName = (typeof KbFileType)[keyof typeof KbFileType]

// -- Browse --

export async function browse(folderId?: number | null): Promise<BrowseResponse> {
    const params = folderId != null ? {folderId} : {}
    const res = await client.get<BrowseResponse>('/kb/browse', {params})
    return res.data
}

export interface FileResponse {
    file: KbFile
    lastEditedByName: string | null
    isFavourite: boolean
    /** What the reader may do with this file. */
    accessLevel?: KbAccessLevelName
    /** The folder whose permission decided that, when one did. */
    accessLevelSource?: string | null
}

interface FolderCreateRequest {
    parentId?: number | null
    name: string
    description?: string
}

interface NodeUpdateRequest {
    name: string
    description?: string
    iconUrl?: string | null
    position?: number
}

const folders = createCrudResource<KbFolder, FolderCreateRequest, NodeUpdateRequest>('/kb/folders')

const files = createCrudResource<
    KbFile,
    NodeUpdateRequest,
    NodeUpdateRequest,
    FileResponse,
    KbFile
>('/kb/files')

// -- Folders --

export async function listFolders(parentId?: number | null): Promise<KbFolder[]> {
    return folders.list({parentId})
}

export const getFolder = folders.get
export const createFolder = folders.create
export const updateFolder = folders.update
export const deleteFolder = folders.remove

// -- Files --

export async function listFiles(folderId?: number | null): Promise<KbFile[]> {
    return files.list({folderId})
}

export const getFile = files.get
export const updateFile = files.update
export const deleteFile = files.remove

// -- File Creation --

export async function createMarkdownFile(data: {
    folderId?: number | null
    name: string
    description?: string
    content?: string
}): Promise<KbFile> {
    const res = await client.post<KbFile>('/kb/files/markdown', data)
    return res.data
}

export async function createYoutubeFile(data: {
    folderId?: number | null
    name: string
    description?: string
    youtubeUrl: string
}): Promise<KbFile> {
    const res = await client.post<KbFile>('/kb/files/youtube', data)
    return res.data
}

export async function createLinkFile(data: {
    folderId?: number | null
    name?: string
    description?: string
    linkUrl: string
}): Promise<KbFile> {
    const res = await client.post<KbFile>('/kb/files/link', data)
    return res.data
}

export async function uploadFile(data: {
    folderId?: number | null
    name?: string
    description?: string
    file: File
}): Promise<KbFile> {
    return uploadMultipart<KbFile>('/kb/files/upload', {
        file: data.file,
        name: data.name || undefined,
        description: data.description || undefined,
        folderId: data.folderId != null ? String(data.folderId) : undefined,
    })
}

export async function importDocument(data: {
    folderId?: number | null
    name?: string
    description?: string
    file: File
}): Promise<KbFile> {
    return uploadMultipart<KbFile>('/kb/files/import-document', {
        file: data.file,
        name: data.name || undefined,
        description: data.description || undefined,
        folderId: data.folderId != null ? String(data.folderId) : undefined,
    })
}

// -- Content --

/**
 * Returns the API path (relative to the shared axios client's baseURL) at
 * which a knowledge-base file's primary content is served. Intended to be
 * passed to {@code AuthImage}/{@code AuthIframe}/{@code downloadAuthed},
 * which fetch through the authenticated client.
 */
export function fileContentUrl(id: number): string {
    return `/kb/files/${id}/content`
}

export async function getMarkdownHtml(id: number): Promise<MarkdownHtmlResponse> {
    const res = await client.get<MarkdownHtmlResponse>(`/kb/files/${id}/html`)
    return res.data
}

export async function getTextContent(id: number): Promise<string> {
    const res = await client.get<string>(`/kb/files/${id}/content`, {
        responseType: 'text',
        transformResponse: [(data: string) => data],
    })
    return res.data
}

export async function updateMarkdownContent(id: number, content: string): Promise<void> {
    await client.put(`/kb/files/${id}/content`, {content})
}

// -- Presentation Original --

/**
 * Returns the API path (relative to the shared axios client's baseURL) for
 * a knowledge-base file's original-format download. Pass to
 * {@code downloadAuthed} to trigger an authenticated save.
 */
export function originalFileUrl(id: number): string {
    return `/kb/files/${id}/original`
}

/**
 * Returns the API path (relative to the shared axios client's baseURL) for the PDF rendering of
 * a markdown or text file. Pass to {@code downloadAuthed} to trigger an authenticated save.
 */
export function pdfExportUrl(id: number): string {
    return `/kb/files/${id}/pdf`
}

/**
 * Returns the API path for the PDF rendering of a file held by a federation partner. The document
 * is headed with the partner's name, since the file is theirs.
 */
export function federatedPdfExportUrl(stationUid: string, fileId: number): string {
    return `/federated/${stationUid}/kb/files/${fileId}/pdf`
}

export async function reuploadOriginal(id: number, file: File): Promise<KbFile> {
    return uploadMultipart<KbFile>(`/kb/files/${id}/original`, {file}, 'put')
}

// -- Versions --

export async function listVersions(id: number): Promise<KbFileVersion[]> {
    const res = await client.get<KbFileVersion[]>(`/kb/files/${id}/versions`)
    return res.data
}

export async function getVersion(fileId: number, version: number): Promise<KbFileVersion> {
    const res = await client.get<KbFileVersion>(`/kb/files/${fileId}/versions/${version}`)
    return res.data
}

export async function revertToVersion(fileId: number, version: number): Promise<void> {
    await client.post(`/kb/files/${fileId}/versions/${version}/revert`)
}

// -- Access Restrictions --

/**
 * What a member may do with a folder or file, from nothing to everything.
 */
export const KbAccessLevel = {
    NONE: 'NONE',
    READ: 'READ',
    WRITE: 'WRITE',
    MANAGE: 'MANAGE',
} as const

export type KbAccessLevelName = (typeof KbAccessLevel)[keyof typeof KbAccessLevel]

/**
 * One audience and what it may do. A null level names an audience and leaves the level to the
 * station permission the member holds, which is what every entry carried before levels existed.
 */
export interface KbGrant {
    userType?: string | null
    groupId?: number | null
    tagId?: number | null
    memberId?: number | null
    level?: KbAccessLevelName | null
}

export interface KbRestrictions {
    userTypes: string[]
    groupIds: number[]
    tagIds: number[]
    memberIds: number[]
    grants?: KbGrant[]
}

export async function getFolderRestrictions(folderId: number): Promise<KbRestrictions> {
    const res = await client.get<KbRestrictions>(`/kb/folders/${folderId}/restrictions`)
    return res.data
}

export async function setFolderRestrictions(folderId: number, data: KbRestrictions): Promise<KbRestrictions> {
    const res = await client.put<KbRestrictions>(`/kb/folders/${folderId}/restrictions`, data)
    return res.data
}

export async function getFileRestrictions(fileId: number): Promise<KbRestrictions> {
    const res = await client.get<KbRestrictions>(`/kb/files/${fileId}/restrictions`)
    return res.data
}

export async function setFileRestrictions(fileId: number, data: KbRestrictions): Promise<KbRestrictions> {
    const res = await client.put<KbRestrictions>(`/kb/files/${fileId}/restrictions`, data)
    return res.data
}

// -- Folder Icons --

/**
 * Returns the API path (relative to the shared axios client's baseURL) for
 * a folder's icon at the requested rendered size. Pass to {@code AuthImage}.
 */
export function folderIconUrl(folderId: number, size = 128): string {
    return `/kb/folders/${folderId}/icon?size=${size}`
}

export async function uploadFolderIcon(folderId: number, file: File): Promise<void> {
    await uploadMultipart(`/kb/folders/${folderId}/icon`, {icon: file})
}

// -- Tags --

export interface KbTag {
    id: number
    stationId: string
    name: string
}

export async function listTags(): Promise<KbTag[]> {
    const res = await client.get<KbTag[]>('/kb/tags')
    return res.data
}

export async function getFileTags(fileId: number): Promise<KbTag[]> {
    const res = await client.get<KbTag[]>(`/kb/files/${fileId}/tags`)
    return res.data
}

export async function setFileTags(fileId: number, tags: string[]): Promise<KbTag[]> {
    const res = await client.put<KbTag[]>(`/kb/files/${fileId}/tags`, {tags})
    return res.data
}

export async function getFolderTags(folderId: number): Promise<KbTag[]> {
    const res = await client.get<KbTag[]>(`/kb/folders/${folderId}/tags`)
    return res.data
}

export interface TagScope {
    matchingFileIds: number[]
    ancestorFolderIds: number[]
}

export async function getTagScope(tagName: string): Promise<TagScope> {
    const res = await client.get<TagScope>(`/kb/tags/${encodeURIComponent(tagName)}/scope`)
    return res.data
}

export async function setFolderTags(folderId: number, tags: string[]): Promise<KbTag[]> {
    const res = await client.put<KbTag[]>(`/kb/folders/${folderId}/tags`, {tags})
    return res.data
}

// -- Related Files --

export async function getRelatedFiles(fileId: number): Promise<KbFile[]> {
    const res = await client.get<KbFile[]>(`/kb/files/${fileId}/related`)
    return res.data
}

export async function setRelatedFiles(fileId: number, targetFileIds: number[]): Promise<KbFile[]> {
    const res = await client.put<KbFile[]>(`/kb/files/${fileId}/related`, {fileIds: targetFileIds})
    return res.data
}

// -- KB Images --

export interface ImageUploadResponse {
    imageId: string
}

export async function uploadKbImage(fileId: number, image: File): Promise<ImageUploadResponse> {
    return uploadMultipart<ImageUploadResponse>(`/kb/files/${fileId}/images`, {image})
}

/**
 * Returns the API path (relative to the shared axios client's baseURL) for
 * an inline KB image. The value is stored verbatim inside markdown content
 * - see {@code KbMarkdownView} and {@code ImageNodeView} which fetch it
 * through the authenticated client at render time.
 */
export function kbImageUrl(imageId: string, size = 1024): string {
    return `/kb/images/${imageId}?size=${size}`
}

// -- Favourites --

export async function listFavourites(): Promise<KbFile[]> {
    const res = await client.get<KbFile[]>('/kb/favourites')
    return res.data
}

export async function addFavourite(fileId: number): Promise<void> {
    await client.post(`/kb/favourites/${fileId}`)
}

export async function removeFavourite(fileId: number): Promise<void> {
    await client.delete(`/kb/favourites/${fileId}`)
}

// -- Search --

export interface SearchResult {
    file: KbFile
    snippet: string
    folderPath: string
    stationName: string | null
    sourceStationUid: string | null
}

// -- Public Visibility --

export async function getPublicVisibility(type: 'files' | 'folders', id: number): Promise<{ visible: boolean | null }> {
    const res = await client.get<{ visible: boolean | null }>(`/kb/${type}/${id}/public-visibility`)
    return res.data
}

export async function setPublicVisibility(type: 'files' | 'folders', id: number, visible: boolean | null): Promise<void> {
    await client.put(`/kb/${type}/${id}/public-visibility`, { visible })
}

// -- Search --

export async function search(query: string, options?: { tag?: string; federated?: boolean }): Promise<SearchResult[]> {
    const params: Record<string, string> = {q: query}
    if (options?.tag) params.tag = options.tag
    if (options?.federated === false) params.federated = 'false'
    const res = await client.get<SearchResult[]>('/kb/search', {params})
    return res.data
}

// -- KB Comments --

export interface KbComment {
    id: number
    fileId: number
    parentId: number | null
    author: MemberIdentity | null
    authorName: string
    content: string
    deleted?: boolean
    createdAt: string
    updatedAt?: string | null
}

interface CommentCreateRequest {
    parentId?: number | null
    content: string
}

interface CommentUpdateRequest {
    content: string
}

const fileComments = createScopedCrudResource<
    KbComment,
    CommentCreateRequest
>((fileId: number) => `/kb/files/${fileId}/comments`)

const comments = createCrudResource<
    KbComment,
    CommentUpdateRequest,
    CommentUpdateRequest,
    KbComment,
    KbComment,
    void
>('/kb/comments')

export const listComments = fileComments.list
export const createComment = fileComments.create
export const updateComment = comments.update
export const deleteComment = comments.remove

// -- Federated files --

/**
 * The file facts a partner publishes for a single file. Folder, position, ownership and
 * restriction data stay on the owning station, so only what the viewer renders crosses over.
 */
export interface FederatedKbFile {
    id: number
    stationUid: string
    name: string
    description: string
    fileType: string
    mimeType: string | null
    fileSize: number
    youtubeUrl: string | null
    linkUrl: string | null
    createdAt: string
    updatedAt: string
    conversionStatus: string | null
}

/**
 * Reads a knowledge-base file served by a federation partner. The partner is addressed by its
 * station UUID because the file id alone is only unique within the station that owns it.
 *
 * The result is widened to the shape the file viewer components take. The fields a partner does
 * not publish are filled with neutral values; every part of the viewer that would read them is
 * hidden for federated files.
 */
export async function getFederatedFile(stationUid: string, fileId: number): Promise<KbFile> {
    const res = await client.get<FederatedKbFile>(`/federated/${stationUid}/kb/files/${fileId}`)
    const file = res.data
    return {
        ...file,
        stationId: file.stationUid,
        folderId: null,
        iconUrl: null,
        position: 0,
        createdBy: 0,
        sourceFileId: null,
        sourceStationId: null,
        restricted: false,
    }
}

/**
 * Reads the text body of a knowledge-base file served by a federation partner. Only textual file
 * types carry content; everything else answers an empty string.
 */
export async function getFederatedFileContent(stationUid: string, fileId: number): Promise<string> {
    const res = await client.get<{fileId: number; content: string}>(
        `/federated/${stationUid}/kb/files/${fileId}/content`,
    )
    return res.data.content ?? ''
}

// Federated KB comments

export async function listFederatedComments(stationUid: string, fileId: number): Promise<KbComment[]> {
    const res = await client.get<KbComment[]>(`/federated/${stationUid}/kb/files/${fileId}/comments`)
    return res.data
}

export async function createFederatedComment(
    stationUid: string,
    fileId: number,
    data: {parentId?: number | null; content: string},
): Promise<KbComment> {
    const res = await client.post<KbComment>(`/federated/${stationUid}/kb/files/${fileId}/comments`, data)
    return res.data
}

export async function updateFederatedComment(stationUid: string, commentId: number, data: {content: string}): Promise<void> {
    await client.put(`/federated/${stationUid}/kb/comments/${commentId}`, data)
}

export async function deleteFederatedComment(stationUid: string, commentId: number): Promise<void> {
    await client.delete(`/federated/${stationUid}/kb/comments/${commentId}`)
}

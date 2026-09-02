/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {ContentMode, type ContentModeName} from './news'
import type {PageRow, SaveRowRequest} from './pageManage'
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

export interface KbBlocks {
    contentMode: ContentModeName
    rows: PageRow[]
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
    /** How a markdown article was written. A rich one is built from blocks. */
    contentMode: ContentModeName
    containerId: number | null
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

/** Which partner stations one entry of this station's wiki is shared with. */
export interface EntryAudience {
    id: number
    fileId: number | null
    folderId: number | null
    scope: string
    partnerIds: number[]
}

export async function getAudiences(): Promise<EntryAudience[]> {
    const res = await client.get<EntryAudience[]>('/kb/audiences')
    return res.data
}

export async function setAudience(
    entry: {fileId?: number; folderId?: number; shared: boolean; everyStation: boolean; partnerIds: number[]},
): Promise<void> {
    await client.put('/kb/audiences', entry)
}

/** A folder a partner shares, drawn in the wiki as any other folder is and opened the same way. */
export interface SharedFolderEntry {
    id: number
    name: string
    description: string
    stationName: string
    sourceStationUid: string | null
}

/**
 * How far each entry of one level reaches: the ids on the public wiki, and the ids shared beyond this
 * station without being open to everyone in it.
 */
export interface Reach {
    publicly: number[]
    federated: number[]
    narrowly: number[]
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
    /** How far each folder reaches. */
    folderReach?: Reach
    /** How far each file reaches. */
    fileReach?: Reach
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

/**
 * What an article points at, and what points at it. The second list is the same rows read the other
 * way round, so a reference shows on both articles while only the one that wrote it can take it
 * away. Articles the reader may not open are left out of both lists rather than counted.
 */
export interface RelatedFiles {
    related: KbFile[]
    backlinks: KbFile[]
}

export async function getRelatedFiles(fileId: number): Promise<RelatedFiles> {
    const res = await client.get<RelatedFiles>(`/kb/files/${fileId}/related`)
    return res.data
}

export async function setRelatedFiles(fileId: number, targetFileIds: number[]): Promise<RelatedFiles> {
    const res = await client.put<RelatedFiles>(`/kb/files/${fileId}/related`, {fileIds: targetFileIds})
    return res.data
}

/**
 * The articles changed most recently, for the picker's state before anything has been typed into
 * it. Filtered the same way a listing is, so it never names an article the reader cannot open.
 */
export async function listRecentFiles(limit = 10): Promise<SearchResult[]> {
    const res = await client.get<SearchResult[]>('/kb/files/recent', {params: {limit}})
    return res.data
}

// -- Moving --

/**
 * Why one entry stayed where it was. The server sends one of these rather than a sentence, so the
 * screen can say it in the reader's language.
 */
export const KbRefusalReason = {
    NO_PERMISSION: 'NO_PERMISSION',
    NAME_TAKEN: 'NAME_TAKEN',
    TARGET_INSIDE: 'TARGET_INSIDE',
    SHARE_TOO_WIDE: 'SHARE_TOO_WIDE',
    NOT_FOUND: 'NOT_FOUND',
} as const

export type KbRefusalReasonName = (typeof KbRefusalReason)[keyof typeof KbRefusalReason]

/** How far an entry is read, on the one scale the wiki marks entries with. */
export const KbReach = {
    INTERNAL: 'INTERNAL',
    NARROW: 'NARROW',
    FEDERATED: 'FEDERATED',
    PUBLIC: 'PUBLIC',
} as const

export type KbReachName = (typeof KbReach)[keyof typeof KbReach]

/** One folder of the tree a move picker offers, with what the reader may do in it. */
export interface KbFolderTreeEntry {
    id: number
    parentId: number | null
    name: string
    level: KbAccessLevelName
}

export interface MoveResponse {
    moved: boolean
    name: string | null
    reason: KbRefusalReasonName | null
}

/** How far an entry reaches now and how far it would reach after a move. */
export interface MovePreview {
    before: KbReachName
    after: KbReachName
}

export interface RefusedEntry {
    name: string | null
    reason: KbRefusalReasonName
}

/**
 * What a bulk action did. {@code refused} names as many of the entries it left alone as a message
 * can carry; {@code refusedTotal} counts all of them.
 */
export interface BulkOutcome {
    doneFolderIds: number[]
    doneFileIds: number[]
    refused: RefusedEntry[]
    refusedTotal: number
}

export async function listFolderTree(): Promise<KbFolderTreeEntry[]> {
    const res = await client.get<KbFolderTreeEntry[]>('/kb/folders/tree')
    return res.data
}

export async function moveFolder(folderId: number, parentId: number | null): Promise<MoveResponse> {
    const res = await client.put<MoveResponse>(`/kb/folders/${folderId}/parent`, {parentId})
    return res.data
}

export async function moveFile(fileId: number, folderId: number | null): Promise<MoveResponse> {
    const res = await client.put<MoveResponse>(`/kb/files/${fileId}/folder`, {folderId})
    return res.data
}

export async function getMovePreview(
    entry: {folderId?: number | null; fileId?: number | null},
    targetFolderId: number | null,
): Promise<MovePreview> {
    const params: Record<string, number> = {}
    if (entry.folderId != null) params.folderId = entry.folderId
    if (entry.fileId != null) params.fileId = entry.fileId
    if (targetFolderId != null) params.targetFolderId = targetFolderId
    const res = await client.get<MovePreview>('/kb/move/preview', {params})
    return res.data
}

export async function bulkMove(
    selection: {folderIds: number[]; fileIds: number[]},
    targetFolderId: number | null,
): Promise<BulkOutcome> {
    const res = await client.post<BulkOutcome>('/kb/bulk/move', {...selection, targetFolderId})
    return res.data
}

export async function bulkTags(
    selection: {folderIds: number[]; fileIds: number[]},
    tags: {addTags: string[]; removeTags: string[]},
): Promise<BulkOutcome> {
    const res = await client.post<BulkOutcome>('/kb/bulk/tags', {...selection, ...tags})
    return res.data
}

// -- KB Images --

export interface ImageUploadResponse {
    imageId: string
}

// -- Blocks --

/**
 * The blocks a rich article is built from. A plain article answers with an empty list, so a reader
 * can ask before it knows which kind it has.
 */
export async function getKbBlocks(fileId: number): Promise<KbBlocks> {
    const res = await client.get<KbBlocks>(`/kb/files/${fileId}/blocks`)
    return res.data
}

/**
 * Turns a plain article into one built from blocks. What was written becomes a single markdown
 * block, and the switch does not go back: the stored body is a projection of the blocks from here
 * on, which is what search, the export and the version history read.
 */
export async function enableKbBlocks(fileId: number): Promise<KbBlocks> {
    const res = await client.post<KbBlocks>(`/kb/files/${fileId}/blocks/enable`)
    return res.data
}

export async function saveKbBlocks(fileId: number, rows: SaveRowRequest[]): Promise<KbBlocks> {
    const res = await client.put<KbBlocks>(`/kb/files/${fileId}/blocks`, {rows})
    return res.data
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
        // A partner cannot send blocks, so an article received from one is always plain text.
        contentMode: ContentMode.SIMPLE,
        containerId: null,
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

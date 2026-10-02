/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {ContentMode} from './news'
import {createCrudResource} from './crud'
import {noMentionables, stationMentionables, type CommentSource} from './comments'
import {StationPermission} from './types'
import {uploadFile as uploadMultipart} from './upload'
import type {
    CommentResponse,
    AudienceRequest,
    BlockRowRequest,
    BlocksResponse,
    BrowseResponse,
    BulkOutcome,
    DeleteImpact,
    EmptyTrashResponse,
    EntryAudience,
    FileContentResponse,
    FileResponse,
    FileUpdateRequest,
    FolderRequest,
    FolderTreeEntry,
    ImageUploadResponse,
    KbFavourite,
    KbFile,
    KbFileSummary,
    KbFileVersion,
    KbFolder,
    KbRestrictionRequest,
    KbRestrictionResponse,
    KbTag,
    KbVersionResponse,
    LinkFileRequest,
    MarkdownFileRequest,
    MarkdownHtmlResponse,
    MovePreview,
    MoveResponse,
    PublicVisibilityResponse,
    RelatedFilesResponse,
    RemoteKbFile,
    RestoreResult,
    SearchResultResponse,
    TagScopeResponse,
    TrashEntry,
    TrashView,
    YoutubeFileRequest,
    components,
} from '@/api/generated/schema'

type Schemas = components['schemas']

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

/** A file a partner shares, drawn in the wiki as any other file is. */
export interface SharedFileEntry {
    file: SharedKbFile
    stationName: string
    sourceStationUid: string | null
}

export async function getAudiences(): Promise<EntryAudience[]> {
    const res = await client.get<EntryAudience[]>('/kb/audiences')
    return res.data
}

export async function setAudience(entry: AudienceRequest): Promise<void> {
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
 * Tells whether a level is enough for an action, using the same order the server checks.
 */
export function levelCovers(level: KbAccessLevelName | undefined, required: KbAccessLevelName): boolean {
    const order = [KbAccessLevel.NONE, KbAccessLevel.READ, KbAccessLevel.WRITE, KbAccessLevel.MANAGE]
    if (!level) return true
    return order.indexOf(level) >= order.indexOf(required)
}

export type KbFileTypeName = Schemas['KbFileType']

export const KbFileType = {
    MARKDOWN: 'MARKDOWN',
    PDF: 'PDF',
    TEXT: 'TEXT',
    IMAGE: 'IMAGE',
    YOUTUBE: 'YOUTUBE',
    LINK: 'LINK',
    PRESENTATION: 'PRESENTATION',
    OTHER: 'OTHER',
} as const satisfies Record<KbFileTypeName, KbFileTypeName>

export async function browse(folderId?: number | null): Promise<BrowseResponse> {
    const params = folderId != null ? {folderId} : {}
    const res = await client.get<BrowseResponse>('/kb/browse', {params})
    return res.data
}

const folders = createCrudResource<KbFolder, FolderRequest, FolderRequest>('/kb/folders')

const files = createCrudResource<
    KbFileSummary,
    FileUpdateRequest,
    FileUpdateRequest,
    FileResponse,
    KbFile,
    KbFile
>('/kb/files')

export async function listFolders(parentId?: number | null): Promise<KbFolder[]> {
    return folders.list({parentId})
}

export const getFolder = folders.get
export const createFolder = folders.create
export const updateFolder = folders.update
export const deleteFolder = folders.remove

export async function listFiles(folderId?: number | null): Promise<KbFileSummary[]> {
    return files.list({folderId})
}

export const getFile = files.get
export const updateFile = files.update
export const deleteFile = files.remove

export async function createMarkdownFile(data: MarkdownFileRequest): Promise<KbFile> {
    const res = await client.post<KbFile>('/kb/files/markdown', data)
    return res.data
}

export async function createYoutubeFile(data: YoutubeFileRequest): Promise<KbFile> {
    const res = await client.post<KbFile>('/kb/files/youtube', data)
    return res.data
}

export async function createLinkFile(data: LinkFileRequest): Promise<KbFile> {
    const res = await client.post<KbFile>('/kb/files/link', data)
    return res.data
}

/** What is said about a file while it is put into the wiki. */
export interface KbUpload {
    folderId?: number | null
    name?: string
    description?: string
    file: File
}

export async function uploadFile(data: KbUpload): Promise<KbFile> {
    return uploadMultipart<KbFile>('/kb/files/upload', {
        file: data.file,
        name: data.name || undefined,
        description: data.description || undefined,
        folderId: data.folderId != null ? String(data.folderId) : undefined,
    })
}

export async function importDocument(data: KbUpload): Promise<KbFile> {
    return uploadMultipart<KbFile>('/kb/files/import-document', {
        file: data.file,
        name: data.name || undefined,
        description: data.description || undefined,
        folderId: data.folderId != null ? String(data.folderId) : undefined,
    })
}

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

/**
 * Returns the API path (relative to the shared axios client's baseURL) for
 * a knowledge-base file's original-format download. Pass to
 * {@code downloadAuthed} to trigger an authenticated save.
 */
export function originalFileUrl(id: number): string {
    return `/kb/files/${id}/original`
}

/**
 * Where the file somebody uploaded is fetched back as it was uploaded.
 *
 * <p>A presentation is shown as the PDF it was converted to, so its own bytes live at a separate
 * address; everything else is shown as itself and its content is the file.
 */
export function rawFileUrl(file: Pick<KbFile, 'id' | 'fileType'>): string {
    return file.fileType === KbFileType.PRESENTATION ? originalFileUrl(file.id) : fileContentUrl(file.id)
}

/** The picture of a file at the longest side asked for, answered 404 where the file has none. */
export function filePictureUrl(id: number, size = 512): string {
    return `/kb/files/${id}/picture?size=${size}`
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

export async function listVersions(id: number): Promise<KbVersionResponse[]> {
    const res = await client.get<KbVersionResponse[]>(`/kb/files/${id}/versions`)
    return res.data
}

export async function getVersion(fileId: number, version: number): Promise<KbFileVersion> {
    const res = await client.get<KbFileVersion>(`/kb/files/${fileId}/versions/${version}`)
    return res.data
}

export async function revertToVersion(fileId: number, version: number): Promise<void> {
    await client.post(`/kb/files/${fileId}/versions/${version}/revert`)
}

export type KbAccessLevelName = Schemas['KbAccessLevel']

/**
 * What a member may do with a folder or file, from nothing to everything.
 */
export const KbAccessLevel = {
    NONE: 'NONE',
    READ: 'READ',
    WRITE: 'WRITE',
    MANAGE: 'MANAGE',
} as const satisfies Record<KbAccessLevelName, KbAccessLevelName>

export async function getFolderRestrictions(folderId: number): Promise<KbRestrictionResponse> {
    const res = await client.get<KbRestrictionResponse>(`/kb/folders/${folderId}/restrictions`)
    return res.data
}

export async function setFolderRestrictions(folderId: number, data: KbRestrictionRequest): Promise<KbRestrictionResponse> {
    const res = await client.put<KbRestrictionResponse>(`/kb/folders/${folderId}/restrictions`, data)
    return res.data
}

export async function getFileRestrictions(fileId: number): Promise<KbRestrictionResponse> {
    const res = await client.get<KbRestrictionResponse>(`/kb/files/${fileId}/restrictions`)
    return res.data
}

export async function setFileRestrictions(fileId: number, data: KbRestrictionRequest): Promise<KbRestrictionResponse> {
    const res = await client.put<KbRestrictionResponse>(`/kb/files/${fileId}/restrictions`, data)
    return res.data
}

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

export async function getTagScope(tagName: string): Promise<TagScopeResponse> {
    const res = await client.get<TagScopeResponse>(`/kb/tags/${encodeURIComponent(tagName)}/scope`)
    return res.data
}

export async function setFolderTags(folderId: number, tags: string[]): Promise<KbTag[]> {
    const res = await client.put<KbTag[]>(`/kb/folders/${folderId}/tags`, {tags})
    return res.data
}

export async function getRelatedFiles(fileId: number): Promise<RelatedFilesResponse> {
    const res = await client.get<RelatedFilesResponse>(`/kb/files/${fileId}/related`)
    return res.data
}

export async function setRelatedFiles(fileId: number, targetFileIds: number[]): Promise<RelatedFilesResponse> {
    const res = await client.put<RelatedFilesResponse>(`/kb/files/${fileId}/related`, {fileIds: targetFileIds})
    return res.data
}

/**
 * The articles changed most recently, for the picker's state before anything has been typed into
 * it. Filtered the same way a listing is, so it never names an article the reader cannot open.
 */
export async function listRecentFiles(limit = 10): Promise<SearchResultResponse[]> {
    const res = await client.get<SearchResultResponse[]>('/kb/files/recent', {params: {limit}})
    return res.data
}

export type KbRefusalReasonName = Schemas['KbRefusalReason']

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
} as const satisfies Record<KbRefusalReasonName, KbRefusalReasonName>

export type KbReachName = Schemas['KbReach']

/** How far an entry is read, on the one scale the wiki marks entries with. */
export const KbReach = {
    INTERNAL: 'INTERNAL',
    NARROW: 'NARROW',
    FEDERATED: 'FEDERATED',
    PUBLIC: 'PUBLIC',
} as const satisfies Record<KbReachName, KbReachName>

export async function listFolderTree(): Promise<FolderTreeEntry[]> {
    const res = await client.get<FolderTreeEntry[]>('/kb/folders/tree')
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

/** The folders and files a bulk action is asked to touch. */
export interface KbSelection {
    folderIds: number[]
    fileIds: number[]
}

export async function bulkMove(selection: KbSelection, targetFolderId: number | null): Promise<BulkOutcome> {
    const res = await client.post<BulkOutcome>('/kb/bulk/move', {...selection, targetFolderId})
    return res.data
}

export async function bulkTags(
    selection: KbSelection,
    tags: {addTags: string[]; removeTags: string[]},
): Promise<BulkOutcome> {
    const res = await client.post<BulkOutcome>('/kb/bulk/tags', {...selection, ...tags})
    return res.data
}

export async function bulkDelete(selection: KbSelection): Promise<BulkOutcome> {
    const res = await client.post<BulkOutcome>('/kb/bulk/delete', selection)
    return res.data
}

export async function getDeleteImpact(selection: KbSelection): Promise<DeleteImpact> {
    const res = await client.post<DeleteImpact>('/kb/bulk/delete/impact', selection)
    return res.data
}

export async function listTrash(): Promise<TrashView> {
    const res = await client.get<TrashView>('/kb/trash')
    return res.data
}

export async function emptyTrash(): Promise<EmptyTrashResponse> {
    const res = await client.delete<EmptyTrashResponse>('/kb/trash')
    return res.data
}

export async function restoreTrashed(entry: TrashEntry): Promise<RestoreResult> {
    const path = entry.folder ? `/kb/trash/folders/${entry.id}/restore` : `/kb/trash/files/${entry.id}/restore`
    const res = await client.post<RestoreResult>(path)
    return res.data
}

export async function purgeTrashed(entry: TrashEntry): Promise<void> {
    const path = entry.folder ? `/kb/trash/folders/${entry.id}` : `/kb/trash/files/${entry.id}`
    await client.delete(path)
}

/**
 * The blocks a rich article is built from. A plain article answers with an empty list, so a reader
 * can ask before it knows which kind it has.
 */
export async function getKbBlocks(fileId: number): Promise<BlocksResponse> {
    const res = await client.get<BlocksResponse>(`/kb/files/${fileId}/blocks`)
    return res.data
}

/**
 * Turns a plain article into one built from blocks. What was written becomes a single markdown
 * block, and the switch does not go back: the stored body is a projection of the blocks from here
 * on, which is what search, the export and the version history read.
 */
export async function enableKbBlocks(fileId: number): Promise<BlocksResponse> {
    const res = await client.post<BlocksResponse>(`/kb/files/${fileId}/blocks/enable`)
    return res.data
}

export async function saveKbBlocks(fileId: number, rows: BlockRowRequest[]): Promise<BlocksResponse> {
    const res = await client.put<BlocksResponse>(`/kb/files/${fileId}/blocks`, {rows})
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

export type KbFavouriteTargetName = Schemas['KbFavouriteTarget']

/** What a favourite points at: a file or folder of this station, or one a partner shares. */
export const KbFavouriteTarget = {
    FILE: 'FILE',
    FOLDER: 'FOLDER',
    PARTNER_FILE: 'PARTNER_FILE',
    PARTNER_FOLDER: 'PARTNER_FOLDER',
} as const satisfies Record<KbFavouriteTargetName, KbFavouriteTargetName>

/** Something that can be marked, named the way the server tells two favourites apart. */
export type FavouriteEntry = Pick<KbFavourite, 'target' | 'entryId'> & {
    /** The partner serving the entry, for the partner kinds only. */
    partnerStationUid?: string | null
}

export async function listFavourites(): Promise<KbFavourite[]> {
    const res = await client.get<KbFavourite[]>('/kb/favourites')
    return res.data
}

export async function markFavourite(entry: FavouriteEntry): Promise<KbFavourite> {
    const res = await client.post<KbFavourite>('/kb/favourites', entry)
    return res.data
}

export async function unmarkFavourite(id: number): Promise<void> {
    await client.delete(`/kb/favourites/${id}`)
}

export async function getPublicVisibility(type: 'files' | 'folders', id: number): Promise<PublicVisibilityResponse> {
    const res = await client.get<PublicVisibilityResponse>(`/kb/${type}/${id}/public-visibility`)
    return res.data
}

export async function setPublicVisibility(type: 'files' | 'folders', id: number, visible: boolean | null): Promise<void> {
    await client.put(`/kb/${type}/${id}/public-visibility`, { visible })
}

export async function search(query: string, options?: { tag?: string; federated?: boolean }): Promise<SearchResultResponse[]> {
    const params: Record<string, string> = {q: query}
    if (options?.tag) params.tag = options.tag
    if (options?.federated === false) params.federated = 'false'
    const res = await client.get<SearchResultResponse[]>('/kb/search', {params})
    return res.data
}

/** The thread under one of the station's own articles; a knowledge manager removes anybody's comment there. */
export function kbCommentSource(fileId: number): CommentSource {
    return {
        list: async () => (await client.get<CommentResponse[]>(`/kb/files/${fileId}/comments`)).data,
        create: (parentId, content) => client.post(`/kb/files/${fileId}/comments`, {parentId, content}),
        update: (commentId, content) => client.put(`/kb/comments/${commentId}`, {content}),
        remove: commentId => client.delete(`/kb/comments/${commentId}`),
        mentionables: () => stationMentionables({type: 'KB_FILE', entityId: fileId}),
        moderator: StationPermission.KNOWLEDGE_MANAGER,
    }
}

/** The thread under an article a partner station shares, where only authors change or remove anything. */
export function partnerKbCommentSource(stationUid: string, fileId: number): CommentSource {
    const base = `/federated/${stationUid}/kb`
    return {
        list: async () => (await client.get<CommentResponse[]>(`${base}/files/${fileId}/comments`)).data,
        create: (parentId, content) => client.post(`${base}/files/${fileId}/comments`, {parentId, content}),
        update: (commentId, content) => client.put(`${base}/comments/${commentId}`, {content}),
        remove: commentId => client.delete(`${base}/comments/${commentId}`),
        mentionables: noMentionables,
        moderator: null,
    }
}

/**
 * Reads a knowledge-base file served by a federation partner. The partner is addressed by its
 * station UUID because the file id alone is only unique within the station that owns it.
 *
 * The result is widened to the shape the file viewer components take. The fields a partner does
 * not publish are filled with neutral values; every part of the viewer that would read them is
 * hidden for federated files. A partner cannot send blocks, so an article received from one is
 * always plain text.
 */
export async function getFederatedFile(stationUid: string, fileId: number): Promise<KbFile> {
    const res = await client.get<RemoteKbFile>(`/federated/${stationUid}/kb/files/${fileId}`)
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
        restrictionMode: 'OR',
        restricted: false,
        contentMode: ContentMode.SIMPLE,
        containerId: null,
    }
}

/**
 * Reads the text body of a knowledge-base file served by a federation partner. Only textual file
 * types carry content; everything else answers an empty string.
 */
export async function getFederatedFileContent(stationUid: string, fileId: number): Promise<string> {
    const res = await client.get<FileContentResponse>(`/federated/${stationUid}/kb/files/${fileId}/content`)
    return res.data.content
}

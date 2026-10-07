/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {uploadFile} from './upload'
import type {DocumentPage, MemberDocumentResponse} from '@/api/generated/schema'

/** What is said about a document while it is put in. */
export interface DocumentUpload {
    file: File
    title: string
    hidden?: boolean
    keepOnArchive?: boolean
    tags?: string[]
    /** The members it already concerns, which is usually known while it is handed over. */
    memberIds?: number[]
}

/** The fields an upload is made of, skipping what was not said. */
function fieldsOf(upload: DocumentUpload): Record<string, string | File | undefined> {
    return {
        file: upload.file,
        title: upload.title,
        hidden: upload.hidden ? 'true' : undefined,
        keepOnArchive: upload.keepOnArchive ? 'true' : undefined,
        tags: upload.tags?.length ? upload.tags.join(',') : undefined,
        memberIds: upload.memberIds?.length ? upload.memberIds.join(',') : undefined,
    }
}

/**
 * Where the documents of one member are read and added, which is the station's own store reached
 * through whichever door the reader comes: the station's screens or the association's.
 */
export interface MemberDocumentSource {
    listOf(memberId: number): Promise<MemberDocumentResponse[]>
    upload(memberId: number, upload: DocumentUpload): Promise<MemberDocumentResponse>
    /** Where the document itself is served from, for a download or an inline view. */
    contentUrl(documentId: number): string
    /** Where the picture of a document is served from, or null where this door serves none. */
    thumbnailUrl: ((documentId: number) => string) | null
}

export async function listForMember(memberId: number): Promise<MemberDocumentResponse[]> {
    const res = await client.get<MemberDocumentResponse[]>(`/station-members/${memberId}/documents`)
    return res.data
}

export async function uploadForMember(memberId: number, upload: DocumentUpload): Promise<MemberDocumentResponse> {
    return uploadFile<MemberDocumentResponse>(`/station-members/${memberId}/documents`, fieldsOf(upload))
}

/** What the store is narrowed by, for a page of it and for every document it matches. */
export interface DocumentFilter {
    memberIds?: number[]
    search?: string
    /** Only the documents that name nobody, which are the station's own paperwork. */
    unbound?: boolean
    /** Only the documents about people who have all left, archived or deleted. */
    departed?: boolean
}

function filterParams(filter: DocumentFilter): Record<string, unknown> {
    const {memberIds, ...rest} = filter
    return {...rest, memberIds: memberIds?.length ? memberIds.join(',') : undefined}
}

/** What the store holds, a page at a time, narrowed by member or by words. */
export async function listStation(params: DocumentFilter & {page?: number, size?: number} = {}): Promise<DocumentPage> {
    const res = await client.get<DocumentPage>('/documents', {params: filterParams(params)})
    return res.data
}

/** Every document the filter matches, by id, so all of them can be chosen at once. */
export async function listIds(filter: DocumentFilter): Promise<number[]> {
    const res = await client.get<number[]>('/documents/ids', {params: filterParams(filter)})
    return res.data
}

/** Removes several documents in one go; one the reader may not remove stops all of it. */
export async function prune(documentIds: number[]): Promise<void> {
    await client.post('/documents/prune', {documentIds})
}

/** Puts a document in the store without binding it to anybody. */
export async function uploadForStation(upload: DocumentUpload): Promise<MemberDocumentResponse> {
    return uploadFile<MemberDocumentResponse>('/documents', fieldsOf(upload))
}

/** Gives a document exactly these members, letting go of the ones left out. */
export async function setMembers(documentId: number, memberIds: number[]): Promise<MemberDocumentResponse> {
    const res = await client.put<MemberDocumentResponse>(`/documents/${documentId}/members`, {memberIds})
    return res.data
}

export async function setTags(documentId: number, tags: string[]): Promise<MemberDocumentResponse> {
    const res = await client.put<MemberDocumentResponse>(`/documents/${documentId}/tags`, {tags})
    return res.data
}

export async function listTags(): Promise<string[]> {
    const res = await client.get<string[]>('/documents/tags')
    return res.data
}

export async function remove(documentId: number): Promise<void> {
    await client.delete(`/documents/${documentId}`)
}

/** Where the document itself is served from, for a download or an inline view. */
export function contentUrl(documentId: number): string {
    return `/documents/${documentId}/content`
}

/**
 * Where the picture of a document is served from, for the tile to show.
 *
 * <p>The size is the longest side. A portrait page is narrower than that, and a tile shows the page
 * across its full width, so the picture asked for is large enough for that width on a dense screen.
 */
export function thumbnailUrl(documentId: number, size = 1024): string {
    return `/documents/${documentId}/thumbnail?size=${size}`
}

/** A member's documents as the station's own screens reach them. */
export const stationDocumentSource: MemberDocumentSource = {
    listOf: listForMember,
    upload: uploadForMember,
    contentUrl,
    thumbnailUrl: documentId => thumbnailUrl(documentId),
}

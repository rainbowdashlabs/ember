/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {uploadFile} from './upload'

/** A file kept for the members it concerns. */
export interface MemberDocument {
    id: number
    title: string
    fileName: string
    mimeType: string
    sizeBytes: number
    /** Kept from the members it belongs to and shown only to whoever may read other members. */
    hidden: boolean
    /** Survives its members being marked former, which is what a binding document needs. */
    keepOnArchive: boolean
    hasThumbnail: boolean
    uploadedBy: number | null
    createdAt: string
    memberIds: number[]
    tags: string[]
}

/** A page of the store, with the number of documents the filters match in all. */
export interface MemberDocumentPage {
    documents: MemberDocument[]
    total: number
}

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

export async function listForMember(memberId: number): Promise<MemberDocument[]> {
    const res = await client.get<MemberDocument[]>(`/station-members/${memberId}/documents`)
    return res.data
}

export async function uploadForMember(memberId: number, upload: DocumentUpload): Promise<MemberDocument> {
    return uploadFile<MemberDocument>(`/station-members/${memberId}/documents`, fieldsOf(upload))
}

/** What the store holds, a page at a time, narrowed by member or by words. */
export async function listStation(params: {
    memberIds?: number[]
    search?: string
    page?: number
    size?: number
} = {}): Promise<MemberDocumentPage> {
    const {memberIds, ...rest} = params
    const res = await client.get<MemberDocumentPage>('/member-documents', {
        params: {...rest, memberIds: memberIds?.length ? memberIds.join(',') : undefined},
    })
    return res.data
}

/** Puts a document in the store without binding it to anybody. */
export async function uploadForStation(upload: DocumentUpload): Promise<MemberDocument> {
    return uploadFile<MemberDocument>('/member-documents', fieldsOf(upload))
}

/** Gives a document exactly these members, letting go of the ones left out. */
export async function setMembers(documentId: number, memberIds: number[]): Promise<MemberDocument> {
    const res = await client.put<MemberDocument>(`/member-documents/${documentId}/members`, {memberIds})
    return res.data
}

export async function setTags(documentId: number, tags: string[]): Promise<MemberDocument> {
    const res = await client.put<MemberDocument>(`/member-documents/${documentId}/tags`, {tags})
    return res.data
}

export async function listTags(): Promise<string[]> {
    const res = await client.get<string[]>('/member-documents/tags')
    return res.data
}

export async function remove(documentId: number): Promise<void> {
    await client.delete(`/member-documents/${documentId}`)
}

/** Where the document itself is served from, for a download or an inline view. */
export function contentUrl(documentId: number): string {
    return `/member-documents/${documentId}/content`
}

/** Where the picture of a document is served from, for the tile to show. */
export function thumbnailUrl(documentId: number, size = 256): string {
    return `/member-documents/${documentId}/thumbnail?size=${size}`
}

/** Whether the application can show the file itself rather than only offer it. */
export function isPreviewable(mimeType: string): boolean {
    return mimeType?.startsWith('image/')
        || mimeType?.startsWith('text/')
        || mimeType === 'application/pdf'
}

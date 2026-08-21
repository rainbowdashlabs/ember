/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface ClusterNews {
    id: number
    title: string
    contentMarkdown: string
    createdAt: string
}

export interface ClusterEvent {
    id: number
    name: string
    description?: string | null
    startTime?: string | null
    endTime?: string | null
}

export async function listNews(): Promise<ClusterNews[]> {
    const res = await client.get<ClusterNews[]>('/cluster/news')
    return res.data
}

export async function createNews(title: string, contentMarkdown: string, contentHtml: string): Promise<ClusterNews> {
    const res = await client.post<ClusterNews>('/cluster/news', {title, contentMarkdown, contentHtml})
    return res.data
}

export async function deleteNews(newsId: number): Promise<void> {
    await client.delete(`/cluster/news/${newsId}`)
}

export async function listEvents(): Promise<ClusterEvent[]> {
    const res = await client.get<ClusterEvent[]>('/cluster/events')
    return res.data
}

export interface NewClusterEvent {
    name: string
    description?: string
    startTime?: string | null
    endTime?: string | null
    requiresRegistration: boolean
}

export async function createEvent(data: NewClusterEvent): Promise<ClusterEvent> {
    const res = await client.post<ClusterEvent>('/cluster/events', data)
    return res.data
}

export async function deleteEvent(eventId: number): Promise<void> {
    await client.delete(`/cluster/events/${eventId}`)
}

// -- Knowledge base --

export interface ClusterKbFolder {
    id: number
    name: string
    description?: string | null
}

export interface ClusterKbFile {
    id: number
    name: string
    description?: string | null
    folderId?: number | null
}

export async function listKbFolders(): Promise<ClusterKbFolder[]> {
    const res = await client.get<ClusterKbFolder[]>('/cluster/knowledge/folders')
    return res.data
}

/** A folder is shared with the whole cluster as it is made; there is no per-folder choice. */
export async function createKbFolder(name: string, description?: string): Promise<ClusterKbFolder> {
    const res = await client.post<ClusterKbFolder>('/cluster/knowledge/folders', {name, description, parentId: null})
    return res.data
}

export async function listKbFiles(folderId?: number | null): Promise<ClusterKbFile[]> {
    const res = await client.get<ClusterKbFile[]>('/cluster/knowledge/files', {
        params: folderId != null ? {folderId} : {},
    })
    return res.data
}

export interface NewClusterKbFile {
    name: string
    description?: string
    content: string
    folderId?: number | null
}

export async function createKbFile(data: NewClusterKbFile): Promise<ClusterKbFile> {
    const res = await client.post<ClusterKbFile>('/cluster/knowledge/files', data)
    return res.data
}

export async function deleteKbFile(fileId: number): Promise<void> {
    await client.delete(`/cluster/knowledge/files/${fileId}`)
}

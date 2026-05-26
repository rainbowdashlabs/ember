/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { Comment, EntityNote, NoteVersion } from './types'

// -- Event Comments --

export async function listEventComments(eventId: number): Promise<Comment[]> {
    const res = await client.get<Comment[]>(`/events/${eventId}/comments`)
    return res.data
}

export async function createEventComment(eventId: number, data: { parentId?: number | null; content: string }): Promise<Comment> {
    const res = await client.post<Comment>(`/events/${eventId}/comments`, data)
    return res.data
}

export async function updateComment(commentId: number, data: { content: string }): Promise<void> {
    await client.put(`/events/comments/${commentId}`, data)
}

export async function deleteComment(commentId: number): Promise<void> {
    await client.delete(`/events/comments/${commentId}`)
}

// -- Notes --

export async function getNote(entityType: string, entityId: number): Promise<EntityNote | null> {
    try {
        const res = await client.get<EntityNote>(`/notes/${entityType}/${entityId}`)
        return res.data
    } catch (e: any) {
        if (e?.response?.status === 404) return null
        throw e
    }
}

export async function updateNote(entityType: string, entityId: number, data: { content: string }): Promise<EntityNote> {
    const res = await client.put<EntityNote>(`/notes/${entityType}/${entityId}`, data)
    return res.data
}

export async function getNoteVersions(entityType: string, entityId: number): Promise<NoteVersion[]> {
    const res = await client.get<NoteVersion[]>(`/notes/${entityType}/${entityId}/versions`)
    return res.data
}

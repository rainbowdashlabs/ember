/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { Comment, EntityNote, NoteVersion } from './types'

// -- Event Comments --

/**
 * Lists comments for an event. When `eventDate` is provided, the list is filtered to
 * comments scoped to that specific occurrence of a recurring event. When `eventDate` is
 * the literal string `'none'`, only whole-event comments (event_date IS NULL on the
 * backend) are returned. Omitted → all comments.
 */
export async function listEventComments(eventId: number, eventDate?: string | null): Promise<Comment[]> {
    const params: Record<string, string> = {}
    if (eventDate !== undefined && eventDate !== null) params.date = eventDate
    const res = await client.get<Comment[]>(`/events/${eventId}/comments`, {params})
    return res.data
}

export async function createEventComment(
    eventId: number,
    data: { parentId?: number | null; content: string; eventDate?: string | null },
): Promise<Comment> {
    const res = await client.post<Comment>(`/events/${eventId}/comments`, data)
    return res.data
}

export async function updateComment(commentId: number, data: { content: string }): Promise<void> {
    await client.put(`/events/comments/${commentId}`, data)
}

export async function deleteComment(commentId: number): Promise<void> {
    await client.delete(`/events/comments/${commentId}`)
}

// -- Federated Event Comments --

export async function listFederatedEventComments(stationUid: string, eventId: number): Promise<Comment[]> {
    const res = await client.get<Comment[]>(`/federated/${stationUid}/events/${eventId}/comments`)
    return res.data
}

export async function createFederatedEventComment(
    stationUid: string,
    eventId: number,
    data: { parentId?: number | null; content: string; eventDate?: string | null },
): Promise<Comment> {
    const res = await client.post<Comment>(`/federated/${stationUid}/events/${eventId}/comments`, data)
    return res.data
}

export async function updateFederatedEventComment(stationUid: string, commentId: number, data: { content: string }): Promise<void> {
    await client.put(`/federated/${stationUid}/events/comments/${commentId}`, data)
}

export async function deleteFederatedEventComment(stationUid: string, commentId: number): Promise<void> {
    await client.delete(`/federated/${stationUid}/events/comments/${commentId}`)
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

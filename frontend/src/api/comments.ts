/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource, createScopedCrudResource, type NoContent } from './crud'
import type { CommentResponse, MemberCompletion, MemberGroup, StationPermission } from './generated/schema'
import { StationPermission as Permission } from './types'
import { listCompletions } from './stationMembers'
import { listGroups } from './memberGroups'
import { apiErrorStatus } from '@/util/apiError'

/** Whom a mention in a thread can name. */
export interface Mentionables {
    members: MemberCompletion[]
    groups: MemberGroup[]
}

/**
 * Everything a comment section needs from the surface it sits on: where its thread is read and
 * written, whom a mention can name, and who besides the author may remove a comment.
 *
 * <p>Only the author changes a comment, on every surface. Removing somebody else's comment takes the
 * surface's own manager right, the one the server checks; on a partner station's thread nobody here
 * holds a right of that station, so there only authors remove.
 */
export interface CommentSource {
    list(): Promise<CommentResponse[]>
    create(parentId: number | null, content: string): Promise<unknown>
    update(commentId: number, content: string): Promise<unknown>
    remove(commentId: number): Promise<unknown>
    mentionables(): Promise<Mentionables>
    /** The right that lets somebody remove a comment they did not write, or null where only authors may. */
    moderator: StationPermission | null
    /** The appointment whose registered and declined members a mention can reach at once. */
    eventId?: number
}

/**
 * The members of this station a mention can name, narrowed to whoever may read what the thread hangs
 * under, and its groups.
 */
export async function stationMentionables(restriction: {type: string; entityId: number}): Promise<Mentionables> {
    const [members, groups] = await Promise.all([listCompletions(restriction), listGroups()])
    return {members, groups}
}

/** Nobody: a partner station's members are not known here, and this station's mean nothing there. */
export function noMentionables(): Promise<Mentionables> {
    return Promise.resolve({members: [], groups: []})
}

export interface EntityNote {
    id: number
    entityType: string
    entityId: number
    stationId: string
    content: string
    updatedBy?: number | null
    updatedAt: string
}

export interface NoteVersion {
    id: number
    noteId: number
    diffPatch: string
    authorId: number
    createdAt: string
}

interface CommentCreateRequest {
    parentId?: number | null
    content: string
    eventDate?: string | null
}

interface CommentUpdateRequest {
    content: string
}

const eventComments = createScopedCrudResource<CommentResponse, CommentCreateRequest>(
    (eventId: number) => `/events/${eventId}/comments`,
)

const comments = createCrudResource<
    CommentResponse,
    CommentUpdateRequest,
    CommentUpdateRequest,
    CommentResponse,
    CommentResponse,
    NoContent
>('/events/comments')

/**
 * The thread under one of the station's own appointments.
 *
 * <p>With a date, the thread is that one occurrence of a repeating appointment: it lists the comments
 * written on that date and a new comment carries it. Without one it is the whole appointment.
 */
export function eventCommentSource(eventId: number, eventDate?: string | null): CommentSource {
    return {
        list: () => eventComments.list(eventId, {date: eventDate}),
        create: (parentId, content) => eventComments.create(eventId, {parentId, content, eventDate: eventDate ?? undefined}),
        update: (commentId, content) => comments.update(commentId, {content}),
        remove: commentId => comments.remove(commentId),
        mentionables: () => stationMentionables({type: 'EVENT_VIEW', entityId: eventId}),
        moderator: Permission.EVENT_MANAGER,
        eventId,
    }
}

/**
 * The thread under an appointment a partner station shares. A mention offers this station's own
 * members, who are the ones reading along here.
 */
export function partnerEventCommentSource(stationUid: string, eventId: number): CommentSource {
    const base = `/federated/${stationUid}/events`
    return {
        list: async () => (await client.get<CommentResponse[]>(`${base}/${eventId}/comments`)).data,
        create: (parentId, content) => client.post(`${base}/${eventId}/comments`, {parentId, content}),
        update: (commentId, content) => client.put(`${base}/comments/${commentId}`, {content}),
        remove: commentId => client.delete(`${base}/comments/${commentId}`),
        mentionables: async () => ({members: await listCompletions(), groups: []}),
        moderator: null,
    }
}

export async function getNote(entityType: string, entityId: number): Promise<EntityNote | null> {
    try {
        const res = await client.get<EntityNote>(`/notes/${entityType}/${entityId}`)
        return res.data
    } catch (e) {
        if (apiErrorStatus(e) === 404) return null
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

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource, createScopedCrudResource } from './crud'
import { uploadFile } from './upload'
import { downloadAuthed } from '@/util/downloadAuthed'
import type { MemberIdentity } from './types'

// -- Types --

export const TicketPriority = {
    LOWEST: 'LOWEST',
    LOW: 'LOW',
    MEDIUM: 'MEDIUM',
    HIGH: 'HIGH',
    HIGHEST: 'HIGHEST',
} as const
export type TicketPriorityName = (typeof TicketPriority)[keyof typeof TicketPriority]

export const LinkType = {
    RELATES_TO: 'RELATES_TO',
    BLOCKS: 'BLOCKS',
    BLOCKED_BY: 'BLOCKED_BY',
    CAUSES: 'CAUSES',
    CAUSED_BY: 'CAUSED_BY',
} as const
export type LinkTypeName = (typeof LinkType)[keyof typeof LinkType]

export const LanePreset = {
    SIMPLE: 'SIMPLE',
    FEEDBACK: 'FEEDBACK',
} as const
export type LanePresetName = (typeof LanePreset)[keyof typeof LanePreset]

export interface Board {
    id: number
    stationId: number
    name: string
    description: string | null
    shortKey: string
    hideDoneAfterDays: number
    ticketCounter: number
    backlogLaneId: number | null
    createdAt: string
}

export interface BoardLane {
    id: number
    boardId: number
    name: string
    color: string | null
    position: number
}

export interface BoardFieldConfig {
    required: boolean
    options: string[]
    laneId?: number | null
}

export interface BoardField {
    id: number
    boardId: number
    name: string
    fieldType: string
    config: BoardFieldConfig
    position: number
}

export interface BoardTicket {
    id: number
    boardId: number
    laneId: number
    ticketNumber: number
    title: string
    description?: string | null
    assignee: MemberIdentity | null
    priority: TicketPriorityName
    dueDate: string | null
    position: number
    creator?: MemberIdentity | null
    createdAt?: string
    updatedAt?: string
    laneEnteredAt: string
    checklistTotal: number
    checklistChecked: number
    attachmentCount: number
}

export interface BoardTicketLink {
    ticketId: number
    linkedTicketId: number
    linkType: LinkTypeName
}

export interface BoardTicketTransition {
    id: number
    ticketId: number
    fromLaneId: number | null
    toLaneId: number | null
    actor: MemberIdentity | null
    actorName: string | null
    movedAt: string
}

export interface BoardChecklistItem {
    id: number
    ticketId: number
    title: string
    checked: boolean
    position: number
}

export interface BoardComment {
    id: number
    ticketId: number
    parentId: number | null
    author: MemberIdentity | null
    content: string
    deleted: boolean
    createdAt: string
    updatedAt: string | null
}

export interface AccessConfig {
    userTypes: string[]
    groupIds: number[]
    tagIds: number[]
}

// -- Board CRUD --

interface BoardCreateRequest {
    name: string
    description?: string
    shortKey: string
    preset?: LanePresetName
}

interface BoardUpdateRequest {
    name: string
    description: string
    hideDoneAfterDays: number
}

const boards = createCrudResource<
    Board,
    BoardCreateRequest,
    BoardUpdateRequest,
    Board,
    Board,
    Board,
    string
>('/boards')

export async function listBoards(visibleOnly?: boolean): Promise<Board[]> {
    return boards.list(visibleOnly ? { visible: 'true' } : undefined)
}

export const getBoard = boards.get
export const createBoard = boards.create
export const updateBoard = boards.update
export const deleteBoard = boards.remove

export async function canEditBoard(boardKey: string): Promise<boolean> {
    const res = await client.get<{ canEdit: boolean }>(`/boards/${boardKey}/can-edit`)
    return res.data.canEdit
}

// -- Members --

export async function getBoardMembers(boardKey: string): Promise<import('./stationMembers').MemberCompletion[]> {
    const res = await client.get<import('./stationMembers').MemberCompletion[]>(`/boards/${boardKey}/members`)
    return res.data
}

/**
 * Whom a ticket on this board may be handed to, which is narrower than the station's members. The
 * full list stays in use for reading names off tickets and comments, so somebody who was assigned
 * a ticket before losing their write access keeps their name on it.
 */
export async function getAssignableMembers(boardKey: string): Promise<import('./stationMembers').MemberCompletion[]> {
    const res = await client.get<import('./stationMembers').MemberCompletion[]>(`/boards/${boardKey}/assignable-members`)
    return res.data
}

// -- Lanes --

export async function getLanes(boardKey: string): Promise<BoardLane[]> {
    const res = await client.get<BoardLane[]>(`/boards/${boardKey}/lanes`)
    return res.data
}

export async function setLanes(
    boardKey: string,
    lanes: { id?: number | null; name: string; color?: string | null }[],
): Promise<BoardLane[]> {
    const res = await client.put<BoardLane[]>(`/boards/${boardKey}/lanes`, lanes)
    return res.data
}

// -- Labels --

export interface BoardLabel {
    id: number
    boardId: number
    name: string
    color: string
}

interface LabelCreateRequest {
    name: string
    color?: string
}

interface LabelUpdateRequest {
    name: string
    color: string
}

const labels = createScopedCrudResource<
    BoardLabel,
    LabelCreateRequest,
    LabelUpdateRequest,
    BoardLabel,
    BoardLabel,
    void,
    number,
    string
>((boardKey: string) => `/boards/${boardKey}/labels`)

export const getLabels = labels.list
export const createLabel = labels.create
export const updateLabel = labels.update
export const deleteLabel = labels.remove

export async function getTicketLabels(boardKey: string, ticketNumber: number): Promise<BoardLabel[]> {
    const res = await client.get<BoardLabel[]>(`/boards/${boardKey}/tickets/${ticketNumber}/labels`)
    return res.data
}

export async function addTicketLabel(boardKey: string, ticketNumber: number, labelId: number): Promise<BoardLabel[]> {
    const res = await client.post<BoardLabel[]>(`/boards/${boardKey}/tickets/${ticketNumber}/labels/${labelId}`)
    return res.data
}

export async function removeTicketLabel(boardKey: string, ticketNumber: number, labelId: number): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/labels/${labelId}`)
}

export async function getAllTicketLabels(boardKey: string): Promise<{ ticketId: number; labelId: number }[]> {
    const res = await client.get<{ ticketId: number; labelId: number }[]>(`/boards/${boardKey}/ticket-labels`)
    return res.data
}

// -- Backlog --

export async function enableBacklog(boardKey: string): Promise<BoardLane> {
    const res = await client.post<BoardLane>(`/boards/${boardKey}/backlog`)
    return res.data
}

export async function disableBacklog(boardKey: string): Promise<void> {
    await client.delete(`/boards/${boardKey}/backlog`)
}

// -- Fields --

export async function getFields(boardKey: string): Promise<BoardField[]> {
    const res = await client.get<BoardField[]>(`/boards/${boardKey}/fields`)
    return res.data
}

export async function setFields(
    boardKey: string,
    fields: { name: string; fieldType: string; config?: BoardFieldConfig }[],
): Promise<BoardField[]> {
    const res = await client.put<BoardField[]>(`/boards/${boardKey}/fields`, fields)
    return res.data
}

// -- Federation config (owning station) --

export interface FederationTarget {
    partnerId: number
    shareMode: 'READ_ONLY' | 'FULL'
    requiredRole: string
}

export interface BoardFederationConfig {
    targets: FederationTarget[]
    editUserTypes: string[]
}

export async function getBoardFederationConfig(boardKey: string): Promise<BoardFederationConfig> {
    const res = await client.get<BoardFederationConfig>(`/boards/${boardKey}/federation`)
    return res.data
}

export async function setBoardFederationConfig(boardKey: string, data: BoardFederationConfig): Promise<void> {
    await client.put(`/boards/${boardKey}/federation`, data)
}

// -- Access --

export async function getViewAccess(boardKey: string): Promise<AccessConfig> {
    const res = await client.get<AccessConfig>(`/boards/${boardKey}/access/view`)
    return res.data
}

export async function setViewAccess(boardKey: string, data: AccessConfig): Promise<void> {
    await client.put(`/boards/${boardKey}/access/view`, data)
}

export async function getEditAccess(boardKey: string): Promise<AccessConfig> {
    const res = await client.get<AccessConfig>(`/boards/${boardKey}/access/edit`)
    return res.data
}

export async function setEditAccess(boardKey: string, data: AccessConfig): Promise<void> {
    await client.put(`/boards/${boardKey}/access/edit`, data)
}

// -- Tickets --

export async function searchTickets(boardKey: string, query: string): Promise<BoardTicket[]> {
    const res = await client.get<BoardTicket[]>(`/boards/${boardKey}/tickets/search`, {
        params: { q: query },
    })
    return res.data
}

interface TicketCreateRequest {
    laneId: number
    title: string
    description?: string
    assignedMemberId?: number | null
    priority?: TicketPriorityName
    dueDate?: string | null
}

interface TicketUpdateRequest {
    title: string
    description?: string | null
    assignedMemberId?: number | null
    priority: TicketPriorityName
    dueDate?: string | null
}

const tickets = createScopedCrudResource<
    BoardTicket,
    TicketCreateRequest,
    TicketUpdateRequest,
    BoardTicket,
    BoardTicket,
    BoardTicket,
    number,
    string
>((boardKey: string) => `/boards/${boardKey}/tickets`)

export const listTickets = tickets.list
export const getTicket = tickets.get
export const createTicket = tickets.create
export const updateTicket = tickets.update
export const deleteTicket = tickets.remove

export async function assignTicket(
    boardKey: string,
    ticketNumber: number,
    assignedMemberId: number | null,
): Promise<BoardTicket> {
    const res = await client.put<BoardTicket>(`/boards/${boardKey}/tickets/${ticketNumber}/assign`, { assignedMemberId })
    return res.data
}

export async function moveTicket(
    boardKey: string,
    ticketNumber: number,
    data: { toLaneId: number; position: number },
): Promise<BoardTicket> {
    const res = await client.put<BoardTicket>(`/boards/${boardKey}/tickets/${ticketNumber}/move`, data)
    return res.data
}

export async function reorderTickets(
    boardKey: string,
    ticketNumber: number,
    data: { laneId: number; orderedIds: number[] },
): Promise<void> {
    await client.put(`/boards/${boardKey}/tickets/${ticketNumber}/reorder`, data)
}

// -- Links --

export async function getLinks(boardKey: string, ticketNumber: number): Promise<BoardTicketLink[]> {
    const res = await client.get<BoardTicketLink[]>(
        `/boards/${boardKey}/tickets/${ticketNumber}/links`,
    )
    return res.data
}

export async function createLink(
    boardKey: string,
    ticketNumber: number,
    data: { linkedTicketId: number; linkType: LinkTypeName },
): Promise<BoardTicketLink[]> {
    const res = await client.post<BoardTicketLink[]>(
        `/boards/${boardKey}/tickets/${ticketNumber}/links`,
        data,
    )
    return res.data
}

export async function deleteLink(
    boardKey: string,
    ticketNumber: number,
    linkedId: number,
): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/links/${linkedId}`)
}

// -- Checklist --

export async function getChecklist(
    boardKey: string,
    ticketNumber: number,
): Promise<BoardChecklistItem[]> {
    const res = await client.get<BoardChecklistItem[]>(
        `/boards/${boardKey}/tickets/${ticketNumber}/checklist`,
    )
    return res.data
}

export async function addChecklistItem(
    boardKey: string,
    ticketNumber: number,
    data: { title: string },
): Promise<BoardChecklistItem> {
    const res = await client.post<BoardChecklistItem>(
        `/boards/${boardKey}/tickets/${ticketNumber}/checklist`,
        data,
    )
    return res.data
}

export async function updateChecklistItem(
    boardKey: string,
    ticketNumber: number,
    itemId: number,
    data: { title: string; checked: boolean },
): Promise<void> {
    await client.put(`/boards/${boardKey}/tickets/${ticketNumber}/checklist/${itemId}`, data)
}

export async function deleteChecklistItem(
    boardKey: string,
    ticketNumber: number,
    itemId: number,
): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/checklist/${itemId}`)
}

export async function reorderChecklist(
    boardKey: string,
    ticketNumber: number,
    data: { orderedIds: number[] },
): Promise<void> {
    await client.put(`/boards/${boardKey}/tickets/${ticketNumber}/checklist/reorder`, data)
}

// -- Transitions --

export async function getTransitions(
    boardKey: string,
    ticketNumber: number,
): Promise<BoardTicketTransition[]> {
    const res = await client.get<BoardTicketTransition[]>(
        `/boards/${boardKey}/tickets/${ticketNumber}/transitions`,
    )
    return res.data
}

// -- Comments --

export async function getComments(
    boardKey: string,
    ticketNumber: number,
): Promise<BoardComment[]> {
    const res = await client.get<BoardComment[]>(
        `/boards/${boardKey}/tickets/${ticketNumber}/comments`,
    )
    return res.data
}

export async function createComment(
    boardKey: string,
    ticketNumber: number,
    data: { parentId?: number | null; content: string },
): Promise<BoardComment> {
    const res = await client.post<BoardComment>(
        `/boards/${boardKey}/tickets/${ticketNumber}/comments`,
        data,
    )
    return res.data
}

export async function updateComment(
    boardKey: string,
    ticketNumber: number,
    commentId: number,
    data: { content: string },
): Promise<void> {
    await client.put(`/boards/${boardKey}/tickets/${ticketNumber}/comments/${commentId}`, data)
}

export async function deleteComment(
    boardKey: string,
    ticketNumber: number,
    commentId: number,
): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/comments/${commentId}`)
}

// -- Weblinks --

export interface BoardWeblink {
    id: number
    ticketId: number
    url: string
    title: string
    position: number
}

export async function getWeblinks(boardKey: string, ticketNumber: number): Promise<BoardWeblink[]> {
    const res = await client.get<BoardWeblink[]>(`/boards/${boardKey}/tickets/${ticketNumber}/weblinks`)
    return res.data
}

export async function addWeblink(
    boardKey: string,
    ticketNumber: number,
    data: { url: string; title?: string },
): Promise<BoardWeblink> {
    const res = await client.post<BoardWeblink>(
        `/boards/${boardKey}/tickets/${ticketNumber}/weblinks`,
        data,
    )
    return res.data
}

export async function deleteWeblink(
    boardKey: string,
    ticketNumber: number,
    weblinkId: number,
): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/weblinks/${weblinkId}`)
}

// -- Attachments --

export interface BoardTicketAttachment {
    id: number
    ticketId: number
    filename: string
    originalName: string
    contentType: string
    sizeBytes: number
    /** Federation-safe author identity: station UUID + member UUID within that station. */
    uploaderStationUid: string
    uploaderMemberUid: string
    createdAt: string
}

export async function getAttachments(boardKey: string, ticketNumber: number): Promise<BoardTicketAttachment[]> {
    const res = await client.get<BoardTicketAttachment[]>(`/boards/${boardKey}/tickets/${ticketNumber}/attachments`)
    return res.data
}

export async function uploadAttachment(
    boardKey: string,
    ticketNumber: number,
    file: File,
): Promise<BoardTicketAttachment> {
    return uploadFile<BoardTicketAttachment>(`/boards/${boardKey}/tickets/${ticketNumber}/attachments`, { file })
}

export async function downloadAttachmentBlob(boardKey: string, ticketNumber: number, attachmentId: number, filename: string): Promise<void> {
    await downloadAuthed(`/boards/${boardKey}/tickets/${ticketNumber}/attachments/${attachmentId}/download`, filename)
}

export async function getAttachmentBlobUrl(boardKey: string, ticketNumber: number, attachmentId: number): Promise<string> {
    const res = await client.get(`/boards/${boardKey}/tickets/${ticketNumber}/attachments/${attachmentId}/download`, { responseType: 'blob' })
    return URL.createObjectURL(res.data as Blob)
}

export async function getAttachmentText(boardKey: string, ticketNumber: number, attachmentId: number): Promise<string> {
    const res = await client.get(`/boards/${boardKey}/tickets/${ticketNumber}/attachments/${attachmentId}/download`, { responseType: 'text' })
    return res.data as string
}

export async function deleteAttachment(
    boardKey: string,
    ticketNumber: number,
    attachmentId: number,
): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/attachments/${attachmentId}`)
}

// -- Watchers --

export async function getWatchers(boardKey: string, ticketNumber: number): Promise<number[]> {
    const res = await client.get<number[]>(`/boards/${boardKey}/tickets/${ticketNumber}/watchers`)
    return res.data
}

export async function watchTicket(boardKey: string, ticketNumber: number): Promise<void> {
    await client.post(`/boards/${boardKey}/tickets/${ticketNumber}/watch`)
}

export async function unwatchTicket(boardKey: string, ticketNumber: number): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/watch`)
}

// -- KB Links --

export interface BoardTicketKbLink {
    id: number
    ticketId: number
    kbFileId: number
    title: string
    folderPath: string
}

export async function getKbLinks(boardKey: string, ticketNumber: number): Promise<BoardTicketKbLink[]> {
    const res = await client.get<BoardTicketKbLink[]>(`/boards/${boardKey}/tickets/${ticketNumber}/kb-links`)
    return res.data
}

export async function addKbLink(boardKey: string, ticketNumber: number, kbFileId: number): Promise<void> {
    await client.post(`/boards/${boardKey}/tickets/${ticketNumber}/kb-links/${kbFileId}`)
}

export async function removeKbLink(boardKey: string, ticketNumber: number, linkId: number): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/kb-links/${linkId}`)
}

// -- History --

export interface BoardTicketHistoryEntry {
    id: number
    ticketId: number
    action: string
    detail: string | null
    actor: MemberIdentity | null
    actorName: string | null
    createdAt: string
}

export async function getHistory(boardKey: string, ticketNumber: number): Promise<BoardTicketHistoryEntry[]> {
    const res = await client.get<BoardTicketHistoryEntry[]>(`/boards/${boardKey}/tickets/${ticketNumber}/history`)
    return res.data
}

// -- Field values --

export const BoardFieldType = {
    STRING: 'STRING',
    NUMBER: 'NUMBER',
    BOOLEAN: 'BOOLEAN',
    ENUM: 'ENUM',
    DATE: 'DATE',
    LANE_ASSIGNEE: 'LANE_ASSIGNEE',
} as const
export type BoardFieldTypeName = (typeof BoardFieldType)[keyof typeof BoardFieldType]

export interface BoardTicketFieldValue {
    ticketId: number
    fieldId: number
    fieldType: BoardFieldTypeName
    value: { value?: unknown; memberId?: number } | null
}

export async function getFieldValues(boardKey: string, ticketNumber: number): Promise<BoardTicketFieldValue[]> {
    const res = await client.get<BoardTicketFieldValue[]>(`/boards/${boardKey}/tickets/${ticketNumber}/fields`)
    return res.data
}

export function buildFieldValueBody(fieldType: BoardFieldTypeName, rawValue: unknown): Record<string, unknown> {
    if (fieldType === BoardFieldType.LANE_ASSIGNEE) return { memberId: Number(rawValue) }
    return { value: rawValue }
}

export async function setFieldValue(
    boardKey: string,
    ticketNumber: number,
    fieldId: number,
    fieldType: BoardFieldTypeName,
    rawValue: unknown,
): Promise<void> {
    await client.put(`/boards/${boardKey}/tickets/${ticketNumber}/fields/${fieldId}`, buildFieldValueBody(fieldType, rawValue))
}

export async function deleteFieldValue(
    boardKey: string,
    ticketNumber: number,
    fieldId: number,
): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/fields/${fieldId}`)
}

// -- Activity --

export interface ActivityEntry {
    type: 'comment' | 'transition'
    id: number
    timestamp: string
}

export async function getActivity(
    boardKey: string,
    ticketNumber: number,
): Promise<ActivityEntry[]> {
    const res = await client.get<ActivityEntry[]>(
        `/boards/${boardKey}/tickets/${ticketNumber}/activity`,
    )
    return res.data
}

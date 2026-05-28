/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

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
    assignedMemberId: number | null
    priority: TicketPriorityName
    dueDate: string | null
    position: number
    createdBy?: number
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
    movedBy: number
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
    authorId: number
    content: string
    deleted: boolean
    createdAt: string
    updatedAt: string | null
}

export interface AccessConfig {
    roleIds: number[]
    groupIds: number[]
    tagIds: number[]
}

// -- Board CRUD --

export async function listBoards(visibleOnly?: boolean): Promise<Board[]> {
    const res = await client.get<Board[]>('/boards', { params: visibleOnly ? { visible: 'true' } : undefined })
    return res.data
}

export async function getBoard(id: number): Promise<Board> {
    const res = await client.get<Board>(`/boards/${id}`)
    return res.data
}

export async function canEditBoard(id: number): Promise<boolean> {
    const res = await client.get<{ canEdit: boolean }>(`/boards/${id}/can-edit`)
    return res.data.canEdit
}

export async function createBoard(data: {
    name: string
    description?: string
    shortKey: string
    preset?: LanePresetName
}): Promise<Board> {
    const res = await client.post<Board>('/boards', data)
    return res.data
}

export async function updateBoard(
    id: number,
    data: { name: string; description: string; hideDoneAfterDays: number },
): Promise<Board> {
    const res = await client.put<Board>(`/boards/${id}`, data)
    return res.data
}

export async function deleteBoard(id: number): Promise<void> {
    await client.delete(`/boards/${id}`)
}

// -- Lanes --

export async function getLanes(boardId: number): Promise<BoardLane[]> {
    const res = await client.get<BoardLane[]>(`/boards/${boardId}/lanes`)
    return res.data
}

export async function setLanes(
    boardId: number,
    lanes: { name: string; color?: string | null }[],
): Promise<BoardLane[]> {
    const res = await client.put<BoardLane[]>(`/boards/${boardId}/lanes`, lanes)
    return res.data
}

// -- Labels --

export interface BoardLabel {
    id: number
    boardId: number
    name: string
    color: string
}

export async function getLabels(boardId: number): Promise<BoardLabel[]> {
    const res = await client.get<BoardLabel[]>(`/boards/${boardId}/labels`)
    return res.data
}

export async function createLabel(boardId: number, data: { name: string; color?: string }): Promise<BoardLabel> {
    const res = await client.post<BoardLabel>(`/boards/${boardId}/labels`, data)
    return res.data
}

export async function updateLabel(boardId: number, labelId: number, data: { name: string; color: string }): Promise<void> {
    await client.put(`/boards/${boardId}/labels/${labelId}`, data)
}

export async function deleteLabel(boardId: number, labelId: number): Promise<void> {
    await client.delete(`/boards/${boardId}/labels/${labelId}`)
}

export async function getTicketLabels(boardId: number, ticketId: number): Promise<BoardLabel[]> {
    const res = await client.get<BoardLabel[]>(`/boards/${boardId}/tickets/${ticketId}/labels`)
    return res.data
}

export async function addTicketLabel(boardId: number, ticketId: number, labelId: number): Promise<BoardLabel[]> {
    const res = await client.post<BoardLabel[]>(`/boards/${boardId}/tickets/${ticketId}/labels/${labelId}`)
    return res.data
}

export async function removeTicketLabel(boardId: number, ticketId: number, labelId: number): Promise<void> {
    await client.delete(`/boards/${boardId}/tickets/${ticketId}/labels/${labelId}`)
}

export async function getAllTicketLabels(boardId: number): Promise<{ ticketId: number; labelId: number }[]> {
    const res = await client.get<{ ticketId: number; labelId: number }[]>(`/boards/${boardId}/ticket-labels`)
    return res.data
}

// -- Backlog --

export async function enableBacklog(boardId: number): Promise<BoardLane> {
    const res = await client.post<BoardLane>(`/boards/${boardId}/backlog`)
    return res.data
}

export async function disableBacklog(boardId: number): Promise<void> {
    await client.delete(`/boards/${boardId}/backlog`)
}

// -- Fields --

export async function getFields(boardId: number): Promise<BoardField[]> {
    const res = await client.get<BoardField[]>(`/boards/${boardId}/fields`)
    return res.data
}

export async function setFields(
    boardId: number,
    fields: { name: string; fieldType: string; config?: BoardFieldConfig }[],
): Promise<BoardField[]> {
    const res = await client.put<BoardField[]>(`/boards/${boardId}/fields`, fields)
    return res.data
}

// -- Federation config (owning station) --

export interface FederationTarget {
    partnerId: number
    shareMode: 'READ_ONLY' | 'FULL'
}

export interface BoardFederationConfig {
    targets: FederationTarget[]
    editRoleIds: number[]
}

export async function getBoardFederationConfig(boardId: number): Promise<BoardFederationConfig> {
    const res = await client.get<BoardFederationConfig>(`/boards/${boardId}/federation`)
    return res.data
}

export async function setBoardFederationConfig(boardId: number, data: BoardFederationConfig): Promise<void> {
    await client.put(`/boards/${boardId}/federation`, data)
}

// -- Access --

export async function getViewAccess(boardId: number): Promise<AccessConfig> {
    const res = await client.get<AccessConfig>(`/boards/${boardId}/access/view`)
    return res.data
}

export async function setViewAccess(boardId: number, data: AccessConfig): Promise<void> {
    await client.put(`/boards/${boardId}/access/view`, data)
}

export async function getEditAccess(boardId: number): Promise<AccessConfig> {
    const res = await client.get<AccessConfig>(`/boards/${boardId}/access/edit`)
    return res.data
}

export async function setEditAccess(boardId: number, data: AccessConfig): Promise<void> {
    await client.put(`/boards/${boardId}/access/edit`, data)
}

// -- Tickets --

export async function searchTickets(boardId: number, query: string): Promise<BoardTicket[]> {
    const res = await client.get<BoardTicket[]>(`/boards/${boardId}/tickets/search`, {
        params: { q: query },
    })
    return res.data
}

export async function listTickets(boardId: number): Promise<BoardTicket[]> {
    const res = await client.get<BoardTicket[]>(`/boards/${boardId}/tickets`)
    return res.data
}

export async function getTicket(boardId: number, ticketId: number): Promise<BoardTicket> {
    const res = await client.get<BoardTicket>(`/boards/${boardId}/tickets/${ticketId}`)
    return res.data
}

export async function createTicket(
    boardId: number,
    data: {
        laneId: number
        title: string
        description?: string
        assignedMemberId?: number | null
        priority?: TicketPriorityName
        dueDate?: string | null
    },
): Promise<BoardTicket> {
    const res = await client.post<BoardTicket>(`/boards/${boardId}/tickets`, data)
    return res.data
}

export async function updateTicket(
    boardId: number,
    ticketId: number,
    data: {
        title: string
        description?: string | null
        assignedMemberId?: number | null
        priority: TicketPriorityName
        dueDate?: string | null
    },
): Promise<BoardTicket> {
    const res = await client.put<BoardTicket>(`/boards/${boardId}/tickets/${ticketId}`, data)
    return res.data
}

export async function deleteTicket(boardId: number, ticketId: number): Promise<void> {
    await client.delete(`/boards/${boardId}/tickets/${ticketId}`)
}

export async function assignTicket(
    boardId: number,
    ticketId: number,
    assignedMemberId: number | null,
): Promise<BoardTicket> {
    const res = await client.put<BoardTicket>(`/boards/${boardId}/tickets/${ticketId}/assign`, { assignedMemberId })
    return res.data
}

export async function moveTicket(
    boardId: number,
    ticketId: number,
    data: { toLaneId: number; position: number },
): Promise<BoardTicket> {
    const res = await client.put<BoardTicket>(`/boards/${boardId}/tickets/${ticketId}/move`, data)
    return res.data
}

export async function reorderTickets(
    boardId: number,
    ticketId: number,
    data: { laneId: number; orderedIds: number[] },
): Promise<void> {
    await client.put(`/boards/${boardId}/tickets/${ticketId}/reorder`, data)
}

// -- Links --

export async function getLinks(boardId: number, ticketId: number): Promise<BoardTicketLink[]> {
    const res = await client.get<BoardTicketLink[]>(
        `/boards/${boardId}/tickets/${ticketId}/links`,
    )
    return res.data
}

export async function createLink(
    boardId: number,
    ticketId: number,
    data: { linkedTicketId: number; linkType: LinkTypeName },
): Promise<BoardTicketLink[]> {
    const res = await client.post<BoardTicketLink[]>(
        `/boards/${boardId}/tickets/${ticketId}/links`,
        data,
    )
    return res.data
}

export async function deleteLink(
    boardId: number,
    ticketId: number,
    linkedId: number,
): Promise<void> {
    await client.delete(`/boards/${boardId}/tickets/${ticketId}/links/${linkedId}`)
}

// -- Checklist --

export async function getChecklist(
    boardId: number,
    ticketId: number,
): Promise<BoardChecklistItem[]> {
    const res = await client.get<BoardChecklistItem[]>(
        `/boards/${boardId}/tickets/${ticketId}/checklist`,
    )
    return res.data
}

export async function addChecklistItem(
    boardId: number,
    ticketId: number,
    data: { title: string },
): Promise<BoardChecklistItem> {
    const res = await client.post<BoardChecklistItem>(
        `/boards/${boardId}/tickets/${ticketId}/checklist`,
        data,
    )
    return res.data
}

export async function updateChecklistItem(
    boardId: number,
    ticketId: number,
    itemId: number,
    data: { title: string; checked: boolean },
): Promise<void> {
    await client.put(`/boards/${boardId}/tickets/${ticketId}/checklist/${itemId}`, data)
}

export async function deleteChecklistItem(
    boardId: number,
    ticketId: number,
    itemId: number,
): Promise<void> {
    await client.delete(`/boards/${boardId}/tickets/${ticketId}/checklist/${itemId}`)
}

export async function reorderChecklist(
    boardId: number,
    ticketId: number,
    data: { orderedIds: number[] },
): Promise<void> {
    await client.put(`/boards/${boardId}/tickets/${ticketId}/checklist/reorder`, data)
}

// -- Transitions --

export async function getTransitions(
    boardId: number,
    ticketId: number,
): Promise<BoardTicketTransition[]> {
    const res = await client.get<BoardTicketTransition[]>(
        `/boards/${boardId}/tickets/${ticketId}/transitions`,
    )
    return res.data
}

// -- Comments --

export async function getComments(
    boardId: number,
    ticketId: number,
): Promise<BoardComment[]> {
    const res = await client.get<BoardComment[]>(
        `/boards/${boardId}/tickets/${ticketId}/comments`,
    )
    return res.data
}

export async function createComment(
    boardId: number,
    ticketId: number,
    data: { parentId?: number | null; content: string },
): Promise<BoardComment> {
    const res = await client.post<BoardComment>(
        `/boards/${boardId}/tickets/${ticketId}/comments`,
        data,
    )
    return res.data
}

export async function updateComment(
    boardId: number,
    ticketId: number,
    commentId: number,
    data: { content: string },
): Promise<void> {
    await client.put(`/boards/${boardId}/tickets/${ticketId}/comments/${commentId}`, data)
}

export async function deleteComment(
    boardId: number,
    ticketId: number,
    commentId: number,
): Promise<void> {
    await client.delete(`/boards/${boardId}/tickets/${ticketId}/comments/${commentId}`)
}

// -- Weblinks --

export interface BoardWeblink {
    id: number
    ticketId: number
    url: string
    title: string
    position: number
}

export async function getWeblinks(boardId: number, ticketId: number): Promise<BoardWeblink[]> {
    const res = await client.get<BoardWeblink[]>(`/boards/${boardId}/tickets/${ticketId}/weblinks`)
    return res.data
}

export async function addWeblink(
    boardId: number,
    ticketId: number,
    data: { url: string; title?: string },
): Promise<BoardWeblink> {
    const res = await client.post<BoardWeblink>(
        `/boards/${boardId}/tickets/${ticketId}/weblinks`,
        data,
    )
    return res.data
}

export async function deleteWeblink(
    boardId: number,
    ticketId: number,
    weblinkId: number,
): Promise<void> {
    await client.delete(`/boards/${boardId}/tickets/${ticketId}/weblinks/${weblinkId}`)
}

// -- Attachments --

export interface BoardTicketAttachment {
    id: number
    ticketId: number
    filename: string
    originalName: string
    contentType: string
    sizeBytes: number
    uploadedBy: number
    createdAt: string
}

export async function getAttachments(boardId: number, ticketId: number): Promise<BoardTicketAttachment[]> {
    const res = await client.get<BoardTicketAttachment[]>(`/boards/${boardId}/tickets/${ticketId}/attachments`)
    return res.data
}

export async function uploadAttachment(
    boardId: number,
    ticketId: number,
    file: File,
): Promise<BoardTicketAttachment> {
    const formData = new FormData()
    formData.append('file', file)
    const res = await client.post<BoardTicketAttachment>(
        `/boards/${boardId}/tickets/${ticketId}/attachments`,
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } },
    )
    return res.data
}

export async function downloadAttachmentBlob(boardId: number, ticketId: number, attachmentId: number, filename: string): Promise<void> {
    const res = await client.get(`/boards/${boardId}/tickets/${ticketId}/attachments/${attachmentId}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url; a.download = filename; a.click()
    URL.revokeObjectURL(url)
}

export async function getAttachmentBlobUrl(boardId: number, ticketId: number, attachmentId: number): Promise<string> {
    const res = await client.get(`/boards/${boardId}/tickets/${ticketId}/attachments/${attachmentId}/download`, { responseType: 'blob' })
    return URL.createObjectURL(res.data as Blob)
}

export async function getAttachmentText(boardId: number, ticketId: number, attachmentId: number): Promise<string> {
    const res = await client.get(`/boards/${boardId}/tickets/${ticketId}/attachments/${attachmentId}/download`, { responseType: 'text' })
    return res.data as string
}

export async function deleteAttachment(
    boardId: number,
    ticketId: number,
    attachmentId: number,
): Promise<void> {
    await client.delete(`/boards/${boardId}/tickets/${ticketId}/attachments/${attachmentId}`)
}

// -- Watchers --

export async function getWatchers(boardId: number, ticketId: number): Promise<number[]> {
    const res = await client.get<number[]>(`/boards/${boardId}/tickets/${ticketId}/watchers`)
    return res.data
}

export async function watchTicket(boardId: number, ticketId: number): Promise<void> {
    await client.post(`/boards/${boardId}/tickets/${ticketId}/watch`)
}

export async function unwatchTicket(boardId: number, ticketId: number): Promise<void> {
    await client.delete(`/boards/${boardId}/tickets/${ticketId}/watch`)
}

// -- KB Links --

export interface BoardTicketKbLink {
    id: number
    ticketId: number
    kbFileId: number
    title: string
    folderPath: string
}

export async function getKbLinks(boardId: number, ticketId: number): Promise<BoardTicketKbLink[]> {
    const res = await client.get<BoardTicketKbLink[]>(`/boards/${boardId}/tickets/${ticketId}/kb-links`)
    return res.data
}

export async function addKbLink(boardId: number, ticketId: number, kbFileId: number): Promise<void> {
    await client.post(`/boards/${boardId}/tickets/${ticketId}/kb-links/${kbFileId}`)
}

export async function removeKbLink(boardId: number, ticketId: number, linkId: number): Promise<void> {
    await client.delete(`/boards/${boardId}/tickets/${ticketId}/kb-links/${linkId}`)
}

// -- History --

export interface BoardTicketHistoryEntry {
    id: number
    ticketId: number
    action: string
    detail: string | null
    actorMemberId: number
    createdAt: string
}

export async function getHistory(boardId: number, ticketId: number): Promise<BoardTicketHistoryEntry[]> {
    const res = await client.get<BoardTicketHistoryEntry[]>(`/boards/${boardId}/tickets/${ticketId}/history`)
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

export async function getFieldValues(boardId: number, ticketId: number): Promise<BoardTicketFieldValue[]> {
    const res = await client.get<BoardTicketFieldValue[]>(`/boards/${boardId}/tickets/${ticketId}/fields`)
    return res.data
}

export function buildFieldValueBody(fieldType: BoardFieldTypeName, rawValue: unknown): Record<string, unknown> {
    if (fieldType === BoardFieldType.LANE_ASSIGNEE) return { memberId: Number(rawValue) }
    return { value: rawValue }
}

export async function setFieldValue(
    boardId: number,
    ticketId: number,
    fieldId: number,
    fieldType: BoardFieldTypeName,
    rawValue: unknown,
): Promise<void> {
    await client.put(`/boards/${boardId}/tickets/${ticketId}/fields/${fieldId}`, buildFieldValueBody(fieldType, rawValue))
}

export async function deleteFieldValue(
    boardId: number,
    ticketId: number,
    fieldId: number,
): Promise<void> {
    await client.delete(`/boards/${boardId}/tickets/${ticketId}/fields/${fieldId}`)
}

// -- Activity --

export interface ActivityEntry {
    type: 'comment' | 'transition'
    id: number
    timestamp: string
}

export async function getActivity(
    boardId: number,
    ticketId: number,
): Promise<ActivityEntry[]> {
    const res = await client.get<ActivityEntry[]>(
        `/boards/${boardId}/tickets/${ticketId}/activity`,
    )
    return res.data
}

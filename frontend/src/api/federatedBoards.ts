/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type { BoardTicket, BoardLane, BoardLabel, BoardField, BoardChecklistItem, BoardComment, BoardTicketLink, BoardTicketTransition, BoardTicketHistoryEntry, BoardTicketAttachment } from './boards'

// -- Types --

export const BoardShareMode = {
    READ_ONLY: 'READ_ONLY',
    FULL: 'FULL',
} as const
export type BoardShareModeName = (typeof BoardShareMode)[keyof typeof BoardShareMode]

export interface DiscoveredBoard {
    partnerId: number
    partnerStationUid: string
    remoteBoardId: number
    name: string
    shortKey: string
    description: string
    shareMode: BoardShareModeName
    partnerStationName: string
}

export interface FederatedBoardDetail {
    board: {
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
    stationName: string
    shareMode: BoardShareModeName
}

export interface FederatedBoardBookmark {
    id: number
    memberId: number
    partnerId: number
    partnerStationUid: string
    remoteBoardId: number
    remoteBoardName: string
    remoteBoardShortKey: string
    shareMode: BoardShareModeName
    createdAt: string
}

export interface FederatedWatchers {
    local: number[]
    federated: { ticketId: number; partnerId: number; remoteMemberId: string }[]
}

export interface FederatedBoardAccess {
    shareMode: BoardShareModeName
    editRoleIds: number[]
}

export interface LocalAccessOverride {
    view: { roleIds: number[]; groupIds: number[]; tagIds: number[] }
    edit: { roleIds: number[]; groupIds: number[]; tagIds: number[] }
}

// -- Discovery --

export async function discoverBoards(): Promise<DiscoveredBoard[]> {
    const res = await client.get<DiscoveredBoard[]>('/federated/boards')
    return res.data
}

// -- Bookmarks --

export async function listBookmarks(): Promise<FederatedBoardBookmark[]> {
    const res = await client.get<FederatedBoardBookmark[]>('/federated/boards/bookmarks')
    return res.data
}

export async function createBookmark(data: {
    partnerUid: string
    remoteBoardId: number
    remoteBoardName: string
    remoteBoardShortKey: string
    shareMode: BoardShareModeName
}): Promise<FederatedBoardBookmark> {
    const res = await client.post<FederatedBoardBookmark>('/federated/boards/bookmarks', data)
    return res.data
}

export async function deleteBookmark(bookmarkId: number): Promise<void> {
    await client.delete(`/federated/boards/bookmarks/${bookmarkId}`)
}

// -- Board read (proxied) --

export async function getBoard(partnerUid: string, boardKey: string): Promise<FederatedBoardDetail> {
    const res = await client.get<FederatedBoardDetail>(`/federated/boards/${partnerUid}/${boardKey}`)
    const data = res.data
    if (!data.stationName) {
        data.stationName = res.headers['x-federation-station-name'] ?? ''
    }
    return data
}

export async function getLanes(partnerUid: string, boardKey: string): Promise<BoardLane[]> {
    const res = await client.get<BoardLane[]>(`/federated/boards/${partnerUid}/${boardKey}/lanes`)
    return res.data
}

export async function getLabels(partnerUid: string, boardKey: string): Promise<BoardLabel[]> {
    const res = await client.get<BoardLabel[]>(`/federated/boards/${partnerUid}/${boardKey}/labels`)
    return res.data
}

export async function getAllTicketLabels(partnerUid: string, boardKey: string): Promise<{ ticketId: number; labelId: number }[]> {
    const res = await client.get<{ ticketId: number; labelId: number }[]>(`/federated/boards/${partnerUid}/${boardKey}/ticket-labels`)
    return res.data
}

export async function getFields(partnerUid: string, boardKey: string): Promise<BoardField[]> {
    const res = await client.get<BoardField[]>(`/federated/boards/${partnerUid}/${boardKey}/fields`)
    return res.data
}

export async function listTickets(partnerUid: string, boardKey: string): Promise<BoardTicket[]> {
    const res = await client.get<BoardTicket[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets`)
    return res.data
}

export async function searchTickets(partnerUid: string, boardKey: string, query: string): Promise<BoardTicket[]> {
    const res = await client.get<BoardTicket[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/search`, {
        params: { q: query },
    })
    return res.data
}

export async function getTicket(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardTicket> {
    const res = await client.get<BoardTicket>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}`)
    return res.data
}

export async function getComments(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardComment[]> {
    const res = await client.get<BoardComment[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/comments`)
    return res.data
}

export async function getChecklist(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardChecklistItem[]> {
    const res = await client.get<BoardChecklistItem[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/checklist`)
    return res.data
}

export async function getLinks(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardTicketLink[]> {
    const res = await client.get<BoardTicketLink[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/links`)
    return res.data
}

export async function getTicketLabels(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardLabel[]> {
    const res = await client.get<BoardLabel[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/labels`)
    return res.data
}

export async function getTransitions(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardTicketTransition[]> {
    const res = await client.get<BoardTicketTransition[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/transitions`)
    return res.data
}

export async function getHistory(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardTicketHistoryEntry[]> {
    const res = await client.get<BoardTicketHistoryEntry[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/history`)
    return res.data
}

export async function getAttachments(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardTicketAttachment[]> {
    const res = await client.get<BoardTicketAttachment[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/attachments`)
    return res.data
}

export async function getWatchers(partnerUid: string, boardKey: string, ticketNumber: number): Promise<FederatedWatchers> {
    const res = await client.get<FederatedWatchers>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/watchers`)
    return res.data
}

// -- Board write (FULL mode only) --

export async function createTicket(partnerUid: string, boardKey: string, data: {
    laneId?: number
    title: string
    description?: string
    priority?: string
    dueDate?: string
}): Promise<BoardTicket> {
    const res = await client.post<BoardTicket>(`/federated/boards/${partnerUid}/${boardKey}/tickets`, data)
    return res.data
}

export async function updateTicket(partnerUid: string, boardKey: string, ticketNumber: number, data: {
    title: string
    description: string | null
    assignedMemberId: number | null
    priority: string
    dueDate: string | null
}): Promise<BoardTicket> {
    const res = await client.put<BoardTicket>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}`, data)
    return res.data
}

export async function deleteTicket(partnerUid: string, boardKey: string, ticketNumber: number): Promise<void> {
    await client.delete(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}`)
}

export async function moveTicket(partnerUid: string, boardKey: string, ticketNumber: number, data: {
    toLaneId: number
    position: number
}): Promise<BoardTicket> {
    const res = await client.put<BoardTicket>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/move`, data)
    return res.data
}

export async function reorderTickets(partnerUid: string, boardKey: string, ticketNumber: number, data: {
    laneId: number
    orderedIds: number[]
}): Promise<void> {
    await client.put(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/reorder`, data)
}

export async function addComment(partnerUid: string, boardKey: string, ticketNumber: number, data: {
    parentId: number | null
    content: string
}): Promise<BoardComment> {
    const res = await client.post<BoardComment>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/comments`, data)
    return res.data
}

export async function addChecklistItem(partnerUid: string, boardKey: string, ticketNumber: number, data: {
    title: string
}): Promise<BoardChecklistItem> {
    const res = await client.post<BoardChecklistItem>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/checklist`, data)
    return res.data
}

export async function updateChecklistItem(partnerUid: string, boardKey: string, ticketNumber: number, itemId: number, data: {
    title: string
    checked: boolean
}): Promise<void> {
    await client.put(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/checklist/${itemId}`, data)
}

export async function deleteChecklistItem(partnerUid: string, boardKey: string, ticketNumber: number, itemId: number): Promise<void> {
    await client.delete(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/checklist/${itemId}`)
}

export async function addTicketLabel(partnerUid: string, boardKey: string, ticketNumber: number, labelId: number): Promise<BoardLabel[]> {
    const res = await client.post<BoardLabel[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/labels/${labelId}`)
    return res.data
}

export async function removeTicketLabel(partnerUid: string, boardKey: string, ticketNumber: number, labelId: number): Promise<void> {
    await client.delete(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/labels/${labelId}`)
}

export async function createLabel(partnerUid: string, boardKey: string, data: {
    name: string
    color?: string
}): Promise<BoardLabel> {
    const res = await client.post<BoardLabel>(`/federated/boards/${partnerUid}/${boardKey}/labels`, data)
    return res.data
}

export async function watchTicket(partnerUid: string, boardKey: string, ticketNumber: number): Promise<void> {
    await client.post(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/watch`)
}

export async function unwatchTicket(partnerUid: string, boardKey: string, ticketNumber: number): Promise<void> {
    await client.delete(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/watch`)
}

// -- Access override --

export async function getAccessOverride(partnerUid: string, boardKey: string): Promise<LocalAccessOverride> {
    const res = await client.get<LocalAccessOverride>(`/federated/boards/${partnerUid}/${boardKey}/access/override`)
    return res.data
}

export async function setAccessOverride(partnerUid: string, boardKey: string, data: {
    viewRoleIds: number[]
    viewGroupIds: number[]
    viewTagIds: number[]
    editRoleIds: number[]
    editGroupIds: number[]
    editTagIds: number[]
}): Promise<void> {
    await client.put(`/federated/boards/${partnerUid}/${boardKey}/access/override`, data)
}

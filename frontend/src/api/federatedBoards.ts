/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource } from './crud'
import { typedFields, type BoardTicketComment, type TypedBoardField } from './boards'
import type {
    AccessOverrideResponse,
    BoardChecklistItem,
    BoardComment,
    BoardField,
    BoardLabel,
    BoardLane,
    BoardTicket,
    BoardTicketAttachment,
    BoardTicketHistoryResponse,
    BoardTicketLink,
    BoardTicketTransitionResponse,
    components,
    DiscoveredBoard,
    EnrichedBookmark,
    FederatedBoardDetail,
    FederationBoardBookmark,
    LocalBookmarkRequest,
    LocalChecklistItemRequest,
    LocalCommentRequest,
    LocalCreateLabelRequest,
    LocalCreateTicketRequest,
    LocalLinkRequest,
    LocalMoveTicketRequest,
    LocalOverrideRequest,
    LocalReorderRequest,
    LocalUpdateChecklistItemRequest,
    LocalUpdateTicketRequest,
    MemberCompletion,
    TicketLabelMapping,
    TicketSummary,
} from './generated/schema'

export type BoardShareModeName = components['schemas']['BoardShareMode']

export const BoardShareMode = {
    READ_ONLY: 'READ_ONLY',
    FULL: 'FULL',
} as const satisfies Record<BoardShareModeName, BoardShareModeName>

export async function discoverBoards(): Promise<DiscoveredBoard[]> {
    const res = await client.get<DiscoveredBoard[]>('/federated/boards')
    return res.data
}

const bookmarks = createCrudResource<EnrichedBookmark, LocalBookmarkRequest, LocalBookmarkRequest, EnrichedBookmark, FederationBoardBookmark>(
    '/federated/boards/bookmarks',
)

export const listBookmarks = bookmarks.list
export const createBookmark = bookmarks.create
export const deleteBookmark = bookmarks.remove

export async function getBoardMembers(partnerUid: string, boardKey: string): Promise<MemberCompletion[]> {
    const res = await client.get<MemberCompletion[]>(`/federated/boards/${partnerUid}/${boardKey}/members`)
    return res.data
}

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

export async function getAllTicketLabels(partnerUid: string, boardKey: string): Promise<TicketLabelMapping[]> {
    const res = await client.get<TicketLabelMapping[]>(`/federated/boards/${partnerUid}/${boardKey}/ticket-labels`)
    return res.data
}

export async function getFields(partnerUid: string, boardKey: string): Promise<TypedBoardField[]> {
    const res = await client.get<BoardField[]>(`/federated/boards/${partnerUid}/${boardKey}/fields`)
    return typedFields(res.data)
}

export async function listTickets(partnerUid: string, boardKey: string): Promise<TicketSummary[]> {
    const res = await client.get<TicketSummary[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets`)
    return res.data
}

export async function searchTickets(partnerUid: string, boardKey: string, query: string): Promise<TicketSummary[]> {
    const res = await client.get<TicketSummary[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/search`, {
        params: { q: query },
    })
    return res.data
}

export async function getTicket(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardTicket> {
    const res = await client.get<BoardTicket>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}`)
    return res.data
}

export async function getComments(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardTicketComment[]> {
    const res = await client.get<BoardTicketComment[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/comments`)
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

export async function getTransitions(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardTicketTransitionResponse[]> {
    const res = await client.get<BoardTicketTransitionResponse[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/transitions`)
    return res.data
}

export async function getHistory(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardTicketHistoryResponse[]> {
    const res = await client.get<BoardTicketHistoryResponse[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/history`)
    return res.data
}

export async function getAttachments(partnerUid: string, boardKey: string, ticketNumber: number): Promise<BoardTicketAttachment[]> {
    const res = await client.get<BoardTicketAttachment[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/attachments`)
    return res.data
}

/** The partner station's members who watch the ticket. */
export async function getWatchers(partnerUid: string, boardKey: string, ticketNumber: number): Promise<number[]> {
    const res = await client.get<number[]>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/watchers`)
    return res.data
}

export async function createTicket(partnerUid: string, boardKey: string, data: LocalCreateTicketRequest): Promise<BoardTicket> {
    const res = await client.post<BoardTicket>(`/federated/boards/${partnerUid}/${boardKey}/tickets`, data)
    return res.data
}

export async function updateTicket(partnerUid: string, boardKey: string, ticketNumber: number, data: LocalUpdateTicketRequest): Promise<BoardTicket> {
    const res = await client.put<BoardTicket>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}`, data)
    return res.data
}

export async function deleteTicket(partnerUid: string, boardKey: string, ticketNumber: number): Promise<void> {
    await client.delete(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}`)
}

export async function moveTicket(partnerUid: string, boardKey: string, ticketNumber: number, data: LocalMoveTicketRequest): Promise<BoardTicket> {
    const res = await client.put<BoardTicket>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/move`, data)
    return res.data
}

export async function reorderTickets(partnerUid: string, boardKey: string, ticketNumber: number, data: LocalReorderRequest): Promise<void> {
    await client.put(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/reorder`, data)
}

export async function addComment(partnerUid: string, boardKey: string, ticketNumber: number, data: LocalCommentRequest): Promise<BoardComment> {
    const res = await client.post<BoardComment>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/comments`, data)
    return res.data
}

export async function updateComment(
    partnerUid: string,
    boardKey: string,
    ticketNumber: number,
    commentId: number,
    data: LocalCommentRequest,
): Promise<void> {
    await client.put(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/comments/${commentId}`, data)
}

export async function deleteComment(partnerUid: string, boardKey: string, ticketNumber: number, commentId: number): Promise<void> {
    await client.delete(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/comments/${commentId}`)
}

export async function addChecklistItem(partnerUid: string, boardKey: string, ticketNumber: number, data: LocalChecklistItemRequest): Promise<BoardChecklistItem> {
    const res = await client.post<BoardChecklistItem>(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/checklist`, data)
    return res.data
}

export async function updateChecklistItem(
    partnerUid: string,
    boardKey: string,
    ticketNumber: number,
    itemId: number,
    data: LocalUpdateChecklistItemRequest,
): Promise<void> {
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

export async function createLabel(partnerUid: string, boardKey: string, data: LocalCreateLabelRequest): Promise<BoardLabel> {
    const res = await client.post<BoardLabel>(`/federated/boards/${partnerUid}/${boardKey}/labels`, data)
    return res.data
}

export async function watchTicket(partnerUid: string, boardKey: string, ticketNumber: number): Promise<void> {
    await client.post(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/watch`)
}

export async function unwatchTicket(partnerUid: string, boardKey: string, ticketNumber: number): Promise<void> {
    await client.delete(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/watch`)
}

export async function createLink(partnerUid: string, boardKey: string, ticketNumber: number, data: LocalLinkRequest): Promise<void> {
    await client.post(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/links`, data)
}

export async function deleteLink(partnerUid: string, boardKey: string, ticketNumber: number, linkedNumber: number): Promise<void> {
    await client.delete(`/federated/boards/${partnerUid}/${boardKey}/tickets/${ticketNumber}/links/${linkedNumber}`)
}

export async function getAccessOverride(partnerUid: string, boardKey: string): Promise<AccessOverrideResponse> {
    const res = await client.get<AccessOverrideResponse>(`/federated/boards/${partnerUid}/${boardKey}/access/override`)
    return res.data
}

export async function setAccessOverride(partnerUid: string, boardKey: string, data: LocalOverrideRequest): Promise<void> {
    await client.put(`/federated/boards/${partnerUid}/${boardKey}/access/override`, data)
}

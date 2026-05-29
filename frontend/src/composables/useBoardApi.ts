/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { boards, stationMembers } from '@/api'
import * as federatedBoards from '@/api/federatedBoards'
import type {
    Board, BoardLane, BoardField, BoardLabel, BoardTicket, BoardChecklistItem,
    BoardTicketLink, BoardTicketTransition, BoardTicketHistoryEntry, BoardComment,
    BoardWeblink, BoardTicketAttachment, BoardTicketKbLink, BoardTicketFieldValue,
    BoardFieldTypeName, TicketPriorityName,
} from '@/api/boards'

/**
 * Provides a unified board API that works for both local and federated boards.
 * When `partnerUid` is present in route params, delegates to the federated API.
 * Otherwise, uses the local API.
 */
export function useBoardApi() {
    const route = useRoute()

    const partnerUid = computed(() => route.params.partnerUid as string | undefined)
    const isFederated = computed(() => !!partnerUid.value)

    const boardId = computed(() => Number(route.params.boardId))
    const ticketId = computed(() => Number(route.params.ticketId))

    const backRoute = computed(() =>
        isFederated.value
            ? `/station/federation/boards/${partnerUid.value}/${boardId.value}`
            : `/station/boards/${boardId.value}`,
    )

    // -- Board / Ticket reads --

    async function getBoard(): Promise<{ board: Board | federatedBoards.FederatedBoardDetail; canEdit: boolean; shareMode?: string }> {
        if (isFederated.value) {
            const detail = await federatedBoards.getBoard(partnerUid.value!, boardId.value)
            const canEdit = detail.shareMode === federatedBoards.BoardShareMode.FULL
            return { board: detail.board as unknown as Board, canEdit, shareMode: detail.shareMode }
        }
        const [board, editResult] = await Promise.all([
            boards.getBoard(boardId.value),
            boards.canEditBoard(boardId.value),
        ])
        return { board, canEdit: editResult }
    }

    async function getTicket(): Promise<BoardTicket> {
        if (isFederated.value) return federatedBoards.getTicket(partnerUid.value!, boardId.value, ticketId.value)
        return boards.getTicket(boardId.value, ticketId.value)
    }

    async function getLanes(): Promise<BoardLane[]> {
        if (isFederated.value) return federatedBoards.getLanes(partnerUid.value!, boardId.value)
        return boards.getLanes(boardId.value)
    }

    async function getFields(): Promise<BoardField[]> {
        if (isFederated.value) return federatedBoards.getFields(partnerUid.value!, boardId.value)
        return boards.getFields(boardId.value)
    }

    async function getLabels(): Promise<BoardLabel[]> {
        if (isFederated.value) return federatedBoards.getLabels(partnerUid.value!, boardId.value)
        return boards.getLabels(boardId.value)
    }

    async function listTickets(): Promise<BoardTicket[]> {
        if (isFederated.value) return federatedBoards.listTickets(partnerUid.value!, boardId.value)
        return boards.listTickets(boardId.value)
    }

    async function getMembers(): Promise<{ id: number; name: string }[]> {
        if (isFederated.value) return [] // no member list for federated boards
        return stationMembers.listCompletions()
    }

    // -- Ticket detail data --

    async function getChecklist(): Promise<BoardChecklistItem[]> {
        if (isFederated.value) return federatedBoards.getChecklist(partnerUid.value!, boardId.value, ticketId.value)
        return boards.getChecklist(boardId.value, ticketId.value)
    }

    async function getLinks(): Promise<BoardTicketLink[]> {
        if (isFederated.value) return federatedBoards.getLinks(partnerUid.value!, boardId.value, ticketId.value)
        return boards.getLinks(boardId.value, ticketId.value)
    }

    async function getTransitions(): Promise<BoardTicketTransition[]> {
        if (isFederated.value) return federatedBoards.getTransitions(partnerUid.value!, boardId.value, ticketId.value)
        return boards.getTransitions(boardId.value, ticketId.value)
    }

    async function getHistory(): Promise<BoardTicketHistoryEntry[]> {
        if (isFederated.value) return federatedBoards.getHistory(partnerUid.value!, boardId.value, ticketId.value)
        return boards.getHistory(boardId.value, ticketId.value)
    }

    async function getComments(): Promise<BoardComment[]> {
        if (isFederated.value) return federatedBoards.getComments(partnerUid.value!, boardId.value, ticketId.value)
        return boards.getComments(boardId.value, ticketId.value)
    }

    async function getWeblinks(): Promise<BoardWeblink[]> {
        if (isFederated.value) return [] // not available for federated
        return boards.getWeblinks(boardId.value, ticketId.value)
    }

    async function getAttachments(): Promise<BoardTicketAttachment[]> {
        if (isFederated.value) return federatedBoards.getAttachments(partnerUid.value!, boardId.value, ticketId.value)
        return boards.getAttachments(boardId.value, ticketId.value)
    }

    async function getFieldValues(): Promise<BoardTicketFieldValue[]> {
        if (isFederated.value) return [] // not available for federated
        return boards.getFieldValues(boardId.value, ticketId.value)
    }

    async function getTicketLabels(): Promise<BoardLabel[]> {
        if (isFederated.value) return federatedBoards.getTicketLabels(partnerUid.value!, boardId.value, ticketId.value)
        return boards.getTicketLabels(boardId.value, ticketId.value)
    }

    async function getKbLinks(): Promise<BoardTicketKbLink[]> {
        if (isFederated.value) return [] // not available for federated
        return boards.getKbLinks(boardId.value, ticketId.value)
    }

    async function getWatchers(): Promise<number[]> {
        if (isFederated.value) {
            const data = await federatedBoards.getWatchers(partnerUid.value!, boardId.value, ticketId.value)
            return data.local ?? []
        }
        return boards.getWatchers(boardId.value, ticketId.value)
    }

    // -- Write operations --

    async function updateTicket(data: { title: string; description?: string | null; assignedMemberId?: number | null; priority: TicketPriorityName; dueDate?: string | null }): Promise<BoardTicket> {
        if (isFederated.value) {
            return federatedBoards.updateTicket(partnerUid.value!, boardId.value, ticketId.value, {
                title: data.title,
                description: data.description ?? null,
                assignedMemberId: data.assignedMemberId ?? null,
                priority: data.priority,
                dueDate: data.dueDate ?? null,
            })
        }
        return boards.updateTicket(boardId.value, ticketId.value, data)
    }

    async function deleteTicket(): Promise<void> {
        if (isFederated.value) return federatedBoards.deleteTicket(partnerUid.value!, boardId.value, ticketId.value)
        return boards.deleteTicket(boardId.value, ticketId.value)
    }

    async function moveTicket(data: { toLaneId: number; position: number }): Promise<void> {
        if (isFederated.value) { await federatedBoards.moveTicket(partnerUid.value!, boardId.value, ticketId.value, data); return }
        await boards.moveTicket(boardId.value, ticketId.value, data)
    }

    async function addChecklistItem(data: { title: string }): Promise<void> {
        if (isFederated.value) { await federatedBoards.addChecklistItem(partnerUid.value!, boardId.value, ticketId.value, data); return }
        await boards.addChecklistItem(boardId.value, ticketId.value, data)
    }

    async function updateChecklistItem(itemId: number, data: { title: string; checked: boolean }): Promise<void> {
        if (isFederated.value) { await federatedBoards.updateChecklistItem(partnerUid.value!, boardId.value, ticketId.value, itemId, data); return }
        await boards.updateChecklistItem(boardId.value, ticketId.value, itemId, data)
    }

    async function deleteChecklistItem(itemId: number): Promise<void> {
        if (isFederated.value) { await federatedBoards.deleteChecklistItem(partnerUid.value!, boardId.value, ticketId.value, itemId); return }
        await boards.deleteChecklistItem(boardId.value, ticketId.value, itemId)
    }

    async function reorderChecklist(data: { orderedIds: number[] }): Promise<void> {
        if (isFederated.value) return // not available for federated
        await boards.reorderChecklist(boardId.value, ticketId.value, data)
    }

    async function createComment(data: { parentId: number | null; content: string }): Promise<void> {
        if (isFederated.value) { await federatedBoards.addComment(partnerUid.value!, boardId.value, ticketId.value, data); return }
        await boards.createComment(boardId.value, ticketId.value, data)
    }

    async function updateComment(commentId: number, data: { content: string }): Promise<void> {
        if (isFederated.value) return // not available for federated
        await boards.updateComment(boardId.value, ticketId.value, commentId, data)
    }

    async function deleteComment(_commentId: number): Promise<void> {
        if (isFederated.value) return // not available for federated
        await boards.deleteComment(boardId.value, ticketId.value, _commentId)
    }

    async function addTicketLabel(labelId: number): Promise<BoardLabel[]> {
        if (isFederated.value) return federatedBoards.addTicketLabel(partnerUid.value!, boardId.value, ticketId.value, labelId)
        return boards.addTicketLabel(boardId.value, ticketId.value, labelId)
    }

    async function removeTicketLabel(labelId: number): Promise<void> {
        if (isFederated.value) return federatedBoards.removeTicketLabel(partnerUid.value!, boardId.value, ticketId.value, labelId)
        await boards.removeTicketLabel(boardId.value, ticketId.value, labelId)
    }

    async function watchTicket(): Promise<void> {
        if (isFederated.value) { await federatedBoards.watchTicket(partnerUid.value!, boardId.value, ticketId.value); return }
        await boards.watchTicket(boardId.value, ticketId.value)
    }

    async function unwatchTicket(): Promise<void> {
        if (isFederated.value) { await federatedBoards.unwatchTicket(partnerUid.value!, boardId.value, ticketId.value); return }
        await boards.unwatchTicket(boardId.value, ticketId.value)
    }

    async function setFieldValue(fieldId: number, fieldType: BoardFieldTypeName, value: unknown): Promise<void> {
        if (isFederated.value) return // not available for federated
        await boards.setFieldValue(boardId.value, ticketId.value, fieldId, fieldType, value)
    }

    async function deleteFieldValue(fieldId: number): Promise<void> {
        if (isFederated.value) return // not available for federated
        await boards.deleteFieldValue(boardId.value, ticketId.value, fieldId)
    }

    async function uploadAttachment(file: File): Promise<BoardTicketAttachment | null> {
        if (isFederated.value) return null // not available for federated
        return boards.uploadAttachment(boardId.value, ticketId.value, file)
    }

    async function createLabel(data: { name: string }): Promise<BoardLabel> {
        if (isFederated.value) return federatedBoards.createLabel(partnerUid.value!, boardId.value, data)
        return boards.createLabel(boardId.value, data)
    }

    return {
        isFederated,
        partnerUid,
        boardId,
        ticketId,
        backRoute,
        // Reads
        getBoard,
        getTicket,
        getLanes,
        getFields,
        getLabels,
        listTickets,
        getMembers,
        getChecklist,
        getLinks,
        getTransitions,
        getHistory,
        getComments,
        getWeblinks,
        getAttachments,
        getFieldValues,
        getTicketLabels,
        getKbLinks,
        getWatchers,
        // Writes
        updateTicket,
        deleteTicket,
        moveTicket,
        addChecklistItem,
        updateChecklistItem,
        deleteChecklistItem,
        reorderChecklist,
        createComment,
        updateComment,
        deleteComment,
        addTicketLabel,
        removeTicketLabel,
        watchTicket,
        unwatchTicket,
        setFieldValue,
        deleteFieldValue,
        uploadAttachment,
        createLabel,
    }
}

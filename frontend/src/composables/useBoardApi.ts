/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { boards } from '@/api'
import * as federatedBoards from '@/api/federatedBoards'
import type {
    AnyBoard, BoardFieldRaw, BoardFieldTypeName, BoardTicketComment, LinkTypeName, TypedBoardField, TypedBoardFieldValue,
} from '@/api/boards'
import type {
    BoardChecklistItem, BoardLabel, BoardLane, BoardTicket, BoardTicketAttachment, BoardTicketCommentRequest,
    BoardTicketHistoryResponse, BoardTicketKbLink, BoardTicketLink, BoardTicketTransitionResponse, BoardWeblink,
    ChecklistItemRequest, MemberCompletion, MoveTicketRequest, ReorderChecklistRequest, TicketSummary, UpdateTicketRequest,
} from '@/api/generated/schema'

/**
 * Provides a unified board API that works for both local and federated boards.
 * When `partnerUid` is present in route params, delegates to the federated API.
 * Otherwise, uses the local API.
 *
 * <p>A partner's board offers no weblinks, field values or wiki links, and does not take reordered
 * checklists or edited and deleted comments: those reads answer empty and those writes do nothing there.
 */
export function useBoardApi() {
    const route = useRoute()

    const partnerUid = computed(() => route.params.partnerUid as string | undefined)
    const isFederated = computed(() => !!partnerUid.value)

    const boardKey = computed(() => route.params.boardKey as string)
    const ticketNumber = computed(() => Number(route.params.ticketNumber))

    const backRoute = computed(() =>
        isFederated.value
            ? `/station/federation/boards/${partnerUid.value}/${boardKey.value}`
            : `/station/boards/${boardKey.value}`,
    )

    async function getBoard(): Promise<{ board: AnyBoard; canEdit: boolean; shareMode?: federatedBoards.BoardShareModeName }> {
        if (isFederated.value) {
            const detail = await federatedBoards.getBoard(partnerUid.value!, boardKey.value)
            const canEdit = detail.shareMode === federatedBoards.BoardShareMode.FULL
            return { board: detail.board, canEdit, shareMode: detail.shareMode }
        }
        const [board, editResult] = await Promise.all([
            boards.getBoard(boardKey.value),
            boards.canEditBoard(boardKey.value),
        ])
        return { board, canEdit: editResult }
    }

    async function getTicket(): Promise<BoardTicket> {
        if (isFederated.value) return federatedBoards.getTicket(partnerUid.value!, boardKey.value, ticketNumber.value)
        return boards.getTicket(boardKey.value, ticketNumber.value)
    }

    async function getLanes(): Promise<BoardLane[]> {
        if (isFederated.value) return federatedBoards.getLanes(partnerUid.value!, boardKey.value)
        return boards.getLanes(boardKey.value)
    }

    async function getFields(): Promise<TypedBoardField[]> {
        if (isFederated.value) return federatedBoards.getFields(partnerUid.value!, boardKey.value)
        return boards.getFields(boardKey.value)
    }

    async function getLabels(): Promise<BoardLabel[]> {
        if (isFederated.value) return federatedBoards.getLabels(partnerUid.value!, boardKey.value)
        return boards.getLabels(boardKey.value)
    }

    async function listTickets(): Promise<TicketSummary[]> {
        if (isFederated.value) return federatedBoards.listTickets(partnerUid.value!, boardKey.value)
        return boards.listTickets(boardKey.value)
    }

    async function getMembers(): Promise<MemberCompletion[]> {
        if (isFederated.value) return federatedBoards.getBoardMembers(partnerUid.value!, boardKey.value)
        return boards.getBoardMembers(boardKey.value)
    }

    /**
     * Whom a ticket may be handed to. A partner's board answers only with its members, and asking
     * it who may write there would change what the two instances promise each other, so a
     * federated board keeps offering all of them and the owning station turns down what it will
     * not take.
     */
    async function getAssignableMembers(): Promise<MemberCompletion[]> {
        if (isFederated.value) return federatedBoards.getBoardMembers(partnerUid.value!, boardKey.value)
        return boards.getAssignableMembers(boardKey.value)
    }

    async function getChecklist(): Promise<BoardChecklistItem[]> {
        if (isFederated.value) return federatedBoards.getChecklist(partnerUid.value!, boardKey.value, ticketNumber.value)
        return boards.getChecklist(boardKey.value, ticketNumber.value)
    }

    async function getLinks(): Promise<BoardTicketLink[]> {
        if (isFederated.value) return federatedBoards.getLinks(partnerUid.value!, boardKey.value, ticketNumber.value)
        return boards.getLinks(boardKey.value, ticketNumber.value)
    }

    async function getTransitions(): Promise<BoardTicketTransitionResponse[]> {
        if (isFederated.value) return federatedBoards.getTransitions(partnerUid.value!, boardKey.value, ticketNumber.value)
        return boards.getTransitions(boardKey.value, ticketNumber.value)
    }

    async function getHistory(): Promise<BoardTicketHistoryResponse[]> {
        if (isFederated.value) return federatedBoards.getHistory(partnerUid.value!, boardKey.value, ticketNumber.value)
        return boards.getHistory(boardKey.value, ticketNumber.value)
    }

    async function getComments(): Promise<BoardTicketComment[]> {
        if (isFederated.value) return federatedBoards.getComments(partnerUid.value!, boardKey.value, ticketNumber.value)
        return boards.getComments(boardKey.value, ticketNumber.value)
    }

    async function getWeblinks(): Promise<BoardWeblink[]> {
        if (isFederated.value) return []
        return boards.getWeblinks(boardKey.value, ticketNumber.value)
    }

    async function getAttachments(): Promise<BoardTicketAttachment[]> {
        if (isFederated.value) return federatedBoards.getAttachments(partnerUid.value!, boardKey.value, ticketNumber.value)
        return boards.getAttachments(boardKey.value, ticketNumber.value)
    }

    async function getFieldValues(): Promise<TypedBoardFieldValue[]> {
        if (isFederated.value) return []
        return boards.getFieldValues(boardKey.value, ticketNumber.value)
    }

    async function getTicketLabels(): Promise<BoardLabel[]> {
        if (isFederated.value) return federatedBoards.getTicketLabels(partnerUid.value!, boardKey.value, ticketNumber.value)
        return boards.getTicketLabels(boardKey.value, ticketNumber.value)
    }

    async function getKbLinks(): Promise<BoardTicketKbLink[]> {
        if (isFederated.value) return []
        return boards.getKbLinks(boardKey.value, ticketNumber.value)
    }

    async function getWatchers(): Promise<number[]> {
        if (isFederated.value) return federatedBoards.getWatchers(partnerUid.value!, boardKey.value, ticketNumber.value)
        return boards.getWatchers(boardKey.value, ticketNumber.value)
    }

    async function updateTicket(data: UpdateTicketRequest): Promise<BoardTicket> {
        if (isFederated.value) return federatedBoards.updateTicket(partnerUid.value!, boardKey.value, ticketNumber.value, data)
        return boards.updateTicket(boardKey.value, ticketNumber.value, data)
    }

    async function deleteTicket(): Promise<void> {
        if (isFederated.value) return federatedBoards.deleteTicket(partnerUid.value!, boardKey.value, ticketNumber.value)
        return boards.deleteTicket(boardKey.value, ticketNumber.value)
    }

    async function moveTicket(data: MoveTicketRequest): Promise<void> {
        if (isFederated.value) { await federatedBoards.moveTicket(partnerUid.value!, boardKey.value, ticketNumber.value, data); return }
        await boards.moveTicket(boardKey.value, ticketNumber.value, data)
    }

    async function addChecklistItem(data: { title: string }): Promise<void> {
        if (isFederated.value) { await federatedBoards.addChecklistItem(partnerUid.value!, boardKey.value, ticketNumber.value, data); return }
        await boards.addChecklistItem(boardKey.value, ticketNumber.value, data)
    }

    async function updateChecklistItem(itemId: number, data: ChecklistItemRequest): Promise<void> {
        if (isFederated.value) { await federatedBoards.updateChecklistItem(partnerUid.value!, boardKey.value, ticketNumber.value, itemId, data); return }
        await boards.updateChecklistItem(boardKey.value, ticketNumber.value, itemId, data)
    }

    async function deleteChecklistItem(itemId: number): Promise<void> {
        if (isFederated.value) { await federatedBoards.deleteChecklistItem(partnerUid.value!, boardKey.value, ticketNumber.value, itemId); return }
        await boards.deleteChecklistItem(boardKey.value, ticketNumber.value, itemId)
    }

    async function reorderChecklist(data: ReorderChecklistRequest): Promise<void> {
        if (isFederated.value) return
        await boards.reorderChecklist(boardKey.value, ticketNumber.value, data)
    }

    async function createComment(data: BoardTicketCommentRequest): Promise<void> {
        if (isFederated.value) { await federatedBoards.addComment(partnerUid.value!, boardKey.value, ticketNumber.value, data); return }
        await boards.createComment(boardKey.value, ticketNumber.value, data)
    }

    async function updateComment(commentId: number, data: BoardTicketCommentRequest): Promise<void> {
        if (isFederated.value) { await federatedBoards.updateComment(partnerUid.value!, boardKey.value, ticketNumber.value, commentId, data); return }
        await boards.updateComment(boardKey.value, ticketNumber.value, commentId, data)
    }

    async function deleteComment(commentId: number): Promise<void> {
        if (isFederated.value) { await federatedBoards.deleteComment(partnerUid.value!, boardKey.value, ticketNumber.value, commentId); return }
        await boards.deleteComment(boardKey.value, ticketNumber.value, commentId)
    }

    async function addTicketLabel(labelId: number): Promise<BoardLabel[]> {
        if (isFederated.value) return federatedBoards.addTicketLabel(partnerUid.value!, boardKey.value, ticketNumber.value, labelId)
        return boards.addTicketLabel(boardKey.value, ticketNumber.value, labelId)
    }

    async function removeTicketLabel(labelId: number): Promise<void> {
        if (isFederated.value) return federatedBoards.removeTicketLabel(partnerUid.value!, boardKey.value, ticketNumber.value, labelId)
        await boards.removeTicketLabel(boardKey.value, ticketNumber.value, labelId)
    }

    async function createLink(linkedTicketId: number, linkedTicketNumber: number, linkType: LinkTypeName): Promise<void> {
        if (isFederated.value) {
            await federatedBoards.createLink(partnerUid.value!, boardKey.value, ticketNumber.value, { linkedTicketNumber, linkType })
            return
        }
        await boards.createLink(boardKey.value, ticketNumber.value, { linkedTicketId, linkType })
    }

    async function deleteLink(linkedTicketId: number, linkedTicketNumber: number): Promise<void> {
        if (isFederated.value) {
            await federatedBoards.deleteLink(partnerUid.value!, boardKey.value, ticketNumber.value, linkedTicketNumber)
            return
        }
        await boards.deleteLink(boardKey.value, ticketNumber.value, linkedTicketId)
    }

    async function watchTicket(): Promise<void> {
        if (isFederated.value) { await federatedBoards.watchTicket(partnerUid.value!, boardKey.value, ticketNumber.value); return }
        await boards.watchTicket(boardKey.value, ticketNumber.value)
    }

    async function unwatchTicket(): Promise<void> {
        if (isFederated.value) { await federatedBoards.unwatchTicket(partnerUid.value!, boardKey.value, ticketNumber.value); return }
        await boards.unwatchTicket(boardKey.value, ticketNumber.value)
    }

    async function setFieldValue(fieldId: number, fieldType: BoardFieldTypeName, value: BoardFieldRaw): Promise<void> {
        if (isFederated.value) return
        await boards.setFieldValue(boardKey.value, ticketNumber.value, fieldId, fieldType, value)
    }

    async function deleteFieldValue(fieldId: number): Promise<void> {
        if (isFederated.value) return
        await boards.deleteFieldValue(boardKey.value, ticketNumber.value, fieldId)
    }

    async function uploadAttachment(file: File): Promise<BoardTicketAttachment | null> {
        if (isFederated.value) return null
        return boards.uploadAttachment(boardKey.value, ticketNumber.value, file)
    }

    async function createLabel(data: { name: string }): Promise<BoardLabel> {
        if (isFederated.value) return federatedBoards.createLabel(partnerUid.value!, boardKey.value, data)
        return boards.createLabel(boardKey.value, data)
    }

    return {
        isFederated,
        partnerUid,
        boardKey,
        ticketNumber,
        backRoute,
        getBoard,
        getTicket,
        getLanes,
        getFields,
        getLabels,
        listTickets,
        getMembers,
        getAssignableMembers,
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
        createLink,
        deleteLink,
        watchTicket,
        unwatchTicket,
        setFieldValue,
        deleteFieldValue,
        uploadAttachment,
        createLabel,
    }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { boards } from '@/api'
import type { BoardTicket } from '@/api/boards'

/**
 * Drag-and-drop interaction for the kanban board: tracks the ticket under the cursor and the
 * drop indicator position, applies the reorder optimistically and reloads the board when the
 * server rejects the move.
 */
export function useBoardDragAndDrop(
    tickets: Ref<BoardTicket[]>,
    boardKey: Ref<string>,
    reload: () => Promise<void>,
) {
    const dragTicket = ref<BoardTicket | null>(null)
    const dropLaneId = ref<number | null>(null)
    const dropPosition = ref<number | null>(null)

    function onTicketDragStart(ticket: BoardTicket, event: DragEvent) {
        dragTicket.value = ticket
        if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = 'move'
            event.dataTransfer.setData('text/plain', String(ticket.id))
        }
    }

    function onLaneDragOver(laneId: number, event: DragEvent) {
        event.preventDefault()
        dropLaneId.value = laneId
        const container = event.currentTarget as HTMLElement
        const ticketElements = container.querySelectorAll('[data-ticket-id]')
        let pos = 0
        for (const el of ticketElements) {
            if (dragTicket.value && el.getAttribute('data-ticket-id') === String(dragTicket.value.id)) continue
            const rect = el.getBoundingClientRect()
            if (event.clientY > rect.top + rect.height / 2) pos++
        }
        dropPosition.value = pos
    }

    function onLaneDragLeave(event: DragEvent) {
        const target = event.currentTarget as HTMLElement
        if (!target.contains(event.relatedTarget as Node)) {
            dropLaneId.value = null
            dropPosition.value = null
        }
    }

    async function onLaneDrop(laneId: number) {
        if (!dragTicket.value) return
        const ticket = dragTicket.value
        const pos = dropPosition.value ?? 0
        dragTicket.value = null
        dropLaneId.value = null
        dropPosition.value = null

        const otherTickets = tickets.value.filter(t => t.laneId === laneId && t.id !== ticket.id).sort((a, b) => a.position - b.position)
        otherTickets.splice(pos, 0, ticket)
        const updatedTicket = { ...ticket, laneId, laneEnteredAt: ticket.laneId !== laneId ? new Date().toISOString() : ticket.laneEnteredAt }
        tickets.value = tickets.value.filter(t => t.id !== ticket.id).map(t => {
            const idx = otherTickets.findIndex(ot => ot.id === t.id)
            return idx >= 0 ? { ...t, position: idx } : t
        })
        tickets.value.push({ ...updatedTicket, position: pos })

        if (ticket.laneId === laneId) {
            try {
                await boards.reorderTickets(boardKey.value, ticket.ticketNumber, { laneId, orderedIds: otherTickets.map(t => t.id) })
            } catch { await reload() }
        } else {
            try {
                await boards.moveTicket(boardKey.value, ticket.ticketNumber, { toLaneId: laneId, position: pos })
            } catch { await reload() }
        }
    }

    function onDragEnd() {
        dragTicket.value = null
        dropLaneId.value = null
        dropPosition.value = null
    }

    return {
        dragTicket,
        dropLaneId,
        dropPosition,
        onTicketDragStart,
        onLaneDragOver,
        onLaneDragLeave,
        onLaneDrop,
        onDragEnd,
    }
}

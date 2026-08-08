/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import type { BoardTicket } from '@/api/boards'

/**
 * The two writes a drag can produce. A local board and a federation partner's board reach
 * different endpoints, so the caller supplies them.
 */
export interface BoardDragTargets {
  reorder: (ticketNumber: number, payload: {laneId: number; orderedIds: number[]}) => Promise<unknown>
  move: (ticketNumber: number, payload: {toLaneId: number; position: number}) => Promise<unknown>
}

/**
 * Drag-and-drop interaction for the kanban board: tracks the ticket under the cursor and the
 * drop indicator position, applies the reorder optimistically, and reloads the board when the
 * server rejects the move.
 *
 * @param tickets the board's tickets, reordered in place while the request is in flight
 * @param targets the endpoints a drop writes to
 * @param reload  called to resynchronise when a write fails
 * @param enabled whether dragging is allowed at all; a read-only board passes a check here
 */
export function useBoardDragAndDrop(
  tickets: Ref<BoardTicket[]>,
  targets: BoardDragTargets,
  reload: () => Promise<void>,
  enabled: () => boolean = () => true,
) {
  const dragTicket = ref<BoardTicket | null>(null)
  const dropLaneId = ref<number | null>(null)
  const dropPosition = ref<number | null>(null)

  function clearDrag() {
    dragTicket.value = null
    dropLaneId.value = null
    dropPosition.value = null
  }

  function onTicketDragStart(ticket: BoardTicket, event: DragEvent) {
    if (!enabled()) return
    dragTicket.value = ticket
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move'
      event.dataTransfer.setData('text/plain', String(ticket.id))
    }
  }

  /**
   * Works out where in the lane the ticket would land by counting the cards whose midpoint the
   * cursor has already passed, skipping the dragged card itself.
   */
  function onLaneDragOver(laneId: number, event: DragEvent) {
    if (!enabled()) return
    event.preventDefault()
    dropLaneId.value = laneId
    const container = event.currentTarget as HTMLElement
    let pos = 0
    for (const el of container.querySelectorAll('[data-ticket-id]')) {
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
    if (!dragTicket.value || !enabled()) return
    const ticket = dragTicket.value
    const pos = dropPosition.value ?? 0
    const movedLane = ticket.laneId !== laneId
    clearDrag()

    const others = tickets.value
      .filter(t => t.laneId === laneId && t.id !== ticket.id)
      .sort((a, b) => a.position - b.position)
    others.splice(pos, 0, ticket)
    tickets.value = tickets.value.filter(t => t.id !== ticket.id).map(t => {
      const idx = others.findIndex(other => other.id === t.id)
      return idx >= 0 ? {...t, position: idx} : t
    })
    tickets.value.push({
      ...ticket,
      laneId,
      laneEnteredAt: movedLane ? new Date().toISOString() : ticket.laneEnteredAt,
      position: pos,
    })

    try {
      if (movedLane) {
        await targets.move(ticket.ticketNumber, {toLaneId: laneId, position: pos})
      } else {
        await targets.reorder(ticket.ticketNumber, {laneId, orderedIds: others.map(t => t.id)})
      }
    } catch {
      await reload()
    }
  }

  return {
    dragTicket,
    dropLaneId,
    dropPosition,
    onTicketDragStart,
    onLaneDragOver,
    onLaneDragLeave,
    onLaneDrop,
    onDragEnd: clearDrag,
  }
}

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { Ref } from 'vue'
import type { TicketSummary } from '@/api/generated/schema'

/**
 * The two writes a move can produce. A local board and a federation partner's board reach
 * different endpoints, so the caller supplies them.
 */
export interface TicketMoveTargets {
  reorder: (ticketNumber: number, payload: {laneId: number; orderedIds: number[]}) => Promise<unknown>
  move: (ticketNumber: number, payload: {toLaneId: number; position: number}) => Promise<unknown>
}

/**
 * Puts a ticket of the kanban board at a new place, whether it was dragged there or sent there from
 * its menu: applies the new order optimistically, writes it, and reloads the board when the server
 * rejects it.
 *
 * <p>A ticket that changes lane is moved; one that stays in its lane is reordered, with the lane's
 * whole new order.
 *
 * @param tickets the board's tickets, reordered in place while the request is in flight
 * @param targets the endpoints a move writes to
 * @param reload  called to resynchronise when a write fails
 */
export function useTicketMoves(
  tickets: Ref<TicketSummary[]>,
  targets: TicketMoveTargets,
  reload: () => Promise<void>,
) {
  /**
   * @param position where the ticket lands among the other tickets of the lane, in their order
   */
  async function moveTicket(ticket: TicketSummary, laneId: number, position: number) {
    const movedLane = ticket.laneId !== laneId
    const others = tickets.value
      .filter(t => t.laneId === laneId && t.id !== ticket.id)
      .sort((a, b) => a.position - b.position)
    others.splice(position, 0, ticket)
    tickets.value = tickets.value.filter(t => t.id !== ticket.id).map(t => {
      const idx = others.findIndex(other => other.id === t.id)
      return idx >= 0 ? {...t, position: idx} : t
    })
    tickets.value.push({
      ...ticket,
      laneId,
      laneEnteredAt: movedLane ? new Date().toISOString() : ticket.laneEnteredAt,
      position,
    })

    try {
      if (movedLane) {
        await targets.move(ticket.ticketNumber, {toLaneId: laneId, position})
      } else {
        await targets.reorder(ticket.ticketNumber, {laneId, orderedIds: others.map(t => t.id)})
      }
    } catch {
      await reload()
    }
  }

  return {moveTicket}
}

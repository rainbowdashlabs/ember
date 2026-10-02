/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { BoardLane, TicketSummary } from '@/api/generated/schema'

/** What the kanban board needs to know about the board it draws. */
export interface KanbanBoardSettings {
  shortKey: string
  backlogLaneId: number | null
  hideDoneAfterDays: number
}

/** The lanes the board shows as columns: every lane but the backlog, which has a page of its own. */
export function boardLanes(lanes: BoardLane[], backlogLaneId: number | null): BoardLane[] {
  return lanes.filter(lane => !backlogLaneId || lane.id !== backlogLaneId)
}

/** The tickets of one lane in their order. */
export function laneTickets(tickets: TicketSummary[], laneId: number): TicketSummary[] {
  return tickets.filter(ticket => ticket.laneId === laneId).sort((a, b) => a.position - b.position)
}

/**
 * Whether a ticket in the last lane has sat there long enough to be put away in the archive.
 *
 * @param now the moment to measure from
 */
export function isPastDoneLimit(ticket: TicketSummary, hideDoneAfterDays: number, now: Date = new Date()): boolean {
  const cutoff = new Date(now)
  cutoff.setDate(cutoff.getDate() - hideDoneAfterDays)
  return new Date(ticket.laneEnteredAt) < cutoff
}

/**
 * Where in a lane a ticket lands when it is put at a place among the cards the reader sees.
 *
 * <p>The cards shown are not always the whole lane: a filter hides some, and the last lane hides the
 * tickets done long ago. The ticket goes before the card it was put in front of, or right after the last
 * card shown when it was put at the end.
 *
 * @param lane         the other tickets of the lane, in their order
 * @param shown        the other cards shown in the lane, in their order
 * @param shownIndex   the place among the shown cards
 * @return the place among all tickets of the lane
 */
export function positionInLane(lane: TicketSummary[], shown: TicketSummary[], shownIndex: number): number {
  const before = shown[shownIndex]
  if (before) return lane.findIndex(ticket => ticket.id === before.id)
  const last = shown[shown.length - 1]
  return last ? lane.findIndex(ticket => ticket.id === last.id) + 1 : lane.length
}

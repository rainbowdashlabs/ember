/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import KanbanLane from './KanbanLane.vue'
import {boardLanes, isPastDoneLimit, laneTickets, positionInLane, type KanbanBoardSettings} from './kanbanLanes'
import type {BoardLabel, BoardLane, MemberCompletion, TicketSummary} from '@/api/generated/schema'

/**
 * The kanban board: one column per lane, the backlog left out, and in the last lane only the tickets
 * that have not yet sat there longer than the board keeps finished work in sight.
 *
 * <p>A ticket can be dragged, by mouse or by finger, or moved from the menu on its card, and both ways
 * end in the same `move`: the ticket, its lane and its place among all tickets of that lane. Where the
 * reader sees only some of a lane, because of a filter or the archive, the place is worked out from the
 * card it was put in front of. What happened is read out to a screen reader after every move.
 *
 * <p>A read-only board offers neither the drag nor the menu.
 */
const props = withDefaults(defineProps<{
  board: KanbanBoardSettings
  lanes: BoardLane[]
  tickets: TicketSummary[]
  labelsForTicket: (ticketId: number) => BoardLabel[]
  /** Narrows the cards shown, as the local board's assignee and label filters do. */
  filter?: (ticket: TicketSummary) => boolean
  /** The station's members, which put names and avatars to the assignees. */
  members?: MemberCompletion[]
  readOnly?: boolean
  /** Whether the count of archived tickets opens the archive, reported as `openArchive`. */
  archiveLinked?: boolean
}>(), {
  filter: undefined,
  members: undefined,
  readOnly: false,
  archiveLinked: false,
})

const emit = defineEmits<{
  move: [ticket: TicketSummary, laneId: number, position: number]
  open: [ticket: TicketSummary]
  openArchive: []
}>()

const {t} = useI18n()

const group = useId()
const announcement = ref('')

const columns = computed(() => boardLanes(props.lanes, props.board.backlogLaneId))
const lastLaneId = computed(() => columns.value[columns.value.length - 1]?.id ?? null)

function isArchived(ticket: TicketSummary, laneId: number): boolean {
  return laneId === lastLaneId.value && isPastDoneLimit(ticket, props.board.hideDoneAfterDays)
}

function filteredLane(laneId: number): TicketSummary[] {
  const lane = laneTickets(props.tickets, laneId)
  return props.filter ? lane.filter(props.filter) : lane
}

function cardsFor(laneId: number): TicketSummary[] {
  return filteredLane(laneId).filter(ticket => !isArchived(ticket, laneId))
}

function archivedCountFor(laneId: number): number {
  return filteredLane(laneId).filter(ticket => isArchived(ticket, laneId)).length
}

function otherLanes(laneId: number): BoardLane[] {
  return columns.value.filter(lane => lane.id !== laneId)
}

/**
 * Puts a ticket at a place among the cards shown in a lane.
 *
 * @param shownIndex the place among the other cards shown there; past the last one is the lane's end
 */
function place(ticketId: number, laneId: number, shownIndex: number) {
  const ticket = props.tickets.find(candidate => candidate.id === ticketId)
  if (!ticket || props.readOnly) return
  const whole = laneTickets(props.tickets, laneId)
  const lane = whole.filter(other => other.id !== ticketId)
  const shown = cardsFor(laneId).filter(other => other.id !== ticketId)
  const position = positionInLane(lane, shown, shownIndex)
  if (ticket.laneId === laneId && whole.indexOf(ticket) === position) return
  emit('move', ticket, laneId, position)
  announcement.value = t('boards.ticketMoved', {
    ticket: `${props.board.shortKey}-${ticket.ticketNumber}`,
    lane: columns.value.find(column => column.id === laneId)?.name ?? '',
    position: Math.min(shownIndex, shown.length) + 1,
  })
}

function send(ticketId: number, laneId: number) {
  place(ticketId, laneId, cardsFor(laneId).length)
}
</script>

<template>
  <div>
    <p class="sr-only" role="status" aria-live="polite" data-testid="kanban-announcement">{{ announcement }}</p>
    <div class="flex flex-col md:flex-row gap-4 md:overflow-x-auto pb-4 min-h-[200px]">
      <KanbanLane
          v-for="lane in columns"
          :key="lane.id"
          :lane="lane"
          :tickets="cardsFor(lane.id)"
          :other-lanes="otherLanes(lane.id)"
          :archived-count="archivedCountFor(lane.id)"
          :archive-linked="archiveLinked"
          :group="group"
          :short-key="board.shortKey"
          :labels-for-ticket="labelsForTicket"
          :members="members"
          :read-only="readOnly"
          @place="place"
          @send="send"
          @open="ticket => emit('open', ticket)"
          @open-archive="emit('openArchive')"
      />
    </div>
  </div>
</template>

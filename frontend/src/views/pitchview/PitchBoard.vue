/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import KanbanLane from '@/views/stationview/boards/boardview/KanbanLane.vue'
import type {PitchBoard} from './pitchTypes'

/** The board of the application itself, fed with the data a demonstration wants to show. */
const props = defineProps<{
  board: PitchBoard
}>()

const ticketsOf = (laneId: number) => props.board.tickets.filter(ticket => ticket.laneId === laneId)
const labelsForTicket = (ticketId: number) => props.board.labels?.[ticketId] ?? []
</script>

<template>
  <div class="flex gap-3 overflow-x-auto">
    <KanbanLane
        v-for="(lane, index) in board.lanes" :key="lane.id"
        :lane="lane" :tickets="ticketsOf(lane.id)"
        :archived-count="board.archivedCount ?? 0"
        :is-last-lane="index === board.lanes.length - 1"
        :drag-ticket="null" :drop-lane-id="null" :drop-position="null"
        :members="board.members ?? []" :short-key="board.shortKey"
        :labels-for-ticket="labelsForTicket"/>
  </div>
</template>

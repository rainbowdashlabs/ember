/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import TicketTile from '@/views/stationview/boards/boardview/TicketTile.vue'
import type {BoardLane, BoardTicket, BoardLabel} from '@/api/boards'

const props = defineProps<{
  lane: BoardLane
  shortKey: string
  isFull: boolean
  visibleTickets: BoardTicket[]
  isLastLane: boolean
  archivedCount: number
  dragTicket: BoardTicket | null
  dropLaneId: number | null
  dropPosition: number | null
  labelsForTicket: (ticketId: number) => BoardLabel[]
}>()

const emit = defineEmits<{
  'dragover': [laneId: number, event: DragEvent]
  'dragleave': [event: DragEvent]
  'drop': [laneId: number]
  'ticket-dragstart': [ticket: BoardTicket, event: DragEvent]
  'ticket-dragend': []
  'open-ticket': [ticket: BoardTicket]
}>()

const {t} = useI18n()

function onDragOver(event: DragEvent) {
  if (props.isFull) emit('dragover', props.lane.id, event)
}

function onDragLeave(event: DragEvent) {
  if (props.isFull) emit('dragleave', event)
}

function onDrop() {
  if (props.isFull) emit('drop', props.lane.id)
}
</script>

<template>
  <div
      class="md:flex-1 md:min-w-[14rem] md:max-w-[24rem] bg-bg-light-accent dark:bg-bg-dark-accent border border-[var(--border)] rounded-lg p-3 border-t-2 transition-colors"
      :style="{ borderTopColor: lane.color ?? 'var(--primary)' }"
      :class="{ 'bg-primary/5': dropLaneId === lane.id && dragTicket }"
      @dragover="onDragOver"
      @dragleave="onDragLeave"
      @drop="onDrop"
  >
    <div class="flex items-center justify-between mb-3">
      <SubHeader class="text-sm text-[var(--text-muted)] uppercase tracking-wide">{{ lane.name }}</SubHeader>
      <BaseBadge bg-class="bg-[var(--bg)]" class="text-[var(--text-muted)]">{{ visibleTickets.length }}</BaseBadge>
    </div>

    <div class="min-h-[3rem]">
      <template v-for="(ticket, idx) in visibleTickets" :key="ticket.id">
        <div v-if="isFull && dropLaneId === lane.id && dropPosition === idx && dragTicket && dragTicket.id !== ticket.id"
             class="h-12 mb-2 rounded-lg border-2 border-dashed border-primary/40 bg-primary/5"/>
        <div
            :data-ticket-id="ticket.id"
            :draggable="isFull"
            class="mb-2"
            :class="{ 'opacity-30': dragTicket?.id === ticket.id }"
            @dragstart="isFull ? emit('ticket-dragstart', ticket, $event) : undefined"
            @dragend="isFull ? emit('ticket-dragend') : undefined"
        >
          <TicketTile
              :ticket="ticket"
              :short-key="shortKey"
              :labels="labelsForTicket(ticket.id)"
              :attachment-count="ticket.attachmentCount"
              @click="emit('open-ticket', ticket)"
          />
        </div>
      </template>
      <div v-if="isFull && dropLaneId === lane.id && dragTicket && dropPosition !== null && dropPosition >= visibleTickets.filter(tt => tt.id !== dragTicket!.id).length"
           class="h-12 rounded-lg border-2 border-dashed border-primary/40 bg-primary/5"/>
    </div>

    <div v-if="isLastLane && archivedCount > 0" class="mt-2 text-xs text-(--text-muted) text-center py-2">
      {{ archivedCount }} {{ t('boards.archived') }}
    </div>

    <p v-if="visibleTickets.length === 0 && !dragTicket" class="text-xs text-[var(--text-muted)] text-center py-4">
      {{ t('boards.noTickets') }}
    </p>
  </div>
</template>

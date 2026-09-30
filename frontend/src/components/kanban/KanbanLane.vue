/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {VueDraggable, type SortableEvent} from 'vue-draggable-plus'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import KanbanCard from './KanbanCard.vue'
import type {BoardLabel, BoardLane, BoardTicket} from '@/api/boards'
import type {MemberCompletion} from '@/api/stationMembers'

/**
 * One column of the kanban board and the cards shown in it.
 *
 * <p>Cards are dragged with a mouse or, after a short press, with a finger; the short press is what
 * leaves a swipe over the board to scroll it. The card's menu is left out of the drag, so pressing it
 * opens the menu rather than picking the card up. Wherever a card ends up, by drag or by menu, the lane
 * reports the same `place`: the ticket, the lane and its place among the cards shown there. Only a move
 * to another lane from the menu is `send`, because the end of that lane is for the board to know.
 */
const props = defineProps<{
  lane: BoardLane
  tickets: BoardTicket[]
  otherLanes: BoardLane[]
  archivedCount: number
  archiveLinked: boolean
  group: string
  shortKey: string
  labelsForTicket: (ticketId: number) => BoardLabel[]
  members?: MemberCompletion[]
  readOnly: boolean
}>()

const emit = defineEmits<{
  place: [ticketId: number, laneId: number, index: number]
  send: [ticketId: number, laneId: number]
  open: [ticket: BoardTicket]
  openArchive: []
}>()

const {t} = useI18n()

const MENU = '[data-kanban-menu]'

const cards = ref<BoardTicket[]>([])
watch(() => props.tickets, tickets => {
  cards.value = [...tickets]
}, {immediate: true})

function memberNameFor(ticket: BoardTicket): string | undefined {
  if (!ticket.assignee || !props.members) return undefined
  return props.members.find(m => m.memberUid === ticket.assignee?.memberUid)?.name
}

function onDragEnd(event: SortableEvent) {
  const unmoved = event.from === event.to && event.oldIndex === event.newIndex
  if (unmoved) return
  const ticketId = Number(event.item.dataset.ticketId)
  const laneId = Number((event.to as HTMLElement).dataset.laneId)
  emit('place', ticketId, laneId, event.newIndex ?? 0)
  cards.value = [...props.tickets]
}
</script>

<template>
  <section
      class="md:flex-1 md:min-w-[14rem] md:max-w-[24rem] bg-bg-light-accent dark:bg-bg-dark-accent border border-[var(--border)] rounded-lg p-3 border-t-2"
      :style="{ borderTopColor: lane.color ?? 'var(--primary)' }"
      :aria-label="lane.name"
      :data-testid="`kanban-lane-${lane.id}`"
  >
    <div class="flex items-center justify-between mb-3">
      <SubHeader class="text-sm text-[var(--text-muted)] uppercase tracking-wide">{{ lane.name }}</SubHeader>
      <BaseBadge bg-class="bg-[var(--bg)]" class="text-[var(--text-muted)]">{{ tickets.length }}</BaseBadge>
    </div>

    <VueDraggable
        v-model="cards"
        :group="group"
        :disabled="readOnly"
        :animation="150"
        :delay="200"
        :delay-on-touch-only="true"
        :filter="MENU"
        :prevent-on-filter="false"
        ghost-class="opacity-30"
        :data-lane-id="lane.id"
        class="min-h-[3rem] space-y-2"
        @end="onDragEnd"
    >
      <KanbanCard
          v-for="(ticket, index) in cards"
          :key="ticket.id"
          :data-ticket-id="ticket.id"
          :ticket="ticket"
          :short-key="shortKey"
          :labels="labelsForTicket(ticket.id)"
          :member-name="memberNameFor(ticket)"
          :identity="members ? ticket.assignee : undefined"
          :first="index === 0"
          :last="index === cards.length - 1"
          :other-lanes="otherLanes"
          :read-only="readOnly"
          @open="emit('open', ticket)"
          @up="emit('place', ticket.id, lane.id, index - 1)"
          @down="emit('place', ticket.id, lane.id, index + 1)"
          @to-lane="laneId => emit('send', ticket.id, laneId)"
      />
    </VueDraggable>

    <SecondaryButton v-if="archivedCount > 0 && archiveLinked" class="mt-2 w-full text-xs" @click="emit('openArchive')">
      {{ archivedCount }} {{ t('boards.archived') }}
    </SecondaryButton>
    <p v-else-if="archivedCount > 0" class="mt-2 text-xs text-(--text-muted) text-center py-2">
      {{ archivedCount }} {{ t('boards.archived') }}
    </p>

    <p v-if="tickets.length === 0" class="text-xs text-[var(--text-muted)] text-center py-4">
      {{ t('boards.noTickets') }}
    </p>
  </section>
</template>

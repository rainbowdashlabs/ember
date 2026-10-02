/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import TicketTile from './TicketTile.vue'
import type {BoardLabel, BoardLane, MemberIdentity, TicketSummary} from '@/api/generated/schema'

/**
 * A card on the kanban board with the menu that moves it.
 *
 * <p>The menu is the way to move a card without dragging it: from the keyboard, with a screen reader, or
 * on a phone where a long press is not what the reader has in mind. It moves the card one place up or
 * down in its lane, or to the end of another lane. A board the reader may only look at shows its cards
 * without the menu.
 */
const props = defineProps<{
  ticket: TicketSummary
  shortKey: string
  labels: BoardLabel[]
  memberName?: string
  identity?: MemberIdentity | null
  first: boolean
  last: boolean
  otherLanes: BoardLane[]
  readOnly: boolean
}>()

const emit = defineEmits<{
  open: [ticket: TicketSummary]
  up: []
  down: []
  toLane: [laneId: number]
}>()

const {t} = useI18n()

const ticketKey = computed(() => `${props.shortKey}-${props.ticket.ticketNumber}`)
</script>

<template>
  <TicketTile
      :ticket="ticket"
      :short-key="shortKey"
      :member-name="memberName"
      :identity="identity"
      :labels="labels"
      :attachment-count="ticket.attachmentCount"
      @click="emit('open', ticket)"
  >
    <template v-if="!readOnly" #actions>
      <ActionsMenu
          :label="t('boards.moveTicket', {ticket: ticketKey})"
          :test-id="`ticket-move-${ticket.id}`"
          class="-mr-1 -mt-1"
          data-kanban-menu
      >
        <DropdownMenuItem :icon="['fas', 'arrow-up']" :disabled="first" @click="emit('up')">
          {{ t('boards.moveUp') }}
        </DropdownMenuItem>
        <DropdownMenuItem :icon="['fas', 'arrow-down']" :disabled="last" @click="emit('down')">
          {{ t('boards.moveDown') }}
        </DropdownMenuItem>
        <DropdownMenuItem
            v-for="lane in otherLanes"
            :key="lane.id"
            :icon="['fas', 'arrow-right']"
            @click="emit('toLane', lane.id)"
        >
          {{ t('boards.moveToLane', {lane: lane.name}) }}
        </DropdownMenuItem>
      </ActionsMenu>
    </template>
  </TicketTile>
</template>

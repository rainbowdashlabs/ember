/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import type {BoardLabel, BoardTicket} from '@/api/boards'
import {priorityColor, priorityIcon} from '@/util/ticketPriority'
import BoardLabelBadge from './BoardLabelBadge.vue'
import type {TicketTableApi} from './useTicketTable'

/**
 * The tickets of a board as a table whose rows open the ticket. The priority reads as its icon and
 * the assignee with their face, as on the board itself.
 */
const props = withDefaults(defineProps<{
  table: TicketTableApi
  shortKey: string
  labelsOf?: (ticket: BoardTicket) => BoardLabel[]
}>(), {
  labelsOf: () => [],
})

const {t} = useI18n()
const router = useRouter()

function open(ticket: BoardTicket) {
  router.push(`/station/boards/${props.shortKey}/tickets/${ticket.ticketNumber}`)
}
</script>

<template>
  <div class="space-y-2">
    <div class="flex justify-end">
      <TableColumnPicker :table="table"/>
    </div>
    <RecordTable :table="table" clickable row-test-id="ticket-row" test-id="ticket-table" @row-click="open">
      <template #cell-key="{text}">
        <span class="font-mono whitespace-nowrap text-(--text-muted)">{{ text }}</span>
      </template>
      <template #cell-labels="{row}">
        <span class="flex flex-wrap gap-1">
          <BoardLabelBadge v-for="label in labelsOf(row)" :key="label.id" :label="label"/>
        </span>
      </template>
      <template #cell-priority="{row, text}">
        <font-awesome-icon :class="priorityColor(row.priority)" :icon="priorityIcon(row.priority)" :title="text" class="text-xs"/>
      </template>
      <template #cell-assignee="{row, text}">
        <span v-if="row.assignee" class="flex items-center gap-1">
          <UserAvatar :identity="row.assignee" size="sm"/>
          <span class="text-xs whitespace-nowrap">{{ text }}</span>
        </span>
      </template>
      <template #empty>
        <MutedText size="sm" tag="p">{{ t('boards.noTickets') }}</MutedText>
      </template>
    </RecordTable>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref } from 'vue'
import RecordTable from '@/components/table/RecordTable.vue'
import type { WaitingListEntryWithScore } from '@/api/waitingList'
import type { DataTableApi } from '@/composables/useDataTable'
import WaitingSectionActions from './WaitingSectionActions.vue'
import WaitingSectionGuardians from './WaitingSectionGuardians.vue'
import WaitingStatusCell from './WaitingStatusCell.vue'
import { BIRTH_DATE_KEY } from './waitingColumns'

/**
 * The people waiting, one line each, numbered in the order the table shows them. A press on a line
 * opens it up to show who is responsible for them; the first name leads to the entry itself.
 */
const props = defineProps<{
  table: DataTableApi<WaitingListEntryWithScore>
  readonly?: boolean
}>()

const emit = defineEmits<{
  invite: [entryId: number]
  backToWaiting: [entryId: number]
  moveToTesting: [entryId: number]
  navigateToEntry: [entryId: number]
  deleteEntry: [entry: WaitingListEntryWithScore]
}>()

const expandedId = ref<number | null>(null)

const rankOf = computed(() => new Map(props.table.rows.map((item, index) => [item.entry.id, index + 1])))

function toggleExpand(item: WaitingListEntryWithScore) {
  expandedId.value = expandedId.value === item.entry.id ? null : item.entry.id
}

function birthDateClass(item: WaitingListEntryWithScore): string {
  return item.belowJoinAge ? 'whitespace-nowrap text-warning font-medium' : 'whitespace-nowrap text-(--text-muted)'
}
</script>

<template>
  <RecordTable :table="table" clickable plain test-id="waiting-table" @row-click="toggleExpand">
    <template #lead-head>#</template>
    <template #lead="{row}">
      <span class="text-(--text-muted)">{{ rankOf.get(row.entry.id) }}</span>
    </template>
    <template #cell-firstname="{row, text}">
      <span class="text-primary hover:underline cursor-pointer" role="link" tabindex="0"
            @click.stop="emit('navigateToEntry', row.entry.id)" @keydown.enter="emit('navigateToEntry', row.entry.id)">
        {{ text }}
      </span>
    </template>
    <template #[`cell-${BIRTH_DATE_KEY}`]="{row, text}">
      <span v-if="text" :class="birthDateClass(row)">
        {{ text }}
        <span v-if="row.age != null" class="text-xs">({{ row.age }})</span>
      </span>
    </template>
    <template #cell-status="{row}">
      <WaitingStatusCell :item="row" />
    </template>
    <template #cell-score="{text}">
      <span class="font-mono">{{ text }}</span>
    </template>
    <template v-if="!readonly" #actions="{row}">
      <WaitingSectionActions
        :item="row"
        @invite="(id) => emit('invite', id)"
        @back-to-waiting="(id) => emit('backToWaiting', id)"
        @move-to-testing="(id) => emit('moveToTesting', id)"
        @navigate-to-entry="(id) => emit('navigateToEntry', id)"
        @delete-entry="(entry) => emit('deleteEntry', entry)"
      />
    </template>
    <template #after-row="{row, span}">
      <tr v-if="expandedId === row.entry.id">
        <td :colspan="span" class="px-4 py-3 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
          <WaitingSectionGuardians :item="row" />
        </td>
      </tr>
    </template>
    <template #card-extra="{row}">
      <div v-if="expandedId === row.entry.id" class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-2">
        <WaitingSectionGuardians :item="row" layout="stack" />
      </div>
    </template>
  </RecordTable>
</template>

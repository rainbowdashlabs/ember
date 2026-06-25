/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TRow from '@/components/table/TRow.vue'
import WaitingListStatusBadge from '@/components/badge/WaitingListStatusBadge.vue'
import WaitingSectionActions from './WaitingSectionActions.vue'
import WaitingSectionGuardians from './WaitingSectionGuardians.vue'
import type { WaitingListEntryWithScore, WaitingListField } from '@/api/types'

const props = defineProps<{
  item: WaitingListEntryWithScore
  index: number
  visibleFields: WaitingListField[]
  expanded: boolean
  readonly?: boolean
}>()

const emit = defineEmits<{
  toggleExpand: [entryId: number]
  invite: [entryId: number]
  moveToTesting: [entryId: number]
  navigateToEntry: [entryId: number]
  deleteEntry: [entry: WaitingListEntryWithScore]
}>()

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: '2-digit' })
}

function getEntryFieldValue(item: WaitingListEntryWithScore, fieldId: number): string {
  const v = item.values.find(v => v.fieldId === fieldId)?.value
  return v == null ? '' : String(v)
}
</script>

<template>
  <TRow class="hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30 cursor-pointer" @click="emit('toggleExpand', props.item.entry.id)">
    <td class="py-2 px-2 text-(--text-muted)">{{ props.index + 1 }}</td>
    <td class="py-2 px-2">
      <span class="text-primary hover:underline cursor-pointer" role="link" tabindex="0" @click.stop="emit('navigateToEntry', props.item.entry.id)" @keydown.enter="emit('navigateToEntry', props.item.entry.id)">
        {{ props.item.entry.firstname }}
      </span>
    </td>
    <td class="py-2 px-2">{{ props.item.entry.lastname }}</td>
    <td class="py-2 px-2 text-(--text-muted) whitespace-nowrap">{{ formatDate(props.item.entry.createdAt) }}</td>
    <td v-for="vf in props.visibleFields" :key="vf.id" class="py-2 px-2 text-(--text-muted)">{{ getEntryFieldValue(props.item, vf.id) || '–' }}</td>
    <td class="py-2 px-2">
      <WaitingListStatusBadge :status="props.item.entry.status" />
    </td>
    <td class="py-2 px-2 text-right font-mono">{{ props.item.score }}</td>
    <td v-if="!props.readonly" class="py-2 px-2">
      <WaitingSectionActions
        :item="props.item"
        @invite="(id) => emit('invite', id)"
        @move-to-testing="(id) => emit('moveToTesting', id)"
        @navigate-to-entry="(id) => emit('navigateToEntry', id)"
        @delete-entry="(entry) => emit('deleteEntry', entry)"
      />
    </td>
  </TRow>
  <tr v-if="props.expanded">
    <td :colspan="6 + props.visibleFields.length + (props.readonly ? 0 : 1)" class="px-4 py-3 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
      <WaitingSectionGuardians :item="props.item" />
    </td>
  </tr>
</template>

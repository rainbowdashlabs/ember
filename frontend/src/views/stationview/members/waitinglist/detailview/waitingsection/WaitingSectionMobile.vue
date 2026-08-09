/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import WaitingSectionMobileCard from './WaitingSectionMobileCard.vue'
import type { WaitingListEntryWithScore } from '@/api/waitingList'

const props = defineProps<{
  entries: WaitingListEntryWithScore[]
  expandedId: number | null
  readonly?: boolean
}>()

const emit = defineEmits<{
  toggleExpand: [entryId: number]
  invite: [entryId: number]
  moveToTesting: [entryId: number]
  navigateToEntry: [entryId: number]
  deleteEntry: [entry: WaitingListEntryWithScore]
}>()
</script>

<template>
  <div class="space-y-3">
    <WaitingSectionMobileCard
      v-for="(item, index) in props.entries"
      :key="item.entry.id"
      :item="item"
      :index="index"
      :expanded="props.expandedId === item.entry.id"
      :readonly="props.readonly"
      @toggle-expand="(id) => emit('toggleExpand', id)"
      @invite="(id) => emit('invite', id)"
      @move-to-testing="(id) => emit('moveToTesting', id)"
      @navigate-to-entry="(id) => emit('navigateToEntry', id)"
      @delete-entry="(entry) => emit('deleteEntry', entry)"
    />
  </div>
</template>

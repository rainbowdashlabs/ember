/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import THead from '@/components/table/THead.vue'
import Th from '@/components/table/Th.vue'
import WaitingSectionDesktopRow from './WaitingSectionDesktopRow.vue'
import type { WaitingListEntryWithScore, WaitingListField } from '@/api/types'

const props = defineProps<{
  entries: WaitingListEntryWithScore[]
  visibleFields: WaitingListField[]
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

const { t } = useI18n()
</script>

<template>
  <div class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <THead>
          <Th class="\!px-2">#</Th>
          <Th class="\!px-2">{{ t('waitingList.firstname') }}</Th>
          <Th class="\!px-2">{{ t('waitingList.lastname') }}</Th>
          <Th class="px-2! whitespace-nowrap">{{ t('waitingList.createdAt') }}</Th>
          <Th class="px-2!" v-for="vf in props.visibleFields" :key="vf.id">{{ vf.name }}</Th>
          <Th class="px-2!">{{ t('waitingList.status') }}</Th>
          <Th class="px-2! text-right">{{ t('waitingList.score') }}</Th>
          <Th v-if="!props.readonly" class="px-2! text-right">{{ t('waitingList.actions') }}</Th>
        </THead>
      </thead>
      <tbody>
        <WaitingSectionDesktopRow
          v-for="(item, index) in props.entries"
          :key="item.entry.id"
          :item="item"
          :index="index"
          :visible-fields="props.visibleFields"
          :expanded="props.expandedId === item.entry.id"
          :readonly="props.readonly"
          @toggle-expand="(id) => emit('toggleExpand', id)"
          @invite="(id) => emit('invite', id)"
          @move-to-testing="(id) => emit('moveToTesting', id)"
          @navigate-to-entry="(id) => emit('navigateToEntry', id)"
          @delete-entry="(entry) => emit('deleteEntry', entry)"
        />
      </tbody>
    </table>
  </div>
</template>

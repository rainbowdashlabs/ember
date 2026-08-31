/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import SortableHeader from '@/components/table/SortableHeader.vue'
import WaitingSectionDesktopRow from './WaitingSectionDesktopRow.vue'
import type { WaitingListEntryWithScore, WaitingListField } from '@/api/waitingList'

const props = defineProps<{
  entries: WaitingListEntryWithScore[]
  visibleFields: WaitingListField[]
  birthDateField: WaitingListField | null
  sortKey: string
  direction: 'asc' | 'desc'
  expandedId: number | null
  readonly?: boolean
}>()

const emit = defineEmits<{
  sort: [key: string]
  toggleExpand: [entryId: number]
  invite: [entryId: number]
  backToWaiting: [entryId: number]
  moveToTesting: [entryId: number]
  navigateToEntry: [entryId: number]
  deleteEntry: [entry: WaitingListEntryWithScore]
}>()

const { t } = useI18n()
</script>

<template>
  <DataTable plain>
    <template #head>
      <Th class="\!px-2">#</Th>
      <SortableHeader
          :active-key="props.sortKey" :direction="props.direction" :label="t('waitingList.firstname')"
          class="px-2!" sort-key="firstname" @sort="(k) => emit('sort', String(k))"/>
      <SortableHeader
          :active-key="props.sortKey" :direction="props.direction" :label="t('waitingList.lastname')"
          class="px-2!" sort-key="lastname" @sort="(k) => emit('sort', String(k))"/>
      <SortableHeader
          :active-key="props.sortKey" :direction="props.direction" :label="t('waitingList.createdAt')"
          class="px-2! whitespace-nowrap" sort-key="createdAt" @sort="(k) => emit('sort', String(k))"/>
      <SortableHeader
          v-if="props.birthDateField"
          :active-key="props.sortKey" :direction="props.direction" :label="props.birthDateField.name"
          class="px-2! whitespace-nowrap" sort-key="birthDate" @sort="(k) => emit('sort', String(k))"/>
      <SortableHeader
          v-for="vf in props.visibleFields" :key="vf.id"
          :active-key="props.sortKey" :direction="props.direction" :label="vf.name"
          :sort-key="`field-${vf.id}`" class="px-2!" @sort="(k) => emit('sort', String(k))"/>
      <SortableHeader
          :active-key="props.sortKey" :direction="props.direction" :label="t('waitingList.status')"
          class="px-2!" sort-key="status" @sort="(k) => emit('sort', String(k))"/>
      <SortableHeader
          :active-key="props.sortKey" :direction="props.direction" :label="t('waitingList.score')"
          align="right" class="px-2!" sort-key="score" @sort="(k) => emit('sort', String(k))"/>
      <Th v-if="!props.readonly" class="px-2! text-right">{{ t('waitingList.actions') }}</Th>
    </template>
    <WaitingSectionDesktopRow
      v-for="(item, index) in props.entries"
      :key="item.entry.id"
      :item="item"
      :index="index"
      :birth-date-field="props.birthDateField"
      :visible-fields="props.visibleFields"
      :expanded="props.expandedId === item.entry.id"
      :readonly="props.readonly"
      @toggle-expand="(id) => emit('toggleExpand', id)"
      @invite="(id) => emit('invite', id)"
      @back-to-waiting="(id) => emit('backToWaiting', id)"
      @move-to-testing="(id) => emit('moveToTesting', id)"
      @navigate-to-entry="(id) => emit('navigateToEntry', id)"
      @delete-entry="(entry) => emit('deleteEntry', entry)"
    />
  </DataTable>
</template>

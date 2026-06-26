/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import DragList from '@/components/input/DragList.vue'
import PageRow from './PageRow.vue'
import type {StationPage} from '@/api/pageManage'

interface FlatPageEntry {
  page: StationPage
  depth: number
}

const props = defineProps<{
  flatPages: FlatPageEntry[]
  canEdit: boolean
  canManage: boolean
  landingPageId: number | null
}>()

const emit = defineEmits<{
  (e: 'reorder', fromIndex: number, toIndex: number): void
  (e: 'edit', page: StationPage): void
  (e: 'duplicate', page: StationPage): void
  (e: 'toggle-publish', page: StationPage): void
  (e: 'set-landing', page: StationPage): void
  (e: 'delete', page: StationPage): void
}>()
</script>

<template>
  <DragList
      :items="props.flatPages"
      :key-fn="(entry: FlatPageEntry) => entry.page.id"
      @reorder="(from: number, to: number) => emit('reorder', from, to)"
  >
    <template #default="{item}: {item: FlatPageEntry}">
      <PageRow
          :page="item.page"
          :depth="item.depth"
          :can-edit="props.canEdit"
          :can-manage="props.canManage"
          :landing-page-id="props.landingPageId"
          @edit="(p: StationPage) => emit('edit', p)"
          @duplicate="(p: StationPage) => emit('duplicate', p)"
          @toggle-publish="(p: StationPage) => emit('toggle-publish', p)"
          @set-landing="(p: StationPage) => emit('set-landing', p)"
          @delete="(p: StationPage) => emit('delete', p)"
      />
    </template>
  </DragList>
</template>

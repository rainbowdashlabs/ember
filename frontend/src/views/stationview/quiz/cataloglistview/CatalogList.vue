/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {QuizCatalog, SharedQuizCatalog} from '@/api/generated/schema'
import LocalCatalogRow from './LocalCatalogRow.vue'
import SharedCatalogRow from './SharedCatalogRow.vue'

defineProps<{
  catalogs: QuizCatalog[]
  sharedCatalogs: SharedQuizCatalog[]
  isMobile: boolean
  /** Whether the reader may share the station's catalogs with its partners. */
  canShare: boolean
  isShared: (catalogId: number) => boolean
  isSharing: (catalogId: number) => boolean
}>()

const emit = defineEmits<{
  exportCatalog: [catalog: QuizCatalog]
  confirmDelete: [catalog: QuizCatalog]
  copyShared: [shared: SharedQuizCatalog]
  toggleShare: [catalog: QuizCatalog]
}>()
</script>

<template>
  <div class="space-y-2">
    <LocalCatalogRow
      v-for="catalog in catalogs"
      :key="'local-' + catalog.id"
      :catalog="catalog"
      :is-mobile="isMobile"
      :can-share="canShare"
      :shared="isShared(catalog.id)"
      :sharing="isSharing(catalog.id)"
      @export-catalog="(c) => emit('exportCatalog', c)"
      @confirm-delete="(c) => emit('confirmDelete', c)"
      @toggle-share="(c) => emit('toggleShare', c)"
    />
    <SharedCatalogRow
      v-for="shared in sharedCatalogs"
      :key="'shared-' + shared.stationUid + '-' + shared.id"
      :shared="shared"
      :is-mobile="isMobile"
      @copy="(s) => emit('copyShared', s)"
    />
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type { QuizCatalog } from '@/api/quiz'
import type { SharedCatalogEntry } from '@/api/quiz'
import LocalCatalogRow from './LocalCatalogRow.vue'
import SharedCatalogRow from './SharedCatalogRow.vue'

defineProps<{
  catalogs: QuizCatalog[]
  sharedCatalogs: SharedCatalogEntry[]
  isMobile: boolean
}>()

const emit = defineEmits<{
  navigate: [catalog: QuizCatalog]
  exportCatalog: [catalog: QuizCatalog]
  confirmDelete: [catalog: QuizCatalog]
  copyShared: [catalogId: number]
}>()
</script>

<template>
  <div class="space-y-2">
    <LocalCatalogRow
      v-for="catalog in catalogs"
      :key="'local-' + catalog.id"
      :catalog="catalog"
      :is-mobile="isMobile"
      @navigate="(c) => emit('navigate', c)"
      @export-catalog="(c) => emit('exportCatalog', c)"
      @confirm-delete="(c) => emit('confirmDelete', c)"
    />
    <SharedCatalogRow
      v-for="shared in sharedCatalogs"
      :key="'shared-' + shared.catalog.id"
      :shared="shared"
      :is-mobile="isMobile"
      @navigate="(c) => emit('navigate', c)"
      @copy="(id) => emit('copyShared', id)"
    />
  </div>
</template>

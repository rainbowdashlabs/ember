/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import type {KbFolder} from '@/api/knowledgeBase'

defineProps<{
  currentFolder: KbFolder | null
  /** Tiles or one entry to a line, offered here as it is in the station's own wiki. */
  viewMode: 'grid' | 'list'
}>()

const emit = defineEmits<{
  (e: 'navigate', folderId: number | null): void
  (e: 'toggleViewMode'): void
}>()

const {t} = useI18n()
</script>

<template>
  <nav class="flex items-center gap-1 text-sm flex-wrap mb-4">
    <IconButton
        v-if="currentFolder"
        :icon="['fas', 'chevron-up']"
        :label="t('publicKb.backToBrowse')"
        class="mr-1"
        @click="emit('navigate', currentFolder.parentId)"
    />
    <IconButton
        :icon="['fas', 'house']"
        :label="t('publicKb.backToBrowse')"
        :class="{'!text-[var(--primary)]': !currentFolder}"
        @click="emit('navigate', null)"
    />
    <template v-if="currentFolder">
      <font-awesome-icon :icon="['fas', 'chevron-right']" class="text-xs text-[var(--text-muted)]"/>
      <span class="font-semibold text-[var(--primary)]">
        {{ currentFolder.name }}
      </span>
    </template>

    <div class="flex-1"/>

    <IconButton
        :icon="['fas', viewMode === 'grid' ? 'table-columns' : 'grip-vertical']"
        :label="viewMode === 'grid' ? t('kb.listView') : t('kb.gridView')"
        @click="emit('toggleViewMode')"
    />
  </nav>
</template>

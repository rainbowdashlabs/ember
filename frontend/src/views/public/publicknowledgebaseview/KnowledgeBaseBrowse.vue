/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import MutedText from '@/components/typography/MutedText.vue'
import KnowledgeBaseFolderCard from './KnowledgeBaseFolderCard.vue'
import KnowledgeBaseFileCard from './KnowledgeBaseFileCard.vue'
import type {KbFile, KbFolder} from '@/api/knowledgeBase'

defineProps<{
  loading: boolean
  currentFolder: KbFolder | null
  folders: KbFolder[]
  files: KbFile[]
  stationUid: string
}>()

const emit = defineEmits<{
  (e: 'open-folder', folderId: number): void
  (e: 'open-file', file: KbFile): void
}>()

const {t} = useI18n()
</script>

<template>
  <div>
    <Spinner v-if="loading"/>
    <template v-else>
      <MutedText v-if="currentFolder?.description" tag="p" size="sm">
        {{ currentFolder.description }}
      </MutedText>

      <div
          v-if="folders.length > 0 || files.length > 0"
          class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3"
      >
        <KnowledgeBaseFolderCard
            v-for="folder in folders"
            :key="'folder-' + folder.id"
            :folder="folder"
            :station-uid="stationUid"
            @open="emit('open-folder', $event)"
        />
        <KnowledgeBaseFileCard
            v-for="file in files"
            :key="'file-' + file.id"
            :file="file"
            @open="emit('open-file', $event)"
        />
      </div>

      <p v-else class="text-[var(--text-muted)] text-center py-8">
        {{ t('publicKb.empty') }}
      </p>
    </template>
  </div>
</template>

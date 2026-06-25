/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import * as publicKb from '@/api/publicKb'
import type {KbFolder} from '@/api/knowledgeBase'

defineProps<{
  folder: KbFolder
  stationUid: string
}>()

const emit = defineEmits<{
  (e: 'open', folderId: number): void
}>()
</script>

<template>
  <NeutralContainer
      class="cursor-pointer hover:border-[var(--primary)] transition-colors"
      @click="emit('open', folder.id)"
  >
    <div class="flex flex-col items-center gap-2 p-2 text-center">
      <img
          v-if="folder.iconUrl"
          :src="publicKb.folderIconUrl(stationUid, folder.id)"
          :alt="folder.name"
          class="w-8 h-8 rounded object-cover"
          @error="($event.target as HTMLImageElement).style.display = 'none'"
      />
      <font-awesome-icon v-else :icon="['fas', 'folder']"
                         class="text-2xl text-[var(--accent)]"/>
      <span class="text-sm font-medium truncate w-full">{{ folder.name }}</span>
      <span v-if="folder.description"
            class="text-xs text-[var(--text-muted)] truncate w-full">
        {{ folder.description }}
      </span>
    </div>
  </NeutralContainer>
</template>

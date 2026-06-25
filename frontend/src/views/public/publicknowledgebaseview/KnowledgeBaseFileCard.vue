/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {KbFile} from '@/api/knowledgeBase'
import {fileIcon} from '@/util/kbFileIcon'

defineProps<{
  file: KbFile
}>()

const emit = defineEmits<{
  (e: 'open', file: KbFile): void
}>()
</script>

<template>
  <NeutralContainer
      class="cursor-pointer hover:border-[var(--primary)] transition-colors"
      @click="emit('open', file)"
  >
    <div class="flex flex-col items-center gap-2 p-2 text-center">
      <font-awesome-icon :icon="fileIcon(file)" class="text-2xl text-[var(--primary)]"/>
      <span class="text-sm font-medium truncate w-full">{{ file.name }}</span>
      <span v-if="file.description"
            class="text-xs text-[var(--text-muted)] truncate w-full">
        {{ file.description }}
      </span>
    </div>
  </NeutralContainer>
</template>

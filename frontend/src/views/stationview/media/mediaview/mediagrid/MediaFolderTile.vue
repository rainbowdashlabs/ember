/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseButton from '@/components/button/BaseButton.vue'
import type {StationFileFolder} from '@/api/generated/schema'
import {useFolderDrop} from '@/composables/useMediaDrop'

/** A folder among the files: opened by a press, and a place to drop files into it. */
const props = defineProps<{
  folder: StationFileFolder
}>()

const emit = defineEmits<{
  open: [folderId: number]
}>()

const drop = useFolderDrop(() => props.folder.id)
</script>

<template>
  <BaseButton class="!p-0 !flex-col !rounded-theme !border overflow-hidden hover:!border-primary hover:!bg-primary/5"
              :class="drop.over.value ? '!border-primary !bg-primary/10' : '!border-(--border)'"
              data-testid="media-folder-tile"
              v-on="drop.handlers"
              @click="emit('open', props.folder.id)">
    <div class="aspect-square w-full bg-(--bg-accent) flex items-center justify-center">
      <font-awesome-icon :icon="['fas', drop.over.value ? 'folder-open' : 'folder']" class="text-4xl text-primary"/>
    </div>
    <div class="p-2 text-xs min-w-0 w-full text-left">
      <p class="truncate font-medium" :title="props.folder.name">{{ props.folder.name }}</p>
    </div>
  </BaseButton>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import type {PageFile, PageFileListing} from '@/api/pageManage'

const props = defineProps<{
  entry: PageFileListing
  isImage: (f: PageFile) => boolean
  urlFor: (f: PageFile) => string
  formatSize: (bytes: number) => string
}>()

const emit = defineEmits<{
  (e: 'pick', file: PageFile): void
  (e: 'edit', file: PageFile): void
}>()

const {t} = useI18n()
</script>

<template>
  <div
      class="group relative flex flex-col rounded-theme border overflow-hidden text-left"
      :class="props.entry.inUse ? 'border-(--border)' : 'border-error/50 bg-error/5'"
  >
    <button
        type="button"
        class="flex-1 text-left hover:bg-primary/5 transition-colors"
        @click="emit('pick', props.entry.file)"
    >
      <div class="aspect-square w-full bg-(--bg-accent) flex items-center justify-center overflow-hidden">
        <img
            v-if="props.isImage(props.entry.file)"
            :src="props.urlFor(props.entry.file)"
            :alt="props.entry.file.defaultAltText ?? props.entry.file.fileName"
            loading="lazy"
            class="w-full h-full object-cover"
        />
        <font-awesome-icon v-else :icon="['fas', 'file']" class="text-3xl text-(--text-muted)"/>
      </div>
      <div class="p-2 text-xs space-y-0.5 min-w-0">
        <p class="truncate font-medium" :title="props.entry.file.fileName">{{ props.entry.file.fileName }}</p>
        <p class="text-(--text-muted)">{{ props.formatSize(props.entry.file.fileSize) }}</p>
        <p v-if="props.entry.file.defaultAltText" class="truncate italic" :title="props.entry.file.defaultAltText">
          {{ props.entry.file.defaultAltText }}
        </p>
      </div>
    </button>
    <div class="absolute top-1 right-1 flex items-center gap-1">
      <span v-if="!props.entry.inUse"
            class="text-[10px] uppercase tracking-wider bg-error text-white rounded px-1.5 py-0.5">
        {{ t('stationPages.editor.unusedBadge') }}
      </span>
      <IconButton
          :icon="['fas', 'pen']"
          :label="t('stationPages.editor.editFileMeta')"
          class="bg-(--bg)/90 backdrop-blur-sm rounded-full !p-1 text-xs"
          @click="emit('edit', props.entry.file)"
      />
    </div>
  </div>
</template>

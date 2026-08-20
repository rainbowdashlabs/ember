/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import BaseButton from '@/components/button/BaseButton.vue'
import type {StationFile, StationFileListing} from '@/api/media'

const props = defineProps<{
  entry: StationFileListing
  isImage: (f: StationFile) => boolean
  urlFor: (f: StationFile) => string
  formatSize: (bytes: number) => string
}>()

const emit = defineEmits<{
  (e: 'pick', file: StationFile): void
  (e: 'edit', file: StationFile): void
}>()

const {t} = useI18n()
</script>

<template>
  <div
      data-testid="media-file"
      class="group relative flex flex-col rounded-theme border overflow-hidden text-left"
      :class="props.entry.inUse ? 'border-(--border)' : 'border-error/50 bg-error/5'"
  >
    <BaseButton
        class="flex-1 flex-col !items-stretch !p-0 !rounded-none !font-normal text-left hover:bg-primary/5"
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
    </BaseButton>
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

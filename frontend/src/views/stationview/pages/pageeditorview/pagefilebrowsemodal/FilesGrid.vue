/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FileCard from './FileCard.vue'
import type {PageFile, PageFileListing} from '@/api/pageManage'

const props = defineProps<{
  loading: boolean
  filtered: PageFileListing[]
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
  <div class="flex-1 min-h-0 overflow-y-auto">
    <AsyncSection
        :empty="props.filtered.length === 0"
        :empty-message="t('stationPages.editor.browseFilesEmpty')"
        :loading="props.loading"
        spinner-size="md"
    >
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-3">
        <FileCard
            v-for="e in props.filtered" :key="e.file.id"
            :entry="e"
            :is-image="props.isImage"
            :url-for="props.urlFor"
            :format-size="props.formatSize"
            @pick="(f: PageFile) => emit('pick', f)"
            @edit="(f: PageFile) => emit('edit', f)"
        />
      </div>
    </AsyncSection>
  </div>
</template>

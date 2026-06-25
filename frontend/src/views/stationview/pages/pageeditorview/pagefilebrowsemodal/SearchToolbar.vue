/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'

const search = defineModel<string>('search', {required: true})

const props = defineProps<{
  acceptAttr: string | undefined
  uploading: boolean
  pruning: boolean
  unusedCount: number
  multiple: boolean | undefined
}>()

const emit = defineEmits<{
  (e: 'upload', file: File): void
  (e: 'upload-many', files: File[]): void
  (e: 'prune'): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-col sm:flex-row gap-2">
    <TextInput
        v-model="search"
        :placeholder="t('stationPages.editor.browseFilesSearch')"
        class="flex-1"
    />
    <FileUploadButton
        :accept="props.acceptAttr"
        :disabled="props.uploading"
        :multiple="props.multiple"
        @select="(f: File) => emit('upload', f)"
        @select-many="(fs: File[]) => emit('upload-many', fs)"
    >
      {{ props.uploading ? t('common.loading') : t('stationPages.editor.uploadNewFile') }}
    </FileUploadButton>
    <ErrorButton :disabled="props.pruning || props.unusedCount === 0" @click="emit('prune')">
      <font-awesome-icon :icon="['fas', 'broom']" class="mr-1"/>
      {{ props.pruning ? t('common.loading') : t('stationPages.editor.pruneUnused', {count: props.unusedCount}) }}
    </ErrorButton>
  </div>
</template>

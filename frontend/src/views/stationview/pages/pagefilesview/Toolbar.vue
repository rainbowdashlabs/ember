/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import IconButton from '@/components/button/IconButton.vue'

const search = defineModel<string>('search', {required: true})

const props = defineProps<{
  multiSelect: boolean
  uploading: boolean
  pruning: boolean
  unusedCount: number
}>()

const emit = defineEmits<{
  (e: 'toggle-multi-select'): void
  (e: 'upload', files: File[]): void
  (e: 'prune'): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="flex flex-wrap items-center gap-2">
    <TextInput v-model="search" :placeholder="t('stationPages.editor.browseFilesSearch')"
               class="flex-1 min-w-[200px]"/>
    <div class="flex flex-wrap items-center gap-2">
      <IconButton :icon="['fas', props.multiSelect ? 'square-check' : 'square']"
                  :label="props.multiSelect ? t('stationPages.editor.multiSelectDisable') : t('stationPages.editor.multiSelectEnable')"
                  :class="props.multiSelect ? '!text-primary' : ''"
                  @click="emit('toggle-multi-select')"/>
      <FileUploadButton :disabled="props.uploading" multiple
                        @select="(f: File) => emit('upload', [f])"
                        @select-many="(fs: File[]) => emit('upload', fs)">
        {{ props.uploading ? t('common.loading') : t('stationPages.editor.uploadNewFile') }}
      </FileUploadButton>
      <ErrorButton :disabled="props.pruning || props.unusedCount === 0" @click="emit('prune')">
        <font-awesome-icon :icon="['fas', 'broom']" class="mr-1"/>
        {{ props.pruning ? t('common.loading') : t('stationPages.editor.pruneUnused', {count: props.unusedCount}) }}
      </ErrorButton>
    </div>
  </NeutralContainer>
</template>

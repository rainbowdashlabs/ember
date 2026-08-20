/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ContentBlockEditor from '@/components/content/ContentBlockEditor.vue'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'
import {ContentMode, type ContentModeName} from '@/api/news'

const title = defineModel<string>('title', {required: true})
const contentMarkdown = defineModel<string>('contentMarkdown', {required: true})
const rows = defineModel<RowEditData[]>('rows', {required: true})

defineProps<{
  mode: ContentModeName
  stationUid: string
}>()

const emit = defineEmits<{
  (e: 'enable-blocks'): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('news.titleField') }}</FieldLabel>
      <TextInput v-model="title" :placeholder="t('news.titlePlaceholder')"/>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('news.content') }}</FieldLabel>

      <template v-if="mode === ContentMode.RICH">
        <ContentBlockEditor v-model:rows="rows" :station-uid="stationUid"/>
      </template>

      <template v-else>
        <MarkdownEditor v-model="contentMarkdown" :placeholder="t('news.contentPlaceholder')"/>
        <div class="flex flex-col sm:flex-row sm:items-center gap-2 pt-2">
          <SecondaryButton :icon="['fas', 'table-columns']" @click="emit('enable-blocks')">
            {{ t('news.enableBlocks') }}
          </SecondaryButton>
          <p class="text-xs text-(--text-muted)">{{ t('news.enableBlocksHint') }}</p>
        </div>
      </template>
    </div>
  </NeutralContainer>
</template>

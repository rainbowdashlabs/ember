/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {KbFileType, type KbFile} from '@/api/knowledgeBase'

const props = defineProps<{
  file: KbFile
  contentUrl: string
  youtubeEmbedUrl: string | null
  renderedHtml: string
  textContent: string
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer v-if="props.file.fileType === KbFileType.MARKDOWN">
    <div v-if="props.renderedHtml" class="markdown-content" v-html="props.renderedHtml"/>
    <p v-else class="text-[var(--text-muted)]">{{ t('publicKb.noContent') }}</p>
  </NeutralContainer>

  <NeutralContainer v-else-if="props.file.fileType === KbFileType.TEXT">
    <pre v-if="props.textContent" class="whitespace-pre-wrap text-sm">{{ props.textContent }}</pre>
    <p v-else class="text-[var(--text-muted)]">{{ t('publicKb.noContent') }}</p>
  </NeutralContainer>

  <NeutralContainer v-else-if="props.file.fileType === KbFileType.PDF" class="p-0">
    <iframe :src="props.contentUrl" class="w-full min-h-[80vh] rounded" :title="props.file.name"/>
  </NeutralContainer>

  <NeutralContainer v-else-if="props.file.fileType === KbFileType.IMAGE" class="flex justify-center">
    <img :src="props.contentUrl" :alt="props.file.name" class="max-w-full max-h-[80vh] rounded"/>
  </NeutralContainer>

  <template v-else-if="props.file.fileType === KbFileType.YOUTUBE">
    <NeutralContainer v-if="props.youtubeEmbedUrl" class="p-0">
      <div class="relative pb-[56.25%] h-0">
        <iframe :src="props.youtubeEmbedUrl"
                class="absolute top-0 left-0 w-full h-full rounded"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowfullscreen :title="props.file.name"/>
      </div>
    </NeutralContainer>
    <Alert v-else variant="error">{{ t('publicKb.noContent') }}</Alert>
  </template>

  <NeutralContainer v-else-if="props.file.fileType === KbFileType.LINK" class="space-y-4">
    <div class="flex items-center gap-2">
      <font-awesome-icon :icon="['fas', 'link']" class="text-[var(--secondary)]"/>
      <a :href="props.file.linkUrl ?? ''" target="_blank" rel="noopener noreferrer"
         class="text-[var(--primary)] hover:underline break-all">
        {{ props.file.linkUrl }}
      </a>
    </div>
    <a :href="props.file.linkUrl ?? ''" target="_blank" rel="noopener noreferrer" class="inline-block">
      <PrimaryButton>
        <font-awesome-icon :icon="['fas', 'arrow-right']"/>
        {{ t('publicKb.openLink') }}
      </PrimaryButton>
    </a>
  </NeutralContainer>

  <NeutralContainer v-else-if="props.file.fileType === KbFileType.PRESENTATION && props.file.conversionStatus === 'SUCCESS'" class="p-0">
    <iframe :src="props.contentUrl" class="w-full min-h-[80vh] rounded" :title="props.file.name"/>
  </NeutralContainer>

  <NeutralContainer v-else class="text-center py-8">
    <font-awesome-icon :icon="['fas', 'file']" class="text-4xl text-[var(--text-muted)] mb-4"/>
    <p class="mb-4">{{ props.file.name }}</p>
    <a :href="props.contentUrl" download class="inline-block">
      <PrimaryButton>
        <font-awesome-icon :icon="['fas', 'download']"/>
        {{ t('publicKb.download') }}
      </PrimaryButton>
    </a>
  </NeutralContainer>
</template>

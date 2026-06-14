/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import KbPresentationContent from './KbPresentationContent.vue'
import type {KbFile} from '@/api/knowledgeBase'
import {KbFileType} from '@/api/knowledgeBase'

const props = defineProps<{
  file: KbFile
  editing: boolean
  contentUrl: string
  textContent: string
  renderedHtml: string
  youtubeEmbedUrl: string
  canEdit: boolean
}>()

const editContent = defineModel<string>('editContent', {required: true})

const emit = defineEmits<{
  contentInput: []
  reupload: []
}>()

const {t} = useI18n()

void props // keep tsc happy if a branch isn't reached
</script>

<template>
  <!-- MARKDOWN -->
  <template v-if="file.fileType === KbFileType.MARKDOWN">
    <MarkdownEditor
        v-if="editing"
        v-model="editContent"
        :file-id="file.id"
        @update:model-value="emit('contentInput')"
    />
    <NeutralContainer v-else>
      <div v-if="renderedHtml" class="markdown-content" v-html="renderedHtml"/>
      <p v-else class="text-[var(--text-muted)]">{{ t('kb.noContent') }}</p>
    </NeutralContainer>
  </template>

  <!-- TEXT -->
  <template v-else-if="file.fileType === KbFileType.TEXT">
    <div v-if="editing">
      <TextAreaInput v-model="editContent" class="font-mono min-h-[400px]" @input="emit('contentInput')"/>
    </div>
    <NeutralContainer v-else>
      <pre v-if="textContent" class="whitespace-pre-wrap text-sm">{{ textContent }}</pre>
      <p v-else class="text-[var(--text-muted)]">{{ t('kb.noContent') }}</p>
    </NeutralContainer>
  </template>

  <!-- PDF -->
  <template v-else-if="file.fileType === KbFileType.PDF">
    <NeutralContainer class="p-0">
      <iframe :src="contentUrl" class="w-full min-h-[80vh] rounded" :title="file.name"/>
    </NeutralContainer>
  </template>

  <!-- IMAGE -->
  <template v-else-if="file.fileType === KbFileType.IMAGE">
    <NeutralContainer class="flex justify-center">
      <img :src="contentUrl" :alt="file.name" class="max-w-full max-h-[80vh] rounded"/>
    </NeutralContainer>
  </template>

  <!-- YOUTUBE -->
  <template v-else-if="file.fileType === KbFileType.YOUTUBE">
    <NeutralContainer v-if="youtubeEmbedUrl" class="p-0">
      <div class="relative pb-[56.25%] h-0">
        <iframe
            :src="youtubeEmbedUrl"
            class="absolute top-0 left-0 w-full h-full rounded"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            allowfullscreen
            :title="file.name"
        />
      </div>
    </NeutralContainer>
    <Alert v-else variant="error">{{ t('kb.noContent') }}</Alert>
  </template>

  <!-- LINK -->
  <template v-else-if="file.fileType === KbFileType.LINK">
    <NeutralContainer class="space-y-4">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'link']" class="text-[var(--secondary)]"/>
        <a :href="file.linkUrl ?? ''" target="_blank" rel="noopener noreferrer"
           class="text-[var(--primary)] hover:underline break-all">
          {{ file.linkUrl }}
        </a>
      </div>
      <a :href="file.linkUrl ?? ''" target="_blank" rel="noopener noreferrer" class="inline-block">
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'arrow-right']"/>
          {{ t('kb.openLink') }}
        </PrimaryButton>
      </a>
    </NeutralContainer>
  </template>

  <!-- PRESENTATION -->
  <template v-else-if="file.fileType === KbFileType.PRESENTATION">
    <KbPresentationContent
        :file="file" :content-url="contentUrl" :can-edit="canEdit"
        @reupload="emit('reupload')"
    />
  </template>

  <!-- OTHER -->
  <template v-else>
    <NeutralContainer class="text-center py-8">
      <font-awesome-icon :icon="['fas', 'file']" class="text-4xl text-[var(--text-muted)] mb-4"/>
      <p class="mb-4">{{ file.name }}</p>
      <a :href="contentUrl" download class="inline-block">
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'download']"/>
          {{ t('kb.download') }}
        </PrimaryButton>
      </a>
    </NeutralContainer>
  </template>
</template>

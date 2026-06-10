/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FileInput from '@/components/input/FileInput.vue'
import type {KbFile} from '@/api/knowledgeBase'

const {t} = useI18n()

defineProps<{
  file: KbFile
  contentUrl: string
  canEdit: boolean
}>()

const emit = defineEmits<{
  reupload: [file: File]
}>()
</script>

<template>
  <template v-if="file.conversionStatus === 'SUCCESS'">
    <NeutralContainer class="p-0">
      <iframe :src="contentUrl" class="w-full min-h-[80vh] rounded" :title="file.name"/>
    </NeutralContainer>
    <FileInput v-if="canEdit" accept=".pptx,.ppt,.odp" :label="t('kb.reupload')" class="mt-3" @select="emit('reupload', $event)"/>
  </template>
  <NeutralContainer v-else-if="file.conversionStatus === 'PENDING'" class="text-center py-8">
    <Spinner size="lg"/>
    <p class="mt-4 text-(--text-muted)">{{ t('kb.conversionPending') }}</p>
  </NeutralContainer>
  <NeutralContainer v-else-if="file.conversionStatus === 'FAILED'" class="text-center py-8">
    <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="text-4xl text-(--error) mb-4"/>
    <p class="mb-4">{{ t('kb.conversionFailed') }}</p>
    <FileInput v-if="canEdit" accept=".pptx,.ppt,.odp" :label="t('kb.reupload')" @select="emit('reupload', $event)"/>
  </NeutralContainer>
</template>

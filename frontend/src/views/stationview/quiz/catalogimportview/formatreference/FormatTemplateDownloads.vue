/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {quiz} from '@/api'

const props = defineProps<{
  format: 'csv' | 'json'
}>()

const {t} = useI18n()

const failed = ref(false)

async function download() {
  failed.value = false
  try {
    await quiz.downloadCatalogTemplate(props.format)
  } catch {
    failed.value = true
  }
}
</script>

<template>
  <div class="space-y-1">
    <SecondaryButton :icon="['fas', 'download']" @click="download">
      {{ format === 'csv' ? t('quiz.format.downloadSheet') : t('quiz.format.downloadFile') }}
    </SecondaryButton>
    <MutedText v-if="!failed" class="block text-xs">{{ t('quiz.format.downloadHint') }}</MutedText>
    <MutedText v-else class="block text-xs text-error">{{ t('common.error') }}</MutedText>
  </div>
</template>

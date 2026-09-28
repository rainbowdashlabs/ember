/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ProgressBar from '@/components/feedback/ProgressBar.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The top of one page of a form being filled in: how far along the reader is, and the page's own
 * title and description where it has them.
 *
 * <p>Drawn only for a form with more than one page. A form nobody split into pages fills exactly as
 * it always did.
 */
defineProps<{
  /** The number of the page along the reader's own path. */
  pageNumber: number
  /** How far along the reader is, from above zero up to one. */
  progress: number
  title?: string
  description?: string
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2" data-testid="form-page-intro">
    <div class="flex items-center gap-3">
      <MutedText size="sm" class="shrink-0">{{ t('forms.fill.pageNumber', {number: pageNumber}) }}</MutedText>
      <ProgressBar :value="progress" :max="1"/>
    </div>
    <p v-if="title" class="text-lg font-semibold">{{ title }}</p>
    <MutedText v-if="description" tag="p" class="whitespace-pre-line">{{ description }}</MutedText>
  </div>
</template>

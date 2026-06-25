/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import GenPreviewAnswer from './GenPreviewAnswer.vue'
import type {GenPreview} from './GenerationReviewPanel.vue'

defineProps<{
  preview: GenPreview
  regenerating: boolean
}>()

const emit = defineEmits<{
  toggle: []
  regenerate: []
}>()

const {t} = useI18n()
</script>

<template>
  <div
      class="rounded-lg border transition-all"
      :class="preview.accepted
        ? 'border-success bg-success/5'
        : 'border-bg-light-accent dark:border-bg-dark-accent opacity-50'"
  >
    <div class="flex items-start gap-3 p-3 cursor-pointer" @click="emit('toggle')">
      <font-awesome-icon
          :icon="['fas', preview.accepted ? 'square-check' : 'square']"
          class="text-lg mt-0.5 shrink-0"
          :class="preview.accepted ? 'text-success' : 'text-(--text-muted)'"
      />
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2 flex-wrap">
          <p class="font-medium text-sm">{{ preview.title }}</p>
          <InfoBadge>{{ t(`quiz.questionTypes.${preview.quizQuestionType}`) }}</InfoBadge>
        </div>
        <GenPreviewAnswer :quiz-question-type="preview.quizQuestionType" :config="preview.config"/>
      </div>
    </div>
    <div class="flex justify-end px-3 pb-2" @click.stop>
      <SecondaryButton :disabled="regenerating" @click="emit('regenerate')">
        <Spinner v-if="regenerating" size="sm"/>
        <font-awesome-icon v-else :icon="['fas', 'rotate']"/>
        {{ t('quiz.ai.regenerate') }}
      </SecondaryButton>
    </div>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type { FormResponse, FormQuestionAnalytics } from '@/api/types'

defineProps<{
  responses: FormResponse[]
  currentResponse: FormResponse | null
  currentResponseIndex: number
  questions: FormQuestionAnalytics[]
  loadingResponse: boolean
  formatAnswer: (questionType: string, config: string | Record<string, unknown>, value: string) => string
  getAnswerForQuestion: (questionId: number) => string
}>()

const emit = defineEmits<{
  prev: []
  next: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <span class="text-sm text-(--text-muted)">
        {{ t('forms.analytics.responseOf', { current: currentResponseIndex + 1, total: responses.length }) }}
      </span>
      <div class="flex items-center gap-2">
        <MutedIconButton :icon="['fas', 'chevron-left']" :label="'Previous'" :disabled="currentResponseIndex === 0"
                         @click="emit('prev')" />
        <MutedIconButton :icon="['fas', 'chevron-right']" :label="'Next'" :disabled="currentResponseIndex === responses.length - 1"
                         @click="emit('next')" />
      </div>
    </div>

    <NeutralContainer v-if="currentResponse">
      <div class="space-y-1 mb-4">
        <p v-if="currentResponse.memberIdentity" class="font-medium">
          <MemberName :identity="currentResponse.memberIdentity"/>
        </p>
        <p class="text-xs text-(--text-muted)">{{ new Date(currentResponse.submittedAt).toLocaleString('de-DE') }}</p>
        <p v-if="currentResponse.submittedByName" class="text-xs text-(--text-muted) italic">{{ t('common.submittedBy', { name: currentResponse.submittedByName }) }}</p>
      </div>

      <Spinner v-if="loadingResponse" size="sm" />
      <div v-else class="space-y-4">
        <div v-for="q in questions" :key="q.questionId"
             class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 pb-3 last:border-0">
          <p class="text-xs text-(--text-muted) mb-1">{{ q.title }}</p>
          <p class="text-sm font-medium">
            {{ formatAnswer(q.questionType, q.config, getAnswerForQuestion(q.questionId)) }}
          </p>
        </div>
      </div>
    </NeutralContainer>
  </div>
</template>

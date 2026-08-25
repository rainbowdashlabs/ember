/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {formatDateTime} from '@/util/format'
import {quiz} from '@/api'
import type {QuizQuestionReport} from '@/api/quiz'

defineProps<{
  reports: QuizQuestionReport[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  acknowledged: []
  error: [message: string]
}>()

const {t} = useI18n()

const working = ref<number | null>(null)

async function acknowledge(report: QuizQuestionReport) {
  working.value = report.id
  try {
    await quiz.acknowledgeReport(report.id)
    emit('acknowledged')
  } catch {
    emit('error', t('common.error'))
  } finally {
    working.value = null
  }
}
</script>

<template>
  <InfoContainer v-if="reports.length > 0" class="space-y-2">
    <div class="flex items-center gap-2">
      <font-awesome-icon :icon="['fas', 'flag']" />
      <span class="text-sm font-medium">{{ t('quiz.report.openCount', {count: reports.length}) }}</span>
    </div>

    <div v-for="report in reports" :key="report.id" class="flex items-start justify-between gap-3">
      <div class="min-w-0">
        <p class="text-sm whitespace-pre-line break-words">{{ report.note }}</p>
        <MutedText class="block text-xs">
          {{ report.reporterName || t('quiz.report.unknownReporter') }} &bull; {{ formatDateTime(report.createdAt) }}
        </MutedText>
      </div>
      <SecondaryButton
          v-if="!readonly"
          class="shrink-0 text-xs"
          :icon="['fas', 'check']"
          :disabled="working === report.id"
          @click="acknowledge(report)"
      >
        {{ t('quiz.report.acknowledge') }}
      </SecondaryButton>
    </div>
  </InfoContainer>
</template>

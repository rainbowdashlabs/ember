/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import { QuizTestStatus } from '@/api/types'
import type { QuizTestSummary } from '@/api/types'

const props = defineProps<{
  summaries: QuizTestSummary[]
}>()

const emit = defineEmits<{
  open: [summary: QuizTestSummary]
}>()

const { t } = useI18n()

const withAttempts = computed(() => props.summaries.filter(s => s.attemptCount > 0))
</script>

<template>
  <div class="space-y-2">
    <NeutralContainer
      v-for="summary in withAttempts"
      :key="summary.test.id"
      class="cursor-pointer"
      @click="emit('open', summary)"
    >
      <div class="flex items-center justify-between gap-4">
        <div class="flex-1 space-y-1">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="font-medium">{{ summary.test.title }}</span>
            <SuccessBadge v-if="summary.test.status === QuizTestStatus.ACTIVE">{{ t('quiz.tests.statusActive') }}</SuccessBadge>
            <ErrorBadge v-else-if="summary.test.status === QuizTestStatus.CLOSED">{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
            <SecondaryBadge v-else>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
          </div>
        </div>
        <span class="text-sm font-mono shrink-0">{{ summary.attemptCount }} {{ t('quiz.attemptCount') }}</span>
      </div>
    </NeutralContainer>
    <EmptyState v-if="withAttempts.length === 0">{{ t('quiz.tests.noResults') }}</EmptyState>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import { QuizTestStatus } from '@/api/types'
import type { QuizTest } from '@/api/types'

defineProps<{
  test: QuizTest
  isMobile: boolean
  canConfigure: boolean
  canReadResults: boolean
  submitted: boolean
  attemptCount: number
  attemptStartedAt: string | null
  attemptSubmittedAt: string | null
}>()

const emit = defineEmits<{
  navigate: [test: QuizTest]
  take: [test: QuizTest]
  edit: [test: QuizTest]
  remove: [test: QuizTest]
}>()

const { t } = useI18n()

function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}
</script>

<template>
  <NeutralContainer class="cursor-pointer" @click="emit('navigate', test)">
    <div v-if="isMobile" class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="font-medium">{{ test.title }}</span>
        <MutedIcon v-if="test.restricted" :icon="['fas', 'lock']" class="ml-1" />
        <SuccessBadge v-if="test.status === QuizTestStatus.ACTIVE">{{ t('quiz.tests.statusActive') }}</SuccessBadge>
        <ErrorBadge v-else-if="test.status === QuizTestStatus.CLOSED">{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
        <SecondaryBadge v-else>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
        <InfoBadge v-if="submitted">{{ t('quiz.tests.taken') }}</InfoBadge>
      </div>
      <p v-if="test.description" class="text-xs text-(--text-muted) line-clamp-2">{{ test.description }}</p>
      <div v-if="canReadResults && (test.startAt || test.endAt)" class="flex items-center justify-between text-xs text-(--text-muted)">
        <span v-if="test.startAt">{{ t('quiz.tests.startAt') }}: {{ formatDateTime(test.startAt) }}</span>
        <span>{{ attemptCount }} {{ t('quiz.attemptCount') }}</span>
        <span v-if="test.endAt">{{ t('quiz.tests.endAt') }}: {{ formatDateTime(test.endAt) }}</span>
      </div>
      <div v-else-if="canReadResults" class="text-xs text-(--text-muted)">
        {{ attemptCount }} {{ t('quiz.attemptCount') }}
      </div>
      <div v-if="!canReadResults && submitted" class="flex items-center justify-between text-xs text-(--text-muted)">
        <span v-if="attemptStartedAt">{{ t('quiz.tests.startedAt') }}: {{ formatDateTime(attemptStartedAt) }}</span>
        <span v-if="attemptSubmittedAt">{{ t('quiz.tests.submittedAt') }}: {{ formatDateTime(attemptSubmittedAt) }}</span>
      </div>
      <div class="flex items-center gap-2 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
        <PrimaryButton v-if="test.status === QuizTestStatus.ACTIVE && !canReadResults && !submitted" @click.stop="emit('take', test)">
          {{ t('quiz.tests.takeTest') }}
        </PrimaryButton>
        <template v-if="canConfigure">
          <SecondaryButton @click.stop="emit('edit', test)">{{ t('common.edit') }}</SecondaryButton>
          <ErrorButton @click.stop="emit('remove', test)">{{ t('common.delete') }}</ErrorButton>
        </template>
      </div>
    </div>

    <div v-else class="flex items-center justify-between gap-4">
      <div class="flex-1 space-y-1">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium">{{ test.title }}</span>
          <MutedIcon v-if="test.restricted" :icon="['fas', 'lock']" class="ml-1" />
          <SuccessBadge v-if="test.status === QuizTestStatus.ACTIVE">{{ t('quiz.tests.statusActive') }}</SuccessBadge>
          <ErrorBadge v-else-if="test.status === QuizTestStatus.CLOSED">{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
          <SecondaryBadge v-else>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
          <InfoBadge v-if="submitted">{{ t('quiz.tests.taken') }}</InfoBadge>
        </div>
        <p v-if="test.description" class="text-xs text-(--text-muted) line-clamp-1">{{ test.description }}</p>
      </div>
      <div class="flex items-center gap-4 text-xs text-(--text-muted) shrink-0">
        <span v-if="canReadResults">{{ attemptCount }} {{ t('quiz.attemptCount') }}</span>
        <div v-if="canReadResults && (test.startAt || test.endAt)" class="text-right">
          <div v-if="test.startAt">{{ t('quiz.tests.startAt') }}: {{ formatDateTime(test.startAt) }}</div>
          <div v-if="test.endAt">{{ t('quiz.tests.endAt') }}: {{ formatDateTime(test.endAt) }}</div>
        </div>
        <div v-if="!canReadResults && submitted" class="text-right">
          <div v-if="attemptStartedAt">{{ t('quiz.tests.startedAt') }}: {{ formatDateTime(attemptStartedAt) }}</div>
          <div v-if="attemptSubmittedAt">{{ t('quiz.tests.submittedAt') }}: {{ formatDateTime(attemptSubmittedAt) }}</div>
        </div>
        <div class="flex items-center gap-2" @click.stop>
          <PrimaryButton v-if="test.status === QuizTestStatus.ACTIVE && !canReadResults && !submitted" @click="emit('take', test)">
            {{ t('quiz.tests.takeTest') }}
          </PrimaryButton>
          <template v-if="canConfigure">
            <SecondaryButton @click="emit('edit', test)">{{ t('common.edit') }}</SecondaryButton>
            <ErrorButton @click="emit('remove', test)">{{ t('common.delete') }}</ErrorButton>
          </template>
        </div>
      </div>
    </div>
  </NeutralContainer>
</template>

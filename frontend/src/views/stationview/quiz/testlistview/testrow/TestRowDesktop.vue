/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import TestStatusBadges from './TestStatusBadges.vue'
import TestRowActions from './TestRowActions.vue'
import type {QuizTest} from '@/api/quiz'
import {formatDateTime} from '@/util/format'

/** A test sheet on a wide screen, where its name, its dates and what can be done with it share a line. */
defineProps<{
  test: QuizTest
  canConfigure: boolean
  canReadResults: boolean
  submitted: boolean
  attemptCount: number
  attemptStartedAt: string | null
  attemptSubmittedAt: string | null
}>()

const emit = defineEmits<{
  take: [test: QuizTest]
  edit: [test: QuizTest]
  remove: [test: QuizTest]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center justify-between gap-4">
    <div class="flex-1 space-y-1">
      <div class="flex items-center gap-2 flex-wrap">
        <span class="font-medium">{{ test.title }}</span>
        <TestStatusBadges :test="test" :submitted="submitted"/>
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
      <ButtonRow>
        <TestRowActions
          :test="test"
          :can-configure="canConfigure"
          :can-read-results="canReadResults"
          :submitted="submitted"
          @take="emit('take', $event)"
          @edit="emit('edit', $event)"
          @remove="emit('remove', $event)"
        />
      </ButtonRow>
    </div>
  </div>
</template>

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

/** A test sheet on a phone, where everything it says stands one line under the next. */
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
  <div class="space-y-2">
    <div class="flex items-center justify-between">
      <span class="font-medium">{{ test.title }}</span>
      <TestStatusBadges :test="test" :submitted="submitted"/>
    </div>
    <p v-if="test.description" class="text-xs text-(--text-muted) line-clamp-2">{{ test.description }}</p>
    <div
      v-if="canReadResults && (test.startAt || test.endAt)"
      class="flex items-center justify-between text-xs text-(--text-muted)"
    >
      <span v-if="test.startAt">{{ t('quiz.tests.startAt') }}: {{ formatDateTime(test.startAt) }}</span>
      <span>{{ attemptCount }} {{ t('quiz.attemptCount') }}</span>
      <span v-if="test.endAt">{{ t('quiz.tests.endAt') }}: {{ formatDateTime(test.endAt) }}</span>
    </div>
    <div v-else-if="canReadResults" class="text-xs text-(--text-muted)">
      {{ attemptCount }} {{ t('quiz.attemptCount') }}
    </div>
    <div
      v-if="!canReadResults && submitted"
      class="flex items-center justify-between text-xs text-(--text-muted)"
    >
      <span v-if="attemptStartedAt">{{ t('quiz.tests.startedAt') }}: {{ formatDateTime(attemptStartedAt) }}</span>
      <span v-if="attemptSubmittedAt">{{ t('quiz.tests.submittedAt') }}: {{ formatDateTime(attemptSubmittedAt) }}</span>
    </div>
    <ButtonRow class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
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
</template>

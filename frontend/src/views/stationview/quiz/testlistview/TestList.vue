/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TestRow from './TestRow.vue'
import type { QuizTest } from '@/api/quiz'

defineProps<{
  tests: QuizTest[]
  isMobile: boolean
  canConfigure: boolean
  canReadResults: boolean
  submittedFor: (test: QuizTest) => boolean
  attemptCountFor: (test: QuizTest) => number
  attemptStartedAtFor: (test: QuizTest) => string | null
  attemptSubmittedAtFor: (test: QuizTest) => string | null
}>()

const emit = defineEmits<{
  navigate: [test: QuizTest]
  take: [test: QuizTest]
  edit: [test: QuizTest]
  remove: [test: QuizTest]
}>()
</script>

<template>
  <div class="space-y-2">
    <TestRow
      v-for="test in tests"
      :key="test.id"
      :test="test"
      :is-mobile="isMobile"
      :can-configure="canConfigure"
      :can-read-results="canReadResults"
      :submitted="submittedFor(test)"
      :attempt-count="attemptCountFor(test)"
      :attempt-started-at="attemptStartedAtFor(test)"
      :attempt-submitted-at="attemptSubmittedAtFor(test)"
      @navigate="emit('navigate', $event)"
      @take="emit('take', $event)"
      @edit="emit('edit', $event)"
      @remove="emit('remove', $event)"
    />
  </div>
</template>

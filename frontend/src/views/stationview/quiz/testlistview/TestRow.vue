/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import TestRowMobile from './testrow/TestRowMobile.vue'
import TestRowDesktop from './testrow/TestRowDesktop.vue'
import type {QuizTest} from '@/api/quiz'
import { pressedAControl } from '@/util/rowPress'

const props = defineProps<{
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
  take: [test: QuizTest]
  edit: [test: QuizTest]
  remove: [test: QuizTest]
}>()

/**
 * The test's own page, for a reader who is allowed to see what was written. Anybody else opens the
 * test by sitting it, which is not a page to address: the attempt begins with the press.
 */
const testPage = computed(() => props.canReadResults
    ? {name: 'quiz-test-detail', params: {id: props.test.id}}
    : null)

/**
 * The press that sits the test, on the row that is not a link. A press that landed on one of the
 * row's own buttons belongs to that button, so the row stays out of it.
 */
function sitTest(event: MouseEvent) {
  if (testPage.value || pressedAControl(event)) return
  emit('take', props.test)
}
</script>

<template>
  <RowLink :to="testPage">
    <NeutralContainer data-testid="test-entry" class="cursor-pointer" @click="sitTest">
      <component
        :is="isMobile ? TestRowMobile : TestRowDesktop"
        :test="test"
        :can-configure="canConfigure"
        :can-read-results="canReadResults"
        :submitted="submitted"
        :attempt-count="attemptCount"
        :attempt-started-at="attemptStartedAt"
        :attempt-submitted-at="attemptSubmittedAt"
        @take="emit('take', $event)"
        @edit="emit('edit', $event)"
        @remove="emit('remove', $event)"
      />
    </NeutralContainer>
  </RowLink>
</template>

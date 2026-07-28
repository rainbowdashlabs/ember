/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {QuizAttemptStatus} from '@/api/quiz'
import type {QuizTestAttempt} from '@/api/quiz'
import type {StationMember} from '@/api/types'
import {useBreakpoint} from '@/composables/useBreakpoint'
import AttemptListGroup from './testattemptlist/AttemptListGroup.vue'

const props = defineProps<{
  testId: number
  attempts: QuizTestAttempt[]
  members: StationMember[]
}>()

const {t} = useI18n()
const {isMobile} = useBreakpoint()

const ungradedAttempts = computed(() =>
  props.attempts.filter(a => a.status !== QuizAttemptStatus.GRADED),
)
const gradedAttempts = computed(() =>
  props.attempts.filter(a => a.status === QuizAttemptStatus.GRADED),
)
</script>

<template>
  <div class="space-y-6">
    <SubHeader>{{ t('quiz.attempt.title') }} ({{ attempts.length }})</SubHeader>

    <EmptyState compact v-if="attempts.length === 0">{{ t('quiz.attempt.noAttempts') }}</EmptyState>

    <AttemptListGroup
      v-if="ungradedAttempts.length > 0"
      :title="`${t('quiz.attempt.groupUngraded')} (${ungradedAttempts.length})`"
      :test-id="testId"
      :attempts="ungradedAttempts"
      :members="members"
      :graded="false"
      :is-mobile="isMobile"
    />

    <AttemptListGroup
      v-if="gradedAttempts.length > 0"
      :title="`${t('quiz.attempt.groupGraded')} (${gradedAttempts.length})`"
      :test-id="testId"
      :attempts="gradedAttempts"
      :members="members"
      :graded="true"
      :is-mobile="isMobile"
    />
  </div>
</template>

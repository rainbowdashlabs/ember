/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {QuizAttemptStatus} from '@/api/types'
import type {QuizTestAttempt, StationMember} from '@/api/types'
import {useBreakpoint} from '@/composables/useBreakpoint'

const props = defineProps<{
  testId: number
  attempts: QuizTestAttempt[]
  members: StationMember[]
}>()

const {t} = useI18n()
const router = useRouter()
const {isMobile} = useBreakpoint()

function memberName(memberId: number): string {
  const m = props.members.find(m => m.id === memberId)
  return m?.name ?? m?.email ?? `#${memberId}`
}

function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.toLocaleDateString()} ${d.toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'})}`
}

function attemptStatusLabel(status: string): string {
  if (status === QuizAttemptStatus.IN_PROGRESS) return t('quiz.attempt.statusInProgress')
  if (status === QuizAttemptStatus.SUBMITTED) return t('quiz.attempt.statusSubmitted')
  if (status === QuizAttemptStatus.GRADED) return t('quiz.attempt.statusGraded')
  return status
}
</script>

<template>
  <div class="space-y-3">
    <SectionHeader>{{ t('quiz.attempt.title') }} ({{ attempts.length }})</SectionHeader>

    <EmptyState compact v-if="attempts.length === 0">{{ t('quiz.attempt.noAttempts') }}</EmptyState>

    <!-- Mobile cards -->
    <template v-if="isMobile">
      <NeutralContainer
        v-for="attempt in attempts"
        :key="attempt.id"
        class="cursor-pointer"
        @click="router.push({name: 'quiz-test-evaluate', params: {id: testId, attemptId: attempt.id}})"
      >
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="font-medium text-sm">{{ memberName(attempt.memberId) }}</span>
            <SuccessBadge v-if="attempt.status === QuizAttemptStatus.GRADED">{{ attemptStatusLabel(attempt.status) }}</SuccessBadge>
            <InfoBadge v-else-if="attempt.status === QuizAttemptStatus.SUBMITTED">{{ attemptStatusLabel(attempt.status) }}</InfoBadge>
            <SecondaryBadge v-else>{{ attemptStatusLabel(attempt.status) }}</SecondaryBadge>
          </div>
          <div class="flex items-center justify-between text-xs text-(--text-muted)">
            <span>{{ formatDateTime(attempt.startedAt) }}</span>
            <span v-if="attempt.status === QuizAttemptStatus.GRADED">
              {{ attempt.totalPoints }}/{{ attempt.maxPoints }} {{ t('quiz.attempt.points') }}
            </span>
          </div>
        </div>
      </NeutralContainer>
    </template>

    <!-- Desktop table -->
    <template v-else>
      <NeutralContainer
        v-for="attempt in attempts"
        :key="attempt.id"
        class="cursor-pointer"
        @click="router.push({name: 'quiz-test-evaluate', params: {id: testId, attemptId: attempt.id}})"
      >
        <div class="flex items-center justify-between gap-4">
          <div class="flex items-center gap-3 flex-1">
            <span class="font-medium text-sm">{{ memberName(attempt.memberId) }}</span>
            <SuccessBadge v-if="attempt.status === QuizAttemptStatus.GRADED">{{ attemptStatusLabel(attempt.status) }}</SuccessBadge>
            <InfoBadge v-else-if="attempt.status === QuizAttemptStatus.SUBMITTED">{{ attemptStatusLabel(attempt.status) }}</InfoBadge>
            <SecondaryBadge v-else>{{ attemptStatusLabel(attempt.status) }}</SecondaryBadge>
          </div>
          <div class="flex items-center gap-4 text-xs text-(--text-muted) shrink-0">
            <span>{{ formatDateTime(attempt.startedAt) }}</span>
            <span v-if="attempt.submittedAt">{{ formatDateTime(attempt.submittedAt) }}</span>
            <span v-if="attempt.status === QuizAttemptStatus.GRADED" class="font-medium text-sm text-(--text)">
              {{ attempt.totalPoints }}/{{ attempt.maxPoints }} {{ t('quiz.attempt.points') }}
            </span>
          </div>
        </div>
      </NeutralContainer>
    </template>
  </div>
</template>

/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import { QuizTestStatus } from '@/api/types'
import type { QuizTest, QuizTestSummary, QuizAvailableTest } from '@/api/types'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import { quiz } from '@/api'
import { useSession } from '@/composables/useSession'
import { useBreakpoint } from '@/composables/useBreakpoint'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'

const { t } = useI18n()
const router = useRouter()
const { canManageQuiz, loaded } = useSession()
const { isMobile } = useBreakpoint()

const testSummaries = ref<QuizTestSummary[]>([])
const tests = ref<QuizTest[]>([])
const availableTests = ref<QuizAvailableTest[]>([])
const loading = ref(true)
const error = ref('')

const confirmModalOpen = ref(false)
const confirmModalMessage = ref('')
const confirmModalAction = ref<(() => Promise<void>) | null>(null)

function showConfirm(message: string, action: () => Promise<void>) {
  confirmModalMessage.value = message
  confirmModalAction.value = action
  confirmModalOpen.value = true
}

async function executeConfirm() {
  confirmModalOpen.value = false
  if (confirmModalAction.value) {
    try { await confirmModalAction.value() } catch { /* handled */ }
  }
}

function testClickRoute(test: QuizTest) {
  if (canManageQuiz()) return { name: 'quiz-test-detail', params: { id: test.id } }
  return { name: 'quiz-test-take', params: { id: test.id } }
}

function attemptCount(test: QuizTest): number {
  const summary = testSummaries.value.find(s => s.test.id === test.id)
  return summary?.attemptCount ?? 0
}

function attemptStatus(test: QuizTest): string | null {
  const available = availableTests.value.find(a => a.test.id === test.id)
  return available?.attemptStatus ?? null
}

function isSubmitted(test: QuizTest): boolean {
  const status = attemptStatus(test)
  return status === 'SUBMITTED' || status === 'GRADED'
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    if (canManageQuiz()) {
      testSummaries.value = await quiz.listTests()
      tests.value = testSummaries.value.map(s => s.test)
      availableTests.value = []
    } else {
      testSummaries.value = []
      availableTests.value = await quiz.listAvailableTests()
      tests.value = availableTests.value.map(a => a.test)
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function deleteTest(test: QuizTest) {
  showConfirm(t('quiz.tests.confirmDelete'), async () => {
    await quiz.deleteTest(test.id)
    await loadData()
  })
}

function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

function attemptStartedAt(test: QuizTest): string | null {
  const available = availableTests.value.find(a => a.test.id === test.id)
  return available?.startedAt ?? null
}

function attemptSubmittedAt(test: QuizTest): string | null {
  const available = availableTests.value.find(a => a.test.id === test.id)
  return available?.submittedAt ?? null
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <div class="flex items-center justify-between">
          <SectionHeader>{{ t('quiz.tests.title') }}</SectionHeader>
          <PrimaryButton :icon="['fas', 'plus']" v-if="canManageQuiz()" @click="router.push({ name: 'quiz-test-create' })">
            {{ t('quiz.tests.create') }}
          </PrimaryButton>
        </div>

        <EmptyState v-if="tests.length === 0">{{ t('quiz.tests.noTests') }}</EmptyState>

        <!-- Mobile card layout -->
        <div v-if="isMobile" class="space-y-2">
          <NeutralContainer
            v-for="test in tests"
            :key="test.id"
            class="cursor-pointer"
            @click="router.push(testClickRoute(test))"
          >
            <div class="space-y-2">
              <div class="flex items-center justify-between">
                <span class="font-medium">{{ test.title }}</span>
                <MutedIcon v-if="test.restricted" :icon="['fas', 'lock']" class="ml-1"/>
                <SuccessBadge v-if="test.status === QuizTestStatus.ACTIVE">{{ t('quiz.tests.statusActive') }}</SuccessBadge>
                <ErrorBadge v-else-if="test.status === QuizTestStatus.CLOSED">{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
                <SecondaryBadge v-else>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
                <InfoBadge v-if="isSubmitted(test)">{{ t('quiz.tests.taken') }}</InfoBadge>
              </div>
              <p v-if="test.description" class="text-xs text-(--text-muted) line-clamp-2">{{ test.description }}</p>
              <div v-if="canManageQuiz() && (test.startAt || test.endAt)" class="flex items-center justify-between text-xs text-(--text-muted)">
                <span v-if="test.startAt">{{ t('quiz.tests.startAt') }}: {{ formatDateTime(test.startAt) }}</span>
                <span>{{ attemptCount(test) }} {{ t('quiz.attemptCount') }}</span>
                <span v-if="test.endAt">{{ t('quiz.tests.endAt') }}: {{ formatDateTime(test.endAt) }}</span>
              </div>
              <div v-else-if="canManageQuiz()" class="text-xs text-(--text-muted)">
                {{ attemptCount(test) }} {{ t('quiz.attemptCount') }}
              </div>
              <div v-if="!canManageQuiz() && isSubmitted(test)" class="flex items-center justify-between text-xs text-(--text-muted)">
                <span v-if="attemptStartedAt(test)">{{ t('quiz.tests.startedAt') }}: {{ formatDateTime(attemptStartedAt(test)) }}</span>
                <span v-if="attemptSubmittedAt(test)">{{ t('quiz.tests.submittedAt') }}: {{ formatDateTime(attemptSubmittedAt(test)) }}</span>
              </div>
              <div class="flex items-center gap-2 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
                <PrimaryButton v-if="test.status === QuizTestStatus.ACTIVE && !canManageQuiz() && !isSubmitted(test)" @click.stop="router.push({ name: 'quiz-test-take', params: { id: test.id } })">
                  {{ t('quiz.tests.takeTest') }}
                </PrimaryButton>
                <template v-if="canManageQuiz()">
                  <SecondaryButton @click.stop="router.push({ name: 'quiz-test-edit', params: { id: test.id } })">
                    {{ t('common.edit') }}
                  </SecondaryButton>
                  <ErrorButton @click.stop="deleteTest(test)">
                    {{ t('common.delete') }}
                  </ErrorButton>
                </template>
              </div>
            </div>
          </NeutralContainer>
        </div>

        <!-- Desktop table layout -->
        <div v-else class="space-y-2">
          <NeutralContainer
            v-for="test in tests"
            :key="test.id"
            class="cursor-pointer"
            @click="router.push(testClickRoute(test))"
          >
            <div class="flex items-center justify-between gap-4">
              <div class="flex-1 space-y-1">
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="font-medium">{{ test.title }}</span>
                  <MutedIcon v-if="test.restricted" :icon="['fas', 'lock']" class="ml-1"/>
                  <SuccessBadge v-if="test.status === QuizTestStatus.ACTIVE">{{ t('quiz.tests.statusActive') }}</SuccessBadge>
                  <ErrorBadge v-else-if="test.status === QuizTestStatus.CLOSED">{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
                  <SecondaryBadge v-else>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
                  <InfoBadge v-if="isSubmitted(test)">{{ t('quiz.tests.taken') }}</InfoBadge>
                </div>
                <p v-if="test.description" class="text-xs text-(--text-muted) line-clamp-1">{{ test.description }}</p>
              </div>
              <div class="flex items-center gap-4 text-xs text-(--text-muted) shrink-0">
                <span v-if="canManageQuiz()">{{ attemptCount(test) }} {{ t('quiz.attemptCount') }}</span>
                <div v-if="canManageQuiz() && (test.startAt || test.endAt)" class="text-right">
                  <div v-if="test.startAt">{{ t('quiz.tests.startAt') }}: {{ formatDateTime(test.startAt) }}</div>
                  <div v-if="test.endAt">{{ t('quiz.tests.endAt') }}: {{ formatDateTime(test.endAt) }}</div>
                </div>
                <div v-if="!canManageQuiz() && isSubmitted(test)" class="text-right">
                  <div v-if="attemptStartedAt(test)">{{ t('quiz.tests.startedAt') }}: {{ formatDateTime(attemptStartedAt(test)) }}</div>
                  <div v-if="attemptSubmittedAt(test)">{{ t('quiz.tests.submittedAt') }}: {{ formatDateTime(attemptSubmittedAt(test)) }}</div>
                </div>
                <div class="flex items-center gap-2" @click.stop>
                  <PrimaryButton v-if="test.status === QuizTestStatus.ACTIVE && !canManageQuiz() && !isSubmitted(test)" @click="router.push({ name: 'quiz-test-take', params: { id: test.id } })">
                    {{ t('quiz.tests.takeTest') }}
                  </PrimaryButton>
                  <template v-if="canManageQuiz()">
                    <SecondaryButton @click="router.push({ name: 'quiz-test-edit', params: { id: test.id } })">
                      {{ t('common.edit') }}
                    </SecondaryButton>
                    <ErrorButton @click="deleteTest(test)">
                      {{ t('common.delete') }}
                    </ErrorButton>
                  </template>
                </div>
              </div>
            </div>
          </NeutralContainer>
        </div>
      </template>

      <Modal v-model="confirmModalOpen">
        <div class="space-y-4">
          <p class="text-sm">{{ confirmModalMessage }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="confirmModalOpen = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton @click="executeConfirm">{{ t('common.confirm') }}</ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
